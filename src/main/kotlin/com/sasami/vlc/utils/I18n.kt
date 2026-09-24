package com.sasami.vlc.utils

import com.sasami.vlc.store.PlayerStore

object I18n {
    private val en = mapOf(
        "titleBar.defaultTitle" to "CineK",
        "titleBar.minimize" to "Minimize",
        "titleBar.maximize" to "Maximize",
        "titleBar.restore" to "Restore",
        "titleBar.close" to "Close",
        
        "playerControls.prev" to "Previous",
        "playerControls.play" to "Play",
        "playerControls.pause" to "Pause",
        "playerControls.stop" to "Stop",
        "playerControls.next" to "Next",
        
        "playerBar.fullscreen" to "Fullscreen",
        "playerBar.exitFullscreen" to "Exit Fullscreen",
        
        "playList.title" to "Playlist",
        "playList.expand" to "Expand Playlist",
        "playList.collapse" to "Collapse Playlist",
        "playList.addVideo" to "Add Video",
        "playList.audioOutput" to "Audio: %s",
        "playList.audioDefault" to "System Audio",
        "playList.refreshAudioDevices" to "Refresh Audio Devices",
        "playList.count" to "items",
        "playList.remove" to "Remove",
        
        "videoPlayer.addVideo" to "Add Video",
        "videoPlayer.addMedia" to "Add Media",
        "videoPlayer.addFolder" to "Add Folder",
        "videoPlayer.libraryTitle" to "Media Library",
        "videoPlayer.librarySubtitle" to "Open files, drop a folder, or pick a recent item to start playback.",
        "videoPlayer.dropTitle" to "Drop media here",
        "videoPlayer.dropSubtitle" to "Videos, music files, and folders are supported",
        "videoPlayer.dropNow" to "Release to add",
        "videoPlayer.recentMedia" to "Recent Media",
        
        "language.zhCN" to "Simplified Chinese",
        "language.zhTW" to "Traditional Chinese",
        "language.en" to "English",
        "language.switchLanguage" to "Switch Language",
        
        "about.title" to "About",
        "about.version" to "Version",
        "about.checkForUpdates" to "Check for Updates",
        "about.checking" to "Checking for updates...",
        "about.updateAvailable" to "New version v%s available. Download now?",
        "about.updateNotAvailable" to "You're up to date",
        "about.downloading" to "Downloading update %s%%",
        "about.downloadComplete" to "Update downloaded. Click to install and restart",
        "about.installAndRestart" to "Install & Restart",
        "about.downloadUpdate" to "Download",
        "about.updateError" to "Update failed: %s"
    )

    private val zhCN = mapOf(
        "titleBar.defaultTitle" to "CineK",
        "titleBar.minimize" to "最小化",
        "titleBar.maximize" to "最大化",
        "titleBar.restore" to "还原",
        "titleBar.close" to "关闭",
        
        "playerControls.prev" to "上一首",
        "playerControls.play" to "播放",
        "playerControls.pause" to "暂停",
        "playerControls.stop" to "停止",
        "playerControls.next" to "下一首",
        
        "playerBar.fullscreen" to "全屏",
        "playerBar.exitFullscreen" to "退出全屏",
        
        "playList.title" to "播放列表",
        "playList.expand" to "展开播放列表",
        "playList.collapse" to "收起播放列表",
        "playList.addVideo" to "添加视频",
        "playList.audioOutput" to "音频: %s",
        "playList.audioDefault" to "系统音频",
        "playList.refreshAudioDevices" to "刷新音频设备",
        "playList.count" to "首",
        "playList.remove" to "移除",
        
        "videoPlayer.addVideo" to "添加视频",
        "videoPlayer.addMedia" to "添加媒体",
        "videoPlayer.addFolder" to "添加文件夹",
        "videoPlayer.libraryTitle" to "媒体库",
        "videoPlayer.librarySubtitle" to "打开文件、拖入文件夹，或选择最近项目开始播放。",
        "videoPlayer.dropTitle" to "拖放媒体到这里",
        "videoPlayer.dropSubtitle" to "支持视频、音乐文件和文件夹",
        "videoPlayer.dropNow" to "松开以添加",
        "videoPlayer.recentMedia" to "最近媒体",
        
        "language.zhCN" to "简体中文",
        "language.zhTW" to "繁体中文",
        "language.en" to "English",
        "language.switchLanguage" to "切换语言",
        
        "about.title" to "关于",
        "about.version" to "版本",
        "about.checkForUpdates" to "检查更新",
        "about.checking" to "正在检查更新...",
        "about.updateAvailable" to "发现新版本 v%s，是否立即下载？",
        "about.updateNotAvailable" to "当前已是最新版本",
        "about.downloading" to "正在下载更新 %s%%",
        "about.downloadComplete" to "更新下载完成，点击安装并重启",
        "about.installAndRestart" to "安装并重启",
        "about.downloadUpdate" to "下载",
        "about.updateError" to "更新失败：%s"
    )

    private val zhTW = mapOf(
        "titleBar.defaultTitle" to "CineK",
        "titleBar.minimize" to "最小化",
        "titleBar.maximize" to "最大化",
        "titleBar.restore" to "還原",
        "titleBar.close" to "關閉",
        
        "playerControls.prev" to "上一首",
        "playerControls.play" to "播放",
        "playerControls.pause" to "暫停",
        "playerControls.stop" to "停止",
        "playerControls.next" to "下一首",
        
        "playerBar.fullscreen" to "全屏",
        "playerBar.exitFullscreen" to "退出全屏",
        
        "playList.title" to "播放列表",
        "playList.expand" to "展開播放列表",
        "playList.collapse" to "收起播放列表",
        "playList.addVideo" to "添加視頻",
        "playList.audioOutput" to "音頻: %s",
        "playList.audioDefault" to "系統音頻",
        "playList.refresh音頻設備" to "刷新音頻設備", // wait, "refreshAudioDevices"
        "playList.refreshAudioDevices" to "刷新音頻設備",
        "playList.count" to "首",
        "playList.remove" to "移除",
        
        "videoPlayer.addVideo" to "添加視頻",
        "videoPlayer.addMedia" to "添加媒體",
        "videoPlayer.addFolder" to "添加文件夾",
        "videoPlayer.libraryTitle" to "媒體庫",
        "videoPlayer.librarySubtitle" to "打開文件、拖入文件夾，或選擇最近項目開始播放。",
        "videoPlayer.dropTitle" to "拖放媒體到這裡",
        "videoPlayer.dropSubtitle" to "支持視頻、音樂文件和文件夾",
        "videoPlayer.dropNow" to "鬆開以添加",
        "videoPlayer.recentMedia" to "最近媒體",
        
        "language.zhCN" to "簡體中文",
        "language.zhTW" to "繁體中文",
        "language.en" to "English",
        "language.switchLanguage" to "切換語言",
        
        "about.title" to "關於",
        "about.version" to "版本",
        "about.checkForUpdates" to "檢查更新",
        "about.checking" to "正在檢查更新...",
        "about.updateAvailable" to "發現新版本 v%s，是否立即下載？",
        "about.updateNotAvailable" to "當前已是最新版本",
        "about.downloading" to "正在下載更新 %s%%",
        "about.downloadComplete" to "更新下載完成，點擊安裝並重啟",
        "about.installAndRestart" to "安裝並重啟",
        "about.downloadUpdate" to "下載",
        "about.updateError" to "更新失敗：%s"
    )

    fun t(key: String, vararg args: Any): String {
        val dict = when (PlayerStore.lang) {
            "zh-CN" -> zhCN
            "zh-TW" -> zhTW
            else -> en
        }
        val template = dict[key] ?: en[key] ?: key
        return try {
            String.format(template, *args)
        } catch (e: java.util.IllegalFormatException) {
            template
        }
    }
}
