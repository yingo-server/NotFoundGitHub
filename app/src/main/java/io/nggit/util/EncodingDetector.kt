/**
 * 文件编码检测工具，通过 BOM 标记和字节特征自动识别文本文件的编码格式，
 * 支持 UTF-8、UTF-16 LE、UTF-16 BE、GBK 等常见编码。
 * 采用多层检测策略：先检查 BOM 标记，再验证 UTF-8 有效性，
 * 最后通过中文字节特征判断是否为 GBK 编码，确保高准确率的编码识别。
 */
package io.nggit.util

import java.io.File
import java.nio.charset.Charset

/**
 * 文件编码检测单例对象，提供静态方法用于自动检测文本编码格式。
 * 主要用于读取 Git 仓库中的文本文件时自动适配正确的编码。
 */
object EncodingDetector {

    /** UTF-8 BOM 标记字节数组 (EF BB BF) */
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    /** UTF-16 小端序 BOM 标记字节数组 (FF FE) */
    private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    /** UTF-16 大端序 BOM 标记字节数组 (FE FF) */
    private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    /**
     * 检测文件的字符编码格式。
     * 读取文件字节内容后依次进行 BOM 检查、UTF-8 校验和 GBK 特征识别。
     *
     * @param file 需要检测编码的目标文件
     * @return 检测到的字符编码，无法识别时默认返回 UTF-8
     */
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

    /**
     * 检测字节数组的字符编码格式。
     * 与文件检测逻辑一致，适用于已读入内存的字节数据。
     *
     * @param content 需要检测编码的字节数组
     * @return 检测到的字符编码，无法识别时默认返回 UTF-8
     */
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

    /**
     * 验证字节数组是否为有效的 UTF-8 编码。
     * 通过 Java NIO 的 CharsetDecoder 进行严格解码验证，
     * 遇到格式错误或无法映射的字符时视为无效。
     *
     * @param bytes 待验证的字节数组
     * @return 如果是有效的 UTF-8 编码返回 true，否则返回 false
     */
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

    /**
     * 检查字节数组是否包含高位字节（值大于 127 的字节）。
     * 高位字节的存在表明内容可能不是纯 ASCII，需要进一步判断具体编码。
     *
     * @param bytes 待检查的字节数组
     * @return 如果包含高位字节返回 true，纯 ASCII 返回 false
     */
    private fun containsHighBytes(bytes: ByteArray): Boolean {
        return bytes.any { it.toInt() and 0xFF > 127 }
    }

    /**
     * 扩展函数：检查字符串是否包含中文字符。
     * 通过遍历字符并判断 Unicode 码点是否在 CJK 统一汉字范围内 (0x4E00-0x9FFF)。
     *
     * @return 如果包含至少一个中文字符返回 true
     */
    private fun String.containsChineseCharacters(): Boolean {
        for (c in this) {
            if (c.code in 0x4E00..0x9FFF) return true
        }
        return false
    }

    /**
     * 扩展函数：检查字节数组是否以指定前缀开头。
     * 逐字节比较前缀内容，确保字节数组长度足够且内容匹配。
     *
     * @param prefix 前缀字节数组
     * @return 如果字节数组以该前缀开头返回 true
     */
    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }
}
