package com.github.lucf15.tiffrenderer.sample.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.github.lucf15.tiffrenderer.sample.ui.picker.PickerScreen
import com.github.lucf15.tiffrenderer.sample.ui.viewer.ViewerScreen

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Picker)

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<Picker> {
                    PickerScreen(
                        onPicked = { uri -> backStack.add(Viewer(uri.toString())) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                entry<Viewer> { key ->
                    ViewerScreen(
                        uri = key.uri,
                        onBack = { backStack.removeLastOrNull() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
    )
}
