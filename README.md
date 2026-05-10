# insane-lineup

Kotlin Multiplatform read-only viewer for the Insane Festival lineup. Targets Android, iOS, and the Web (Compose for Wasm). Connects directly to the Supabase `insane_lineup` table for refreshes, caches the result locally, and works completely offline.

## What it does

- On launch, instantly renders **cached** data (or **bundled** fallback on first run) so there is zero blocking on network.
- In the background, fetches the latest lineup from Supabase and updates the cache.
- Tap the ↻ icon to force-refresh.
- **Favorites sync online** under an anonymous Supabase identity created on first launch.
- **Friends**: tap the people icon to share a 6-character invite code (or QR), redeem one from a friend, and toggle each friend's favorites onto the timeline. See the friends-feature setup below.
- No login. No edit mode.

## One-time setup

### 1. Run the friends-feature migration

Paste `supabase/migrations/001_friends_feature.sql` into the Supabase SQL editor and run it. It is idempotent — safe to re-run after schema tweaks. It does:

- Creates the `insane` schema and **moves** `insane_lineup` into it (`alter table … set schema insane`).
- Creates `profiles`, `favorites`, `friendships` tables (with RLS) in `insane`.
- Creates `rotate_friend_code()` and `redeem_friend_code(text)` RPCs (security definer) in `insane`.
- Adds a trigger that creates a profile row on every new auth user.
- Adds an `authenticated`-role SELECT policy on `insane.insane_lineup` (the existing `anon` policy follows the table).

> **Heads-up for the Axl-Lvy admin route.** Because `insane_lineup` moves out of `public`, the Next.js admin route that writes lineup edits needs its Supabase client switched to the `insane` schema, e.g. `createClient(url, key, { db: { schema: 'insane' } })`. Until that change ships, lineup writes from the website will 404.

### 2. Enable anonymous sign-ins

In the Supabase dashboard: **Authentication → Providers → Anonymous Sign-Ins → Enable**. The app calls `POST /auth/v1/signup` on first launch to mint a session — without this toggle every device gets stuck.

### 3. Expose the `insane` schema to PostgREST

All insane tables live in the `insane` schema. Supabase only proxies schemas that are explicitly listed: **Project Settings → API → Exposed schemas** → add `insane`. Without this every API call from the app returns 404.

### 4. Fill in `Config.kt`

Edit `composeApp/src/commonMain/kotlin/fr/axllvy/insane/Config.kt` with your project URL and anon key (same values as `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` in the website's `.env`):

```kotlin
const val SUPABASE_URL: String = "https://xxxxx.supabase.co"
const val SUPABASE_ANON_KEY: String = "eyJ..."
```

The anon key is safe to ship — it has no special privileges beyond what the policy allows.

## Build & run

| Target | Command |
| --- | --- |
| Android (debug install) | `./gradlew :composeApp:installDebug` |
| Web dev server | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| Web production bundle | `./gradlew :composeApp:wasmJsBrowserDistribution` |
| iOS | open `iosApp/iosApp.xcodeproj` in Xcode and run on a simulator/device |

iOS targets are disabled when building on Windows — that is expected. Use a Mac for iOS.

`NSCameraUsageDescription` is already wired into `iosApp/iosApp/Info.plist` for the QR scanner.

### Web output

After `wasmJsBrowserDistribution`, the static site is in:

```
composeApp/build/dist/wasmJs/productionExecutable/
```

To vendor it into the parent `Axl-Lvy` site (same pattern as `public/tarotmeter/` and `public/memorchess/`):

```bash
rm -rf ../Axl-Lvy/public/insane
cp -R composeApp/build/dist/wasmJs/productionExecutable ../Axl-Lvy/public/insane
```

Then add a rewrite in `Axl-Lvy/next.config.mjs` so `/insane` resolves to `index.html`. The wasm runtime needs `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: require-corp`, just like `/tarotmeter` and `/memorchess`. Add a matching block under `headers()` for `/insane/:path*`.

## Offline behavior

- **Native (Android/iOS)** — first launch shows the bundled JSON; subsequent launches show the most-recent successful fetch from Supabase. A failing refresh leaves the cached data in place.
- **Web** — `sw.js` is a stale-while-revalidate service worker that caches the wasm/js shell on first visit so the app works fully offline thereafter. Supabase API responses are deliberately *not* cached by the worker — the app's own JSON cache (in `localStorage`) handles offline data.

## Project layout

```
composeApp/src/
├── commonMain/         # all UI + data layer + Lineup model
│   ├── kotlin/fr/axllvy/insane/
│   │   ├── App.kt
│   │   ├── Config.kt              # Supabase URL + anon key
│   │   ├── data/                  # repository, cache, Supabase client
│   │   └── ui/                    # Compose timeline UI
│   └── composeResources/files/lineup_fallback.json
├── androidMain/        # MainActivity, Application, SettingsFactory
├── iosMain/            # MainViewController, SettingsFactory
└── wasmJsMain/         # main.kt, index.html, sw.js, manifest.webmanifest

iosApp/                 # Xcode project — opens in Xcode
```
