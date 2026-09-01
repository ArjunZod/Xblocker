package com.antigravity.shieldx.music

import android.content.Context
import com.antigravity.shieldx.core.runtime.ContentPolicyGate
import com.antigravity.shieldx.core.runtime.MusicController
import com.antigravity.shieldx.data.local.AppDatabase

/**
 * Everything TaRZI-Music owns. Takes a ContentPolicyGate rather than App's
 * concrete PolicyEngine — the same contract MusicControllerImpl depends on.
 */
class MusicGraph(context: Context, policyGate: ContentPolicyGate, database: AppDatabase) {

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
    val playHistoryRepository = PlayHistoryRepository(database.playHistoryDao())
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
