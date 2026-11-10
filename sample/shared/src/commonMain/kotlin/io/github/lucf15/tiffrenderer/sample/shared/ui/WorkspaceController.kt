package io.github.lucf15.tiffrenderer.sample.shared.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import io.github.lucf15.tiffrenderer.RenderSize
import io.github.lucf15.tiffrenderer.TiffError
import io.github.lucf15.tiffrenderer.TiffIOException
import io.github.lucf15.tiffrenderer.TiffOptions
import io.github.lucf15.tiffrenderer.TiffPageInfo
import io.github.lucf15.tiffrenderer.TiffRenderer
import io.github.lucf15.tiffrenderer.sample.shared.platform.PickedTiff
import io.github.lucf15.tiffrenderer.sample.shared.toImageBitmap
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class WorkspaceStage {
    EMPTY,
    OPENING,
    READY,
    FAILED,
}

@Stable
internal interface WorkspaceState {
    val stage: WorkspaceStage
    val name: String
    val pageCount: Int
    val pageIndex: Int
    val info: TiffPageInfo?
    val image: ImageBitmap?
    val rendering: Boolean
    val error: String?
    val zoom: Int
    val previews: Map<Int, ThumbnailState>
    val renderMillis: Long?
}

@Stable
internal interface ThumbnailState {
    val image: ImageBitmap?
}

@Stable
private class MutableThumbnailState : ThumbnailState {
    override var image by mutableStateOf<ImageBitmap?>(null)
    var info: TiffPageInfo? = null
    var requestedWidth: Int = 0
    var requestedHeight: Int = 0
}

@Stable
private class MutableWorkspaceState : WorkspaceState {
    override var stage by mutableStateOf(WorkspaceStage.EMPTY)
    override var name by mutableStateOf("")
    override var pageCount by mutableIntStateOf(0)
    override var pageIndex by mutableIntStateOf(0)
    override var info by mutableStateOf<TiffPageInfo?>(null)
    override var image by mutableStateOf<ImageBitmap?>(null)
    override var rendering by mutableStateOf(false)
    override var error by mutableStateOf<String?>(null)
    override var zoom by mutableIntStateOf(100)
    override var previews by mutableStateOf<Map<Int, ThumbnailState>>(emptyMap())
    override var renderMillis by mutableStateOf<Long?>(null)
}

@Stable
internal class WorkspaceController(private val scope: CoroutineScope) {
    private val mutable = MutableWorkspaceState()
    val state: WorkspaceState = mutable

    private data class RenderedFrame(val info: TiffPageInfo, val image: ImageBitmap) {
        val bytes: Long
            get() = image.width.toLong() * image.height * 4
    }

    private val frames = linkedMapOf<Int, RenderedFrame>()
    private var document: TiffRenderer? = null
    private var operation: Job? = null
    private var previewsJob: Job? = null
    private var visiblePages: List<Int> = emptyList()
    private var previewWidth = 320
    private var previewHeight = 480
    private var generation = 0
    private var width = 1200
    private var height = 1600
    private var disposed = false

    fun open(input: PickedTiff) = openSource(input.name) { input.source }

    fun openExample(example: WorkspaceExample) =
        openSource("${example.title}.tiff") { example.source() }

    private fun openSource(
        name: String,
        source: suspend () -> io.github.lucf15.tiffrenderer.TiffSource,
    ) = schedule { token ->
        closeDocument()
        Snapshot.withMutableSnapshot {
            mutable.previews = emptyMap()
            mutable.stage = WorkspaceStage.OPENING
            mutable.name = name
            mutable.error = null
            mutable.image = null
            mutable.info = null
            mutable.pageIndex = 0
            mutable.pageCount = 0
            mutable.zoom = 100
            mutable.renderMillis = null
        }
        try {
            val opened = TiffRenderer.open(source(), TiffOptions(cacheBytes = 16L * 1024 * 1024))
            document = opened
            val count = opened.pageCount()
            check(count > 0) { "Document has no pages" }
            if (!current(token)) return@schedule
            Snapshot.withMutableSnapshot {
                mutable.pageCount = count
                mutable.stage = WorkspaceStage.READY
            }
            render(token, opened, 0, width, height)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            closeDocument()
            if (current(token))
                Snapshot.withMutableSnapshot {
                    mutable.stage = WorkspaceStage.FAILED
                    mutable.error = message(e)
                    mutable.rendering = false
                }
        }
    }

    fun select(index: Int) {
        if (state.stage != WorkspaceStage.READY || index !in 0 until state.pageCount) return
        request(index, clear = index != state.pageIndex)
    }

    fun setZoom(value: Int) {
        val zoom = value.coerceIn(50, 200)
        if (zoom == state.zoom) return
        mutable.zoom = zoom
        if (state.stage == WorkspaceStage.READY) request(state.pageIndex, false, debounce = true)
    }

    fun viewport(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || (this.width == width && this.height == height)) return
        this.width = width
        this.height = height
        if (state.stage == WorkspaceStage.READY) request(state.pageIndex, false)
    }

    fun cancel() = schedule {
        closeDocument()
        Snapshot.withMutableSnapshot {
            mutable.previews = emptyMap()
            mutable.stage = WorkspaceStage.EMPTY
            mutable.image = null
            mutable.error = null
            mutable.info = null
            mutable.rendering = false
            mutable.name = ""
            mutable.pageCount = 0
        }
    }

    private fun request(index: Int, clear: Boolean, debounce: Boolean = false) {
        val active = document ?: return
        val targetWidth =
            (width.toLong() * state.zoom / 100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val targetHeight =
            (height.toLong() * state.zoom / 100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val reusable = reusableFrame(index, targetWidth, targetHeight)
        if (reusable != null) {
            if (index == state.pageIndex && !state.rendering && state.image === reusable.image)
                return
            Snapshot.withMutableSnapshot {
                mutable.pageIndex = index
                mutable.image = reusable.image
                mutable.info = reusable.info
                mutable.error = null
                mutable.rendering = false
            }
            schedule { loadPreviews() }
            return
        }
        Snapshot.withMutableSnapshot {
            mutable.pageIndex = index
            mutable.error = null
            mutable.rendering = true
            if (clear) {
                val preview = state.previews[index] as? MutableThumbnailState
                mutable.image = preview?.image
                mutable.info = if (preview?.image != null) preview.info else null
                mutable.renderMillis = null
            }
        }
        schedule { token ->
            if (debounce) delay(120)
            if (active === document) render(token, active, index, targetWidth, targetHeight)
        }
    }

    private suspend fun render(
        token: Int,
        active: TiffRenderer,
        index: Int,
        width: Int,
        height: Int,
    ) {
        if (current(token)) mutable.rendering = true
        try {
            val started = TimeSource.Monotonic.markNow()
            val info = active.pageInfo(index)
            val (outputWidth, outputHeight) = outputSize(info, width, height)
            val image =
                active
                    .renderPage(
                        index,
                        RenderSize.fit(outputWidth, outputHeight),
                    )
                    .image
                    .toImageBitmap()
            if (current(token)) {
                rememberFrame(RenderedFrame(info, image))
                Snapshot.withMutableSnapshot {
                    mutable.info = info
                    mutable.image = image
                    mutable.error = null
                    mutable.rendering = false
                    mutable.renderMillis = started.elapsedNow().inWholeMilliseconds
                }
            }
            if (current(token)) loadPreviews()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (current(token))
                Snapshot.withMutableSnapshot {
                    mutable.image = null
                    mutable.rendering = false
                    mutable.error = message(e)
                }
        }
    }

    private fun outputSize(info: TiffPageInfo, width: Int, height: Int): Pair<Int, Int> {
        val scale =
            min(
                1.0,
                min(
                    min(width.toDouble() / info.width, height.toDouble() / info.height),
                    min(
                        sqrt(8_000_000.0 / (info.width.toDouble() * info.height)),
                        16_384.0 / maxOf(info.width, info.height),
                    ),
                ),
            )
        return (info.width * scale).roundToInt().coerceAtLeast(1) to
            (info.height * scale).roundToInt().coerceAtLeast(1)
    }

    private fun reusableFrame(index: Int, width: Int, height: Int): RenderedFrame? {
        val currentInfo = state.info
        val currentImage = state.image
        val cached = frames[index]
        val candidate =
            if (
                currentInfo?.index == index &&
                    currentImage != null &&
                    (cached == null || currentImage.width >= cached.image.width)
            )
                RenderedFrame(currentInfo, currentImage)
            else cached ?: return null
        val (requiredWidth, requiredHeight) = outputSize(candidate.info, width, height)
        if (candidate.image.width < requiredWidth || candidate.image.height < requiredHeight)
            return null
        frames.remove(index)?.let { frames[index] = it }
        return candidate
    }

    private fun rememberFrame(frame: RenderedFrame) {
        frames.remove(frame.info.index)
        val budget = 24L * 1024 * 1024
        if (frame.bytes > budget) return
        while (
            frames.isNotEmpty() &&
                (frames.size >= 4 || frames.values.sumOf { it.bytes } + frame.bytes > budget)
        ) {
            frames.remove(frames.keys.first())
        }
        frames[frame.info.index] = frame
    }

    fun visiblePages(indices: List<Int>, width: Int = 320, height: Int = 480) {
        val bounded = indices.filter { it in 0 until state.pageCount }.distinct().take(12)
        val targetWidth = width.coerceIn(1, 512)
        val targetHeight = height.coerceIn(1, 896)
        if (bounded == visiblePages && targetWidth == previewWidth && targetHeight == previewHeight)
            return
        visiblePages = bounded
        previewWidth = targetWidth
        previewHeight = targetHeight
        loadPreviews()
    }

    private fun loadPreviews() {
        val active = document ?: return
        if (disposed || state.rendering || state.stage != WorkspaceStage.READY) return
        val token = generation
        val requested = visiblePages.filter { it in 0 until state.pageCount }
        val cells = mutable.previews.toMutableMap()
        var changed = false
        for (index in requested) {
            if (index !in cells) {
                if (cells.size >= 48)
                    cells.keys.firstOrNull { it !in requested }?.let(cells::remove)
                cells[index] = MutableThumbnailState()
                changed = true
            }
        }
        if (changed) mutable.previews = cells.toMap()
        val width = previewWidth
        val height = previewHeight
        val pages = requested.filter {
            val cell = mutable.previews[it] as? MutableThumbnailState
            cell == null ||
                cell.image == null ||
                cell.requestedWidth < width ||
                cell.requestedHeight < height
        }
        val previous = previewsJob
        previous?.cancel()
        if (pages.isEmpty()) return
        previewsJob = scope.launch {
            previous?.join()
            delay(150)
            for (index in pages) {
                if (!current(token) || active !== document) break
                try {
                    val info = active.pageInfo(index)
                    val scale =
                        min(1.0, sqrt((16.0 * 1024 * 1024 / 12 / 4) / (width.toDouble() * height)))
                    val image =
                        active
                            .renderPage(
                                index,
                                RenderSize.fit(
                                    (width * scale).toInt().coerceAtLeast(1),
                                    (height * scale).toInt().coerceAtLeast(1),
                                ),
                            )
                            .image
                            .toImageBitmap()
                    if (!current(token) || active !== document) break
                    val cell = mutable.previews[index] as? MutableThumbnailState ?: continue
                    val incomingBytes = image.width.toLong() * image.height * 4
                    var retainedBytes =
                        mutable.previews
                            .filterKeys { it != index }
                            .values
                            .sumOf {
                                it.image?.let { picture ->
                                    picture.width.toLong() * picture.height * 4
                                } ?: 0L
                            }
                    for ((key, value) in mutable.previews) {
                        if (retainedBytes + incomingBytes <= 16L * 1024 * 1024) break
                        if (key !in requested && value is MutableThumbnailState) {
                            value.image?.let { retainedBytes -= it.width.toLong() * it.height * 4 }
                            value.image = null
                        }
                    }
                    cell.info = info
                    cell.requestedWidth = width
                    cell.requestedHeight = height
                    cell.image = image
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: TiffIOException) {}
            }
        }
    }

    private fun current(token: Int): Boolean = !disposed && token == generation

    private fun schedule(action: suspend (Int) -> Unit) {
        if (disposed) return
        val token = ++generation
        val previous = operation
        previous?.cancel()
        val previousPreviews = previewsJob
        previousPreviews?.cancel()
        operation = scope.launch {
            previous?.join()
            previousPreviews?.join()
            if (current(token)) action(token)
        }
    }

    private suspend fun closeDocument() =
        withContext(NonCancellable) {
            frames.clear()
            val previous = document
            document = null
            try {
                previous?.close()
            } catch (_: TiffIOException) {
                // The core releases its resources even if a failed worker cannot acknowledge close.
            }
        }

    suspend fun ownLifetime(): Nothing =
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                disposed = true
                generation++
                operation?.cancel()
                previewsJob?.cancel()
                operation?.join()
                previewsJob?.join()
                closeDocument()
            }
        }
}

private fun message(error: Exception): String =
    when ((error as? TiffIOException)?.reason) {
        TiffError.RESOURCE_LIMIT ->
            "This page exceeds the document's memory limit. Try a smaller file."
        TiffError.PARTIAL_DECODE -> "Part of this page is damaged and could not be decoded safely."
        TiffError.INPUT_UNAVAILABLE -> "This file is no longer available. Choose it again."
        else -> "We couldn't read this document. Check the file and try again."
    }
