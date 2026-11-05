package com.youwhang.tryingsome.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.youwhang.tryingsome.AppGraph
import com.youwhang.tryingsome.api.Post
import com.youwhang.tryingsome.api.fileUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

object Downloader {
    /** 원본 파일을 사진/동영상/다운로드 폴더의 tryingsome 폴더에 저장하고, 저장 위치를 돌려줘요 */
    suspend fun save(context: Context, post: Post): String = withContext(Dispatchers.IO) {
        val url = post.fileUrl() ?: throw IOException("파일 주소가 없어요")
        val ext = post.file.ext.lowercase()
        val name = "e621_${post.id}.$ext"
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (ext == "swf") "application/x-shockwave-flash" else "application/octet-stream"

        val (collection: Uri, dir: String) = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
        }
        val relative = "$dir/tryingsome"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: throw IOException("저장 공간에 파일을 만들지 못했어요")
        try {
            AppGraph.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val body = r.body ?: throw IOException("빈 응답")
                resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) }
                    ?: throw IOException("파일을 열지 못했어요")
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        "$relative/$name"
    }
}
