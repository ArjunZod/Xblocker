package com.antigravity.shieldx.ui.music

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.antigravity.shieldx.music.MusicApp
import com.antigravity.shieldx.music.MusicGraph
import com.antigravity.shieldx.ui.theme.Canvas
import com.antigravity.shieldx.ui.theme.TarziTheme
import kotlinx.coroutines.launch

/**
 * The standalone TaRZI Music app's only screen host.
 *
 * It composes the same MusicHomeScreen / MiniPlayerBar / NowPlayingScreen the
 * all-in-one build uses — there is one implementation of each, shared.
 *
 * It also answers the platform's "play something" search intent, which is how
 * the assistant app asks this one to play a track across the process boundary.
 */
class MusicActivity : ComponentActivity() {

    private var pendingSearch by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingSearch = searchQueryFrom(intent)

        setContent {
            TarziTheme {
                MusicAppRoot(
                    graph = MusicApp.instance.graph,
                    pendingSearch = pendingSearch,
                    onSearchHandled = { pendingSearch = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        searchQueryFrom(intent)?.let { pendingSearch = it }
    }

    /**
     * Accepts both the platform media-search action and a plain query extra, so
     * a caller can say "play kesariya" without knowing anything about TaRZI.
     */
    private fun searchQueryFrom(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH,
            Intent.ACTION_SEARCH,
            ACTION_PLAY_QUERY ->
                intent.getStringExtra(SearchManagerQuery)
                    ?: intent.getStringExtra(EXTRA_QUERY)
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    companion object {
        /** Explicit action for TaRZI-to-TaRZI calls. */
        const val ACTION_PLAY_QUERY = "com.antigravity.tarzi.music.action.PLAY_QUERY"
        const val EXTRA_QUERY = "query"
        private const val SearchManagerQuery = "query"
    }
}

@Composable
private fun MusicAppRoot(
    graph: MusicGraph,
    pendingSearch: String?,
    onSearchHandled: () -> Unit
) {
    val playbackState by graph.musicController.playbackStateFlow.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(pendingSearch) {
        val query = pendingSearch ?: return@LaunchedEffect
        scope.launch { graph.musicController.searchAndPlay(query) }
        onSearchHandled()
    }

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                if (playbackState.currentTrack != null) {
                    MiniPlayerBar(
                        playbackState = playbackState,
                        musicGraph = graph,
                        onExpandClick = { showFullPlayer = true }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MusicHomeScreen(
                musicGraph = graph,
                onOpenFullPlayer = { showFullPlayer = true }
            )
        }
    }

    if (showFullPlayer) {
        NowPlayingScreen(
            playbackState = playbackState,
            musicGraph = graph,
            onDismiss = { showFullPlayer = false }
        )
    }
}
