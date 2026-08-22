package io.nggit.util

import android.util.Log
import java.io.*
import java.util.zip.ZipInputStream

object ZipUtil {

    private const val TAG = "ZipUtil"

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
