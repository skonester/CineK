package me.xdan.aperture

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import me.xdan.aperture.ui.artwork.LocalHdArtwork
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.MainViewModel
import me.xdan.aperture.ui.navigation.Destination
import me.xdan.aperture.ui.navigation.NavGraph
import me.xdan.aperture.ui.navigation.isPlayback
import me.xdan.aperture.ui.screen.ambient.AmbientMode
import me.xdan.aperture.ui.screen.open.displayName
import me.xdan.aperture.ui.theme.ApertureTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis())
    private var isAmbientActive by mutableStateOf(false)
    private var isPlayerActive by mutableStateOf(false)
    private val ambientDismissKeys = mutableSetOf<Int>()
    private var consumingAmbientTouch = false

    /** A file or stream another app asked CineK to open, waiting for the backstack. */
    private var externalMedia by mutableStateOf<Destination.ExternalPlayer?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a configuration change the restored backstack already holds the player.
        if (savedInstanceState == null) externalMedia = externalMediaFrom(intent)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val themeId by mainViewModel.themeId.collectAsState()
            val dynamicAccentArgb by mainViewModel.dynamicAccentArgb.collectAsState()
            val hdArtwork by mainViewModel.hdArtwork.collectAsState()
            var launchFinished by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay(900)
                launchFinished = true
            }

            ApertureTheme(
                themeId = themeId,
                dynamicAccent = dynamicAccentArgb?.let { androidx.compose.ui.graphics.Color(it) }
            ) { CompositionLocalProvider(LocalHdArtwork provides hdArtwork) {
                Box(Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = launchFinished,
                        enter = fadeIn(tween(360)) + scaleIn(tween(420), initialScale = 0.985f)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                        ) {
                            @Suppress("UNCHECKED_CAST")
                            val backstack = rememberNavBackStack(Destination.Home) as NavBackStack<Destination>

                            LaunchedEffect(Unit) {
                                while (true) {
                                    delay(1_000)
                                    if (!isPlayerActive &&
                                        System.currentTimeMillis() - lastInteractionTime > 5 * 60 * 1_000
                                    ) {
                                        isAmbientActive = true
                                    }
                                }
                            }

                            if (isAmbientActive) {
                                BackHandler {
                                    lastInteractionTime = System.currentTimeMillis()
                                    isAmbientActive = false
                                }
                                AmbientMode()
                            } else {
                                NavGraph(
                                    backstack = backstack,
                                    mainViewModel = mainViewModel,
                                    onPlayerStateChanged = { isPlayerActive = it },
                                    onPreviewAmbientMode = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        isAmbientActive = true
                                    },
                                    onNavigate = { destination ->
                                        lastInteractionTime = System.currentTimeMillis()
                                        if (destination.isPlayback) isPlayerActive = true
                                        backstack.add(destination)
                                    }
                                )
                            }

                            LaunchedEffect(externalMedia) {
                                val media = externalMedia ?: return@LaunchedEffect
                                lastInteractionTime = System.currentTimeMillis()
                                isAmbientActive = false
                                isPlayerActive = true
                                if (backstack.last() != media) backstack.add(media)
                                externalMedia = null
                            }
                        }
                    }

                    AnimatedVisibility(visible = !launchFinished, exit = fadeOut(tween(260))) {
                        Column(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.cinek_logo),
                                contentDescription = null,
                                modifier = Modifier.size(200.dp)
                            )
                            Text("CineK", style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        externalMediaFrom(intent)?.let { externalMedia = it }
    }

    private fun externalMediaFrom(intent: Intent?): Destination.ExternalPlayer? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        return Destination.ExternalPlayer(uri.toString(), displayName(this, uri))
    }

    // Touch counts as activity for ambient mode the way remote keys do, and the
    // first touch while ambient mode is showing only dismisses it.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        if (event.actionMasked == MotionEvent.ACTION_DOWN && isAmbientActive) {
            isAmbientActive = false
            consumingAmbientTouch = true
        }
        if (consumingAmbientTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                consumingAmbientTouch = false
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        if (keyCode != KeyEvent.KEYCODE_BACK && (isAmbientActive || keyCode in ambientDismissKeys)) {
            ambientDismissKeys += keyCode
            isAmbientActive = false
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        if (ambientDismissKeys.remove(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }
}
