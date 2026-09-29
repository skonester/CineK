# CineK Android TV

The Android app uses Aperture's TV library and Media3 player under CineK branding. It scans videos in Android's media library and folders selected through the system folder picker. It includes library browsing, search, Continue Watching, favorites, metadata details, playback controls, subtitle and audio track selection, and settings. The layout is designed for Android TV remotes.

CineK opens directly to the library and scans available media in the background. If the library is empty, open **Library settings** to grant optional **Device videos** access or add a **Media folder**. If Android does not show the permission prompt, use **App permissions** in the same section. **Scan again** on Home and **Force Rescan Local Files** in Settings refresh the library without blocking navigation.

## Build

Install JDK 21, Android SDK platform 37.0, and build tools 36.0.0. Copy `local.properties.example` to `local.properties` and set `sdk.dir` for your machine. The TMDB and OpenSubtitles keys are optional. A TMDB key enables artwork and metadata matching; without one, local videos still appear in the library with their filename titles. OpenSubtitles account features require its API key.

```powershell
.\gradlew.bat -p android :app:assembleDebug
```

The APK is at `android/app/build/outputs/apk/debug/app-debug.apk`. CI builds this debug signed APK as an artifact. Release distribution requires a CineK signing key and a release build.

The package ID remains `com.sasami.cinek.experimental`. An APK can upgrade an installed prototype only when both are signed with the same certificate. A CI debug APK may use a different certificate from a locally built APK. The minimum Android version is API 27.

The Android app is based on Aperture under GPLv3; see [NOTICE.md](NOTICE.md) and [APERTURE-LICENSE](APERTURE-LICENSE).
