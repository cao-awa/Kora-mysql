package com.github.cao.awa.kora.mysql.client

import com.github.cao.awa.kora.mysql.client.handshake.HandshakeData
import com.github.cao.awa.kora.mysql.config.KoraMysqlClientConfig
import com.github.cao.awa.kora.mysql.data.ColumnDefinition
import com.github.cao.awa.kora.mysql.data.result.ResultSet
import com.github.cao.awa.kora.mysql.data.row.RowDefinition
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

class KoraMysqlClient(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val database: String = ""
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger("KoraMysqlClient")
        private var REAL_INSTANCE: KoraMysqlClient? = null
        val INSTANCE: KoraMysqlClient
            get() = REAL_INSTANCE!!
        private const val CLIENT_LONG_PASSWORD = 0x00000001
        private const val CLIENT_LONG_FLAG = 0x00000004
        private const val CLIENT_CONNECT_WITH_DB = 0x00000008
        private const val CLIENT_PROTOCOL_41 = 0x00000200
        private const val CLIENT_SECURE_CONNECTION = 0x00008000
        private const val CLIENT_PLUGIN_AUTH = 0x00080000
        private const val CLIENT_CONNECT_ATTRS = 0x00100000
        private const val CLIENT_DEPRECATE_EOF = 0x01000000

        fun init(config: KoraMysqlClientConfig) {
            REAL_INSTANCE = KoraMysqlClient(
                config.host(),
                config.port(),
                config.username(),
                config.password(),
                config.database()
            )

            try {
                INSTANCE.connect()

                LOGGER.info("Initialized mysql client, connected to ${config.host()}:${config.port()}")
                if (config.database() == "") {
                    LOGGER.warn("Mysql database are not set, please execute 'USE database_name' later")
                }
            } catch (e: Exception) {
                throw IllegalStateException("Cannot initialize mysql client", e)
            }
        }
    }

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var sequenceId = 0

    fun connect() {
        val socket = Socket(this.host, this.port)
        this.socket = socket
        socket.tcpNoDelay = true
        this.input = socket.getInputStream()
        this.output = socket.getOutputStream()
        this.sequenceId = 0
        val handshakePacket = readPacket()
        val handshake = parseHandshake(handshakePacket)
        sendHandshakeResponse(handshake)
        handleAuthResult(handshake)
    }

    fun close() {
        this.socket?.close()
    }

    fun execute(sql: String): ResultSet? {
        this.sequenceId = 0
        val sqlBytes = sql.toByteArray(StandardCharsets.UTF_8)
        val payload = ByteArray(1 + sqlBytes.size)
        payload[0] = 0x03
        System.arraycopy(sqlBytes, 0, payload, 1, sqlBytes.size)
        writePacket(payload)
        val firstPacket = readPacket()
        return when (firstPacket[0].toInt() and 0xFF) {
            0x00 -> {
                null
            }
            0xFF -> throwMysqlError(firstPacket)
            else -> handleResultSet(firstPacket)
        }
    }

    private fun parseColumnDefinition(packet: ByteArray): ColumnDefinition {
        var pos = 0
        repeat(4) {
            val len = readLenEnc(packet, pos)
            pos += lengthEncodeSize(packet, pos) + len
        }
        val nameLength = readLenEnc(packet, pos)
        pos += lengthEncodeSize(packet, pos)
        val name = String(packet, pos, nameLength, StandardCharsets.UTF_8)
        return ColumnDefinition(name)
    }

    private fun readLenEnc(data: ByteArray, offset: Int): Int {
        val first = data[offset].toInt() and 0xFF
        return when {
            first < 0xFB -> first
            first == 0xFC -> (data[offset + 1].toInt() and 0xFF) or ((data[offset + 2].toInt() and 0xFF) shl 8)
            first == 0xFD -> (data[offset + 1].toInt() and 0xFF) or ((data[offset + 2].toInt() and 0xFF) shl 8) or ((data[offset + 3].toInt() and 0xFF) shl 16)
            first == 0xFE -> (data[offset + 1].toInt() and 0xFF) or ((data[offset + 2].toInt() and 0xFF) shl 8) or ((data[offset + 3].toInt() and 0xFF) shl 16) or ((data[offset + 4].toInt() and 0xFF) shl 24)
            else -> throw RuntimeException("Invalid length-encoded integer: $first")
        }
    }

    private fun lengthEncodeSize(data: ByteArray, offset: Int): Int {
        val first = data[offset].toInt() and 0xFF
        return when {
            first < 0xFB -> 1
            first == 0xFC -> 3
            first == 0xFD -> 4
            first == 0xFE -> 9
            else -> throw RuntimeException("Invalid length-encoded prefix: $first")
        }
    }

    private fun handleResultSet(firstPacket: ByteArray): ResultSet {
        val columnCount = firstPacket[0].toInt() and 0xFF
        val columns = (0 until columnCount).map {
            val colPacket = readPacket()
            val columnDefinition = parseColumnDefinition(colPacket)
            columnDefinition
        }

        readPacket()

        val rows = mutableListOf<RowDefinition>()
        while (true) {
            val rowPacket = readPacket()
            val header = rowPacket[0].toInt() and 0xFF
            when {
                header == 0xFF -> throwMysqlError(rowPacket)
                header == 0xFE && rowPacket.size < 9 -> break
                header == 0x00 && rowPacket.size >= 7 -> break
                header == 0xFE -> break
                else -> {
                    val values = parseTextRow(rowPacket)
                    rows.add(RowDefinition(values))
                }
            }
        }

        return ResultSet(columns, rows)
    }

    private fun parseTextRow(packet: ByteArray): List<String?> {
        val result = mutableListOf<String?>()
        var pos = 0
        while (pos < packet.size) {
            val first = packet[pos].toInt() and 0xFF
            if (first == 0xFB) {
                result.add(null)
                pos++
                continue
            }
            val len = readLenEnc(packet, pos)
            pos += lengthEncodeSize(packet, pos)
            val value = String(packet, pos, len, StandardCharsets.UTF_8)
            result.add(value)
            pos += len
        }
        return result
    }

    private fun parseHandshake(payload: ByteArray): HandshakeData {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get()
        while (buffer.get() != 0.toByte()) {
        }
        buffer.getInt()
        val scramble1 = ByteArray(8)
        buffer.get(scramble1)
        buffer.get()
        buffer.getShort()
        buffer.get()
        buffer.getShort()
        buffer.getShort()
        val authPluginDataLength = buffer.get().toInt() and 0xFF
        repeat(10) { buffer.get() }
        val scramble2 = ByteArray(maxOf(13, authPluginDataLength - 8))
        buffer.get(scramble2)
        val scramble = ByteArray(20)
        System.arraycopy(scramble1, 0, scramble, 0, 8)
        System.arraycopy(scramble2, 0, scramble, 8, 12)
        var plugin = "caching_sha2_password"
        if (buffer.hasRemaining()) {
            val remain = ByteArray(buffer.remaining())
            buffer.get(remain)
            val parsed = String(remain).trim { it.code == 0 || it <= ' ' }
            if (parsed.isNotEmpty()) {
                plugin = parsed
            }
        }
        return HandshakeData(scramble, plugin)
    }

    private fun sendHandshakeResponse(handshake: HandshakeData) {
        var capabilities = CLIENT_LONG_PASSWORD or CLIENT_LONG_FLAG or CLIENT_PROTOCOL_41 or CLIENT_SECURE_CONNECTION or CLIENT_PLUGIN_AUTH or CLIENT_CONNECT_ATTRS or CLIENT_DEPRECATE_EOF
        if (this.database != "") {
            capabilities = capabilities or CLIENT_CONNECT_WITH_DB
        }
        val body = ByteArrayOutputStream()
        writeInt4(body, capabilities)
        writeInt4(body, 0xFFFFFF)
        body.write(33)
        body.write(ByteArray(23))
        body.write(this.username.toByteArray(StandardCharsets.UTF_8))
        body.write(0)
        val authResponse = calculateAuthResponse(handshake.authPluginName, handshake.scramble)
        body.write(authResponse.size)
        body.write(authResponse)
        if (this.database != "") {
            body.write(this.database.toByteArray(StandardCharsets.UTF_8))
            body.write(0)
        }
        body.write(handshake.authPluginName.toByteArray(StandardCharsets.UTF_8))
        body.write(0)
        body.write(0)
        writePacket(body.toByteArray())
    }

    private fun handleAuthResult(handshake: HandshakeData) {
        while (true) {
            val response = readPacket()
            when (response[0]) {
                0x00.toByte() -> return
                0xFF.toByte() -> throwMysqlError(response)
                0x01.toByte() -> {
                    val status = response[1].toInt() and 0xFF
                    if (status == 0x03) continue
                    if (status == 0x04) {
                        writePacket(byteArrayOf(0x02))
                        val keyPacket = readPacket()
                        val pem = String(keyPacket, 1, keyPacket.size - 1, StandardCharsets.UTF_8)
                        val encrypted = encryptPasswordWithPayload(this.password, handshake.scramble, pem)
                        writePacket(encrypted)
                    }
                }
                else -> return
            }
        }
    }

    private fun readPacket(): ByteArray {
        val header = ByteArray(4)
        readFully(header)
        val length =
            (header[0].toInt() and 0xFF) or ((header[1].toInt() and 0xFF) shl 8) or ((header[2].toInt() and 0xFF) shl 16)
        val serverSequence = header[3].toInt() and 0xFF
        this.sequenceId = serverSequence + 1
        val payload = ByteArray(length)
        readFully(payload)
        return payload
    }

    private fun writePacket(payload: ByteArray) {
        val header = ByteArray(4)
        val len = payload.size
        header[0] = (len and 0xFF).toByte()
        header[1] = ((len shr 8) and 0xFF).toByte()
        header[2] = ((len shr 16) and 0xFF).toByte()
        header[3] = (this.sequenceId and 0xFF).toByte()
        this.output!!.write(header)
        this.output!!.write(payload)
        this.output!!.flush()
        this.sequenceId++
    }

    private fun readFully(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val read = this.input!!.read(bytes, offset, bytes.size - offset)
            if (read == -1) throw RuntimeException("Connection closed")
            offset += read
        }
    }

    private fun writeInt4(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF); out.write((value shr 8) and 0xFF); out.write((value shr 16) and 0xFF); out.write((value shr 24) and 0xFF)
    }

    private fun throwMysqlError(packet: ByteArray): Nothing {
        val code = (packet[1].toInt() and 0xFF) or ((packet[2].toInt() and 0xFF) shl 8)
        val message = if (packet.size > 9) {
            String(packet, 9, packet.size - 9, StandardCharsets.UTF_8)
        } else {
            "Unknown MySQL error"
        }
        throw RuntimeException("MySQL Error [$code]: $message")
    }

    private fun calculateAuthResponse(plugin: String, scramble: ByteArray): ByteArray {
        if (this.password.isEmpty()) return ByteArray(0)
        return when (plugin) {
            "mysql_native_password" -> scrambleMysqlNative(this.password, scramble)
            "caching_sha2_password" -> scrambleCachingSha2(this.password, scramble)
            else -> throw UnsupportedOperationException("Unsupported auth plugin: $plugin")
        }
    }

    private fun scrambleMysqlNative(password: String, scramble: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        val p1 = md.digest(password.toByteArray(StandardCharsets.UTF_8))
        val p2 = md.digest(p1)
        md.reset()
        val p3 = md.apply { update(scramble); update(p2) }.digest()
        val result = ByteArray(20)
        for (i in result.indices) {
            result[i] = (p1[i].toInt() xor p3[i].toInt()).toByte()
        }
        return result
    }

    private fun scrambleCachingSha2(password: String, scramble: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val p1 = md.digest(password.toByteArray(StandardCharsets.UTF_8))
        val p2 = md.digest(p1)
        md.reset()
        val p3 = md.apply { update(p2); update(scramble) }.digest()
        val result = ByteArray(32)
        for (i in result.indices) {
            result[i] = (p1[i].toInt() xor p3[i].toInt()).toByte()
        }
        return result
    }

    private fun encryptPasswordWithPayload(password: String, scramble: ByteArray, pemKey: String): ByteArray {
        val passBytes = password.toByteArray(StandardCharsets.UTF_8)
        val plain = ByteArray(passBytes.size + 1)
        System.arraycopy(passBytes, 0, plain, 0, passBytes.size)
        plain[plain.size - 1] = 0
        val xor = ByteArray(plain.size)
        for (i in plain.indices) xor[i] = (plain[i].toInt() xor scramble[i % scramble.size].toInt()).toByte()
        val publicKeyString = pemKey.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "").trim()
        val keyBytes = Base64.getDecoder().decode(publicKeyString)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(spec)
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(xor)
    }
}
