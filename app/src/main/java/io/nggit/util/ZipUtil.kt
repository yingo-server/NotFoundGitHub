/**
 * ZIP 压缩包解压工具，支持完整解压和单文件提取，
 * 自动过滤系统文件（如 MACOSX 目录和 .DS_Store），
 * 提供解压进度回调。
 * 针对从 GitHub 等平台下载的仓库压缩包进行了优化，
 * 自动去除 ZIP 包中的顶层目录结构，提取有意义的文件内容。
 */
package io.nggit.util

import android.util.Log
import java.io.*
import java.util.zip.ZipInputStream

/**
 * ZIP 解压单例对象，提供 ZIP 压缩包的解压和文件提取功能。
 * 适用于解压克隆的仓库压缩包、下载的 release 资源等场景。
 */
object ZipUtil {

    private const val TAG = "ZipUtil"

    /**
     * 完整解压 ZIP 文件到指定目标目录。
     * 先遍历统计条目总数，再逐条解压，自动过滤 MACOSX 和 .DS_Store 等系统文件。
     * 解压过程中去除 ZIP 包的顶层目录结构，保留有意义的文件路径。
     * 支持进度回调，可实时获取当前解压进度。
     *
     * @param zipFilePath ZIP 压缩包文件路径
     * @param destDir 解压目标目录
     * @param onProgress 可选的进度回调，参数为(当前条目序号, 总条目数, 文件名)
     * @return 成功解压的文件数量
     * @throws IOException 解压过程中发生 IO 错误时抛出异常
     */
    fun unzip(zipFilePath: File, destDir: File, onProgress: ((current: Int, total: Int, name: String) -> Unit)? = null): Int {
        destDir.mkdirs()
        var count = 0

        try {
            val zipIn = ZipInputStream(BufferedInputStream(FileInputStream(zipFilePath)))
            val entries = mutableListOf<String>()
            var entry = zipIn.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zipIn.nextEntry
            }
            val totalEntries = entries.size
            zipIn.close()

            val zipIn2 = ZipInputStream(BufferedInputStream(FileInputStream(zipFilePath)))
            var current = 0
            entry = zipIn2.nextEntry
            while (entry != null) {
                current++
                val entryName = entry.name
                if (entryName.contains("__MACOSX") || entryName.endsWith(".DS_Store")) {
                    zipIn2.closeEntry()
                    entry = zipIn2.nextEntry
                    continue
                }

                val parts = entryName.split("/")
                val meaningfulParts = if (parts.size > 1) parts.subList(1, parts.size) else parts
                val cleanPath = meaningfulParts.joinToString("/")

                if (cleanPath.isEmpty()) {
                    zipIn2.closeEntry()
                    entry = zipIn2.nextEntry
                    continue
                }

                val outFile = File(destDir, cleanPath)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    val outputStream = BufferedOutputStream(FileOutputStream(outFile))
                    val buffer = ByteArray(4096)
                    var len: Int
                    while (zipIn2.read(buffer).also { len = it } > 0) {
                        outputStream.write(buffer, 0, len)
                    }
                    outputStream.close()
                    count++
                }

                onProgress?.invoke(current, totalEntries, cleanPath)
                zipIn2.closeEntry()
                entry = zipIn2.nextEntry
            }
            zipIn2.close()
        } catch (e: IOException) {
            Log.e(TAG, "解压失败: ${zipFilePath.name}", e)
            throw e
        }
        return count
    }

    /**
     * 从 ZIP 压缩包中提取单个文件的内容。
     * 遍历 ZIP 条目查找匹配的文件名（支持精确匹配和路径后缀匹配），
     * 读取并返回该文件的字节内容。
     *
     * @param zipFilePath ZIP 压缩包文件路径
     * @param entryName 要提取的文件名
     * @return 提取到的文件字节数组，未找到或提取失败时返回 null
     */
    fun unzipEntry(zipFilePath: File, entryName: String): ByteArray? {
        return try {
            val zipIn = ZipInputStream(BufferedInputStream(FileInputStream(zipFilePath)))
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == entryName || entry.name.endsWith("/$entryName")) {
                    val bytes = zipIn.readBytes()
                    zipIn.closeEntry()
                    zipIn.close()
                    return bytes
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()
            null
        } catch (e: IOException) {
            Log.e(TAG, "解压单个文件失败: $entryName", e)
            null
        }
    }
}
