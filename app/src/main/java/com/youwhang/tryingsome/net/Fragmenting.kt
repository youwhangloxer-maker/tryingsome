package com.youwhang.tryingsome.net

import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/**
 * 통신사 DPI(SNI 검사) 우회용 소켓.
 * TLS 연결의 첫 데이터(ClientHello)에 접속 주소(SNI)가 평문으로 들어 있어서,
 * 이를 쪼개 보내면 검사 장비가 주소를 알아보지 못해요. 서버는 정상적으로 다시 합쳐서 처리해요.
 * 다른 서버를 거치지 않으므로 속도 손실이 없어요.
 */
class FragmentingSocketFactory(private val mode: BypassMode) : SocketFactory() {
    override fun createSocket(): Socket = FragmentingSocket(mode)

    override fun createSocket(host: String?, port: Int): Socket =
        createSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress(host, port))
        }

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        createSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port))
        }
}

class FragmentingSocket(private val mode: BypassMode) : Socket() {
    private var wrapped: OutputStream? = null

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        super.connect(endpoint, timeout)
        // 쪼갠 조각이 각각 별도 패킷으로 나가도록
        runCatching { tcpNoDelay = true }
    }

    @Synchronized
    override fun getOutputStream(): OutputStream {
        wrapped?.let { return it }
        val out = FragmentingOutputStream(super.getOutputStream(), mode)
        wrapped = out
        return out
    }
}

class FragmentingOutputStream(
    private val out: OutputStream,
    private val mode: BypassMode,
) : OutputStream() {
    private var first = true

    override fun write(b: Int) = out.write(b)

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (first) {
            first = false
            if (mode != BypassMode.OFF && writeFragmented(b, off, len)) return
        }
        out.write(b, off, len)
    }

    override fun flush() = out.flush()
    override fun close() = out.close()

    private fun writeFragmented(b: ByteArray, off: Int, len: Int): Boolean {
        // TLS 핸드셰이크 레코드인지 확인 (0x16)
        if (len < 6 || b[off].toInt() != 0x16) return false
        val recLen = ((b[off + 3].toInt() and 0xff) shl 8) or (b[off + 4].toInt() and 0xff)
        if (recLen < 2 || recLen + 5 > len) return false

        val payloadStart = off + 5
        val sni = TlsHello.findSni(b, payloadStart, recLen)
        // 호스트 이름 한가운데를 자름. 못 찾으면 앞쪽에서 자름
        val split = (if (sni != null) sni.first + maxOf(1, sni.second / 2) else minOf(recLen / 2, 64))
            .coerceIn(1, recLen - 1)
        val rest = len - (recLen + 5)

        when (mode) {
            BypassMode.TCP_SPLIT -> {
                val cut = 5 + split
                out.write(b, off, cut)
                out.flush()
                pause()
                out.write(b, off + cut, len - cut)
            }
            BypassMode.TLS_SPLIT, BypassMode.BOTH -> {
                val rec1 = ByteArray(5 + split)
                rec1[0] = b[off]; rec1[1] = b[off + 1]; rec1[2] = b[off + 2]
                rec1[3] = (split shr 8).toByte(); rec1[4] = split.toByte()
                System.arraycopy(b, payloadStart, rec1, 5, split)

                val len2 = recLen - split
                val rec2 = ByteArray(5 + len2)
                rec2[0] = b[off]; rec2[1] = b[off + 1]; rec2[2] = b[off + 2]
                rec2[3] = (len2 shr 8).toByte(); rec2[4] = len2.toByte()
                System.arraycopy(b, payloadStart + split, rec2, 5, len2)

                if (mode == BypassMode.BOTH) {
                    out.write(rec1, 0, 1)
                    out.flush()
                    pause()
                    out.write(rec1, 1, rec1.size - 1)
                } else {
                    out.write(rec1)
                }
                out.flush()
                pause()
                out.write(rec2)
                if (rest > 0) out.write(b, off + 5 + recLen, rest)
            }
            BypassMode.OFF -> return false
        }
        out.flush()
        return true
    }

    private fun pause() {
        try {
            Thread.sleep(3)
        } catch (_: InterruptedException) {
        }
    }
}

object TlsHello {
    /** ClientHello 안에서 SNI 호스트 이름의 (payload 기준 시작 위치, 길이)를 찾음 */
    fun findSni(b: ByteArray, start: Int, len: Int): Pair<Int, Int>? {
        try {
            val end = start + len
            var p = start
            if (b[p].toInt() != 0x01) return null // client_hello
            p += 4 // type + length(3)
            p += 2 + 32 // version + random
            p += 1 + u8(b, p) // session id
            p += 2 + u16(b, p) // cipher suites
            p += 1 + u8(b, p) // compression
            if (p + 2 > end) return null
            val extEnd = minOf(end, p + 2 + u16(b, p))
            p += 2
            while (p + 4 <= extEnd) {
                val type = u16(b, p)
                val l = u16(b, p + 2)
                p += 4
                if (type == 0 && l >= 5) {
                    val nameLen = u16(b, p + 3)
                    val nameStart = p + 5
                    return if (nameStart + nameLen <= extEnd && nameLen > 0) Pair(nameStart - start, nameLen) else null
                }
                p += l
            }
        } catch (_: IndexOutOfBoundsException) {
        }
        return null
    }

    private fun u8(b: ByteArray, p: Int) = b[p].toInt() and 0xff
    private fun u16(b: ByteArray, p: Int) = (u8(b, p) shl 8) or u8(b, p + 1)
}
