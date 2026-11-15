# CineK Android architecture

CineK is an Android media player for phones and Android TV. The application and its tests are in `android/app/`. Its Gradle project is in `android/`; the repository root supplies the Gradle wrapper and Android branding tooling.

## Application flow

[`CineKActivity.kt`](android/app/src/main/java/me/xdan/aperture/CineKActivity.kt) is the entry point. The manifest exposes both `LAUNCHER` for phones and `LEANBACK_LAUNCHER` for Android TV, declares touchscreen and leanback as optional, and accepts local media and HTTP(S)/HLS `ACTION_VIEW` intents.

The activity hosts [`NavGraph.kt`](android/app/src/main/java/me/xdan/aperture/ui/navigation/NavGraph.kt). Screens and view models live under `ui/screen`. Reusable wrappers under `ui/component/touch` add tap, long-press, and drag behavior to TV controls while preserving D-pad focus behavior. Phones use landscape orientation.

## Data flow

```text
MediaScanner / MediaRepository
          |
       Room database
          |
     ViewModels
          |
   Compose screens

TMDB + source providers -> DiscoverRepository -> source picker -> Media3
M3U sources ------------> LiveChannelRepository -------------> Media3
```

Hilt provides database, network, repository, scraper, and player dependencies. Room stores local media, playback progress, and remote playback records. DataStore preferences hold user settings. Local playback remains available if remote metadata requests fail.

[`LiveChannels.kt`](android/app/src/main/java/me/xdan/aperture/data/live/LiveChannels.kt) stores M3U playlist sources and parsed channels as `live-channels.json` in the app's private files, keeping saved channels browsable offline. The parser also preserves supported stream request headers.

## Playback

- Local files, content URIs, and HLS use Media3/ExoPlayer.
- Nextlib's FFmpeg extensions provide additional decoding support.
- Source scrapers and host resolvers resolve remote media before playback.
- The live-channel model feeds the Media3 player with channel URLs and request headers.
- OpenSubtitles API and session classes isolate subtitle search and account features.

The player supports D-pad and touch controls, track selection, subtitle appearance and timing, fit/stretch/zoom, resume, and ambient mode after inactivity. `HeaderAwareMediaSourceFactory` carries each stream's headers into its manifest and segment requests and adjusts HLS retry behavior for live channels.

## Configuration

`android/tmdb-client.properties` supplies bundled TMDB application configuration. A nonblank `TMDB_API_KEY` in `android/local.properties` overrides it. `OPENSUBTITLES_API_KEY` is optional. Local properties also specify the machine's Android SDK path and are excluded from Git.

The Android application ID is `com.sasami.cinek.experimental`. The internal `me.xdan.aperture` package preserves the adapted Aperture implementation's origin. License and attribution details are in [android/NOTICE.md](android/NOTICE.md).

## Build and release

[`android/app/build.gradle.kts`](android/app/build.gradle.kts) defines the application version, SDK levels, build types, and dependencies. [`android/gradle/libs.versions.toml`](android/gradle/libs.versions.toml) pins dependency versions. The Gradle wrapper at the repository root launches this project with `-p android`.

```powershell
.\gradlew.bat -p android :app:assembleDebug :app:testDebugUnitTest
```

The [build and release workflow](.github/workflows/build-and-release.yml) reads the Android version, builds and tests the debug APK, uploads it, and publishes a GitHub pre-release. The APK includes its Media3 dependencies and FFmpeg extensions.

Unit tests cover parsing, persistence, remote playback, player settings, and Robolectric/Compose UI behavior. Third-party source availability checks require explicit opt-in through `CINEK_SCRAPER_LIVE=1`.

## Known boundaries

- Music is available through **Open**, but is not currently shown in the library.
- **Open** and **Live TV** playback is scoped to the foreground app lifecycle.
- CI produces debug-signed APKs; a production signing setup is still required for release distribution.
