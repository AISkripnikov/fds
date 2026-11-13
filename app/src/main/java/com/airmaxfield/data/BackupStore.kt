package com.airmaxfield.data

import java.io.File

/** Резервные копии конфигурации пушек в памяти приложения (понадобятся для отката на этапе 4). */
class BackupStore(private val dir: File) {
    init { dir.mkdirs() }

    fun save(name: String, text: String): File = File(dir, name).also { it.writeText(text) }

    /** Новые первыми. */
    fun list(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".cfg") }.orEmpty()
        .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })

    fun delete(f: File) { if (f.parentFile?.canonicalPath == dir.canonicalPath) f.delete() }
}
