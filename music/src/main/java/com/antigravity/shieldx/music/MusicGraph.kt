package com.antigravity.shieldx.music

import android.content.Context
import com.antigravity.shieldx.core.runtime.ContentPolicyGate
import com.antigravity.shieldx.core.runtime.MusicController

/**
 * Everything TaRZI-Music owns. Takes a ContentPolicyGate rather than App's
 * concrete PolicyEngine, and a PlayHistoryDao rather than App's AppDatabase —
 * :music must not depend on :app.
 */
class MusicGraph(context: Context, policyGate: ContentPolicyGate, playHistoryDao: PlayHistoryDao) {

    val innerTubeClient = InnerTubeClient()
    val lavalinkNodeManager = LavalinkNodeManager()
    val musicResolver = MusicIdentifierResolver(lavalinkNodeManager, innerTubeClient)
    val musicSearchEngine = TaRziMusicSearchEngine(
        listOf(
            YouTubeMusicAdapter(innerTubeClient),
            SpotifyCatalogAdapter(),
            DeezerCatalogAdapter()
        )
    )
    val playHistoryRepository = PlayHistoryRepository(playHistoryDao)
    val musicLibraryRepository = MusicLibraryRepository(context, playHistoryRepository)
    val lyricsService = LyricsService()

    val musicController: MusicController = MusicControllerImpl(
        context = context,
        searchEngine = musicSearchEngine,
        resolver = musicResolver,
        lyricsService = lyricsService,
        policyGate = policyGate,
        playHistoryRepository = playHistoryRepository,
        libraryRepository = musicLibraryRepository
    )
}
