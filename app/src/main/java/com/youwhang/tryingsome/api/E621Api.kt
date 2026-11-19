package com.youwhang.tryingsome.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class E621Api(private val client: OkHttpClient, private val baseUrl: String) {

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }

        // e621 규칙: 초당 최대 2회 요청
        private val throttle = Mutex()
        private var lastRequest = 0L
    }

    suspend fun posts(tags: String, page: Int, limit: Int = 60): List<Post> {
        val url = "$baseUrl/posts.json".toHttpUrl().newBuilder()
            .addQueryParameter("tags", tags)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("mode", "extended") // 새 형식에서 태그 분류를 받기 위해
            .build()
        return parsePosts(get(url))
    }

    suspend fun autocomplete(query: String): List<TagSuggestion> {
        val url = "$baseUrl/tags/autocomplete.json".toHttpUrl().newBuilder()
            .addQueryParameter("search[name_matches]", query)
            .addQueryParameter("expiry", "7")
            .build()
        val body = get(url).trim()
        if (!body.startsWith("[")) return emptyList()
        return json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(TagSuggestion.serializer()), body
        )
    }

    /** 옛 형식({"posts":[...]})과 새 형식([...], files/stats) 모두 읽어요. 이상한 게시물 하나 때문에 전체가 실패하지 않게 하나씩 해석 */
    private fun parsePosts(body: String): List<Post> {
        val root = json.parseToJsonElement(body)
        val list = when (root) {
            is JsonArray -> root
            is JsonObject -> root["posts"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        var firstError: Throwable? = null
        val posts = list.mapNotNull { el ->
            runCatching {
                if ((el as? JsonObject)?.containsKey("files") == true) {
                    json.decodeFromJsonElement(NewPost.serializer(), el).toPost()
                } else {
                    json.decodeFromJsonElement(Post.serializer(), el)
                }
            }.onFailure { if (firstError == null) firstError = it }.getOrNull()
        }
        if (posts.isEmpty() && list.isNotEmpty()) {
            throw IOException("e621 응답 형식을 읽지 못했어요: ${firstError?.message ?: "알 수 없는 오류"}")
        }
        return posts
    }

    private suspend fun get(url: HttpUrl): String {
        throttle.withLock {
            val wait = 550 - (System.currentTimeMillis() - lastRequest)
            if (wait > 0) delay(wait)
            lastRequest = System.currentTimeMillis()
        }
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("서버 응답 오류 (HTTP ${r.code})")
                r.body?.string() ?: throw IOException("빈 응답")
            }
        }
    }
}

fun friendlyError(e: Throwable): String = when (e) {
    is UnknownHostException -> "주소를 찾지 못했어요. 인터넷 연결이나 설정의 DNS를 확인해 주세요."
    is SocketTimeoutException -> "응답이 너무 늦어요. 다시 시도하거나 설정에서 우회 방식을 바꿔 보세요."
    is SSLException, is SocketException ->
        "연결이 끊겼어요. 통신사 차단일 수 있으니 설정에서 우회 방식을 바꿔 보세요. (${e.javaClass.simpleName})"
    else -> e.message ?: e.javaClass.simpleName
}
