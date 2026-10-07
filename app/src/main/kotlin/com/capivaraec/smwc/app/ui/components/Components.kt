package com.capivaraec.smwc.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.capivaraec.smwc.app.R
import com.capivaraec.smwc.app.ui.theme.LocalSmwcColors
import com.capivaraec.smwc.core.model.Difficulty

/** Wide screens (landscape phones, the AYN Thor's top screen, tablets) show a list and its details side by side. */
@Composable
fun isWideLayout(): Boolean = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp }

/** A hack's screenshot, cropped to fill; a plain tile while it loads or when there is none. */
@Composable
fun Thumbnail(url: String?, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** A small rounded label, e.g. "20 exits" or "★ 4.8". */
@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null,
) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(container).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

/** The difficulty, colored by level from green (Newcomer) to purple (Grandmaster). */
@Composable
fun DifficultyChip(id: String?, modifier: Modifier = Modifier) {
    val name = Difficulty.displayName(id) ?: return
    val (container, content) = LocalSmwcColors.current.difficulty(id)
    InfoChip(name, modifier, container, content)
}

/** "This hack has a RetroAchievements set", with a short detail such as "38 · 400 pts". */
@Composable
fun RaChip(text: String, modifier: Modifier = Modifier) {
    InfoChip(
        text,
        modifier,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = Icons.Filled.EmojiEvents,
    )
}

/** An attention badge above a title, e.g. "NEW VERSION". */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondary,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.secondary).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A small upper-case heading for a group, e.g. "NOTES". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** A row of equal segments with one selected, e.g. Backlog / Playing / Completed. */
@Composable
fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).height(40.dp).semantics {
                    role = Role.Tab
                    this.selected = isSelected
                },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                    // Shrinks to fit narrow panes (e.g. beside a detail pane) instead of cutting the text.
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                    )
                }
            }
        }
    }
}

/** An outlined pill button for list controls (sort, filters); highlighted when [active]. */
@Composable
fun ControlChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, active: Boolean = false) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = color,
        modifier = modifier.height(36.dp).border(1.dp, if (active) color else MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** A thin rounded progress bar. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

/** For short confirmations ("Saved…", "Added…") shown at the bottom of the screen. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/**
 * A list with its details beside it, for wide screens. [detail] null shows [emptyText] in the
 * detail pane instead.
 */
@Composable
fun ListDetailLayout(
    list: @Composable () -> Unit,
    detail: (@Composable () -> Unit)?,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        // About 45% for the list, but never so narrow that its cards' buttons get cut.
        val listWidth = (maxWidth * 0.45f).coerceIn(360.dp, 480.dp).coerceAtMost(maxWidth * 0.6f)
        Row {
            Box(Modifier.width(listWidth)) { list() }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f)) {
                if (detail != null) {
                    detail()
                } else {
                    Text(
                        emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
            }
        }
    }
}

/** The app's mark: the gold block with a checklist from the launcher icon. */
@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Image(painterResource(R.drawable.app_mark), contentDescription = null, modifier = modifier.size(size))
}
