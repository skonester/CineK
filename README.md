# CineK

<img src="icon.png" alt="CineK" width="128">

CineK is a media player for your own videos, music and live TV. It comes as two apps with the same name, logo and gold-on-charcoal look:

- **CineK for Windows** is a desktop player built with Compose Desktop and VLC. It plays almost any file or stream and keeps a library of your videos and music.
- **CineK for Android** is a single APK for **phones and Android TV**. It builds a movie-and-show library from the videos on the device and plays them with Media3. It also opens individual files and streams, and plays live TV.

Each push to `main` publishes both as a GitHub pre-release; see [Automated builds](#automated-builds).

## What the Windows app does

- **Plays files and streams.** Open files or whole folders, drop them on the window, or use Windows **Open with**. Setup can make CineK the default player for MP4, MKV, AVI, MP3, FLAC and 18 other formats. **Open stream** plays a URL. The bundled VLC handles almost any format, so nothing else needs installing.
- **Keeps a library.** **Library**, **Videos**, **Music** and **Folders** pages show everything you've added, with search, sorting and a grid or list view. Home shows a featured item and a **Continue Watching** shelf. CineK remembers where you stopped and resumes there.
- **Organizes.** **Favorites**, **Recently played**, and **Playlists** that import and export M3U files, plus an **Up next** queue you can reorder.
- **Live Channels.** Watch free Pluto TV, Plex, Samsung TV Plus and Roku channels, or your own M3U playlists, by category with search; see [Live Channels](#live-channels).
- **Playback controls.**
  - **Audio and subtitles:** audio and subtitle track selection, loading subtitle files, audio and subtitle delay, and choice of audio output device.
  - **Video:** playback speed, aspect ratio, chapters, frame-by-frame stepping and snapshots.
  - **Playback:** a sleep timer, shuffle and repeat.
- **Fullscreen.** The controls hide when the mouse is idle and come back when you move it. Keyboard shortcuts cover play and pause, seeking, volume, fullscreen, next and previous, and more; they're listed in Settings.
- **Starts quickly.** The CineK logo shows as a splash screen while Java and VLC load.

## What the Android app does

The same APK installs on Android phones and on Android TV (Android 8.1, API 27, and newer). It appears in both the phone app drawer and the Android TV home screen.

- **Builds a video library.** On first launch CineK asks to see the device's videos and music, and links to Android settings if access was refused or limited. It then scans Android's media library, plus any folders you add in Settings, such as a USB drive on a TV.
  - Videos are sorted into **Movies** and **Shows**, with episodes grouped by season.
  - With a TMDB API key, CineK adds posters, backdrops and descriptions.
  - **Home** shows a spotlight, **Continue Watching** and suggestions. **My List** holds favorites, and **Search** finds titles.
- **Plays them.** The player resumes where you left off. It also has:
  - **Tracks:** audio and subtitle selection, subtitle timing and appearance, and online subtitle search when OpenSubtitles is configured.
  - **Picture:** fit, stretch or zoom.
  - **Formats:** FFmpeg decoders for audio formats the device can't play itself.
- **Opens anything else.** The **Open** page plays a file chosen from the device, a USB drive or cloud storage, or a stream URL such as an HLS `.m3u8` link. Other apps can also send video, audio and stream links to CineK with **Open with**.
- **Live TV.** The same free Pluto TV, Plex, Samsung TV Plus and Roku playlists as the desktop app, or your own M3U playlist, browsed by category with search. Channel up and down (or the player's previous and next buttons) changes channel.
- **Extras.** Color themes, an ambient screensaver after five minutes idle, and hiding or marking items as watched from a long-press menu.

**Phones and TVs.** On a TV, everything works with the remote's D-pad, as in [Aperture](https://github.com/XDanfr/Aperture), which the app is built from. On a phone, the same screens respond to touch:
- Tap any button, card or menu item; long-press a poster for its menu.
- Drag sliders.
- In the player, tap to show or hide controls, double-tap the left or right side to skip 10 seconds, and drag the seek bar.

Phones stay in landscape, because the screens are laid out for a TV.

**Current limits.**
- Music access is requested, but songs don't appear in the library yet. Play them with **Open a file**.
- Playback from Open and Live TV stops when you leave the app.
- The home spotlight changes by itself but can't be swiped.
- Release APKs are debug-signed test builds; see [android/README.md](android/README.md).

## Interface direction

The desktop interface takes design cues from [Aperture](https://github.com/XDanfr/Aperture), an Android TV player distributed under GPLv3, the same license used by CineK. The desktop polish pass adapts its layered surface palette, rounded media and action shapes, prominent featured item, Continue Watching shelf, and visible focus treatment for cards and navigation. These are original Compose Desktop implementations. The separate Android app now includes adapted Aperture source; see [Android attribution](android/NOTICE.md).

Continue Watching uses CineK's existing resume rule and only shows library items with a useful saved position. The featured item and shelf scroll with the media collection. At the minimum window height, the shelf takes priority over the featured item so the resume cards remain visible.

The desktop surfaces follow the dark design language of [FletBox](https://github.com/kuko53348/flet-box): a zinc neutral scale, 1 px hairline borders instead of shadows, pill-shaped buttons, chips and fields, 16 dp cards and 200 ms ease transitions, with CineK's gold accent kept. Reusable desktop colors, shapes and motion live in `src/main/kotlin/com/sasami/vlc/theme/Theme.kt`. The Android theme uses the same CineK gold. Aperture's `androidx.tv.material3` controls and Media3 engine remain Android-specific and are not desktop dependencies.

## Live Channels

Open **Live Channels** in the sidebar. Add an M3U playlist URL, import a local `.m3u`/`.m3u8` file, or choose one of the free Pluto TV, Plex, Samsung TV Plus, or Roku playlists by service and region. The free source links use the generated playlists from [app-m3u-generator](https://github.com/BuddyChewChew/app-m3u-generator); they require an internet connection and can be refreshed from the playlist panel. Choose a category, search for a channel, and click a channel to watch it in CineK's player. Sources and downloaded channel lists are saved in `%APPDATA%\CineK\live-channels.json` so channels remain browsable between launches. Stream availability and geographic access depend on each service.

## Video backend

Use the slider icon in the title bar to select **Automatic**, **Direct3D 11**, **Direct3D 9**, or **OpenGL**. Restart CineK after changing the setting so VLC can initialize the selected video output. OpenGL is the default: with Direct3D 11 (which Automatic selects), the window can turn white and stop responding when it covers the whole screen. The menu only shows outputs present in the bundled VLC runtime. Vulkan is unavailable in the bundled VLC 3.0.23 runtime.

## Build and run

Requires JDK 17 or newer with `JAVA_HOME` set. The Gradle wrapper is included. Windows VLC binaries and plugins are in `resources/common/vlc/`; keep this complete runtime together.

```powershell
.\gradlew.bat run
.\gradlew.bat check createDistributable
```

Launch `build/compose/binaries/main/app/CineK/CineK.exe`. The CineK logo appears as a splash screen as soon as it starts: the launcher draws `resources/common/splash.png` (with `@125pct`–`@2x` versions for scaled displays) before Java loads, and the main window stays hidden until the library and VLC are ready, at least 1.5 seconds after launch (`StartupSplash.MINIMUM_MILLIS`). The adjacent `app` and `runtime` folders are required; Java and VLC do not need to be installed separately on the destination computer.

## Windows setup

Install Inno Setup 6, then run:

```powershell
.\scripts\build-setup.ps1
```

If the compiler is installed elsewhere, pass `-IsccPath 'C:\path\to\ISCC.exe'`. The script builds and checks the app, bundles the complete runtime, and compiles `installer/CineK.iss` into `out/setup/CineK-1.0.2-Setup.exe`.

Setup supports all-users or current-user installation, a Start menu shortcut, an optional desktop shortcut, and optional audio/video associations. It registers CineK in Open with and Windows Default Apps, with the CineK icon and quoted commands that open the selected file. The final setup page can open Windows Settings to choose default media types. Existing default-app choices are preserved. Uninstall removes CineK's registrations and installed files, while retaining the user's library and preferences.

Supported associations: MP4, AVI, MKV, MOV, WMV, FLV, WEBM, M4V, MPEG, MPG, 3GP, TS, M2TS, MTS, MP3, AAC, FLAC, M4A, OGG, OPUS, WAV, WMA and AIFF.

The registration follows [Inno Setup's association guidance](https://jrsoftware.org/isfaq.php#assoc) and [Windows Default Programs registration](https://learn.microsoft.com/en-us/windows/win32/shell/default-programs).

## Verification

```powershell
.\gradlew.bat check desktopSmoke startupSmoke
```

`check` covers queue/resume behavior, legacy configuration, playlist round trips, library persistence and association coverage. The optional smoke tasks open the actual Windows UI with isolated settings and local media fixtures. They verify native playback, video corners in the library preview, fullscreen transitions, idle controls, and opening filenames containing spaces and Unicode through the application entry point. Captures and test files go to `build/`.

## Settings and source

Settings, the library, and live channel sources are saved in `%APPDATA%\CineK` (`config.json`, `library.json`, and `live-channels.json`). The internal `com.sasami.vlc` package remains stable. The native loader and VLC surface ownership are in `src/main/kotlin/com/sasami/vlc/player/VlcPlayer.kt`.

The gold and orange accents use RGB values sampled from the supplied CineK artwork; the neutrals are FletBox's zinc scale. `icon.png` and `src/main/resources/icon.png` supply the interface logo; `icon.ico` contains the Windows icon sizes from 16 to 256 pixels.

Generated files in `.gradle/`, `.kotlin/`, `build/` and `out/` are ignored by Git. The packaged player contains its own Java and VLC runtimes.

## Automated builds

One workflow, `.github/workflows/build-and-release.yml`, runs on every push to `main` (or manually). It builds and checks the Windows installer with the Inno Setup script and the Android APK with its unit tests, then publishes both as a GitHub pre-release named `CineK <version> (build <run>)` with the tag `v<version>-build.<run>`: `CineK-<version>-Setup.exe` and `CineK-<version>-Android.apk`. The version comes from `build.gradle.kts`, and the run fails if Android's `versionName` in `android/app/build.gradle.kts` differs, so change both together.

The Android app uses its own Media3 engine; desktop VLC binaries are not part of the APK. Android build instructions and implementation notes are in [android/README.md](android/README.md).
