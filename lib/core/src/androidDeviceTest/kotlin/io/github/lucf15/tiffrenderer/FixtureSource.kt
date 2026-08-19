package io.github.lucf15.tiffrenderer

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.goncalossilva.resources.Resource
import java.io.File
import java.io.FileOutputStream

internal actual fun openFixtureSource(name: String, bytes: ByteArray): TiffSource =
    TiffSource.fromParcelFileDescriptor(openFixturePfd(name, bytes))

internal fun openFixturePfd(name: String): ParcelFileDescriptor =
    openFixturePfd(name, Resource(name).readBytes())

internal fun openFixturePfd(name: String, bytes: ByteArray): ParcelFileDescriptor {
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val copy = File(targetContext.cacheDir, name)
    FileOutputStream(copy).use { it.write(bytes) }
    return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
}
