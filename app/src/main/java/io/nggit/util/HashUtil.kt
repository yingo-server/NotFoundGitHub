/**
 * 哈希计算工具类，提供 MD5、SHA-256 等常用哈希算法的字符串和文件内容摘要计算功能。
 * 用于生成唯一标识符、校验数据完整性、文件指纹识别等场景，
 * 是数据安全和校验机制的基础工具。
 */
package io.nggit.util

import java.security.MessageDigest

/**
 * 哈希计算单例对象，提供多种哈希算法的便捷调用接口。
 * 支持字符串和文件的哈希计算，返回十六进制格式的摘要字符串。
 */
object HashUtil {

    /**
     * 计算输入字符串的 MD5 哈希值。
     * 将字符串按 UTF-8 编码后进行 MD5 摘要计算，返回 32 位十六进制字符串。
     *
     * @param input 需要计算哈希值的原始字符串
     * @return MD5 哈希值的十六进制字符串表示
     */
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算输入字符串的 SHA-256 哈希值。
     * 使用安全哈希算法 SHA-256 生成更安全的摘要，返回 64 位十六进制字符串。
     *
     * @param input 需要计算哈希值的原始字符串
     * @return SHA-256 哈希值的十六进制字符串表示
     */
    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算文件内容的 SHA-256 哈希值。
     * 读取文件全部内容后进行哈希计算，适用于文件完整性校验和去重判断。
     *
     * @param file 需要计算哈希值的目标文件
     * @return 文件内容的 SHA-256 哈希值十六进制字符串，计算失败时抛出异常
     */
    fun fileHash(file: java.io.File): String {
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算内容字符串的哈希值。
     * 内部委托给 sha256() 方法实现，提供语义化的调用接口。
     *
     * @param content 需要计算哈希值的内容字符串
     * @return SHA-256 哈希值的十六进制字符串表示
     */
    fun contentHash(content: String): String {
        return sha256(content)
    }
}
