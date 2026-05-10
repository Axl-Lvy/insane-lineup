# insane-lineup

Tiny Kotlin Multiplatform read-only viewer for the Insane Festival lineup. Targets Android, iOS, and the Web (Compose for Wasm). Connects directly to the Supabase `insane_lineup` table for refreshes, caches the result locally, and works completely offline.

## What it does

- On launch, instantly renders **cached** data (or **bundled** fallback on first run) so there is zero blocking on network.
- In the background, fetches the latest lineup from Supabase and updates the cache.
- Tap the ↻ icon to force-refresh.
- Favorites are kept locally per device (same as the website's localStorage favorites).
- No login. No edit mode.

## One-time setup

### 1. Add an anon SELECT policy to `insane_lineup`

The table currently has RLS enabled with no policies, so the public anon key reads nothing. Add this policy in the Supabase SQL editor:

```sql
create policy "anon read lineup"
  on public.insane_lineup
  for select
  to anon
  using (true);
```

This gives unauthenticated reads only — writes still require the service role key (used by the Next.js admin route).

### 2. Fill in `Config.kt`

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
