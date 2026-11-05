package com.youwhang.tryingsome.net

import android.content.Context
import okhttp3.Cache
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.conscrypt.Conscrypt
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetFactory {
    const val USER_AGENT_APP = "tryingsome/0.1"

    /**
     * 앱에 내장한 Conscrypt를 "엔진 소켓" 모드로 써요.
     * 이 모드에서는 TLS 데이터가 우리가 만든 소켓의 OutputStream을 거쳐 나가므로 쪼개기가 가능해요.
     */
    private val tls: Pair<SSLSocketFactory, X509TrustManager> by lazy {
        val provider = Conscrypt.newProvider()
        val tm = Conscrypt.getDefaultX509TrustManager()
        val ctx = SSLContext.getInstance("TLS", provider)
        ctx.init(null, arrayOf<TrustManager>(tm), null)
        val sf = ctx.socketFactory
        Conscrypt.setUseEngineSocket(sf, true)
        sf to tm
    }

    private var httpCache: Cache? = null

    fun create(context: Context, s: AppSettings): OkHttpClient {
        val (sf, tm) = tls
        val cache = httpCache ?: Cache(File(context.cacheDir, "http"), 64L * 1024 * 1024).also { httpCache = it }

        val base = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .sslSocketFactory(sf, tm)
            .build()

        val ua = "$USER_AGENT_APP (by ${s.username.ifBlank { "anonymous" }} on e621)"
        val auth = if (s.username.isNotBlank() && s.apiKey.isNotBlank()) Credentials.basic(s.username.trim(), s.apiKey.trim()) else null

        return base.newBuilder()
            .cache(cache)
            .socketFactory(FragmentingSocketFactory(s.bypass))
            .dns(createDns(base, s.dns))
            .addInterceptor { chain ->
                val req = chain.request()
                val b = req.newBuilder().header("User-Agent", ua)
                val host = req.url.host
                if (auth != null && (host == "e621.net" || host == "e926.net")) b.header("Authorization", auth)
                chain.proceed(b.build())
            }
            .build()
    }

    private fun createDns(bootstrap: OkHttpClient, mode: DnsMode): Dns {
        fun doh(url: String, vararg ips: String) = DnsOverHttps.Builder()
            .client(bootstrap)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) })
            .includeIPv6(false)
            .build()

        return when (mode) {
            DnsMode.CLOUDFLARE -> doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1")
            DnsMode.GOOGLE -> doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4")
            DnsMode.QUAD9 -> doh("https://dns.quad9.net/dns-query", "9.9.9.9", "149.112.112.112")
            DnsMode.SYSTEM -> Dns.SYSTEM
        }
    }
}
