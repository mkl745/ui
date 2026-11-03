package com.orbit.launcher.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** A disposable, bounded cache in app-private cache storage. Call only from IO dispatchers. */
@Singleton
class IconDiskCache @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.cacheDir, "orbit-icons")
    private val lock = Any()
    private val maxBytes = 32L * 1024 * 1024
    private fun file(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.png")
    }

    fun read(key: String, size: Int): Bitmap? = synchronized(lock) {
        try {
            val entry = file(key)
            if (!entry.isFile || entry.length() > 2L * 1024 * 1024) return@synchronized null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(entry.path, options)
            if (options.outWidth != size || options.outHeight != size) { entry.delete(); return@synchronized null }
            BitmapFactory.decodeFile(entry.path)?.also { entry.setLastModified(System.currentTimeMillis()) }
        } catch (_: Exception) { null }
    }

    fun write(key: String, bitmap: Bitmap) = synchronized(lock) {
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return@synchronized
            val atomic = AtomicFile(file(key))
            val stream = atomic.startWrite()
            try {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                atomic.finishWrite(stream)
            } catch (error: Exception) { atomic.failWrite(stream); throw error }
            val entries = directory.listFiles()?.filter { it.isFile && it.extension == "png" }?.sortedBy { it.lastModified() }.orEmpty()
            var total = entries.sumOf { it.length() }
            for (entry in entries) {
                if (total <= maxBytes) break
                val bytes = entry.length()
                if (entry.delete()) total -= bytes
            }
        } catch (_: Exception) { /* Cache failures must not prevent launching apps. */ }
    }
}
