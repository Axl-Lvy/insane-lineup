"""
One-shot tool: scrape SoundCloud for an avatar of every artist in the lineup,
upload to the Supabase 'artist-images' bucket, and emit a manifest the app
reads at runtime to map display names to {file, url} pairs (storage object
key + the artist's SoundCloud permalink, so taps in the detail dialog open
the artist's page).

Run from repo root:

    python scripts/fetch_artist_images.py                   # full run
    python scripts/fetch_artist_images.py --dry-run         # scrape only, no upload
    python scripts/fetch_artist_images.py --only "Vortek's" # one artist (repeatable)
    python scripts/fetch_artist_images.py --force           # re-upload existing slugs

Credentials are read from ../Axl-Lvy/.env (NEXT_PUBLIC_SUPABASE_URL +
SUPABASE_SERVICE_ROLE_KEY). The service-role key is required because the
storage REST API rejects the anon key for uploads.
"""

from __future__ import annotations

import argparse
import io
import json
import re
import sys
import time
import unicodedata
from pathlib import Path
from typing import Any

import requests
from dotenv import dotenv_values
from PIL import Image

# Windows console defaults to cp1252 — force UTF-8 so the script can print
# both the artist names and any progress arrows without crashing.
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]

REPO_ROOT = Path(__file__).resolve().parents[1]
LINEUP_JSON = REPO_ROOT / "composeApp/src/commonMain/composeResources/files/lineup_fallback.json"
MANIFEST_OUT = REPO_ROOT / "composeApp/src/commonMain/composeResources/files/artist_images.json"
AUDIT_OUT = REPO_ROOT / "scripts/_audit.json"
ENV_FILE = REPO_ROOT.parent / "Axl-Lvy" / ".env"

BUCKET = "artist-images"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

# Free-tier Supabase egress is 5 GB/mo. SoundCloud's t500x500 avatars are ~30–60 kB;
# downscaling to 192×192 quality-75 JPEG brings them to ~10 kB and is more than
# enough for the modest size at which the app renders them.
TARGET_SIZE = 192
JPEG_QUALITY = 75

# Manual overrides — keyed by the exact display name as it appears (post-split)
# in lineup_fallback.json. Lets us pin the SoundCloud permalink for ambiguous
# names ('Eyes', 'Restricted', 'Medusa', etc.) where the top search hit is wrong.
# Value is the bit after soundcloud.com/, e.g. 'vortek-s' for vortek-s.
OVERRIDES: dict[str, str] = {
    "Mandragora": "dj-mandragora",
    "AMR": "djamr",
    "Prost": "prost-one",
    "Melina": "melina_dj",
    "Jejeje": "jejeje",
    "Eyes": "eyesoftechh",
    "Furious": "furiousgabba",
    # Unicorn On Ketamine: the SoundCloud search already finds 'unicorn_on_k'
    # which matches their Instagram (@unicorn.on.k). Both 'unicorn_on_ketamine'
    # and 'unicornonketamine' are 403/default-avatar, so leave the default.
}

# Names with no SoundCloud presence — skipped entirely. Any previously-uploaded
# avatar for these artists is removed from the bucket so the app falls back to
# the letter-on-disk placeholder instead of showing the wrong person.
SKIP: set[str] = {
    "Secret B2B",
    "Le F",
    "Solere",
    "Twist",
    "RV",
}


# ── Slugify ────────────────────────────────────────────────────────────────


def slugify(name: str) -> str:
    """Produce a filesystem-safe, ASCII-only slug.

    The Kotlin side does NOT re-derive slugs — it reads them from the manifest.
    So this rule only needs to be self-consistent, not portable.
    """
    decomposed = unicodedata.normalize("NFKD", name)
    no_marks = "".join(c for c in decomposed if unicodedata.category(c) != "Mn")
    lowered = no_marks.lower()
    replaced = re.sub(r"[^a-z0-9]+", "-", lowered)
    return replaced.strip("-")


# ── Artist extraction ──────────────────────────────────────────────────────

# Match ' b2b ', ' f2f ', ' B2B ', ' F2F ' as delimiters. Surrounding spaces
# are required so things like "Secret B2B" (no second name) don't split.
SPLIT_RE = re.compile(r"\s+(?:b2b|f2f)\s+", re.IGNORECASE)


def split_artists(set_artist: str) -> list[str]:
    return [s.strip() for s in SPLIT_RE.split(set_artist) if s.strip()]


def collect_artists(lineup: dict[str, Any]) -> list[str]:
    seen: dict[str, None] = {}
    for stages in lineup.values():
        for sets in stages.values():
            for entry in sets:
                for artist in split_artists(entry["a"]):
                    seen.setdefault(artist, None)
    return list(seen.keys())


# ── SoundCloud ─────────────────────────────────────────────────────────────


def fetch_sc_client_id(session: requests.Session) -> str:
    """Extract a working client_id from one of SoundCloud's JS bundles.

    The id is embedded as `client_id:"..."` in one of the asset chunks. We
    walk the chunks (newest first) until we find it.
    """
    home = session.get("https://soundcloud.com/", timeout=15).text
    bundles = re.findall(r"https://a-v2\.sndcdn\.com/assets/[a-z0-9._-]+\.js", home)
    # De-dupe while preserving order; later bundles tend to hold the API keys.
    seen: dict[str, None] = {}
    ordered = [b for b in reversed(bundles) if not seen.setdefault(b, None)]
    for url in ordered:
        js = session.get(url, timeout=15).text
        m = re.search(r'client_id:"([A-Za-z0-9_-]+)"', js)
        if m:
            return m.group(1)
    raise RuntimeError("No client_id found in SoundCloud bundles")


def upgrade_avatar_url(url: str) -> str:
    """SoundCloud serves avatars at multiple sizes; bump -large.jpg to t500x500.jpg."""
    return re.sub(r"-large(\.[a-z]+)$", r"-t500x500\1", url)


def is_default_avatar(url: str) -> bool:
    return "default_avatar" in url


def score_candidate(c: dict[str, Any]) -> tuple[int, int]:
    """Higher is better. Verified accounts and follower count both matter."""
    return (1 if c.get("verified") else 0, int(c.get("followers_count") or 0))


def find_avatar(
    session: requests.Session,
    client_id: str,
    name: str,
) -> tuple[dict[str, Any] | None, list[dict[str, Any]]]:
    """Search SoundCloud, return (chosen_avatar_or_None, all_candidates_for_audit)."""
    override = OVERRIDES.get(name)
    if override:
        r = session.get(
            "https://api-v2.soundcloud.com/resolve",
            params={"url": f"https://soundcloud.com/{override}", "client_id": client_id},
            timeout=15,
        )
        if r.status_code != 200:
            return None, []
        user = r.json()
        if is_default_avatar(user.get("avatar_url", "")):
            return None, []
        chosen = {
            "username": user.get("username"),
            "permalink_url": user.get("permalink_url"),
            "avatar_url": upgrade_avatar_url(user["avatar_url"]),
            "source": "override",
        }
        return chosen, [chosen]

    r = session.get(
        "https://api-v2.soundcloud.com/search/users",
        params={"q": name, "client_id": client_id, "limit": 5, "app_locale": "en"},
        timeout=15,
    )
    r.raise_for_status()
    raw = r.json().get("collection", [])
    candidates = [
        {
            "username": u.get("username"),
            "permalink_url": u.get("permalink_url"),
            "followers": u.get("followers_count"),
            "verified": u.get("verified"),
            "city": u.get("city"),
            "country_code": u.get("country_code"),
            "avatar_url": upgrade_avatar_url(u.get("avatar_url", "")),
            "default_avatar": is_default_avatar(u.get("avatar_url", "")),
        }
        for u in raw
    ]
    pickable = [u for u in raw if not is_default_avatar(u.get("avatar_url", ""))]
    if not pickable:
        return None, candidates
    norm = name.casefold()
    exact = [u for u in pickable if (u.get("username") or "").casefold() == norm]
    pool = exact or pickable
    best = max(pool, key=score_candidate)
    chosen = {
        "username": best.get("username"),
        "permalink_url": best.get("permalink_url"),
        "avatar_url": upgrade_avatar_url(best["avatar_url"]),
        "source": "search-exact" if exact else "search-top",
    }
    return chosen, candidates


# ── Supabase storage ───────────────────────────────────────────────────────


class StorageClient:
    def __init__(self, base_url: str, key: str, bucket: str):
        self.base_url = base_url.rstrip("/")
        self.bucket = bucket
        self.headers = {"Authorization": f"Bearer {key}", "apikey": key}

    def list_objects(self) -> set[str]:
        """Return the set of object keys currently in the bucket."""
        keys: set[str] = set()
        offset = 0
        while True:
            r = requests.post(
                f"{self.base_url}/storage/v1/object/list/{self.bucket}",
                headers={**self.headers, "Content-Type": "application/json"},
                json={"prefix": "", "limit": 100, "offset": offset},
                timeout=20,
            )
            r.raise_for_status()
            batch = r.json()
            if not batch:
                break
            for item in batch:
                keys.add(item["name"])
            if len(batch) < 100:
                break
            offset += 100
        return keys

    def ensure_bucket(self) -> None:
        # Supabase storage returns 200 if found, 400 (not 404) if missing.
        # Just attempt create and accept the "already exists" failure mode.
        r = requests.post(
            f"{self.base_url}/storage/v1/bucket",
            headers={**self.headers, "Content-Type": "application/json"},
            json={"id": self.bucket, "name": self.bucket, "public": True},
            timeout=15,
        )
        if r.status_code in (200, 201):
            print(f"Created bucket '{self.bucket}' (public).")
            return
        # 409 / 400 with "already exists" body — fine, bucket already there.
        body = r.text.lower()
        if "already exists" in body or "duplicate" in body:
            return
        r.raise_for_status()

    def delete(self, paths: list[str]) -> None:
        if not paths:
            return
        r = requests.delete(
            f"{self.base_url}/storage/v1/object/{self.bucket}",
            headers={**self.headers, "Content-Type": "application/json"},
            json={"prefixes": paths},
            timeout=20,
        )
        # 200 = deleted, 404 = already gone — both fine.
        if r.status_code not in (200, 404):
            r.raise_for_status()

    def upload(self, path: str, content: bytes, content_type: str) -> None:
        r = requests.post(
            f"{self.base_url}/storage/v1/object/{self.bucket}/{path}",
            headers={
                **self.headers,
                "Content-Type": content_type,
                # 'upsert: true' makes re-runs idempotent.
                "x-upsert": "true",
                "Cache-Control": "public, max-age=2592000",
            },
            data=content,
            timeout=30,
        )
        if r.status_code >= 300:
            raise RuntimeError(f"upload failed for {path}: {r.status_code} {r.text}")


# ── Main ───────────────────────────────────────────────────────────────────


def resize_to_jpeg(raw: bytes) -> bytes:
    """Downscale to TARGET_SIZE × TARGET_SIZE (cover-cropped) and re-encode JPEG.

    Everything ends up as JPEG to keep the manifest simple and the wire size
    predictable; PNG transparency would just become a colored background on
    the dialog anyway.
    """
    img = Image.open(io.BytesIO(raw))
    # Coerce to RGB so PNGs/WebPs with alpha don't break JPEG encoding.
    if img.mode != "RGB":
        img = img.convert("RGB")
    # Center-crop to a square before resizing — SoundCloud avatars are
    # already square but external pictures may not be.
    w, h = img.size
    if w != h:
        side = min(w, h)
        left = (w - side) // 2
        top = (h - side) // 2
        img = img.crop((left, top, left + side, top + side))
    if img.size[0] > TARGET_SIZE:
        img = img.resize((TARGET_SIZE, TARGET_SIZE), Image.LANCZOS)
    out = io.BytesIO()
    img.save(out, format="JPEG", quality=JPEG_QUALITY, optimize=True, progressive=True)
    return out.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="don't upload or write manifest")
    parser.add_argument("--force", action="store_true", help="re-upload even if already present")
    parser.add_argument("--only", action="append", default=[], help="limit to a specific artist (repeatable)")
    args = parser.parse_args()

    env = dotenv_values(ENV_FILE)
    supabase_url = env.get("NEXT_PUBLIC_SUPABASE_URL")
    service_key = env.get("SUPABASE_SERVICE_ROLE_KEY")
    if not supabase_url or not service_key:
        sys.exit(f"missing NEXT_PUBLIC_SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY in {ENV_FILE}")

    lineup = json.loads(LINEUP_JSON.read_text(encoding="utf-8"))
    artists = collect_artists(lineup)
    if args.only:
        wanted = {a.casefold() for a in args.only}
        artists = [a for a in artists if a.casefold() in wanted]
        if not artists:
            sys.exit(f"no matching artists for --only {args.only}")

    print(f"{len(artists)} unique artists.")

    session = requests.Session()
    session.headers["User-Agent"] = USER_AGENT
    client_id = fetch_sc_client_id(session)
    print(f"SoundCloud client_id: {client_id[:6]}…")

    storage = StorageClient(supabase_url, service_key, BUCKET)
    if not args.dry_run:
        storage.ensure_bucket()
        existing = storage.list_objects()
    else:
        existing = set()

    manifest: dict[str, dict[str, str]] = {}
    missing: list[str] = []
    audit: dict[str, dict[str, Any]] = {}
    # When a slug is already in the bucket we skip the SoundCloud lookup, so
    # we need a fallback source for the permalink — load whatever the previous
    # run wrote out. Coerce the older flat-string format into the new shape
    # so a one-time migration doesn't require --force.
    prior_manifest: dict[str, dict[str, str]] = {}
    if MANIFEST_OUT.exists():
        try:
            raw_prior = json.loads(MANIFEST_OUT.read_text(encoding="utf-8"))
            for k, v in raw_prior.items():
                if isinstance(v, str):
                    prior_manifest[k] = {"file": v}
                elif isinstance(v, dict):
                    prior_manifest[k] = v
        except Exception:  # noqa: BLE001
            prior_manifest = {}
    # The audit log carries chosen permalinks from the most recent --force run.
    # Use it as a secondary source so manifest re-generations don't need to
    # re-hit the SoundCloud API for every already-uploaded artist.
    audit_prior: dict[str, str] = {}
    if AUDIT_OUT.exists():
        try:
            for k, v in json.loads(AUDIT_OUT.read_text(encoding="utf-8")).items():
                url = (v.get("chosen") or {}).get("permalink_url")
                if url:
                    audit_prior[k] = url
        except Exception:  # noqa: BLE001
            audit_prior = {}
    for i, name in enumerate(artists, 1):
        if name in SKIP:
            # Drop any stale bucket object so the app's letter fallback kicks
            # in instead of pointing at the wrong person.
            slug = slugify(name)
            stale = [k for k in existing if k.rsplit(".", 1)[0] == slug]
            if stale and not args.dry_run:
                try:
                    storage.delete(stale)
                    existing.difference_update(stale)
                    print(f"[{i:>3}/{len(artists)}] purge     {name} ({', '.join(stale)})")
                    continue
                except Exception as e:  # noqa: BLE001
                    print(f"[{i:>3}/{len(artists)}] purge-err {name}: {e}")
                    continue
            print(f"[{i:>3}/{len(artists)}] skip      {name}")
            continue

        slug = slugify(name)
        if not slug:
            missing.append(name)
            print(f"[{i:>3}/{len(artists)}] no-slug   {name!r}")
            continue

        # Look at what's already uploaded (any extension) — newest match wins.
        already = sorted(k for k in existing if k.rsplit(".", 1)[0] == slug)
        if already and not args.force:
            entry = {"file": already[-1]}
            # Preserve the permalink from earlier runs so re-generations don't
            # cost a fresh SoundCloud search.
            prev_url = (prior_manifest.get(name) or {}).get("url") or audit_prior.get(name)
            if prev_url:
                entry["url"] = prev_url
            manifest[name] = entry
            print(f"[{i:>3}/{len(artists)}] cached    {name} → {already[-1]}")
            continue

        try:
            found, candidates = find_avatar(session, client_id, name)
        except Exception as e:  # noqa: BLE001
            missing.append(name)
            print(f"[{i:>3}/{len(artists)}] err       {name}: {e}")
            continue

        audit[name] = {"chosen": found, "candidates": candidates}

        if not found:
            missing.append(name)
            print(f"[{i:>3}/{len(artists)}] miss      {name}")
            continue

        # All outputs are JPEG post-resize; this keeps the storage layout
        # uniform and the manifest extension-agnostic.
        key = f"{slug}.jpg"
        entry = {"file": key, "url": found["permalink_url"]}

        if args.dry_run:
            manifest[name] = entry
            print(f"[{i:>3}/{len(artists)}] dry       {name} → {found['permalink_url']}")
            time.sleep(0.15)
            continue

        try:
            raw = session.get(found["avatar_url"], timeout=20).content
            img = resize_to_jpeg(raw)
            storage.upload(key, img, "image/jpeg")
        except Exception as e:  # noqa: BLE001
            missing.append(name)
            print(f"[{i:>3}/{len(artists)}] up-fail   {name}: {e}")
            continue

        manifest[name] = entry
        existing.add(key)
        size_kb = len(img) / 1024
        print(f"[{i:>3}/{len(artists)}] ok        {name} → {key}  ({found['source']}, {size_kb:.1f} kB)")
        # SoundCloud's search endpoint will rate-limit hard; pace ourselves.
        time.sleep(0.15)

    print()
    print(f"matched: {len(manifest)}  missing: {len(missing)}")
    if missing:
        print("missing:")
        for m in missing:
            print(f"  - {m}")

    # Audit log lands on disk regardless of --dry-run so the user can review
    # the top-5 candidates per artist and update OVERRIDES if needed. Merge
    # with the prior log so --only runs don't clobber every other entry.
    if audit:
        prior_audit: dict[str, Any] = {}
        if AUDIT_OUT.exists():
            try:
                prior_audit = json.loads(AUDIT_OUT.read_text(encoding="utf-8"))
            except Exception:  # noqa: BLE001
                prior_audit = {}
        prior_audit.update(audit)
        AUDIT_OUT.write_text(
            json.dumps(prior_audit, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"wrote {AUDIT_OUT.relative_to(REPO_ROOT)} ({len(prior_audit)} entries)")

    if not args.dry_run:
        # When --only narrows the run we still want to preserve every other
        # artist's entry, so merge the new results on top of whatever the
        # previous manifest held. Insertion order follows the lineup JSON, so
        # rebuild the dict in lineup order rather than dict-update order.
        # SKIP names are always omitted — they explicitly have no profile.
        final: dict[str, dict[str, str]] = {}
        for name in artists:
            if name in SKIP:
                continue
            if name in manifest:
                final[name] = manifest[name]
            elif name in prior_manifest:
                final[name] = prior_manifest[name]
        # Also keep any prior entries for names not in this run's `artists`
        # list (e.g. older lineup snapshots), at the end — minus SKIP.
        for name, entry in prior_manifest.items():
            if name in SKIP:
                continue
            final.setdefault(name, entry)
        MANIFEST_OUT.write_text(
            json.dumps(final, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"wrote {MANIFEST_OUT.relative_to(REPO_ROOT)} ({len(final)} entries)")

    return 0


if __name__ == "__main__":
    sys.exit(main())
