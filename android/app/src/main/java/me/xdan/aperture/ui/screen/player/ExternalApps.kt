package me.xdan.aperture.ui.screen.player

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.media3.common.MimeTypes
import me.xdan.aperture.domain.model.LivePlayerApp

/**
 * A view intent for [item], for [packageName] or for any app that plays video. MX
 * Player reads the title and a `headers` array of name, value pairs, so streams that
 * need a User-Agent or Referer still play there; other apps ignore those extras.
 */
fun streamViewIntent(item: PlayableItem, packageName: String? = null): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        val type = if (looksLikeHls(item.uri, item.live)) MimeTypes.APPLICATION_M3U8 else "video/*"
        setDataAndType(Uri.parse(item.uri), type)
        packageName?.let(::setPackage)
        putExtra("title", item.title)
        if (item.headers.isNotEmpty()) {
            putExtra("headers", item.headers.flatMap { (name, value) -> listOf(name, value) }.toTypedArray())
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

/** The first of [app]'s packages that is installed, or null. */
fun installedPackage(context: Context, app: LivePlayerApp): String? = app.packages.firstOrNull { name ->
    runCatching { context.packageManager.getPackageInfo(name, 0) }.isSuccess
}

/** Hands [item] to [app]. Returns false when it isn't installed or wouldn't open the stream. */
fun openInApp(context: Context, item: PlayableItem, app: LivePlayerApp): Boolean {
    val packageName = installedPackage(context, app) ?: return false
    return startSafely(context, streamViewIntent(item, packageName))
}

/** Lets the viewer pick any other video app for [item]. Returns false when there is none. */
fun openInOtherApp(context: Context, item: PlayableItem): Boolean {
    val intent = streamViewIntent(item)
    // CineK opens streams itself, so leave it out of its own chooser.
    val (own, others) = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .partition { it.activityInfo.packageName == context.packageName }
    if (others.isEmpty()) return false
    val chooser = Intent.createChooser(intent, "Open ${item.title} with").apply {
        val excluded = own.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        if (excluded.isNotEmpty()) putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded.toTypedArray())
    }
    return startSafely(context, chooser)
}

private fun startSafely(context: Context, intent: Intent): Boolean = try {
    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
