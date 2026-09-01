package com.antigravity.shieldx.ui.blocked

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.ui.components.PrimaryButton
import com.antigravity.shieldx.ui.theme.*

/**
 * Shown when a request is blocked. It appears at a bad moment for the user, so
 * the tone is matter-of-fact: what was blocked, why, and a way out. No alarm
 * graphics, no lecture.
 */
class BlockInterceptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = intent.getStringExtra("EXTRA_TARGET").orEmpty()
        val reason = intent.getStringExtra("EXTRA_REASON")
            ?: "This site is on your blocked list."

        setContent {
            TarziTheme {
                BlockInterceptScreen(
                    target = target,
                    reason = reason,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun BlockInterceptScreen(
    target: String,
    reason: String,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .padding(Space.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(SurfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Block,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(Modifier.height(Space.xl))

        Text(
            text = "Blocked",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )

        if (target.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                text = target,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(Space.md))

        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Space.section))

        PrimaryButton(
            text = "Go back",
            onClick = onClose,
            modifier = Modifier.widthIn(max = 260.dp)
        )
    }
}
