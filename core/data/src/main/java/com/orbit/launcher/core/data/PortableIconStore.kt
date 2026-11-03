package com.orbit.launcher.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Portable icon snapshots are bounded, app-private PNG files; no original image is exported. */
@Singleton
class PortableIconStore @Inject constructor(@ApplicationContext private val context: Context) {
    fun snapshot(uri: String): String {
        val source = Uri.parse(uri)
        require(source.scheme == "content")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (context.contentResolver.openInputStream(source) ?: error("Icon is unavailable")).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth in 1..16384 && bounds.outHeight in 1..16384)
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = (context.contentResolver.openInputStream(source) ?: error("Icon is unavailable")).use { BitmapFactory.decodeStream(it, null, options) } ?: error("Invalid icon")
        val thumbnail = Bitmap.createScaledBitmap(decoded, 256, 256, true)
        val output = ByteArrayOutputStream()
        try {
            check(thumbnail.compress(Bitmap.CompressFormat.PNG, 100, output))
        } finally {
            if (thumbnail !== decoded) decoded.recycle()
            thumbnail.recycle()
        }
        val bytes = output.toByteArray()
        require(bytes.size <= 1_048_576)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun validate(encoded: String): ByteArray {
        require(encoded.length <= 1_400_000)
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size in 1..1_048_576)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outMimeType == "image/png" && options.outWidth in 1..256 && options.outHeight in 1..256)
        return bytes
    }

    fun save(bytes: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "restored-icons")
        check(directory.isDirectory || directory.mkdirs())
        val file = AtomicFile(File(directory, "$hash.png"))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (error: Exception) { file.failWrite(stream); throw error }
        return Uri.Builder().scheme("content").authority("${context.packageName}.restored-icons").appendPath("$hash.png").build().toString()
    }
}
