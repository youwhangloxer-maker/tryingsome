package com.youwhang.tryingsome.video

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.youwhang.tryingsome.AppGraph

const val SEEK_MS = 5_000L

/** 앱 네트워크(우회 기능 포함)로 영상을 받는 플레이어. 되감기/빨리감기는 양쪽 다 5초 */
@OptIn(UnstableApi::class)
fun buildPlayer(context: Context, url: String, startMs: Long = 0L, autoPlay: Boolean = true): ExoPlayer {
    val dataSource = OkHttpDataSource.Factory(AppGraph.client)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
        .setSeekBackIncrementMs(SEEK_MS)
        .setSeekForwardIncrementMs(SEEK_MS)
        .build()
        .apply {
            setMediaItem(MediaItem.fromUri(url), startMs.coerceAtLeast(0L))
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = autoPlay
            prepare()
        }
}

/**
 * 화면 왼쪽 절반을 두 번 탭하면 5초 뒤로, 오른쪽 절반은 5초 앞으로.
 * 한 번 탭하면 조작 버튼을 보이거나 숨겨요.
 */
@OptIn(UnstableApi::class)
@SuppressLint("ClickableViewAccessibility")
fun PlayerView.enableDoubleTapSeek() {
    val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isControllerFullyVisible) hideController() else showController()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val p = player ?: return true
            if (e.x < width / 2f) p.seekBack() else p.seekForward()
            showController() // 진행 바로 바뀐 위치를 보여줘요
            return true
        }
    })
    setOnTouchListener { _, event ->
        detector.onTouchEvent(event)
        true
    }
}
