package io.github.lucf15.tiffrenderer.sample.shared.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.lucf15.tiffrenderer.sample.shared.platform.rememberTiffSourceOpener
import io.github.lucf15.tiffrenderer.sample.shared.ui.picker.PickerScreen
import io.github.lucf15.tiffrenderer.sample.shared.ui.viewer.ViewerScreen

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(navConfig, Picker)
    val openTiffSource = rememberTiffSourceOpener()

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<Picker> {
                    PickerScreen(
                        onPicked = { id -> backStack.add(Viewer(id)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                entry<Viewer> { key ->
                    ViewerScreen(
                        source = openTiffSource(key.uri),
                        onBack = { backStack.removeLastOrNull() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
    )
}
