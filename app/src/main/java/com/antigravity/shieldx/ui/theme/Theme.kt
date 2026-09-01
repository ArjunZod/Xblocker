package com.antigravity.shieldx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Tarzi is a dark-only product. Offering a half-finished light theme would be
 * worse than committing to one that is properly tuned, so the scheme is fixed.
 */
@Composable
fun TarziTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TarziColorScheme,
        typography = TarziTypography,
        content = content
    )
}
