package com.capivaraec.smwc.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.capivaraec.smwc.app.SmwcApplication
import com.capivaraec.smwc.app.ui.components.isWideLayout
import com.capivaraec.smwc.core.ra.RaAchievement
import com.capivaraec.smwc.core.ra.RaComment

/**
 * The comments players left on an achievement's RetroAchievements page, oldest first. On wide
 * screens the achievement stays beside the comments.
 */
@Composable
fun AchievementCommentsScreen(app: SmwcApplication, gameId: Long, achievementId: Long, onBack: () -> Unit) {
    val vm: AchievementCommentsViewModel = viewModel(key = "ra-comments-$achievementId") { AchievementCommentsViewModel(app, gameId, achievementId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val wide = isWideLayout()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                state.comments?.let { if (it.size == 1) "1 comment" else "${it.size} comments" } ?: "Comments",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Pull down to fetch the comments again.
        PullToRefreshBox(isRefreshing = state.loading && state.comments != null, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        AchievementHeader(state.achievement)
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { comments(state, onRetry = vm::load) }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") { AchievementHeader(state.achievement, Modifier.padding(bottom = 8.dp)) }
                    comments(state, onRetry = vm::load)
                }
            }
        }
    }
}

private fun LazyListScope.comments(state: AchievementCommentsUiState, onRetry: () -> Unit) {
    val comments = state.comments
    when {
        comments == null && state.loading -> item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        comments == null -> item(key = "error") {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(state.error ?: "Could not load the comments", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
        else -> {
            // A failed refresh keeps the comments already shown.
            state.error?.let { error -> item(key = "error") { Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } }
            if (comments.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No comments yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            itemsIndexed(comments, key = { index, _ -> index }) { _, comment -> CommentItem(comment) }
        }
    }
}

/** Badge, title, description and points; nothing until the set has loaded. */
@Composable
private fun AchievementHeader(achievement: RaAchievement?, modifier: Modifier = Modifier) {
    achievement ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        AsyncImage(
            model = achievement.badgeUrl,
            contentDescription = null,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .alpha(if (achievement.earned) 1f else 0.35f),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(achievement.title, style = MaterialTheme.typography.titleLarge)
            achievement.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                listOfNotNull(
                    "${achievement.points} pts",
                    if (achievement.earnedHardcoreAt != null) "Unlocked in hardcore" else "Unlocked".takeIf { achievement.earned },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CommentItem(comment: RaComment) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.user,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                comment.submittedAt?.let {
                    Text(formatCommentDate(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SelectionContainer { Text(rememberTextWithLinks(comment.text), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
