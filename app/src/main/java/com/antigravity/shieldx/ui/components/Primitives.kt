package com.antigravity.shieldx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.ui.theme.*

/**
 * The component vocabulary. Screens compose these rather than inventing their
 * own boxes, which is what kept producing a different visual language on every
 * screen.
 */

// ============================================================================
// Structure
// ============================================================================

/**
 * A screen's title block. One per screen, at the top, always in the same place
 * so the eye knows where to start.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.gutter)
            .padding(top = if (onBack != null) Space.md else Space.lg, bottom = Space.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(Radius.sm)
                    .background(SurfaceRaised)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(Space.md))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.md))
            trailing()
        }
    }
}

/**
 * Groups rows under a quiet heading. Sentence case, not a shouted label - the
 * heading orients, it does not compete with the content beneath it.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .padding(top = Space.section, bottom = Space.md)
    )
}

/**
 * A grouped container for related rows, the way platform settings group things.
 * One border around the group rather than a border around every row - that is
 * the difference between a settings list and a wall of tiles.
 */
@Composable
fun Grouped(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clip(Radius.md)
            .background(Surface)
            .border(1.dp, Border, Radius.md)
    ) {
        content()
    }
}

/** Hairline between rows inside a [Grouped]. Inset to align with row text. */
@Composable
fun RowDivider(insetStart: androidx.compose.ui.unit.Dp = Space.lg) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = insetStart)
            .height(1.dp)
            .background(Border)
    )
}

// ============================================================================
// Rows
// ============================================================================

/**
 * The workhorse: a settings-style row. An optional leading icon, a title, an
 * optional description, and one trailing control or value.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = TextSecondary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Space.md))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(Space.md))
            trailing()
        }
    }
}

/** A row whose trailing control is a switch. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SettingRow(
        title = title,
        description = description,
        icon = icon,
        modifier = modifier,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    checkedBorderColor = Accent,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = SurfaceRaised,
                    uncheckedBorderColor = Border
                )
            )
        }
    )
}

/** Right-aligned secondary text, for a row that reports a value. */
@Composable
fun RowValue(text: String, tint: Color = TextSecondary) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = tint
    )
}

// ============================================================================
// Status
// ============================================================================

enum class StatusTone { Neutral, Positive, Caution, Critical }

/**
 * A small chip reporting real state. Colour here is information, so it is one
 * of four tones and never chosen for looks.
 */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val (fg, bg) = when (tone) {
        StatusTone.Neutral -> TextSecondary to SurfaceRaised
        StatusTone.Positive -> Success to SuccessMuted
        StatusTone.Caution -> Warning to WarningMuted
        StatusTone.Critical -> Danger to DangerMuted
    }

    Box(
        modifier = modifier
            .clip(Radius.full)
            .background(bg)
            .padding(horizontal = Space.md, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}

// ============================================================================
// Actions
// ============================================================================

/** The one primary action on a screen. Filled, accent, full width. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Color = Accent
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.sm)
            .background(if (enabled) tone else SurfaceRaised)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Color.White else TextTertiary
        )
    }
}

/** A quieter action: outlined, for anything that is not the primary path. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(Radius.sm)
            .background(SurfaceRaised)
            .border(1.dp, Border, Radius.sm)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.lg, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) TextPrimary else TextTertiary
        )
    }
}

// ============================================================================
// States
// ============================================================================

/**
 * Empty, error, and permission-required states all share this shape so an
 * unusual state never looks like a broken screen.
 */
@Composable
fun StateMessage(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.section, vertical = Space.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(Space.md))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        if (description != null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Space.lg))
            action()
        }
    }
}

/** Standard bottom padding for scrollable lists inside Scaffold. */
val ContentBottomPadding = PaddingValues(bottom = Space.lg)

// ============================================================================
// Assistant Conversation Components
// ============================================================================

/**
 * Contextual suggestion chip row (Gemini / Claude style).
 */
@Composable
fun SuggestionChipRow(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        items(suggestions.size) { index ->
            val text = suggestions[index]
            Box(
                modifier = Modifier
                    .clip(Radius.full)
                    .background(Surface)
                    .border(1.dp, Border, Radius.full)
                    .clickable { onSuggestionClick(text) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * Interactive verified action execution tile.
 */
@Composable
fun ActionExecutionCard(
    toolName: String,
    summary: String,
    modifier: Modifier = Modifier,
    statusTone: StatusTone = StatusTone.Positive,
    actionButton: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.md)
            .background(Surface)
            .border(1.dp, Border, Radius.md)
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    text = toolName.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    tone = statusTone
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
        if (actionButton != null) {
            Spacer(Modifier.width(Space.md))
            actionButton()
        }
    }
}

/**
 * Inline reassuring confirmation prompt for sensitive actions.
 */
@Composable
fun ConfirmationPromptCard(
    prompt: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.md)
            .background(Surface)
            .border(1.dp, AccentMuted, Radius.md)
            .padding(Space.lg)
    ) {
        Text(
            text = "Action Confirmation Required",
            style = MaterialTheme.typography.labelMedium,
            color = Accent
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(Space.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(Radius.sm)
                    .background(SurfaceRaised)
                    .clickable(onClick = onCancel)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(Radius.sm)
                    .background(Accent)
                    .clickable(onClick = onConfirm)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Confirm", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}
