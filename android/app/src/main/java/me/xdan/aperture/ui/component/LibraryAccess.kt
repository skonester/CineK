package me.xdan.aperture.ui.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton
import me.xdan.aperture.ui.component.touch.Surface

/** What CineK may read from shared storage when it builds the library. */
data class LibraryAccess(
    val videos: Boolean,
    val music: Boolean,
    /** Android 14+: the user shared only some photos and videos. */
    val selectedVideosOnly: Boolean = false
) {
    val isComplete get() = videos && music
    val hasAny get() = videos || music || selectedVideosOnly

    companion object {
        val None = LibraryAccess(videos = false, music = false)
    }
}

object LibraryPermissions {
    private val SplitMediaPermissions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    private val PartialMediaAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Requested together so Android shows video and music in one flow. */
    val requested: Array<String> = when {
        // Declaring the user-selected permission lets Android 14 offer limited
        // access instead of silently granting nothing.
        PartialMediaAccess -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        SplitMediaPermissions -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun current(context: Context): LibraryAccess {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (!SplitMediaPermissions) {
            val storage = granted(Manifest.permission.READ_EXTERNAL_STORAGE)
            return LibraryAccess(videos = storage, music = storage)
        }
        val videos = granted(Manifest.permission.READ_MEDIA_VIDEO)
        return LibraryAccess(
            videos = videos,
            music = granted(Manifest.permission.READ_MEDIA_AUDIO),
            selectedVideosOnly = PartialMediaAccess && !videos &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        )
    }

    internal fun missing(access: LibraryAccess): List<String> = when {
        !SplitMediaPermissions -> if (access.videos) emptyList() else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> buildList {
            if (!access.videos) add(Manifest.permission.READ_MEDIA_VIDEO)
            if (!access.music) add(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }

    fun appSettingsIntent(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
}

/**
 * Library access shared by the whole app. [request] shows Android's permission
 * prompt, or Android's app settings once Android has stopped showing the prompt.
 */
@Stable
class LibraryAccessController internal constructor() {
    var access by mutableStateOf(LibraryAccess.None)
        internal set

    /** Android won't show its prompt again; only app settings can grant access now. */
    var mustUseSettings by mutableStateOf(false)
        internal set

    internal var launchRequest: () -> Unit = {}

    fun request() = launchRequest()
}

val LocalLibraryAccess = staticCompositionLocalOf { LibraryAccessController() }

private const val AccessPreferences = "library_access"
private const val AskedKey = "asked"

/**
 * Tracks library access, re-reading it whenever CineK returns to the foreground
 * (for example from Android settings), and calls [onAccessGained] when Android
 * grants something new so the library can be rescanned.
 */
@Composable
fun rememberLibraryAccess(onAccessGained: () -> Unit): LibraryAccessController {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // Read the starting state up front so access granted before launch doesn't
    // count as newly gained on the first resume.
    val controller = remember {
        LibraryAccessController().also { it.access = LibraryPermissions.current(context) }
    }
    val preferences = remember(context) {
        context.getSharedPreferences(AccessPreferences, Context.MODE_PRIVATE)
    }
    val currentOnAccessGained by rememberUpdatedState(onAccessGained)

    fun refresh() {
        val access = LibraryPermissions.current(context)
        val previous = controller.access
        controller.access = access
        val missing = LibraryPermissions.missing(access)
        // Android only reports "show rationale" after one denial and stops showing
        // its prompt after the second, so asked + no rationale means settings.
        controller.mustUseSettings = preferences.getBoolean(AskedKey, false) &&
            missing.isNotEmpty() &&
            missing.none { activity?.shouldShowRequestPermissionRationale(it) == true }
        val gained = (access.videos && !previous.videos) ||
            (access.music && !previous.music) ||
            (access.selectedVideosOnly && !previous.selectedVideosOnly)
        if (gained) currentOnAccessGained()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        preferences.edit().putBoolean(AskedKey, true).apply()
        refresh()
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    controller.launchRequest = {
        val launched = if (controller.mustUseSettings) {
            runCatching { settingsLauncher.launch(LibraryPermissions.appSettingsIntent(context)) }
        } else {
            runCatching { permissionLauncher.launch(LibraryPermissions.requested) }
        }
        // Some TV builds have no permission UI; app settings is the remaining route.
        if (launched.isFailure) {
            runCatching { settingsLauncher.launch(LibraryPermissions.appSettingsIntent(context)) }
        }
    }

    LifecycleResumeEffect(controller) {
        refresh()
        onPauseOrDispose { }
    }
    return controller
}

fun LibraryAccess.videoStatus(): String = when {
    videos -> "Allowed"
    selectedVideosOnly -> "Only the videos you selected"
    else -> "Not allowed"
}

fun LibraryAccess.musicStatus(): String = if (music) "Allowed" else "Not allowed"

/** The label for the button that fixes access, matching what tapping it will open. */
fun LibraryAccessController.actionLabel(): String =
    if (mustUseSettings) "Open Android settings" else "Allow access"

/** Guidance shown when access is incomplete, or null when there is nothing to fix. */
fun LibraryAccessController.guidance(): String? = when {
    access.isComplete -> null
    mustUseSettings -> "Android won't ask again. In Android settings, open Permissions and allow " +
        "Photos and videos (choose Allow all) and Music and audio."
    access.selectedVideosOnly -> "CineK can only see the videos you picked. Choose Allow all " +
        "for Photos and videos to add the rest."
    else -> null
}

@Composable
fun LibraryAccessPrompt(controller: LibraryAccessController, onDismiss: () -> Unit) {
    val access = controller.access
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { primaryFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(24.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(28.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Let CineK find your videos and music", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "CineK builds its library from the videos and music stored on this device. " +
                            "You can change this later in Settings.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AccessStatusRow(Icons.Rounded.VideoLibrary, "Videos", access.videoStatus(), access.videos)
                    AccessStatusRow(Icons.Rounded.MusicNote, "Music", access.musicStatus(), access.music)
                    controller.guidance()?.let { hint ->
                        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        OutlinedButton(onClick = onDismiss) { Text("Not now") }
                        Button(
                            onClick = controller::request,
                            modifier = Modifier.focusRequester(primaryFocus)
                        ) { Text(controller.actionLabel()) }
                    }
                }
            }
        }
    }
}

@Composable
fun AccessStatusRow(icon: ImageVector, label: String, status: String, allowed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 72.dp))
        Icon(
            if (allowed) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
