# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

Kotlin Multiplatform + Compose Multiplatform app. Single Gradle module `composeApp` with targets:

- `androidTarget` — APK (`fr.axllvy.insane`).
- `iosX64` / `iosArm64` / `iosSimulatorArm64` — static framework `ComposeApp` consumed by `iosApp/` Xcode project.
- `wasmJs` — browser bundle (`outputModuleName = insane-lineup`).
- `jvm` — **tests only**, no shipping artifact. Hosts live Supabase integration tests that need real `Dispatchers.Default` for supabase-kt's lifecycle hooks (Android unit tests would need Robolectric, wasmJs needs a browser).

Backend: Supabase (`insane` schema). Anon auth (mandatory — every device mints an anonymous session on first launch). Tables: `insane_lineup`, `profiles`, `favorites`, `friendships`. RPCs: `rotate_friend_code()`, `redeem_friend_code(text)`, `favorite_counts()`. Migrations in `supabase/migrations/`.

## Build & run

**Always run Gradle with JDK 21:**

| Task                   | Command                                             |
|------------------------|-----------------------------------------------------|
| Android debug install  | `./gradlew :composeApp:installDebug`                |
| Web dev server         | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| Web production bundle  | `./gradlew :composeApp:wasmJsBrowserDistribution`   |
| iOS                    | open `iosApp/iosApp.xcodeproj` in Xcode             |

Web output: `composeApp/build/dist/wasmJs/productionExecutable/`. Wasm runtime needs `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: require-corp` headers when served.

iOS targets disabled on Windows (expected). `NSCameraUsageDescription` already wired in `iosApp/iosApp/Info.plist` for the QR scanner.

## Tests

| Set         | Command                                                                            | Notes                                                                                                               |
|-------------|------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| commonTest  | `./gradlew :composeApp:testDebugUnitTest`                                          | KMP common sources compiled into Android unit-test variant. This is what CI runs (`.github/workflows/tests.yml`).   |
| jvmTest     | `./gradlew :composeApp:jvmTest`                                                    | **Live integration test against the production Supabase project.** Requires network, fails closed on RLS misconfig. |
| Single test | `./gradlew :composeApp:testDebugUnitTest --tests fr.axllvy.insane.data.LineupTest` |                                                                                                                     |

## Formatting

ktfmt (kotlinLangStyle) is applied to every subproject via the root `build.gradle.kts`. **Run after every dev change:**

```sh
./gradlew ktfmtFormat
```

## Tooling rules

- **Do not invoke `mvn` directly.** Always use the activepivot-tools MCP (global rule).
- This project uses Gradle, not Maven — same principle: drive builds through the `./gradlew` wrapper, never a system Gradle.

## Configuration

`composeApp/src/commonMain/kotlin/fr/axllvy/insane/Config.kt` holds `SUPABASE_URL` and `SUPABASE_ANON_KEY`. Anon key is safe to ship (no privileges beyond RLS policies). For Supabase to work the project must have:

1. Anonymous sign-ins enabled (Authentication → Providers).
2. `insane` listed under Project Settings → API → Exposed schemas.
3. Migrations in `supabase/migrations/` applied.

Without (1) every device hangs on first launch. Without (2) every PostgREST call returns 404.

## Architecture

Offline-first, single-source-of-truth state flow:

```
commonMain/kotlin/fr/axllvy/insane/
├── App.kt                          # Compose entry, wires repos to UI
├── Config.kt                       # Supabase URL + anon key
├── Logging.kt
├── data/
│   ├── LineupRepository.kt         # StateFlow<LineupState?>: Bundled → Cached → Fresh
│   ├── SupabaseLineupClient.kt     # Postgrest fetch of insane.insane_lineup
│   ├── SupabaseFactory.kt          # platform-specific SupabaseClient builder
│   ├── SettingsFactory.kt          # expect/actual multiplatform-settings (cache backing)
│   ├── FavoritesRepository.kt      # synced under anon identity
│   ├── FriendsRepository.kt        # 6-char invite codes, QR redemption
│   ├── ArtistImages.kt
│   ├── SearchIndex.kt
│   ├── Lineup.kt / LineupJson.kt   # domain model + JSON shape
├── notifications/                  # local notification planning + scheduling
└── ui/
    ├── LineupScreen.kt
    ├── DayLabels.kt / Theme.kt
    ├── lineup/                     # timeline, header, search, detail, avatars
    └── friends/                    # sheet, QR view, QR scanner, display-name dialog
```

Platform-specific source sets supply only what `commonMain` cannot express:

- `androidMain` — `MainActivity`, `Application`, Ktor `okhttp` engine, Play Services code scanner (QR).
- `iosMain` — `MainViewController`, Ktor `darwin` engine.
- `wasmJsMain` — `main.kt`, `index.html`, `sw.js` (stale-while-revalidate shell cache; Supabase responses are *deliberately not* cached by the worker — `LineupRepository` owns the data cache via `multiplatform-settings` → `localStorage`).
- `jvmMain` / `jvmTest` — Ktor `okhttp` engine for the integration test only.

### Lineup state machine

`LineupRepository.loadInitial()` resolves the fastest source:

1. `settings[lineup_json_v1]` → `LineupSource.Cached`.
2. Else bundled resource `files/lineup_fallback.json` → `LineupSource.Bundled`.

Then `refresh()` fetches Supabase; on success replaces state with `LineupSource.Fresh` and writes the cache; on failure preserves the existing data and surfaces `RefreshOutcome.Error`/`Offline`. UI never blocks on network.

### Friends feature

Anon Supabase user → `profiles` row (created by trigger) → 6-char `friend_code`. Sharing happens via QR (qrose) or text. Redemption goes through the `redeem_friend_code` security-definer RPC so RLS stays tight. Friend toggles overlay another user's favorites onto the timeline with deterministic per-friend colors (`ui/friends/FriendColors.kt`).

## Repo layout outside composeApp

- `iosApp/` — Xcode project. Open directly.
- `supabase/migrations/` — apply in numeric order via the Supabase SQL editor. `001` moves `insane_lineup` from `public` to `insane`, which **breaks the Axl-Lvy Next.js admin writes** until that site's Supabase client is switched to `{ db: { schema: 'insane' } }`.
- `scripts/` — Python utilities (`fetch_artist_images.py`, `verify_artist_links.py`). Not part of the build.
- `.github/workflows/` — `tests.yml` (JVM unit tests on every PR), `build_ios.yml`, `deploy_wasm.yml`, `check_translations.yml`.
