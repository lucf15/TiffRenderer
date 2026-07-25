package io.github.lucf15.tiffrenderer.sample.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Picker : NavKey

@Serializable
data class Viewer(
    val uri: String,
) : NavKey
