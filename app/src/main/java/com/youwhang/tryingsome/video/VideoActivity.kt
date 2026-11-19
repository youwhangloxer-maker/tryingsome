package com.youwhang.tryingsome.video

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** 동영상 전체화면. 닫을 때 재생 위치를 돌려줘요 */
class VideoActivity : ComponentActivity() {
    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_POSITION = "position"
        const val EXTRA_LANDSCAPE = "landscape"

        fun intent(context: Context, url: String, positionMs: Long, landscape: Boolean): Intent =
            Intent(context, VideoActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_POSITION, positionMs)
                .putExtra(EXTRA_LANDSCAPE, landscape)
    }

    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null) {
            finish()
            return
        }
        val start = savedInstanceState?.getLong(EXTRA_POSITION) ?: intent.getLongExtra(EXTRA_POSITION, 0L)
        requestedOrientation = if (intent.getBooleanExtra(EXTRA_LANDSCAPE, true)) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val p = buildPlayer(this, url, start)
        player = p
        val view = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            this.player = p
            keepScreenOn = true
            enableDoubleTapSeek()
            // 전체화면 버튼을 다시 누르면 원래 화면으로
            setFullscreenButtonState(true)
            setFullscreenButtonClickListener { finish() }
        }
        setContentView(view)
    }

    override fun finish() {
        player?.let { setResult(RESULT_OK, Intent().putExtra(EXTRA_POSITION, it.currentPosition)) }
        super.finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        player?.let { outState.putLong(EXTRA_POSITION, it.currentPosition) }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
