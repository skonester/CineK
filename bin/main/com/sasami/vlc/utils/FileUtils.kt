package com.sasami.vlc.utils

import java.io.File

object FileUtils {
    private val videoExtensions = setOf(
        "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "mpeg", "mpg", "3gp", "ts", "m2ts", "mts"
    )

    private val audioExtensions = setOf(
        "mp3", "aac", "flac", "m4a", "ogg", "opus", "wav", "wma", "aiff"
    )

    val mediaExtensions = videoExtensions + audioExtensions

    fun isSupportedVideoFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in videoExtensions
    }

    fun isSupportedMediaFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in mediaExtensions
    }

    fun collectMediaFiles(files: List<File>): List<File> {
        val collected = mutableListOf<File>()
        val seen = mutableSetOf<String>()

        fun visit(file: File) {
            val absPath = try { file.canonicalPath } catch (_: java.io.IOException) { return }
            if (absPath in seen || !file.exists()) return
            seen.add(absPath)

            if (file.isDirectory) {
                file.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { visit(it) }
            } else if (file.isFile && isSupportedMediaFile(file.name)) {
                collected.add(file)
            }
        }

        files.forEach { visit(it) }
        return collected
    }

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val index = (Math.log(size.toDouble()) / Math.log(1024.0)).toInt().coerceAtMost(units.size - 1)
        val value = size / Math.pow(1024.0, index.toDouble())
        return String.format("%.2f %s", value, units[index])
    }

    fun pickFile(parentFrame: java.awt.Frame? = null): String? {
        val dialog = java.awt.FileDialog(parentFrame, "Select Media", java.awt.FileDialog.LOAD)
        dialog.file = "*.mp4;*.mkv;*.avi;*.mp3;*.wav;*.flac;*.aac"
        dialog.isVisible = true
        return if (dialog.directory != null && dialog.file != null) {
            File(dialog.directory, dialog.file).absolutePath
        } else {
            null
        }
    }

    fun pickFolder(): String? {
        val chooser = javax.swing.JFileChooser()
        chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        val result = chooser.showOpenDialog(null)
        return if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else {
            null
        }
    }
}
