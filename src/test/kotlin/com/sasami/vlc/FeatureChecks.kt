package com.sasami.vlc

import com.sasami.vlc.player.PlaybackPolicy
import com.sasami.vlc.player.VideoBackend
import com.sasami.vlc.store.*
import com.sasami.vlc.utils.PlaylistFiles
import kotlinx.serialization.json.Json
import java.io.File

object FeatureChecks {
    @JvmStatic fun main(args: Array<String>) {
        check(PlaybackPolicy.nextIndex(0, 0, "all", true) == null)
        check(PlaybackPolicy.nextIndex(3, 2, "off", true) == null)
        check(PlaybackPolicy.nextIndex(3, 2, "all", true) == 0)
        check(PlaybackPolicy.nextIndex(3, 1, "one", true) == 1)
        check(PlaybackPolicy.nextIndex(3, 1, "one", false) == 2)
        check(PlaybackPolicy.nextIndex(3, 2, "off", false) == 0)
        check(PlaybackPolicy.resumePosition(30000, 90000) == 30000L)
        check(PlaybackPolicy.resumePosition(88000, 90000) == 0L)
        check(PlaybackPolicy.resumePosition(1000, 90000) == 0L)
        check(PlaybackPolicy.resumePosition(5000, 0) == 0L)
        val old = Json.decodeFromString<AppConfig>("""{"lang":"zh-CN","player":{"playerList":[{"name":"Movie","path":"C:/movie.mp4","type":"video","size":12}],"audioOutput":"mmdevice","audioDeviceId":"test"}}""")
        check(old.player.playerList.single().name == "Movie")
        check(old.player.audioDeviceId == "test")
        check(old.player.videoOutput == "auto")
        check(VideoBackend.fromKey("glwin32") == VideoBackend.OPENGL)
        check(VideoBackend.fromKey("vulkan") == VideoBackend.AUTOMATIC)

        val base = File("build/feature-checks").absoluteFile.apply { mkdirs() }
        val parsed = PlaylistFiles.parse("\uFEFF#EXTM3U\n#EXTINF:-1,A title\nmovie.mp4\n# comment\nhttps://example.test/live\n#EXTINF:-1,音楽\nsong.flac\nmovie.mp4\n", base)
        check(parsed.size == 3)
        check(parsed.first().name == "A title")
        check(parsed.first().path == File(base, "movie.mp4").absolutePath)
        check(parsed[1].type == "stream")
        check(parsed[2].name == "音楽")
        check(PlaylistFiles.parse(PlaylistFiles.encode(parsed), base) == parsed)
        check(PlaylistFiles.parse("#EXTM3U\n# only comments\n", base).isEmpty())
        val live = M3uChannels.parse("""#EXTM3U url-tvg="https://guide.example/guide.xml"
#EXTINF:-1 tvg-id="news" tvg-chno="7" group-title="News, Local",Channel One
https://example.test/live/one.m3u8
#EXTINF:-1 group-title="Sports",Channel Two
relative/two.m3u8
#EXTINF:-1,Duplicate
https://example.test/live/one.m3u8
""", "source", "https://example.test/list.m3u")
        check(live.size == 2)
        check(live[0].group == "News, Local" && live[0].number == "7" && live[0].tvgId == "news")
        check(live[1].url == "https://example.test/relative/two.m3u8")
        check(runCatching { M3uChannels.parse("<html>bad</html>", "source", "https://example.test/list") }.isFailure)

        System.setProperty("sasami.configDir", base.path)
        LibraryStore.favorites = setOf("a", "b")
        LibraryStore.history = listOf(WatchEntry("a", 20000, 80000, 1))
        LibraryStore.playlists = listOf(SavedPlaylist("p", "Weekend", listOf("a")))
        LibraryStore.addToPlaylist("p", "a")
        check(LibraryStore.playlists.single().paths.size == 1)
        LibraryStore.addToPlaylist("p", "b")
        PlayerStore.volume = 37
        LibraryStore.save()
        LibraryStore.favorites = emptySet()
        LibraryStore.history = emptyList()
        LibraryStore.playlists = emptyList()
        LibraryStore.load()
        check(LibraryStore.favorites == setOf("a", "b"))
        check(LibraryStore.history.single().position == 20000L)
        check(LibraryStore.playlists.single().paths == listOf("a", "b"))
        check(PlayerStore.volume == 37)
        LibraryStore.remove("a")
        check("a" !in LibraryStore.favorites)
        check(LibraryStore.history.isEmpty())
        check(LibraryStore.playlists.single().paths == listOf("b"))
        PlayerStore.videoOutput = VideoBackend.OPENGL.key
        PlayerStore.save()
        PlayerStore.videoOutput = "auto"
        PlayerStore.load()
        check(PlayerStore.videoOutput == VideoBackend.OPENGL.key)
        println("PASS: queue/repeat boundaries, resume boundaries, legacy config, M3U round trip, library persistence and removal")
    }
}
