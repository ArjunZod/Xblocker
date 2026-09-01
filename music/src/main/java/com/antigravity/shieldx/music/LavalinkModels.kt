package com.antigravity.shieldx.music

enum class LavalinkNodeStatus {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
    RECOVERING
}

data class LavalinkNode(
    val id: String,
    val host: String,
    val port: Int = 443,
    val password: String = "youshallnotpass",
    val secure: Boolean = true,
    var status: LavalinkNodeStatus = LavalinkNodeStatus.HEALTHY,
    var latencyMs: Long = 0L,
    var failureCount: Int = 0,
    var lastSuccessTime: Long = 0L,
    var version: String = "v4"
) {
    val httpBaseUrl: String
        get() = "${if (secure) "https" else "http"}://$host:$port"

    val wsBaseUrl: String
        get() = "${if (secure) "wss" else "ws"}://$host:$port"
}

data class LavalinkTrackInfo(
    val encoded: String = "",
    val identifier: String = "",
    val isSeekable: Boolean = true,
    val author: String = "",
    val length: Long = 0L,
    val isStream: Boolean = false,
    val title: String = "",
    val uri: String = "",
    val sourceName: String = "",
    val artworkUrl: String? = null
)

data class LavalinkLoadResult(
    val loadType: String, // track, playlist, search, empty, error
    val data: Any? = null
)
