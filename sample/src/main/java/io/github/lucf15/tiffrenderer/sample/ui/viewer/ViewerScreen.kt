package io.github.lucf15.tiffrenderer.sample.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lucf15.tiffrenderer.sample.theme.AppTheme
import io.github.lucf15.tiffrenderer.sample.ui.components.TextActionButton

private val TopBarHorizontalPadding = 20.dp
private val PageGap = 16.dp

@Composable
fun ViewerScreen(
    uri: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val parsedUri = remember(uri, uri::toUri)
    val application = remember(context) { context.applicationContext as Application }
    val viewModel: ViewerScreenViewModel =
        viewModel(factory = ViewerScreenViewModel.Factory(application, parsedUri))

    when (val state = viewModel.uiState) {
        is ViewerUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(text = "Loading…", style = AppTheme.textStyles.bodyMedium)
            }
        }

        is ViewerUiState.Failed -> {
            FailedState(
                message = state.message,
                onBack = onBack,
                modifier = modifier,
            )
        }

        is ViewerUiState.Loaded -> {
            LoadedViewer(
                viewModel = viewModel,
                pageCount = state.pageCount,
                pageSizes = state.pageSizes,
                onBack = onBack,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun FailedState(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            BasicText(
                text = "⚠️",
                style = AppTheme.textStyles.displayMedium,
            )
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = "Couldn't open that file: $message",
                style = AppTheme.textStyles.bodyMedium.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(20.dp))
            TextActionButton(text = "Back", onClick = onBack)
        }
    }
}

@Composable
private fun LoadedViewer(
    viewModel: ViewerScreenViewModel,
    pageCount: Int,
    pageSizes: List<IntSize>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = AppTheme.colors.background,
        topBar = { ViewerTopBar(pageCount = pageCount, onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                innerPadding +
                    PaddingValues(
                        start = TopBarHorizontalPadding,
                        end = TopBarHorizontalPadding,
                        top = PageGap,
                    ) +
                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(PageGap),
        ) {
            items(
                count = pageCount,
                key = { it },
            ) { index ->
                TiffPageItem(
                    viewModel = viewModel,
                    index = index,
                    pageSize = pageSizes[index],
                )
            }
        }
    }
}

@Composable
private fun ViewerTopBar(
    pageCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(4.dp)
                .background(AppTheme.colors.surfaceContainer.copy(alpha = 0.92f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .padding(horizontal = TopBarHorizontalPadding, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "$pageCount page${if (pageCount == 1) "" else "s"}",
                style = AppTheme.textStyles.labelLarge,
            )
            TextActionButton(text = "Change file", onClick = onBack)
        }
    }
}

@Composable
private fun TiffPageItem(
    viewModel: ViewerScreenViewModel,
    index: Int,
    pageSize: IntSize,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    var error by remember(index) { mutableStateOf<String?>(null) }
    val aspectRatio = pageSize.width / pageSize.height.toFloat()

    LaunchedEffect(index) {
        runCatching { viewModel.renderPage(index) }
            .onSuccess { bitmap = it }
            .onFailure { error = it.message ?: "failed to render page" }
    }

    PageContent(
        bitmap = bitmap,
        error = error,
        index = index,
        aspectRatio = aspectRatio,
        modifier =
            modifier
                .fillMaxWidth()
                .dropShadow(
                    shape = RectangleShape,
                    shadow =
                        Shadow(
                            radius = 8.dp,
                            spread = 0.dp,
                            color = Color.Black.copy(alpha = 0.25f),
                            offset = DpOffset(0.dp, 2.dp),
                        ),
                ),
    )
}

@Composable
private fun PageContent(
    bitmap: Bitmap?,
    error: String?,
    index: Int,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (bitmap == null) Modifier.aspectRatio(aspectRatio) else Modifier)
                .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        } else if (error != null) {
            BasicText(
                text = "⚠️ $error",
                style = AppTheme.textStyles.bodyMedium.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
