package com.youwhang.tryingsome.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youwhang.tryingsome.AppGraph
import com.youwhang.tryingsome.api.Post
import com.youwhang.tryingsome.api.TagSuggestion
import com.youwhang.tryingsome.api.friendlyError
import com.youwhang.tryingsome.api.isBlocked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    val posts = mutableStateListOf<Post>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set
    var suggestions by mutableStateOf<List<TagSuggestion>>(emptyList())
        private set

    private var currentTags = ""
    private var page = 1
    private val seen = HashSet<Long>()
    private var job: Job? = null
    private var generation = 0
    private var suggestJob: Job? = null

    init {
        search("")
    }

    fun search(tags: String) {
        generation++
        job?.cancel()
        suggestJob?.cancel()
        suggestions = emptyList()
        query = tags
        currentTags = tags.trim()
        posts.clear()
        seen.clear()
        page = 1
        endReached = false
        error = null
        loading = false
        loadMore()
    }

    fun reload() = search(query)

    fun retry() {
        error = null
        loadMore()
    }

    fun loadMore() {
        if (loading || endReached || error != null) return
        loading = true
        val gen = generation
        job = viewModelScope.launch {
            try {
                // 블랙리스트로 다 걸러지면 몇 페이지 더 시도
                var tries = 0
                while (tries < 4) {
                    val result = AppGraph.api.posts(currentTags, page)
                    page++
                    if (result.isEmpty()) {
                        endReached = true
                        break
                    }
                    val bl = AppGraph.settings.blacklistSet()
                    val fresh = result.filter { seen.add(it.id) && !it.isBlocked(bl) }
                    posts.addAll(fresh)
                    if (fresh.isNotEmpty()) break
                    tries++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                if (gen == generation) loading = false
            }
        }
    }

    fun onQueryChange(text: String) {
        query = text
        suggestJob?.cancel()
        val last = text.substringAfterLast(' ').trimStart('-', '~')
        if (last.length < 3 || text.endsWith(" ")) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            suggestions = try {
                AppGraph.api.autocomplete(last)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun applySuggestion(name: String) {
        suggestJob?.cancel()
        val lastToken = query.substringAfterLast(' ')
        val prefixChars = lastToken.takeWhile { it == '-' || it == '~' }
        val head = query.dropLast(lastToken.length)
        query = "$head$prefixChars$name "
        suggestions = emptyList()
    }
}
