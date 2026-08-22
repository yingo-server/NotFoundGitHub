package io.nggit.util

import java.io.File
import java.nio.charset.Charset

object EncodingDetector {

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    fun detect(file: File): Charset {
        val bytes = file.readBytes()
        if (bytes.size < 3) return Charsets.UTF_8

        if (bytes.startsWith(UTF8_BOM)) return Charsets.UTF_8
        if (bytes.startsWith(UTF16_LE_BOM)) return Charsets.UTF_16LE
        if (bytes.startsWith(UTF16_BE_BOM)) return Charsets.UTF_16BE

        if (isValidUtf8(bytes)) return Charsets.UTF_8

        if (containsHighBytes(bytes)) {
            try {
                val decoded = String(bytes, Charset.forName("GBK"))
                if (decoded.containsChineseCharacters()) {
                    return Charset.forName("GBK")
                }
            } catch (_: Exception) {}
        }

        return Charsets.UTF_8
    }

    fun detect(content: ByteArray): Charset {
        if (content.size < 3) return Charsets.UTF_8

        if (content.startsWith(UTF8_BOM)) return Charsets.UTF_8
        if (content.startsWith(UTF16_LE_BOM)) return Charsets.UTF_16LE
        if (content.startsWith(UTF16_BE_BOM)) return Charsets.UTF_16BE

        if (isValidUtf8(content)) return Charsets.UTF_8

        if (containsHighBytes(content)) {
            try {
                val decoded = String(content, Charset.forName("GBK"))
                if (decoded.containsChineseCharacters()) {
                    return Charset.forName("GBK")
                }
            } catch (_: Exception) {}
        }

        return Charsets.UTF_8
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        try {
            val charset = Charset.forName("UTF-8")
            val decoder = charset.newDecoder()
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun containsHighBytes(bytes: ByteArray): Boolean {
        return bytes.any { it.toInt() and 0xFF > 127 }
    }

    private fun String.containsChineseCharacters(): Boolean {
        for (c in this) {
            if (c.code in 0x4E00..0x9FFF) return true
        }
        return false
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }
}
