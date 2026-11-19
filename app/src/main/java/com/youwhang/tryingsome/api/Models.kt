package com.youwhang.tryingsome.api

import com.youwhang.tryingsome.net.ImageQuality
import com.youwhang.tryingsome.net.VideoQuality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
data class Sample(
    val has: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val url: String? = null,
    // 형식이 바뀐 적이 있어서 직접 해석해요
    val alternates: JsonElement? = null,
)

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

/** 이미지 품질 설정에 맞는 주소. GIF는 샘플이 정지 이미지라서 항상 원본 */
fun Post.imageUrl(q: ImageQuality): String? = when {
    kind == Kind.GIF -> fileUrl()
    q == ImageQuality.ORIGINAL -> fileUrl() ?: sampleUrl()
    else -> sampleUrl()
}

private fun altUrl(e: JsonElement?): String? {
    val o = e as? JsonObject ?: return null
    (o["url"] as? JsonPrimitive)?.contentOrNull?.let { return it }
    val list = (o["urls"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: return null
    return list.firstOrNull { it.endsWith(".mp4") } ?: list.firstOrNull()
}

private fun Post.videoSample(key: String): String? {
    val alt = sample.alternates as? JsonObject ?: return null
    val samples = alt["samples"] as? JsonObject
    return altUrl(samples?.get(key)) ?: altUrl(alt[key])
}

/** 이 동영상에서 고를 수 있는 화질 */
fun Post.videoQualities(): List<VideoQuality> = buildList {
    if (videoSample("480p") != null) add(VideoQuality.P480)
    if (videoSample("720p") != null) add(VideoQuality.P720)
    add(VideoQuality.ORIGINAL)
}

/** 원하는 화질이 없으면 그보다 낮은 화질, 그것도 없으면 원본 */
fun Post.videoUrl(q: VideoQuality): String? = when (q) {
    VideoQuality.P480 -> videoSample("480p") ?: fileUrl()
    VideoQuality.P720 -> videoSample("720p") ?: videoSample("480p") ?: fileUrl()
    VideoQuality.ORIGINAL -> fileUrl()
}

// ---- 2026년 11월 e621 새 API 형식 ----
// posts.json이 [...] 배열로 오고, 파일 정보는 files, 점수는 stats 안에 들어 있어요.

@Serializable
data class NewPost(
    val id: Long,
    val files: NewFiles = NewFiles(),
    val stats: NewStats = NewStats(),
    val rating: String = "s",
    val sources: List<String> = emptyList(),
    val description: String = "",
    val tags: JsonElement? = null,
)

@Serializable
data class NewFiles(
    val meta: NewMeta = NewMeta(),
    val original: NewOriginal = NewOriginal(),
    val preview: NewImage = NewImage(),
    val sample: NewImage = NewImage(),
    val video: JsonElement? = null,
)

@Serializable
data class NewMeta(
    val md5: String? = null,
    val ext: String = "",
    val size: Long = 0,
    @SerialName("has_sample") val hasSample: Boolean = false,
)

@Serializable
data class NewOriginal(val width: Int = 0, val height: Int = 0, val url: String? = null)

@Serializable
data class NewImage(val width: Int = 0, val height: Int = 0, val jpg: String? = null, val webp: String? = null)

@Serializable
data class NewStats(
    val score: Score = Score(),
    @SerialName("fav_count") val favCount: Int = 0,
)

private fun strings(e: JsonElement?): List<String> =
    (e as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

/** 태그가 그냥 목록이면 전부 '일반'으로, 분류된 객체면 분류대로 */
fun parseTags(e: JsonElement?): Tags = when (e) {
    is JsonArray -> Tags(general = strings(e))
    is JsonObject -> Tags(
        general = strings(e["general"]),
        artist = strings(e["artist"]) + strings(e["contributor"]),
        copyright = strings(e["copyright"]),
        character = strings(e["character"]),
        species = strings(e["species"]),
        meta = strings(e["meta"]),
        lore = strings(e["lore"]),
        invalid = strings(e["invalid"]),
    )
    else -> Tags()
}

fun NewPost.toPost(): Post = Post(
    id = id,
    file = FileInfo(
        width = files.original.width,
        height = files.original.height,
        ext = files.meta.ext,
        size = files.meta.size,
        md5 = files.meta.md5,
        url = files.original.url,
    ),
    preview = Preview(files.preview.width, files.preview.height, files.preview.jpg ?: files.preview.webp),
    sample = Sample(
        has = files.meta.hasSample,
        width = files.sample.width,
        height = files.sample.height,
        url = files.sample.jpg ?: files.sample.webp,
        alternates = files.video,
    ),
    score = stats.score,
    tags = parseTags(tags),
    rating = rating,
    favCount = stats.favCount,
    description = description,
    sources = sources,
)
