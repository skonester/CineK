package com.sasami.vlc.utils

import com.sasami.vlc.store.LibraryStore
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.application.withFileKitDialogSettings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Native, parented file dialogs; results return to Tao's UI dispatcher. */
object DesktopFiles {
    var owner: NucleusWindow? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var picking = false

    private fun pick(block: suspend NucleusWindow.() -> Unit) {
        val window = owner ?: return
        if (picking) return
        picking = true
        scope.launch {
            try { window.block() }
            catch (e: Exception) { LibraryStore.message = "File dialog failed: ${e.message}" }
            finally { picking = false }
        }
    }

    fun open(extensions: Set<String>, multiple: Boolean = false, selected: (List<File>) -> Unit) = pick {
        withFileKitDialogSettings { settings ->
            val files = if (multiple) FileKit.openFilePicker(
                type = FileKitType.File(extensions.toList()), mode = FileKitMode.Multiple(), dialogSettings = settings)
            else FileKit.openFilePicker(type = FileKitType.File(extensions.toList()), dialogSettings = settings)?.let(::listOf)
            files?.let { selected(it.map { entry -> File(entry.path) }) }
        }
    }

    fun folder(selected: (File) -> Unit) = pick {
        withFileKitDialogSettings { settings ->
            FileKit.openDirectoryPicker(dialogSettings = settings)?.let { selected(File(it.path)) }
        }
    }

    // The platform save dialog owns overwrite confirmation.
    fun save(name: String, extension: String, selected: (File) -> Unit) = pick {
        withFileKitDialogSettings { settings ->
            FileKit.openFileSaver(suggestedName = name, defaultExtension = extension, dialogSettings = settings)
                ?.let { selected(File(it.path)) }
        }
    }
}
