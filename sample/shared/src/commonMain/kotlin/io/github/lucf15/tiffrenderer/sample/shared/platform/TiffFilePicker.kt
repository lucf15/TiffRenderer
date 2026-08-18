package io.github.lucf15.tiffrenderer.sample.shared.platform

import androidx.compose.runtime.Composable
import io.github.lucf15.tiffrenderer.TiffSource

/** Returns a launch function for the platform's file picker. [onPicked] receives a
 * platform-specific identifier (an Android content URI, or an iOS filesystem path) that
 * [rememberTiffSourceOpener] can later reopen. */
@Composable
expect fun rememberTiffFilePickerLauncher(onPicked: (String) -> Unit): () -> Unit

/** Reopens a file identified by [rememberTiffFilePickerLauncher]'s callback into a [TiffSource]. */
@Composable
expect fun rememberTiffSourceOpener(): (String) -> TiffSource
