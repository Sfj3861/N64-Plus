package com.sfj.n64plus

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class GameEntry(val uri: Uri, val title: String, val extension: String, val size: Long)

class SnesLibraryScanner(private val context: Context) {
    private val supported = setOf("sfc", "smc", "fig", "swc", "zip")

    fun scan(folderUri: Uri): List<GameEntry> {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        val found = mutableListOf<GameEntry>()
        scanNode(root, found, depth = 0)
        return found.sortedBy { it.title.lowercase() }
    }

    private fun scanNode(node: DocumentFile, out: MutableList<GameEntry>, depth: Int) {
        if (depth > 2 || !node.isDirectory) return
        node.listFiles().forEach { file ->
            if (file.isDirectory) scanNode(file, out, depth + 1)
            else if (file.isFile) {
                val name = file.name ?: return@forEach
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in supported) out += GameEntry(file.uri, name.substringBeforeLast('.', name), ext, file.length())
            }
        }
    }
}
