"""
Cross-check each artist's SoundCloud pick against DuckDuckGo's top result for
"<artist> soundcloud". Google-style ranking weighs link authority, which
catches the cases where SoundCloud's own search picks a popular-but-unrelated
account (e.g. AMR -> Amr Diab).

Writes scripts/_verification.json with three lists:
  matches    -> first DDG soundcloud hit equals our current pick
  conflicts  -> first DDG hit differs; review and decide
  no_result  -> no SoundCloud link in DDG's first page

Usage:
    python scripts/verify_artist_links.py
    python scripts/verify_artist_links.py --only "Restricted" --only "Twist"

Read-only: never touches OVERRIDES, the manifest, or the bucket.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import requests

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]

REPO_ROOT = Path(__file__).resolve().parents[1]
MANIFEST = REPO_ROOT / "composeApp/src/commonMain/composeResources/files/artist_images.json"
OUT = REPO_ROOT / "scripts/_verification.json"

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

# Strip /sets, /tracks, /albums, etc. — only the artist root permalink counts.
NON_PROFILE_SEGMENTS = {"sets", "tracks", "albums", "tags", "search", "stations", "you", "stream"}

LINK_RE = re.compile(r"https?://(?:www\.)?soundcloud\.com/[A-Za-z0-9_\-./]+")


def normalize_permalink(url: str) -> str | None:
    """Return https://soundcloud.com/<user> or None if the URL is a sub-path
    (track / set / tag / etc.) rather than an artist profile."""
    try:
        parts = urlparse(url)
    except ValueError:
        return None
    if parts.netloc.replace("www.", "") != "soundcloud.com":
        return None
    segs = [s for s in parts.path.split("/") if s]
    if not segs:
        return None
    user = segs[0]
    if user in NON_PROFILE_SEGMENTS:
        return None
    # DDG sometimes returns "soundcloud.com/user-1234/tracks/..." — keep root.
    return f"https://soundcloud.com/{user}"


def ddg_first_soundcloud(session: requests.Session, query: str) -> str | None:
    """Return the first SoundCloud artist permalink from DDG's HTML results."""
    r = session.post(
        "https://html.duckduckgo.com/html/",
        data={"q": query},
        timeout=20,
    )
    r.raise_for_status()
    # The HTML endpoint wraps real URLs in DDG's redirector. Pull the literal
    # soundcloud.com matches from the raw body — there are usually plenty.
    for raw in LINK_RE.findall(r.text):
        profile = normalize_permalink(raw)
        if profile:
            return profile
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", action="append", default=[])
    parser.add_argument("--sleep", type=float, default=1.2, help="seconds between DDG hits")
    args = parser.parse_args()

    raw = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if args.only:
        wanted = {a.casefold() for a in args.only}
        names = [n for n in raw if n.casefold() in wanted]
    else:
        names = list(raw.keys())

    session = requests.Session()
    session.headers["User-Agent"] = USER_AGENT

    matches: list[dict[str, Any]] = []
    conflicts: list[dict[str, Any]] = []
    no_result: list[str] = []

    for i, name in enumerate(names, 1):
        current = (raw.get(name) or {}).get("url")
        current_norm = normalize_permalink(current) if current else None
        try:
            top = ddg_first_soundcloud(session, f"{name} soundcloud")
        except Exception as e:  # noqa: BLE001
            print(f"[{i:>3}/{len(names)}] err   {name}: {e}")
            no_result.append(name)
            time.sleep(args.sleep)
            continue

        if top is None:
            print(f"[{i:>3}/{len(names)}] none  {name}")
            no_result.append(name)
        elif current_norm and top.casefold() == current_norm.casefold():
            print(f"[{i:>3}/{len(names)}] ok    {name}")
            matches.append({"name": name, "url": top})
        else:
            print(f"[{i:>3}/{len(names)}] DIFF  {name}: {current_norm} -> {top}")
            conflicts.append({"name": name, "current": current_norm, "ddg_top": top})

        time.sleep(args.sleep)

    OUT.write_text(
        json.dumps(
            {"matches": matches, "conflicts": conflicts, "no_result": no_result},
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print()
    print(f"matches: {len(matches)}  conflicts: {len(conflicts)}  no_result: {len(no_result)}")
    print(f"wrote {OUT.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
