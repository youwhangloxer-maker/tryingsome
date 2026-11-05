package com.youwhang.tryingsome.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostsResponse(val posts: List<Post> = emptyList())

@Serializable
data class Post(
    val id: Long,
    val file: FileInfo = FileInfo(),
    val preview: Preview = Preview(),
    val sample: Sample = Sample(),
    val score: Score = Score(),
    val tags: Tags = Tags(),
    val rating: String = "s",
    @SerialName("fav_count") val favCount: Int = 0,
    val description: String = "",
    val sources: List<String> = emptyList(),
)

@Serializable
data class FileInfo(
    val width: Int = 0,
    val height: Int = 0,
    val ext: String = "",
    val size: Long = 0,
    val md5: String? = null,
    val url: String? = null,
)

@Serializable
data class Preview(val width: Int = 0, val height: Int = 0, val url: String? = null)

@Serializable
data class Sample(val has: Boolean = false, val width: Int = 0, val height: Int = 0, val url: String? = null)

@Serializable
data class Score(val up: Int = 0, val down: Int = 0, val total: Int = 0)

@Serializable
data class Tags(
    val general: List<String> = emptyList(),
    val artist: List<String> = emptyList(),
    val copyright: List<String> = emptyList(),
    val character: List<String> = emptyList(),
    val species: List<String> = emptyList(),
    val meta: List<String> = emptyList(),
    val lore: List<String> = emptyList(),
    val invalid: List<String> = emptyList(),
) {
    fun all(): List<String> = general + artist + copyright + character + species + meta + lore + invalid
}

@Serializable
data class TagSuggestion(
    val name: String,
    @SerialName("post_count") val postCount: Int = 0,
    val category: Int = 0,
)

enum class Kind { IMAGE, GIF, VIDEO, FLASH }

val Post.kind: Kind
    get() = when (file.ext.lowercase()) {
        "webm", "mp4" -> Kind.VIDEO
        "swf" -> Kind.FLASH
        "gif" -> Kind.GIF
        else -> Kind.IMAGE
    }

private const val STATIC = "https://static1.e621.net/data"

private fun md5Path(md5: String) = "${md5.substring(0, 2)}/${md5.substring(2, 4)}/$md5"

/** 비로그인 상태에서 주소가 숨겨진 게시물은 md5로 주소를 만들어요 */
fun Post.fileUrl(): String? =
    file.url ?: file.md5?.takeIf { it.length > 4 && file.ext.isNotEmpty() }?.let { "$STATIC/${md5Path(it)}.${file.ext}" }

fun Post.previewUrl(): String? =
    preview.url ?: file.md5?.takeIf { it.length > 4 && kind != Kind.FLASH }?.let { "$STATIC/preview/${md5Path(it)}.jpg" }

fun Post.sampleUrl(): String? {
    if (sample.has) {
        sample.url?.let { return it }
        file.md5?.takeIf { it.length > 4 }?.let { return "$STATIC/sample/${md5Path(it)}.jpg" }
    }
    return when (kind) {
        Kind.IMAGE, Kind.GIF -> fileUrl()
        else -> previewUrl()
    }
}

fun Post.isBlocked(blacklist: Set<String>): Boolean {
    if (blacklist.isEmpty()) return false
    if ("rating:$rating" in blacklist) return true
    return tags.all().any { it.lowercase() in blacklist }
}
