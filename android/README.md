# CineK for Android (phones and Android TV)

The Android app uses Aperture's TV library and Media3 player under CineK branding. One APK runs on phones and Android TV. What it does is described in the [main README](../README.md#what-the-android-app-does).

On launch CineK asks for access to the device's videos and music, and scans in the background once access is granted. If access was refused, Android stops showing its prompt after the second refusal. CineK then sends you to Android settings, and rescans when you come back. If the library is empty, Home explains why and offers **Allow access**, **Open a file** and **Scan again**. The same controls are on the **Open** page, and in Settings under **Device videos and music**, **Media folders** and **Force Rescan Local Files**.

## How phones and TVs share one app

- **Install:** the manifest has both the `LAUNCHER` and `LEANBACK_LAUNCHER` entries, and declares touchscreen and leanback as optional. Phones and TVs can both install it.
- **Controls:** the screens use `androidx.tv.material3` controls, which only respond to the D-pad select key. `ui/component/touch` wraps `Surface`, `Button`, `OutlinedButton`, `IconButton` and `NavigationDrawerItem` with the same signatures. The wrappers keep the TV focus visuals and add tap and long-press. `touchSeek` adds dragging to sliders and seek bars.
- **Player gestures:** tap toggles the controls, double-tapping a side skips 10 seconds, and touch also dismisses the ambient screensaver.
- **Orientation:** phones are kept in landscape (`sensorLandscape`). TVs ignore that setting.
- **Library-independent playback:** **Open** and **Live TV** play through `MediaQueuePlayer`. It's a small Media3 player with standard touch and remote controls, and treats live channel addresses as HLS (`data/live`, `ui/screen/live`). Live TV playlists are saved as `live-channels.json` in the app's private files.

## Build

Install JDK 21, Android SDK platform 37.0, and build tools 36.0.0. Copy `local.properties.example` to `local.properties` and set `sdk.dir` for your machine. The TMDB and OpenSubtitles keys are optional. A TMDB key enables artwork and metadata matching; without one, local videos still appear in the library with their filename titles. OpenSubtitles account features require its API key.

```powershell
.\gradlew.bat -p android :app:assembleDebug
```

The APK is at `android/app/build/outputs/apk/debug/app-debug.apk`. `:app:testDebugUnitTest` runs the unit tests, including Robolectric UI tests for the touch controls, the Open page and Live TV. CI publishes this debug-signed APK as `CineK-<version>-Android.apk` in each pre-release. Release distribution requires a CineK signing key and a release build.

The package ID remains `com.sasami.cinek.experimental`. An APK can replace an installed one only when both are signed with the same certificate. CI creates a new debug certificate on each run, so installing a newer pre-release means uninstalling the previous one first. That also clears the app's library and settings. The minimum Android version is API 27.

The Android app is based on Aperture under GPLv3; see [NOTICE.md](NOTICE.md) and [APERTURE-LICENSE](APERTURE-LICENSE).
