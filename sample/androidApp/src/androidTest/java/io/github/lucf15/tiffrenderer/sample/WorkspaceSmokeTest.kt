package io.github.lucf15.tiffrenderer.sample

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun packagedResourcesAndDocumentControlsWork() {
        val context = instrumentation.targetContext
        val resources = "composeResources/io.github.lucf15.tiffrenderer.sample.resources"
        for (path in listOf("files/example_catalog.tiff", "font/inter_regular.ttf", "drawable/example_catalog.png")) {
            context.assets.open("$resources/$path").use { assertTrue(it.read() >= 0) }
        }
        val intent = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        val activity = instrumentation.startActivitySync(intent)
        try {
            awaitText("Page 1 of 4")
            screenshot("android-reader")
            click("Next page")
            awaitText("Page 2 of 4")
            click("Zoom in")
            awaitText("125%")
            click("Document information")
            await("information panel actions") { find { it.contentDescription?.toString() == "Close document" } != null }
            await("floating controls hidden") { find { it.contentDescription?.toString()?.startsWith("Next page") == true } == null }
            screenshot("android-information")
            click("Close document")
            awaitText("Welcome to")
            screenshot("android-welcome")
            click("Open example: Form & Field")
            awaitText("Page 1 of 4")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun click(prefix: String) {
        var previous = Rect()
        var stable = 0
        val bounds = Rect()
        await("visible button $prefix") {
            val node = find {
                it.isVisibleToUser && it.contentDescription?.toString()?.startsWith(prefix) == true
            }
            if (node == null) false
            else {
                node.getBoundsInScreen(bounds)
                stable = if (bounds == previous) stable + 1 else 0
                previous = Rect(bounds)
                stable >= 2 && !bounds.isEmpty
            }
        }
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
    }

    private fun awaitText(text: String) = await(text) {
        find { it.text?.toString()?.contains(text) == true } != null
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue("Timed out waiting for $description", condition())
    }

    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (predicate(node)) return node
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                visit(child)?.let { return it }
            }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(250)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val path = File(instrumentation.targetContext.getExternalFilesDir("test-screenshots"), "$name.png")
        path.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
