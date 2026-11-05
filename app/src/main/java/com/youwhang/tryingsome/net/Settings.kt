package com.youwhang.tryingsome.net

import android.content.Context

enum class BypassMode(val label: String, val desc: String) {
    TLS_SPLIT("TLS 레코드 분할 (추천)", "접속 주소가 담긴 첫 데이터를 두 개의 TLS 레코드로 나눠 보내요"),
    TCP_SPLIT("TCP 패킷 분할", "첫 패킷을 주소 중간에서 잘라 보내요 (GoodbyeDPI와 같은 원리)"),
    BOTH("둘 다", "레코드 분할과 패킷 분할을 함께 써요"),
    OFF("끄기", "우회하지 않아요 (VPN을 켜 둘 때)")
}

enum class ImageQuality(val label: String, val desc: String) {
    SAMPLE("샘플 (빠름)", "가로 850px 정도로 줄인 이미지. 크게 볼 때만 원본을 받아요"),
    ORIGINAL("원본", "처음부터 원본 이미지를 받아요 (느림)")
}

enum class VideoQuality(val label: String) {
    P480("480p"),
    P720("720p"),
    ORIGINAL("원본")
}

enum class DnsMode(val label: String) {
    CLOUDFLARE("Cloudflare (1.1.1.1)"),
    GOOGLE("Google (8.8.8.8)"),
    QUAD9("Quad9 (9.9.9.9)"),
    SYSTEM("휴대폰 기본 DNS")
}

data class AppSettings(
    val bypass: BypassMode = BypassMode.TLS_SPLIT,
    val dns: DnsMode = DnsMode.CLOUDFLARE,
    val safeMode: Boolean = false,
    val username: String = "",
    val apiKey: String = "",
    val blacklist: String = "",
    val ageConfirmed: Boolean = false,
    val imageQuality: ImageQuality = ImageQuality.SAMPLE,
    val videoQuality: VideoQuality = VideoQuality.P480,
) {
    /** 이 값들이 바뀌면 네트워크를 다시 만들어야 해요 */
    fun networkKey() = listOf(bypass, dns, safeMode, username.trim(), apiKey.trim())

    fun blacklistSet(): Set<String> =
        blacklist.split(Regex("[\\s,]+")).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
}

class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            bypass = runCatching { BypassMode.valueOf(p.getString("bypass", null)!!) }.getOrDefault(d.bypass),
            dns = runCatching { DnsMode.valueOf(p.getString("dns", null)!!) }.getOrDefault(d.dns),
            safeMode = p.getBoolean("safeMode", d.safeMode),
            username = p.getString("username", "") ?: "",
            apiKey = p.getString("apiKey", "") ?: "",
            blacklist = p.getString("blacklist", "") ?: "",
            ageConfirmed = p.getBoolean("ageConfirmed", false),
            imageQuality = runCatching { ImageQuality.valueOf(p.getString("imageQuality", null)!!) }.getOrDefault(d.imageQuality),
            videoQuality = runCatching { VideoQuality.valueOf(p.getString("videoQuality", null)!!) }.getOrDefault(d.videoQuality),
        )
    }

    fun save(s: AppSettings) {
        p.edit()
            .putString("bypass", s.bypass.name)
            .putString("dns", s.dns.name)
            .putBoolean("safeMode", s.safeMode)
            .putString("username", s.username)
            .putString("apiKey", s.apiKey)
            .putString("blacklist", s.blacklist)
            .putBoolean("ageConfirmed", s.ageConfirmed)
            .putString("imageQuality", s.imageQuality.name)
            .putString("videoQuality", s.videoQuality.name)
            .apply()
    }
}
