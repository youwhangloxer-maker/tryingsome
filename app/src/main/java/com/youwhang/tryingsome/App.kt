package com.youwhang.tryingsome

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.youwhang.tryingsome.api.E621Api
import com.youwhang.tryingsome.net.AppSettings
import com.youwhang.tryingsome.net.NetFactory
import com.youwhang.tryingsome.net.SettingsStore
import com.youwhang.tryingsome.util.ViewedStore
import okhttp3.OkHttpClient

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}

/** 설정이 바뀌면 네트워크 객체를 다시 만들어요 */
object AppGraph {
    private lateinit var app: Application
    private lateinit var store: SettingsStore
    lateinit var viewed: ViewedStore
        private set

    var settings by mutableStateOf(AppSettings())
        private set
    var client: OkHttpClient by mutableStateOf(OkHttpClient())
        private set
    var imageLoader: ImageLoader? by mutableStateOf(null)
        private set
    var api: E621Api by mutableStateOf(E621Api(OkHttpClient(), "https://e621.net"))
        private set

    fun init(app: Application) {
        this.app = app
        store = SettingsStore(app)
        settings = store.load()
        viewed = ViewedStore(app.filesDir)
        rebuild()
    }

    fun update(s: AppSettings) {
        val netChanged = s.networkKey() != settings.networkKey()
        store.save(s)
        settings = s
        if (netChanged) rebuild()
    }

    private fun rebuild() {
        val c = NetFactory.create(app, settings)
        client = c
        imageLoader = ImageLoader.Builder(app)
            .okHttpClient(c)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
        api = E621Api(c, if (settings.safeMode) "https://e926.net" else "https://e621.net")
    }

    fun loader(): ImageLoader = imageLoader!!

    fun cacheDir(): java.io.File = app.cacheDir
}
