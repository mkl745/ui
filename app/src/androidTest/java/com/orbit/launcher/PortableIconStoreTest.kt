package com.orbit.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.launcher.core.data.PortableIconStore
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableIconStoreTest {
    @Test fun restoredIconCanBeReadAndResnapshotted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PortableIconStore(context)
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        source.eraseColor(0xFF000000.toInt() or (System.nanoTime().toInt() and 0x00FFFFFF))
        val stream = ByteArrayOutputStream()
        check(source.compress(Bitmap.CompressFormat.PNG, 100, stream))
        source.recycle()
        val uri = store.save(stream.toByteArray())
        try {
            val encoded = store.snapshot(uri)
            val decoded = store.validate(encoded)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size, bounds)
            assertEquals(256, bounds.outWidth)
            assertEquals(256, bounds.outHeight)
        } finally {
            File(context.filesDir, "restored-icons/${Uri.parse(uri).lastPathSegment}").delete()
        }
    }
}
