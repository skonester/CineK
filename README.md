# CineK

<img src="icon.png" alt="CineK" width="128">

CineK is a Windows media player built with Compose Desktop and libVLC/vlcj. It includes a searchable library, playlists, favorites, playback history, live channels, track selection and fullscreen playback with controls that hide when idle.

## Live Channels

Open **Live Channels** in the sidebar. Add an M3U playlist URL, import a local `.m3u`/`.m3u8` file, or choose one of the free Pluto TV, Plex, Samsung TV Plus, or Roku playlists by service and region. The free source links use the generated playlists from [app-m3u-generator](https://github.com/BuddyChewChew/app-m3u-generator); they require an internet connection and can be refreshed from the playlist panel. Choose a category, search for a channel, and click a channel to watch it in CineK's player. Sources and downloaded channel lists are saved in `%APPDATA%\SasamiVLC\live-channels.json` so channels remain browsable between launches. Stream availability and geographic access depend on each service.

## Video backend

Use the slider icon in the title bar to select **Automatic**, **Direct3D 11**, **Direct3D 9**, or **OpenGL**. Restart CineK after changing the setting so VLC can initialize the selected video output. The menu only shows outputs present in the bundled VLC runtime. Vulkan is unavailable in the bundled VLC 3.0.23 runtime.

## Build and run

Requires JDK 17 or newer with `JAVA_HOME` set. The Gradle wrapper is included. Windows VLC binaries and plugins are in `resources/common/vlc/`; keep this complete runtime together.

```powershell
.\gradlew.bat run
.\gradlew.bat check createDistributable
```

Launch `build/compose/binaries/main/app/CineK/CineK.exe`. The adjacent `app` and `runtime` folders are required; Java and VLC do not need to be installed separately on the destination computer.

## Windows setup

Install Inno Setup 6, then run:

```powershell
.\scripts\build-setup.ps1
```

If the compiler is installed elsewhere, pass `-IsccPath 'C:\path\to\ISCC.exe'`. The script builds and checks the app, bundles the complete runtime, and compiles `installer/CineK.iss` into `out/setup/CineK-1.0.0-Setup.exe`.

Setup supports all-users or current-user installation, a Start menu shortcut, an optional desktop shortcut, and optional audio/video associations. It registers CineK in Open with and Windows Default Apps, with the CineK icon and quoted commands that open the selected file. The final setup page can open Windows Settings to choose default media types. Existing default-app choices are preserved. Uninstall removes CineK's registrations and installed files, while retaining the user's library and preferences.

Supported associations: MP4, AVI, MKV, MOV, WMV, FLV, WEBM, M4V, MPEG, MPG, 3GP, TS, M2TS, MTS, MP3, AAC, FLAC, M4A, OGG, OPUS, WAV, WMA and AIFF.

The registration follows [Inno Setup's association guidance](https://jrsoftware.org/isfaq.php#assoc) and [Windows Default Programs registration](https://learn.microsoft.com/en-us/windows/win32/shell/default-programs).

## Verification

```powershell
.\gradlew.bat check desktopSmoke startupSmoke
```

`check` covers queue/resume behavior, legacy configuration, playlist round trips, library persistence and association coverage. The optional smoke tasks open the actual Windows UI with isolated settings and local media fixtures. They verify native playback, video corners in the library preview, fullscreen transitions, idle controls, and opening filenames containing spaces and Unicode through the application entry point. Captures and test files go to `build/`.

## Settings and source

Existing settings remain in `%APPDATA%\SasamiVLC\config.json` and `library.json` so the CineK rename preserves saved data. The internal `com.sasami.vlc` package remains stable. The native loader and VLC surface ownership are in `src/main/kotlin/com/sasami/vlc/player/VlcPlayer.kt`.

The charcoal, gold and orange theme uses RGB values sampled from the supplied CineK artwork. `icon.png` and `src/main/resources/icon.png` supply the interface logo; `icon.ico` contains the Windows icon sizes from 16 to 256 pixels.

Generated files in `.gradle/`, `.kotlin/`, `build/` and `out/` are ignored by Git. The packaged player contains its own Java and VLC runtimes.

## Automated builds

The Windows workflow builds and checks the desktop app, runs the existing Inno Setup script, and uploads `CineK-<version>-Setup.exe` as a workflow artifact. It also publishes the installer to a GitHub Release. You can push a tag matching the version in `build.gradle.kts` (for example, `v1.0.0`), or run the workflow manually from `main`; a manual run creates the version tag if needed. Increase the version before publishing a new release.

The separate Android workflow builds `android/app/build/outputs/apk/debug/app-debug.apk` and uploads it as a workflow artifact. You can run it manually or let it run when the Android project changes on `main`. The APK is a debug build for testing. It is a small native Android prototype that opens a local video or direct HTTPS media URL with Android's built-in player. It does not yet include the Compose Desktop interface, bundled VLC codecs, library, playlists, or live-channel features. Playback support depends on the device's media codecs. To build locally, install Android SDK platform 36 and build tools 36.0.0, then run `./gradlew -p android :app:assembleDebug`.
