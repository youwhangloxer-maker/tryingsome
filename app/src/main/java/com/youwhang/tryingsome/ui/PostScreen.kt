package com.youwhang.tryingsome.ui

import android.content.Intent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.youwhang.tryingsome.AppGraph
import com.youwhang.tryingsome.api.Kind
import com.youwhang.tryingsome.api.Post
import com.youwhang.tryingsome.api.fileUrl
import com.youwhang.tryingsome.api.friendlyError
import com.youwhang.tryingsome.api.kind
import com.youwhang.tryingsome.api.previewUrl
import com.youwhang.tryingsome.api.sampleUrl
import com.youwhang.tryingsome.flash.FlashActivity
import com.youwhang.tryingsome.util.Downloader
import kotlinx.coroutines.launch

@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostScreen(vm: MainViewModel, startIndex: Int, onBack: () -> Unit, onTag: (String) -> Unit) {
    val posts = vm.posts
    if (posts.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, posts.size - 1)) { posts.size }
    var zoomUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage >= posts.size - 5) vm.loadMore()
    }

    Scaffold(
        topBar = {
            val current = posts.getOrNull(pager.currentPage)
            TopAppBar(
                title = { Text(current?.let { "#${it.id}" } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://e621.net/posts/${current.id}")
                            }
                            context.startActivity(Intent.createChooser(send, "링크 공유"))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "공유")
                        }
                    }
                },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.padding(padding).fillMaxSize(),
            key = { posts.getOrNull(it)?.id ?: it.toLong() },
            beyondViewportPageCount = 1,
        ) { page ->
            val post = posts.getOrNull(page) ?: return@HorizontalPager
            PostPage(
                post = post,
                isCurrent = pager.currentPage == page,
                onTag = onTag,
                onZoom = { zoomUrl = it },
            )
        }
    }

    zoomUrl?.let { url -> ZoomDialog(url) { zoomUrl = null } }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostPage(post: Post, isCurrent: Boolean, onTag: (String) -> Unit, onZoom: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ratio = if (post.file.width > 0 && post.file.height > 0) {
        (post.file.width.toFloat() / post.file.height).coerceIn(0.4f, 3f)
    } else 1f
    val mediaModifier = Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.Black)

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
        item {
            when (post.kind) {
                Kind.IMAGE, Kind.GIF -> {
                    val url = if (post.kind == Kind.GIF) post.fileUrl() else post.sampleUrl()
                    if (url == null) {
                        Missing(mediaModifier)
                    } else {
                        AsyncImage(
                            model = url,
                            imageLoader = AppGraph.loader(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = mediaModifier.clickable { onZoom(post.fileUrl() ?: url) },
                        )
                    }
                }
                Kind.VIDEO -> {
                    val url = post.fileUrl()
                    when {
                        url == null -> Missing(mediaModifier)
                        isCurrent -> VideoPlayer(url, mediaModifier)
                        else -> AsyncImage(
                            model = post.sampleUrl(),
                            imageLoader = AppGraph.loader(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = mediaModifier,
                        )
                    }
                }
                Kind.FLASH -> FlashCard(post, Modifier.fillMaxWidth().aspectRatio(ratio.coerceAtLeast(1f)).background(Color.Black))
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "▲ ${post.score.total}   ♥ ${post.favCount}   등급 ${ratingLabel(post.rating)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${post.file.width}×${post.file.height} · ${post.file.ext.uppercase()} · ${sizeLabel(post.file.size)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = {
                    scope.launch {
                        val msg = try {
                            "저장했어요: " + Downloader.save(context, post)
                        } catch (e: Exception) {
                            "저장 실패: " + friendlyError(e)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                    Toast.makeText(context, "저장 중…", Toast.LENGTH_SHORT).show()
                }) { Text("저장") }
            }
        }

        val groups = listOf(
            "작가" to (post.tags.artist to 1),
            "저작권" to (post.tags.copyright to 3),
            "캐릭터" to (post.tags.character to 4),
            "종" to (post.tags.species to 5),
            "일반" to (post.tags.general to 0),
            "설정" to (post.tags.lore to 8),
            "메타" to (post.tags.meta to 7),
        ).filter { it.second.first.isNotEmpty() }

        groups.forEach { (label, pair) ->
            val (tags, category) = pair
            item(key = "tags-$label") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { tag ->
                            SuggestionChip(
                                onClick = { onTag(tag) },
                                label = { Text(tag, color = tagColor(category)) },
                            )
                        }
                    }
                }
            }
        }

        if (post.description.isNotBlank()) {
            item {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("설명", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(post.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (post.sources.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("출처", style = MaterialTheme.typography.titleSmall)
                    post.sources.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Missing(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            "파일을 불러올 수 없어요\n(삭제됐거나 로그인이 필요한 게시물)",
            textAlign = TextAlign.Center,
            color = Color.White,
        )
    }
}

@Composable
private fun FlashCard(post: Post, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier, contentAlignment = Alignment.Center) {
        post.previewUrl()?.let {
            AsyncImage(
                model = it,
                imageLoader = AppGraph.loader(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = 0.4f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val url = post.fileUrl()
        if (url == null) {
            Text("플래시 파일 주소를 받을 수 없어요", color = Color.White)
        } else {
            Button(onClick = {
                context.startActivity(
                    Intent(context, FlashActivity::class.java)
                        .putExtra(FlashActivity.EXTRA_URL, url)
                        .putExtra(FlashActivity.EXTRA_ID, post.id)
                )
            }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("플래시 실행 (${sizeLabel(post.file.size)})")
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        val dataSource = OkHttpDataSource.Factory(AppGraph.client)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                prepare()
            }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = modifier,
    )
}

@Composable
private fun ZoomDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = { onDismiss() },
                    )
                },
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).size(4096).build(),
                imageLoader = AppGraph.loader(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
            )
        }
    }
}

private fun ratingLabel(r: String) = when (r) {
    "s" -> "전체"
    "q" -> "주의"
    "e" -> "성인"
    else -> r
}

fun sizeLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format("%.1fMB", bytes / 1024f / 1024f)
    bytes >= 1024 -> "${bytes / 1024}KB"
    else -> "${bytes}B"
}
