package com.youwhang.tryingsome.flash

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.youwhang.tryingsome.AppGraph
import com.youwhang.tryingsome.api.friendlyError
import com.youwhang.tryingsome.ui.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * e621 플래시(.swf) 재생 화면.
 * 앱에 내장한 Ruffle(플래시 에뮬레이터)을 WebView에서 돌려요.
 * swf 파일은 우회 기능이 들어간 앱 네트워크로 먼저 받아 두고, WebView에는 로컬에서 넘겨줘요.
 */
class FlashActivity : ComponentActivity() {
    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_ID = "id"
        const val HOST = "appassets.androidplatform.net"
    }

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null) {
            finish()
            return
        }
        val id = intent.getLongExtra(EXTRA_ID, 0L)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            AppTheme {
                FlashScreen(url, id, onWebView = { webView = it }, onClose = { finish() })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}

@Composable
private fun FlashScreen(url: String, id: Long, onWebView: (WebView) -> Unit, onClose: () -> Unit) {
    var file by remember { mutableStateOf<File?>(null) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        try {
            file = downloadSwf(AppGraph.client, url, id) { progress = it }
        } catch (e: Exception) {
            error = friendlyError(e)
        }
    }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        val f = file
        when {
            f != null -> AndroidView(
                factory = { ctx -> createPlayerWebView(ctx, f).also(onWebView) },
                modifier = Modifier.fillMaxSize(),
            )
            error != null -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { attempt++ }) { Text("다시 시도") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClose) { Text("닫기") }
            }
            else -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (progress >= 0f) "플래시 파일 받는 중… ${(progress * 100).toInt()}%" else "플래시 파일 받는 중…",
                    color = androidx.compose.ui.graphics.Color.White,
                )
                Spacer(Modifier.height(12.dp))
                if (progress >= 0f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private suspend fun downloadSwf(
    client: okhttp3.OkHttpClient,
    url: String,
    id: Long,
    onProgress: (Float) -> Unit,
): File = withContext(Dispatchers.IO) {
    val dir = File(AppGraph.cacheDir(), "swf").apply { mkdirs() }
    val target = File(dir, "$id.swf")
    if (target.exists() && target.length() > 0) return@withContext target

    // 오래된 캐시 정리 (최근 20개만 유지)
    dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }

    val tmp = File(dir, "$id.part")
    client.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
        val body = r.body ?: throw IOException("빈 응답")
        val total = body.contentLength()
        var done = 0L
        body.byteStream().use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) withContext(Dispatchers.Main) { onProgress(done.toFloat() / total) }
                }
            }
        }
    }
    if (!tmp.renameTo(target)) throw IOException("파일 저장 실패")
    target
}

private const val PLAYER_HTML = """<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
#c{width:100%;height:100%}#err{color:#fff;font-family:sans-serif;padding:24px}</style>
<script>
window.RufflePlayer = window.RufflePlayer || {};
window.RufflePlayer.config = {
  autoplay: "on",
  unmuteOverlay: "visible",
  letterbox: "on",
  splashScreen: false,
  warnOnUnsupportedContent: false,
  showSwfDownload: false,
  openUrlMode: "deny",
  allowScriptAccess: false
};
</script>
<script src="/ruffle/ruffle.js"></script>
</head><body><div id="c"></div><div id="err"></div>
<script>
window.addEventListener("load", function () {
  try {
    var ruffle = window.RufflePlayer.newest();
    var player = ruffle.createPlayer();
    player.style.width = "100%";
    player.style.height = "100%";
    document.getElementById("c").appendChild(player);
    var api = (typeof player.ruffle === "function") ? player.ruffle() : player;
    var p = api.load({ url: "/swf/current.swf" });
    if (p && p.catch) p.catch(function (e) { document.getElementById("err").textContent = "재생 실패: " + e; });
  } catch (e) {
    document.getElementById("err").textContent = "플레이어를 불러오지 못했어요: " + e;
  }
});
</script>
</body></html>"""

@SuppressLint("SetJavaScriptEnabled")
private fun createPlayerWebView(context: Context, swf: File): WebView = WebView(context).apply {
    setBackgroundColor(Color.BLACK)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    isFocusable = true
    isFocusableInTouchMode = true

    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val u = request.url
            if (u.host != FlashActivity.HOST) return null
            val path = u.path ?: return notFound()
            return when {
                path == "/player.html" -> ok("text/html", PLAYER_HTML.byteInputStream())
                path == "/swf/current.swf" -> ok("application/x-shockwave-flash", swf.inputStream())
                path.startsWith("/ruffle/") -> {
                    val name = path.removePrefix("/")
                    try {
                        ok(mimeOf(name), context.assets.open(name))
                    } catch (e: IOException) {
                        notFound()
                    }
                }
                else -> notFound()
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
    }

    loadUrl("https://${FlashActivity.HOST}/player.html")
}

private fun mimeOf(name: String): String = when {
    name.endsWith(".js") -> "text/javascript"
    name.endsWith(".wasm") -> "application/wasm"
    name.endsWith(".json") -> "application/json"
    else -> "application/octet-stream"
}

private fun ok(mime: String, data: InputStream): WebResourceResponse {
    val text = mime.startsWith("text/") || mime.endsWith("json")
    return WebResourceResponse(
        mime,
        if (text) "utf-8" else null,
        200,
        "OK",
        mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-cache"),
        data,
    )
}

private fun notFound(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), "".byteInputStream())
