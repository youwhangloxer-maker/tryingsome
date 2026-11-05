package com.youwhang.tryingsome.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
            .build()
        return json.decodeFromString(PostsResponse.serializer(), get(url)).posts
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
