package com.sasami.vlc.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.sasami.vlc.player.VideoBackend

@Serializable
data class PlayerItem(
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val error: String = ""
)

@Serializable
data class PlayerSection(
    val playerList: List<PlayerItem> = emptyList(),
    val audioOutput: String = "",
    val audioDeviceId: String = "",
    val videoOutput: String = "auto"
)

@Serializable
data class AppConfig(
    val lang: String = "en",
    val player: PlayerSection = PlayerSection()
)

object PlayerStore {
    var lang by mutableStateOf("en")
    var playerList by mutableStateOf<List<PlayerItem>>(emptyList())
    var activeId by mutableStateOf("") // Path of currently playing video
    var audioOutput by mutableStateOf("")
    var audioDeviceId by mutableStateOf("")
    var videoOutput by mutableStateOf("auto")
    
    // Runtime-only states
    var volume by mutableStateOf(80)
    var isPlaying by mutableStateOf(false)
    var currentTime by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var isPlayListCollapsed by mutableStateOf(true)
    var isDraggingOver by mutableStateOf(false)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val configFile: File by lazy {
        val appData = System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/AppData/Roaming")
        System.getProperty("sasami.configDir")?.let { File(it, "config.json") } ?: File(appData, "SasamiVLC/config.json")
    }

    fun load() {
        try {
            if (configFile.exists()) {
                val content = configFile.readText()
                val config = json.decodeFromString<AppConfig>(content)
                lang = config.lang
                playerList = config.player.playerList
                audioOutput = config.player.audioOutput
                audioDeviceId = config.player.audioDeviceId
                videoOutput = VideoBackend.fromKey(config.player.videoOutput).key
            } else {
                save()
            }
        } catch (e: Exception) {
            System.err.println("Error loading config: ${e.message}")
        }
    }

    fun save() {
        try {
            configFile.parentFile.mkdirs()
            val config = AppConfig(
                lang = lang,
                player = PlayerSection(
                    playerList = playerList,
                    audioOutput = audioOutput,
                    audioDeviceId = audioDeviceId,
                    videoOutput = videoOutput
                )
            )
            val content = json.encodeToString(AppConfig.serializer(), config)
            configFile.writeText(content)
        } catch (e: Exception) {
            System.err.println("Error saving config: ${e.message}")
        }
    }

    fun addPlayerList(item: PlayerItem) {
        if (playerList.none { it.path == item.path }) {
            playerList = playerList + item
            save()
        }
    }

    fun removeVideo(item: PlayerItem) {
        val index = playerList.indexOfFirst { it.path == item.path }
        if (index != -1) {
            if (item.path == activeId) {
                activeId = ""
                isPlaying = false
            }
            playerList = playerList.filterIndexed { i, _ -> i != index }
            save()
        }
    }
}
