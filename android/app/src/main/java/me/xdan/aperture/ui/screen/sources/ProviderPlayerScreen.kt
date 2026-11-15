package me.xdan.aperture.ui.screen.sources

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.xdan.aperture.data.remoteplayback.RemoteMediaRepository
import me.xdan.aperture.data.sources.ProviderWebTarget
import me.xdan.aperture.data.sources.SourceSite
import me.xdan.aperture.data.sources.providerNavigationAllowed
import me.xdan.aperture.data.sources.providerWebTarget
import me.xdan.aperture.ui.component.touch.OutlinedButton
import javax.inject.Inject

data class ProviderPlayerState(val target: ProviderWebTarget? = null, val error: String? = null)

@HiltViewModel
class ProviderPlayerViewModel @Inject constructor(private val repository: RemoteMediaRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProviderPlayerState())
    val state = _state.asStateFlow()
    private var route: Pair<String, String>? = null
    private var work: Job? = null

    fun load(contentId: String, provider: String) {
        if (route == (contentId to provider)) return
        route = contentId to provider
        work?.cancel()
        _state.value = ProviderPlayerState()
        work = viewModelScope.launch {
            try {
                val site = SourceSite.valueOf(provider)
                val request = repository.request(contentId) ?: error("Missing title")
                val target = providerWebTarget(site, request.query) ?: error("Unsupported provider")
                _state.value = ProviderPlayerState(target)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = ProviderPlayerState(error = "This provider page is unavailable. Return to sources and try again.") }
        }
    }
}

@Composable
fun ProviderPlayerScreen(
    contentId: String, provider: String, onBack: () -> Unit,
    viewModel: ProviderPlayerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(contentId, provider) { viewModel.load(contentId, provider) }
    val target = state.target
    if (target != null) {
        key(target) { ProviderWebPlayer(target, onBack) }
    } else {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(state.error ?: "Opening provider…")
            OutlinedButton(onClick = onBack) { Text("Back to sources") }
        }
    }
}

@Composable
private fun ProviderWebPlayer(target: ProviderWebTarget, onBack: () -> Unit) {
    var host by remember { mutableStateOf<ProviderWebView?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> host?.resume()
                Lifecycle.Event.ON_PAUSE -> host?.pause()
                Lifecycle.Event.ON_STOP -> host?.stopPlayback()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            host?.release()
        }
    }
    BackHandler {
        if (host?.exitFullscreen() != true && host?.goBack() != true) onBack()
    }
    Column(Modifier.fillMaxSize()) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("Back to sources") }
                Text("${target.site.displayName} · ${target.title}", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { error = null; host?.reload() }) { Text("Reload") }
            }
            if (target.site == SourceSite.RIDOMOVIES) {
                Text("Complete the site's verification if offered, then find this title on Ridomovies. The site may still refuse access.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            error?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.error) }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                val container = FrameLayout(context)
                try {
                    val player = ProviderWebView(context, target, { fullscreen = it }, { error = it })
                    host = player
                    container.addView(player, FrameLayout.LayoutParams(-1, -1))
                } catch (_: RuntimeException) {
                    error = "Web playback needs an enabled, up-to-date Android System WebView on this device."
                }
                container
            }
        )
    }
}

/** Owns only web content; it exposes no app JavaScript bridge or device file access. */
internal class ProviderWebView(
    context: Context, private val target: ProviderWebTarget,
    private val onFullscreen: (Boolean) -> Unit, private val onError: (String?) -> Unit
) : FrameLayout(context) {
    internal val web = WebView(context)
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var stopped = false
    private var released = false

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        keepScreenOn = true
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !providerNavigationAllowed(target, request.url.toString(), request.isForMainFrame)

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { onError(null) }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) onError("The provider page could not load. Check your connection or try another source.")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) onError("The provider returned HTTP ${response.statusCode}. Complete verification if the page offers it, or choose another source.")
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                onError("The provider's secure connection could not be verified.")
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view
                customCallback = callback
                web.visibility = View.INVISIBLE
                addView(view, LayoutParams(-1, -1))
                onFullscreen(true)
                view.requestFocus()
            }

            override fun onHideCustomView() { exitFullscreen() }
            // Popups and external app launches are not part of the provider player.
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean = false
        }
        addView(web, LayoutParams(-1, -1))
        web.loadUrl(target.url)
        web.requestFocus()
    }

    fun exitFullscreen(): Boolean {
        val view = customView ?: return false
        val callback = customCallback
        customView = null
        customCallback = null
        removeView(view)
        web.visibility = View.VISIBLE
        onFullscreen(false)
        callback?.onCustomViewHidden()
        web.requestFocus()
        return true
    }

    fun goBack(): Boolean {
        if (target.site != SourceSite.RIDOMOVIES || !web.canGoBack()) return false
        web.goBack()
        return true
    }

    fun reload() { if (!released) web.reload() }
    fun pause() { if (!released) web.onPause() }
    fun resume() {
        if (released) return
        web.onResume()
        if (stopped) { stopped = false; web.loadUrl(target.url) }
    }

    fun stopPlayback() {
        if (released) return
        exitFullscreen()
        web.stopLoading()
        web.loadUrl("about:blank")
        stopped = true
    }

    fun release() {
        if (released) return
        stopPlayback()
        released = true
        keepScreenOn = false
        removeView(web)
        web.destroy()
    }
}
