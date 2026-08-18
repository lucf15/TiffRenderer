package io.github.lucf15.tiffrenderer.sample.shared.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * KMP has no reflection, so unlike Android-only Nav3 usage (`rememberNavBackStack(Picker)`),
 * destination-key serialization needs this spelled out explicitly.
 */
val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Picker::class, Picker.serializer())
            subclass(Viewer::class, Viewer.serializer())
        }
    }
}
