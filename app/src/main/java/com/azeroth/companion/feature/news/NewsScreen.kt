package com.azeroth.companion.feature.news

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.azeroth.companion.ui.components.WowLoading
import com.azeroth.companion.R
import com.azeroth.companion.data.NewsBlock
import com.azeroth.companion.data.NewsItem
import com.azeroth.companion.ui.components.Divider
import com.azeroth.companion.ui.components.EmptyState
import com.azeroth.companion.ui.components.Pill
import com.azeroth.companion.ui.components.Radius
import com.azeroth.companion.ui.components.Screen
import com.azeroth.companion.ui.components.ScreenTitle
import com.azeroth.companion.ui.components.Spacing
import java.time.Duration
import java.time.Instant

@Composable
fun NewsScreen(
    onOpenArticle: (String) -> Unit,
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Screen {
        item {
            ScreenTitle(
                stringResource(R.string.title_news),
                subtitle = stringResource(R.string.news_subtitle),
            )
        }

        when {
            state.loading -> item { Loading() }
            state.error != null -> item {
                EmptyState(
                    title = stringResource(R.string.news_error),
                    detail = state.error,
                )
            }
            state.items.isEmpty() -> item {
                EmptyState(title = stringResource(R.string.news_empty))
            }
            else -> {
                // La primera noticia va en grande: es la portada, y una lista
                // donde todo pesa igual no tiene portada.
                item { LeadStory(state.items.first(), onOpenArticle) }
                items(state.items.size - 1) { index ->
                    val item = state.items[index + 1]
                    Column {
                        Divider()
                        StoryRow(item, onOpenArticle)
                    }
                }
                item {
                    if (state.loadingMore) {
                        Loading()
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.lg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Pill(
                                stringResource(R.string.news_load_more),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { viewModel.loadMore() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(Spacing.xxl), Alignment.Center) {
        WowLoading()
    }
}

@Composable
private fun LeadStory(item: NewsItem, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.none))
            .clickable { onOpen(item.id) },
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(Radius.none))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
            Spacer(Modifier.height(Spacing.md))
        }
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        if (item.summary.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                item.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.publishedAt?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                relativeTime(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StoryRow(item: NewsItem, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.none))
            .clickable { onOpen(item.id) }
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 62.dp)
                    .clip(RoundedCornerShape(Radius.none))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            item.publishedAt?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    relativeTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ArticleScreen(articleId: String, viewModel: ArticleViewModel = hiltViewModel()) {
    LaunchedEffect(articleId) { viewModel.load(articleId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Screen {
        when {
            state.loading -> item { Loading() }
            state.error != null || state.article == null -> item {
                EmptyState(
                    title = stringResource(R.string.news_error),
                    detail = state.error,
                )
            }
            else -> {
                val article = state.article!!
                item {
                    Column {
                        Text(article.title, style = MaterialTheme.typography.headlineMedium)
                        article.publishedAt?.let {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                relativeTime(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(article.blocks.size) { index ->
                    ArticleBlock(article.blocks[index])
                }
                item {
                    Spacer(Modifier.height(Spacing.lg))
                    // La fuente siempre visible: la app muestra el texto, pero
                    // el artículo es de Blizzard y hay que poder ir al original.
                    Pill(
                        stringResource(R.string.news_open_original),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, article.url.toUri()),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleBlock(block: NewsBlock) {
    val context = LocalContext.current
    when (block) {
        is NewsBlock.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = Spacing.md),
        )
        is NewsBlock.Paragraph -> Text(
            block.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        is NewsBlock.Image -> AsyncImage(
            model = block.url,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.none))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        is NewsBlock.Link -> Pill(
            block.text,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, block.url.toUri()))
                }
            },
        )
        NewsBlock.Rule -> Divider(Modifier.padding(vertical = Spacing.sm))
    }
}

@Composable
private fun relativeTime(instant: Instant): String {
    val d = Duration.between(instant, Instant.now())
    val days = d.toDays()
    val hours = d.toHours()
    return when {
        days >= 2 -> stringResource(R.string.news_days_ago, days)
        days == 1L -> stringResource(R.string.news_yesterday)
        hours >= 1 -> stringResource(R.string.news_hours_ago, hours)
        else -> stringResource(R.string.news_just_now)
    }
}
