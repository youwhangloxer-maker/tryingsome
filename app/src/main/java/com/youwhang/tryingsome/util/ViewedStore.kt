package com.youwhang.tryingsome.util

import androidx.compose.runtime.mutableStateMapOf
import java.io.File
import java.util.concurrent.Executors

/** 이미 본 게시물 번호를 기억해요. 파일에 한 줄씩 추가로 저장해요 */
class ViewedStore(dir: File) {
    private val file = File(dir, "viewed.txt")
    private val io = Executors.newSingleThreadExecutor()
    private val ids = mutableStateMapOf<Long, Boolean>()

    init {
        if (file.exists()) {
            runCatching {
                file.forEachLine { line -> line.trim().toLongOrNull()?.let { ids[it] = true } }
            }
        }
    }

    val count: Int get() = ids.size

    fun isViewed(id: Long): Boolean = ids[id] == true

    fun mark(id: Long) {
        if (ids.put(id, true) != null) return
        io.execute { runCatching { file.appendText("$id\n") } }
    }

    fun clear() {
        ids.clear()
        io.execute { runCatching { file.delete() } }
    }
}
