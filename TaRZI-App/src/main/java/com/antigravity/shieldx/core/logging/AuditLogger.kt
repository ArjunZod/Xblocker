package com.antigravity.shieldx.core.logging

import java.net.URI

/**
 * Redaction utility ensuring privacy compliance and zero credential leakage.
 */
object Redactor {

    private val SENSITIVE_PARAM_KEYS = setOf(
        "password", "pass", "pwd", "token", "auth", "secret", "apikey", "api_key",
        "access_token", "session", "code", "pin", "ssn", "credit_card"
    )

    /**
     * Redact sensitive query parameters from URLs.
     */
    fun redactUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return try {
            val uri = URI(rawUrl)
            val host = uri.host ?: ""
            val path = uri.path ?: ""
            val query = uri.query

            if (query.isNullOrEmpty()) {
                "$host$path"
            } else {
                val params = query.split('&')
                val sanitizedParams = params.map { param ->
                    val parts = param.split('=', limit = 2)
                    val key = parts[0].lowercase()
                    if (SENSITIVE_PARAM_KEYS.contains(key) || key.contains("key") || key.contains("token")) {
                        "${parts[0]}=[REDACTED]"
                    } else if (parts.size > 1 && parts[1].length > 40) {
                        "${parts[0]}=[REDACTED_BLOB]"
                    } else {
                        param
                    }
                }
                "$host$path?${sanitizedParams.joinToString("&")}"
            }
        } catch (_: Exception) {
            // Fallback for malformed URLs: return first 32 chars followed by [REDACTED]
            if (rawUrl.length > 32) "${rawUrl.take(32)}...[REDACTED]" else rawUrl
        }
    }

    /**
     * Truncate snippet length to prevent storing raw explicit blobs.
     */
    fun redactContentSnippet(snippet: String, maxLength: Int = 64): String {
        if (snippet.length <= maxLength) return snippet
        return "${snippet.take(maxLength)}...[TRUNCATED]"
    }
}
