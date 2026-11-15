# CineK

<p align="center">
  <img src="icon.png" alt="CineK" width="128">
</p>

<p align="center"><strong>Movies, shows, streams, and live TV on Android phones and Android TV.</strong></p>

<p align="center">
  <a href="https://github.com/skonester/CineK/releases"><img src="https://img.shields.io/badge/Download-Android%20APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Download CineK Android APK"></a>
</p>

<p align="center">
  <a href="https://github.com/skonester/CineK/actions/workflows/build-and-release.yml"><img src="https://github.com/skonester/CineK/actions/workflows/build-and-release.yml/badge.svg" alt="Android build and release"></a>
  <a href="LICENSE.md"><img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="GPL-3.0 license"></a>
  <a href="https://github.com/skonester/CineK/releases"><img src="https://img.shields.io/github/v/release/skonester/CineK?include_prereleases&label=latest%20build" alt="Latest Android build"></a>
</p>

CineK is a media player for **Android phones and Android TV**. One APK supports touch controls and TV remotes, with a library for your local movies and shows, online discovery, and live channels. Requires **Android 8.1 or newer (API 27+)**.

## What the Android app does

### Your movie and TV library

- Scan videos on your device and add media folders.
- Browse movies and shows with posters, backdrops, and title details.
- Choose seasons and episodes, or display episodes in season rows.
- Search your library, save favorites in **My List**, mark titles watched or unwatched, and hide titles.
- Resume from **Continue Watching** or start a title from the beginning.
- Enable automatic playback of the next local episode and choose when its **Up next** card appears.

### Discover movies and shows

- Browse TMDB titles by **Popular**, **Top rated**, or **Newest**.
- Filter by genre and release decade.
- View descriptions, ratings, cast, and related titles.
- Select a movie or a show's season and episode to look up available streaming sources.
- Refresh or switch sources, resume remote playback, and return to recent Discover titles.
- Open Vidsrc's browser-based player inside CineK when its current embed cannot be resolved to a native stream.
- Open the Ridomovies website inside CineK when it requires browser verification.

Source availability depends on the provider. A title appearing in Discover does not guarantee a playable stream.

### Open files and streams

- Open a local video or audio file without adding it to the library.
- Play network URLs, including HLS streams.
- Use **Open with CineK** from other Android apps.
- Check media access, grant permissions, and rescan from the **Open** page.

### Live TV

- Add M3U playlists from a URL or local file.
- Browse featured free playlist sources and channel categories.
- Favorite channels and categories, refresh playlists, and keep saved channel lists between launches.
- Watch with CineK's player or send live channels to an installed **MX Player** or **VLC** Android app.
- Configure the channel banner, on-screen clock, and confirmation before leaving live TV.

### Playback and subtitles

- Play, pause, seek, and resume with touch or remote controls.
- Select audio and subtitle tracks.
- Load subtitle files and choose their text encoding.
- Adjust subtitle timing, text size, color, and background.
- Search and download subtitles through the optional **OpenSubtitles** integration, which requires a configured build and an account.
- Choose **Fit**, **Stretch**, or **Zoom** for the picture.
- Choose classic or streamlined controls for library playback.

### Make it yours

- Choose a theme, artwork quality, and TV show layout.
- Customize Home's Spotlight and Continue Watching rows.
- Use cinematic backdrops or a poster wall in ambient mode, with an optional clock.
- Manage hidden titles, refresh artwork, and clear the artwork cache.

## Phone and TV controls

| Android phone | Android TV |
| --- | --- |
| Tap and long-press to browse and open actions. | Navigate with the remote's D-pad and select button. |
| Tap the video to show or hide controls. Double-tap the left or right side to skip 10 seconds during library playback. | Use the playback controls and quick menus for seeking, tracks, and subtitles. |
| Drag the seek bar. In **Open** and **Live TV**, swipe vertically on the left for brightness and on the right for volume. | Browse channel categories and change live channels with the remote. |

Phones use landscape orientation. Both device types use the same APK.

## Install

1. Download the latest **Android APK** from [Releases](https://github.com/skonester/CineK/releases).
2. Allow installation from the app you used to download or open the APK when Android prompts you.
3. Open CineK and grant media access to scan your device, or use **Open** to choose a file or stream.

Published pre-release APKs are debug-signed test builds. Updating between CI builds may require uninstalling the previous APK because its signing certificate can differ. Uninstalling clears the app's library and settings.

## Current limitations

- Music can be opened and played through **Open**, but does not have a music library view.
- Playback from **Open** and **Live TV** stops when you leave CineK.
- Discover, online metadata, subtitle downloads, and network streams require an internet connection. Local playback remains available when metadata requests fail.
- Provider web playback requires Android System WebView and uses the site's own controls. Native-player resume tracking and subtitle settings do not apply to web playback; provider verification or access restrictions can still prevent playback.

## Development and license

For Android setup and build instructions, see the [Android development guide](android/README.md). The [architecture guide](architecture.md) describes the app's playback and storage.

CineK is licensed under the [GNU General Public License v3.0](LICENSE.md) and includes adapted [Aperture](https://github.com/XDanfr/Aperture) source. See the [Android attribution](android/NOTICE.md) and [Aperture license](android/APERTURE-LICENSE).
