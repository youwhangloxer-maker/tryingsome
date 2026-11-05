package com.youwhang.tryingsome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.youwhang.tryingsome.AppGraph
import com.youwhang.tryingsome.api.Kind
import com.youwhang.tryingsome.api.Post
import com.youwhang.tryingsome.api.kind
import com.youwhang.tryingsome.api.previewUrl

@Composable
fun SearchScreen(vm: MainViewModel, onOpen: (Int) -> Unit, onSettings: () -> Unit) {
    val gridState = rememberLazyGridState()
    val focus = LocalFocusManager.current

    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 12
        }
    }
    LaunchedEffect(nearEnd, vm.posts.size) {
        if (nearEnd) vm.loadMore()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("태그 검색 (예: wolf solo)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "지우기")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    vm.search(vm.query)
                }),
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "설정")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                state = gridState,
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                itemsIndexed(vm.posts, key = { _, p -> p.id }) { index, post ->
                    Thumb(post) {
                        focus.clearFocus()
                        onOpen(index)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Footer(vm)
                }
            }

            if (vm.suggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column {
                        vm.suggestions.take(8).forEach { s ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { vm.applySuggestion(s.name) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(s.name, color = tagColor(s.category), modifier = Modifier.weight(1f))
                                Text(formatCount(s.postCount), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Footer(vm: MainViewModel) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            vm.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vm.error ?: "", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::retry) { Text("다시 시도") }
            }
            vm.loading -> CircularProgressIndicator()
            vm.endReached && vm.posts.isEmpty() -> Text("검색 결과가 없어요")
            vm.endReached -> Text("마지막이에요", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Thumb(post: Post, onClick: () -> Unit) {
    Box(
        Modifier.padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        val url = post.previewUrl()
        if (url != null) {
            AsyncImage(
                model = url,
                imageLoader = AppGraph.loader(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                post.file.ext.uppercase().ifEmpty { "?" },
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        val badge = when (post.kind) {
            Kind.VIDEO -> "VIDEO"
            Kind.FLASH -> "FLASH"
            Kind.GIF -> "GIF"
            Kind.IMAGE -> null
        }
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Text(
            "▲${post.score.total}",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
