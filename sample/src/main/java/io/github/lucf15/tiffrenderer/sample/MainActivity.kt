package io.github.lucf15.tiffrenderer.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.lucf15.tiffrenderer.sample.navigation.MainNavigation
import io.github.lucf15.tiffrenderer.sample.theme.AppTheme
import io.github.lucf15.tiffrenderer.sample.theme.ProvideAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            ProvideAppTheme {
                MainNavigation(
                    modifier = Modifier.fillMaxSize().background(AppTheme.colors.background),
                )
            }
        }
    }
}
