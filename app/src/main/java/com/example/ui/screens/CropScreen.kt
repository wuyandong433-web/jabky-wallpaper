package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LensBlur
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Vignette
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BlurRainbowStyle
import com.example.data.model.BlurStyle
import com.example.data.model.BlurTintMode
import com.example.data.model.CropFillMode
import com.example.data.model.OriginalMedia
import com.example.data.model.PageItem
import com.example.ui.blurRainbowStyleLabelRes
import com.example.ui.blurStyleDescRes
import com.example.ui.blurStyleShortLabelRes
import com.example.ui.blurTintModeLabelRes
import com.example.ui.cropFillModeLabelRes
import com.example.util.ImageProcessingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private fun calculatePreviewTravel(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Float,
    targetHeight: Float,
    scale: Float,
    panX: Float,
    cropFillMode: CropFillMode,
    amount: Float
): Pair<Float, Float> {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0f || targetHeight <= 0f) {
        return 0f to 0f
    }

    val sourceRatio = sourceWidth.toFloat() / sourceHeight
    val targetRatio = targetWidth / targetHeight
    val baseCropWidth = if (sourceRatio > targetRatio) sourceHeight * targetRatio else sourceWidth.toFloat()
    val cropWidth = if (cropFillMode == CropFillMode.CROP) {
        baseCropWidth / scale.coerceIn(1f, 3f)
    } else {
        baseCropWidth
    }
    val left = if (cropFillMode == CropFillMode.CROP) {
        val centerX = sourceWidth / 2f -
            panX.coerceIn(-1f, 1f) * ((sourceWidth - cropWidth) / 2f).coerceAtLeast(0f)
        (centerX - cropWidth / 2f).coerceIn(0f, sourceWidth - cropWidth)
    } else {
        val fitScale = minOf(targetWidth / sourceWidth, targetHeight / sourceHeight) *
            scale.coerceIn(1f, 3f)
        val drawWidth = sourceWidth * fitScale
        val centerX = targetWidth / 2f +
            panX.coerceIn(-1f, 1f) * ((drawWidth - targetWidth) / 2f).coerceAtLeast(0f)
        centerX - drawWidth / 2f
    }
    val availableLeftTravel: Float
    val availableRightTravel: Float
    if (cropFillMode == CropFillMode.CROP) {
        availableLeftTravel = left * targetWidth / cropWidth
        availableRightTravel = (sourceWidth - left - cropWidth) * targetWidth / cropWidth
    } else {
        availableLeftTravel = (-left).coerceAtLeast(0f)
        availableRightTravel = (left + sourceWidth * minOf(
            targetWidth / sourceWidth,
            targetHeight / sourceHeight
        ) * scale.coerceIn(1f, 3f) - targetWidth).coerceAtLeast(0f)
    }
    val travelAmount = amount.coerceIn(0f, 1f)
    return (availableLeftTravel * travelAmount).coerceAtLeast(0f) to
        (availableRightTravel * travelAmount).coerceAtLeast(0f)
}

private enum class EditorPreviewMode {
    SINGLE_PAGE,
    FULL_IMAGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaEditorScreen(
    media: OriginalMedia,
    page: PageItem,
    pageCount: Int,
    isSaving: Boolean,
    initialSplitFraction: Float = 0.40f,
    onSplitFractionChange: (Float) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (PageItem) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(media.id) { mutableStateOf<Bitmap?>(null) }
    var blurredBitmap by remember(media.id) { mutableStateOf<Bitmap?>(null) }
    var panX by remember(media.id, page.pageIndex) { mutableFloatStateOf(page.cropPanX) }
    var panY by remember(media.id, page.pageIndex) { mutableFloatStateOf(page.cropPanY) }
    var zoom by remember(media.id, page.pageIndex) { mutableFloatStateOf(page.cropScale) }
    var isAlreadyDark by remember(media.id, page.pageIndex) { mutableStateOf(page.isAlreadyDark) }
    var cropFillMode by remember(media.id, page.pageIndex) { mutableStateOf(page.cropFillMode) }
    var blurAmount by remember(media.id, page.pageIndex) { mutableFloatStateOf(page.blurAmount) }
    var blurStyle by remember(media.id, page.pageIndex) { mutableStateOf(page.blurStyle) }
    var blurTintMode by remember(media.id, page.pageIndex) { mutableStateOf(page.blurTintMode) }
    var blurTintColor by remember(media.id, page.pageIndex) { mutableIntStateOf(page.blurTintColor) }
    var blurTintIntensity by remember(media.id, page.pageIndex) { mutableFloatStateOf(page.blurTintIntensity) }
    var blurRainbowStyle by remember(media.id, page.pageIndex) { mutableStateOf(page.blurRainbowStyle) }
    var parallaxAmount by remember(media.id, page.pageIndex) { mutableFloatStateOf(page.parallaxAmount) }
    var previewMode by remember(media.id, page.pageIndex) { mutableStateOf(EditorPreviewMode.FULL_IMAGE) }
    var splitFraction by remember {
        mutableFloatStateOf(initialSplitFraction.coerceIn(0.22f, 0.72f))
    }

    LaunchedEffect(initialSplitFraction) {
        splitFraction = initialSplitFraction.coerceIn(0.22f, 0.72f)
    }

    BackHandler(enabled = !isSaving) {
        onSplitFractionChange(splitFraction)
        onDismiss()
    }

    LaunchedEffect(media.id) {
        val loaded = withContext(Dispatchers.IO) {
            val file = File(File(context.filesDir, "originals"), media.fileName)
            if (file.exists()) {
                val sampleSize = (
                    maxOf(media.width, media.height) / 2048
                ).coerceAtLeast(1)
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                )
            } else {
                null
            }
        }
        bitmap = loaded
    }

    LaunchedEffect(
        media.id,
        bitmap,
        cropFillMode,
        blurAmount,
        blurStyle,
        blurTintMode,
        blurTintColor,
        blurTintIntensity,
        blurRainbowStyle
    ) {
        val source = bitmap ?: return@LaunchedEffect
        if (cropFillMode == CropFillMode.CROP) {
            blurredBitmap = null
            return@LaunchedEffect
        }
        if (blurredBitmap != null) {
            delay(40)
        }
        val preview = withContext(Dispatchers.Default) {
            ImageProcessingUtils.renderBackdropPreviewBitmap(
                srcBmp = source,
                targetWidth = 270,
                targetHeight = 585,
                blurAmount = blurAmount,
                blurStyle = blurStyle,
                blurTintMode = blurTintMode,
                blurTintColor = blurTintColor,
                blurTintIntensity = blurTintIntensity,
                blurRainbowStyle = blurRainbowStyle
            )
        }
        blurredBitmap = preview
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.crop_edit_page_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.crop_edit_page_subtitle, media.width, media.height),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onSplitFractionChange(splitFraction)
                            onDismiss()
                        },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("crop_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            onSplitFractionChange(splitFraction)
                            onDismiss()
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f).height(50.dp).testTag("crop_cancel_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            onSplitFractionChange(splitFraction)
                            onSave(
                                page.copy(
                                    isAlreadyDark = isAlreadyDark,
                                    cropFillMode = cropFillMode,
                                    blurAmount = blurAmount,
                                    blurStyle = blurStyle,
                                    blurTintMode = blurTintMode,
                                    blurTintColor = blurTintColor,
                                    blurTintIntensity = blurTintIntensity,
                                    blurRainbowStyle = blurRainbowStyle,
                                    cropPanX = panX,
                                    cropPanY = panY,
                                    cropScale = zoom,
                                    parallaxAmount = parallaxAmount
                                )
                            )
                        },
                        enabled = !isSaving && bitmap != null,
                        modifier = Modifier.weight(1.5f).height(50.dp).testTag("crop_confirm_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(if (isSaving) R.string.crop_saving else R.string.crop_save_settings))
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            val availableHeight = this.maxHeight
            val availableWidth = this.maxWidth
            val density = LocalDensity.current
            val dividerHeight = 28.dp
            val minPreviewHeight = 132.dp
            val minSettingsHeight = 160.dp
            val usableHeight = (availableHeight - dividerHeight).coerceAtLeast(minPreviewHeight)
            val maxPreviewHeight = (usableHeight - minSettingsHeight).coerceAtLeast(minPreviewHeight)
            val previewPaneHeight = (usableHeight * splitFraction).coerceIn(minPreviewHeight, maxPreviewHeight)

            val viewportAspect = 9f / 19.5f
            val maxViewportWidth = (availableWidth - 48.dp).coerceAtLeast(60.dp)
            val maxViewportHeight = (previewPaneHeight - 16.dp).coerceAtLeast(100.dp)
            val viewportHeight = minOf(maxViewportHeight, maxViewportWidth / viewportAspect)
            val viewportWidth = viewportHeight * viewportAspect
            val viewportWidthPx = with(density) { viewportWidth.toPx() }.coerceAtLeast(1f)
            val viewportHeightPx = with(density) { viewportHeight.toPx() }.coerceAtLeast(1f)
            val previewTravel = bitmap?.let {
                calculatePreviewTravel(
                    sourceWidth = it.width,
                    sourceHeight = it.height,
                    targetWidth = viewportWidthPx,
                    targetHeight = viewportHeightPx,
                    scale = zoom,
                    panX = panX,
                    cropFillMode = cropFillMode,
                    amount = parallaxAmount
                )
            } ?: (0f to 0f)
            val leftPreviewTravelPx = previewTravel.first
            val rightPreviewTravelPx = previewTravel.second

            val switchPreviewCd = stringResource(R.string.crop_preview_switch_cd)
            val resizeDividerCd = stringResource(R.string.crop_split_divider_cd)

            Column(modifier = Modifier.fillMaxSize()) {
                // Fixed non-scrolling top preview pane
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewPaneHeight)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clipToBounds()
                        .background(Color.Black)
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        )
                        .clickable(
                            onClickLabel = switchPreviewCd,
                            role = Role.Button
                        ) {
                            previewMode = if (previewMode == EditorPreviewMode.SINGLE_PAGE) {
                                EditorPreviewMode.FULL_IMAGE
                            } else {
                                EditorPreviewMode.SINGLE_PAGE
                            }
                        }
                        .testTag("crop_merged_preview"),
                    contentAlignment = Alignment.Center
                ) {
                    val source = bitmap
                    if (previewMode == EditorPreviewMode.SINGLE_PAGE) {
                        val singlePageShape = RoundedCornerShape((viewportWidth * 0.09f).coerceIn(6.dp, 12.dp))
                        Box(
                            modifier = Modifier
                                .size(width = viewportWidth, height = viewportHeight)
                                .clip(singlePageShape)
                                .clipToBounds()
                                .background(Color.Black)
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    singlePageShape
                                )
                                .pointerInput(bitmap, cropFillMode, zoom) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        panX = (panX + dragAmount.x / size.width * 2f).coerceIn(-1f, 1f)
                                        panY = (panY + dragAmount.y / size.height * 2f).coerceIn(-1f, 1f)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (source != null) {
                                if (cropFillMode != CropFillMode.CROP) {
                                    Image(
                                        bitmap = (blurredBitmap ?: source).asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(if (blurredBitmap == null) (blurAmount * 20f).dp else 0.dp)
                                    )
                                }
                                Canvas(Modifier.fillMaxSize().clipToBounds()) {
                                    val targetWidth = size.width
                                    val targetHeight = size.height
                                    val sourceRatio = source.width.toFloat() / source.height
                                    val targetRatio = targetWidth / targetHeight
                                    val baseCropWidth = if (sourceRatio > targetRatio) {
                                        source.height * targetRatio
                                    } else {
                                        source.width.toFloat()
                                    }
                                    val baseCropHeight = if (sourceRatio > targetRatio) {
                                        source.height.toFloat()
                                    } else {
                                        source.width / targetRatio
                                    }
                                    val drawPaint = Paint(
                                        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
                                    )
                                    drawIntoCanvas { composeCanvas ->
                                        val nativeCanvas = composeCanvas.nativeCanvas
                                        if (cropFillMode == CropFillMode.CROP) {
                                            val cropWidth = baseCropWidth / zoom
                                            val cropHeight = baseCropHeight / zoom
                                            val centerX = source.width / 2f -
                                                panX * ((source.width - cropWidth) / 2f).coerceAtLeast(0f)
                                            val centerY = source.height / 2f -
                                                panY * ((source.height - cropHeight) / 2f).coerceAtLeast(0f)
                                            val left = (centerX - cropWidth / 2f)
                                                .coerceIn(0f, source.width - cropWidth)
                                            val top = (centerY - cropHeight / 2f)
                                                .coerceIn(0f, source.height - cropHeight)
                                            val sourceLeft = left.toInt().coerceIn(0, source.width - 1)
                                            val sourceTop = top.toInt().coerceIn(0, source.height - 1)
                                            nativeCanvas.drawBitmap(
                                                source,
                                                Rect(
                                                    sourceLeft,
                                                    sourceTop,
                                                    (left + cropWidth).toInt()
                                                        .coerceIn(sourceLeft + 1, source.width),
                                                    (top + cropHeight).toInt()
                                                        .coerceIn(sourceTop + 1, source.height)
                                                ),
                                                Rect(0, 0, targetWidth.toInt(), targetHeight.toInt()),
                                                drawPaint
                                            )
                                        } else {
                                            val fitScale = minOf(
                                                targetWidth / source.width,
                                                targetHeight / source.height
                                            ) * zoom
                                            val imageWidth = source.width * fitScale
                                            val imageHeight = source.height * fitScale
                                            val centerX = targetWidth / 2f +
                                                panX * ((imageWidth - targetWidth) / 2f).coerceAtLeast(0f)
                                            val centerY = targetHeight / 2f +
                                                panY * ((imageHeight - targetHeight) / 2f).coerceAtLeast(0f)
                                            val fgDstRect = RectF(
                                                centerX - imageWidth / 2f,
                                                centerY - imageHeight / 2f,
                                                centerX + imageWidth / 2f,
                                                centerY + imageHeight / 2f
                                            )
                                            ImageProcessingUtils.drawForegroundShadow(nativeCanvas, fgDstRect)
                                            nativeCanvas.drawBitmap(
                                                source,
                                                null,
                                                fgDstRect,
                                                drawPaint
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(stringResource(R.string.crop_unable_to_load), color = Color.White)
                            }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), singlePageShape)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("crop_parallax_bounds_preview")
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                val src = source ?: return@Canvas
                                val imageScale = minOf(size.width / src.width, size.height / src.height)
                                val imageWidth = src.width * imageScale
                                val imageHeight = src.height * imageScale
                                val imageLeft = (size.width - imageWidth) / 2f
                                val imageTop = (size.height - imageHeight) / 2f
                                val targetRatio = viewportWidthPx / viewportHeightPx
                                val sourceRatio = src.width.toFloat() / src.height
                                val baseCropWidth = if (sourceRatio > targetRatio) {
                                    src.height * targetRatio
                                } else {
                                    src.width.toFloat()
                                }
                                val baseCropHeight = if (sourceRatio > targetRatio) {
                                    src.height.toFloat()
                                } else {
                                    src.width / targetRatio
                                }
                                val frameLeft: Float
                                val frameRight: Float
                                val frameTop: Float
                                val frameBottom: Float
                                val sourcePerViewportPixel: Float
                                if (cropFillMode == CropFillMode.CROP) {
                                    val cropWidth = baseCropWidth / zoom
                                    val cropHeight = baseCropHeight / zoom
                                    val cropCenterX = src.width / 2f -
                                        panX * ((src.width - cropWidth) / 2f).coerceAtLeast(0f)
                                    val cropCenterY = src.height / 2f -
                                        panY * ((src.height - cropHeight) / 2f).coerceAtLeast(0f)
                                    frameLeft = (cropCenterX - cropWidth / 2f)
                                        .coerceIn(0f, src.width - cropWidth)
                                    frameRight = frameLeft + cropWidth
                                    frameTop = (cropCenterY - cropHeight / 2f)
                                        .coerceIn(0f, src.height - cropHeight)
                                    frameBottom = frameTop + cropHeight
                                    sourcePerViewportPixel = cropWidth / viewportWidthPx
                                } else {
                                    val imageFitScale = minOf(
                                        viewportWidthPx / src.width,
                                        viewportHeightPx / src.height
                                    ) * zoom
                                    val drawWidth = src.width * imageFitScale
                                    val drawHeight = src.height * imageFitScale
                                    val imageLeftInViewport = viewportWidthPx / 2f +
                                        panX * ((drawWidth - viewportWidthPx) / 2f).coerceAtLeast(0f) -
                                        drawWidth / 2f
                                    val imageTopInViewport = viewportHeightPx / 2f +
                                        panY * ((drawHeight - viewportHeightPx) / 2f).coerceAtLeast(0f) -
                                        drawHeight / 2f
                                    frameLeft = ((-imageLeftInViewport) / imageFitScale)
                                        .coerceIn(0f, src.width.toFloat())
                                    frameRight = ((viewportWidthPx - imageLeftInViewport) / imageFitScale)
                                        .coerceIn(frameLeft, src.width.toFloat())
                                    frameTop = ((-imageTopInViewport) / imageFitScale)
                                        .coerceIn(0f, src.height.toFloat())
                                    frameBottom = ((viewportHeightPx - imageTopInViewport) / imageFitScale)
                                        .coerceIn(frameTop, src.height.toFloat())
                                    sourcePerViewportPixel = 1f / imageFitScale
                                }
                                val leftSourceTravel = leftPreviewTravelPx * sourcePerViewportPixel
                                val rightSourceTravel = rightPreviewTravelPx * sourcePerViewportPixel
                                val reachLeft = (frameLeft - leftSourceTravel).coerceAtLeast(0f)
                                val reachRight = (frameRight + rightSourceTravel)
                                    .coerceAtMost(src.width.toFloat())
                                val nativeCanvas = drawContext.canvas.nativeCanvas
                                val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                nativeCanvas.drawBitmap(
                                    src,
                                    null,
                                    RectF(imageLeft, imageTop, imageLeft + imageWidth, imageTop + imageHeight),
                                    imagePaint
                                )

                                val mapX = { x: Float -> imageLeft + x * imageScale }
                                val mapY = { y: Float -> imageTop + y * imageScale }
                                val reachBounds = RectF(
                                    mapX(reachLeft),
                                    mapY(frameTop),
                                    mapX(reachRight),
                                    mapY(frameBottom)
                                )
                                val staticBounds = RectF(
                                    mapX(frameLeft),
                                    mapY(frameTop),
                                    mapX(frameRight),
                                    mapY(frameBottom)
                                )
                                val reachStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.rgb(255, 180, 0)
                                    style = Paint.Style.STROKE
                                    strokeWidth = 2.dp.toPx()
                                }
                                val staticStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.rgb(80, 220, 255)
                                    style = Paint.Style.STROKE
                                    strokeWidth = 2.dp.toPx()
                                }
                                nativeCanvas.drawRect(reachBounds, reachStroke)
                                nativeCanvas.drawRect(staticBounds, staticStroke)
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp, start = 10.dp, end = 10.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.72f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF50DCFF))
                                        )
                                        Text(
                                            text = stringResource(R.string.crop_legend_static),
                                            color = Color(0xFF50DCFF),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFFB400))
                                        )
                                        Text(
                                            text = stringResource(R.string.crop_legend_reach),
                                            color = Color(0xFFFFB400),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Mode indicator pill in top-right corner of merged preview
                    Surface(
                        onClick = {
                            previewMode = if (previewMode == EditorPreviewMode.SINGLE_PAGE) {
                                EditorPreviewMode.FULL_IMAGE
                            } else {
                                EditorPreviewMode.SINGLE_PAGE
                            }
                        },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.68f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .testTag("crop_preview_mode_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (previewMode == EditorPreviewMode.SINGLE_PAGE) {
                                    Icons.Default.Smartphone
                                } else {
                                    Icons.Default.AspectRatio
                                },
                                contentDescription = switchPreviewCd,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (previewMode == EditorPreviewMode.SINGLE_PAGE) {
                                        R.string.crop_preview_mode_single
                                    } else {
                                        R.string.crop_preview_mode_full
                                    }
                                ),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Android split-screen-style draggable resize handle
                val usableHeightPx = with(density) { usableHeight.toPx() }.coerceAtLeast(1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dividerHeight)
                        .semantics { contentDescription = resizeDividerCd }
                        .pointerInput(usableHeightPx) {
                            detectVerticalDragGestures(
                                onDragEnd = { onSplitFractionChange(splitFraction) },
                                onDragCancel = { onSplitFractionChange(splitFraction) }
                            ) { change, dragAmount ->
                                change.consume()
                                splitFraction = (splitFraction + dragAmount / usableHeightPx)
                                    .coerceIn(0.22f, 0.72f)
                            }
                        }
                        .testTag("crop_split_divider"),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                        thickness = 1.dp
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(width = 44.dp, height = 5.dp)
                    ) {}
                }

                // Scrollable bottom settings pane
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(stringResource(R.string.crop_image_fit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CropFillMode.values().forEach { mode ->
                                FilterChip(
                                    selected = cropFillMode == mode,
                                    onClick = {
                                        cropFillMode = mode
                                        if (mode == CropFillMode.BLUR &&
                                            (blurStyle == BlurStyle.WIN7_AERO || blurStyle == BlurStyle.MODERN_ACRYLIC)
                                        ) {
                                            blurStyle = BlurStyle.SMOOTH_GAUSSIAN
                                        } else if (mode == CropFillMode.GLASS &&
                                            blurStyle == BlurStyle.SMOOTH_GAUSSIAN
                                        ) {
                                            blurStyle = BlurStyle.WIN7_AERO
                                        }
                                    },
                                    label = { Text(stringResource(cropFillModeLabelRes(mode)), style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("crop_fill_${mode.name.lowercase()}")
                                )
                            }
                        }
                        if (cropFillMode != CropFillMode.CROP) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.crop_blur_style), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BlurStyle.values().forEach { style ->
                                        FilterChip(
                                            selected = blurStyle == style,
                                            onClick = {
                                                blurStyle = style
                                                cropFillMode = if (
                                                    style == BlurStyle.WIN7_AERO ||
                                                    style == BlurStyle.MODERN_ACRYLIC ||
                                                    style == BlurStyle.PRISM_REFRACTION
                                                ) {
                                                    CropFillMode.GLASS
                                                } else {
                                                    CropFillMode.BLUR
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = blurStyleIcon(style),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text(stringResource(blurStyleShortLabelRes(style)), style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.testTag("blur_style_${style.name.lowercase()}")
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(blurStyleDescRes(blurStyle)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.crop_bg_blur, (blurAmount * 100).toInt()), style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = blurAmount,
                                    onValueChange = { blurAmount = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.testTag("crop_blur_slider")
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        stringResource(R.string.crop_color_tinting),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BlurTintMode.values().forEach { mode ->
                                        FilterChip(
                                            selected = blurTintMode == mode,
                                            onClick = { blurTintMode = mode },
                                            label = { Text(stringResource(blurTintModeLabelRes(mode)), style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("tint_mode_${mode.name.lowercase()}")
                                        )
                                    }
                                }

                                when (blurTintMode) {
                                    BlurTintMode.NONE -> Unit

                                    BlurTintMode.SOLID -> {
                                        val hsv = remember(blurTintColor) {
                                            FloatArray(3).also {
                                                android.graphics.Color.colorToHSV(blurTintColor, it)
                                            }
                                        }
                                        val selectTintColorCd = stringResource(R.string.crop_tint_swatch_cd)
                                        val selectedTintColorCd = stringResource(R.string.crop_tint_selected_cd)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TINT_SWATCH_COLORS.forEachIndexed { idx, swatchColor ->
                                                val selected = blurTintColor == swatchColor
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                        .clickable(
                                                            role = Role.RadioButton,
                                                            onClickLabel = selectTintColorCd
                                                        ) { blurTintColor = swatchColor }
                                                        .testTag("tint_swatch_$idx"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(swatchColor))
                                                            .border(
                                                                width = if (selected) 2.5.dp else 1.dp,
                                                                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f),
                                                                shape = CircleShape
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (selected) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = selectedTintColorCd,
                                                                tint = if (Color(swatchColor).luminanceCompat() > 0.6f) Color.Black else Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(blurTintColor))
                                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                            )
                                            Text(
                                                stringResource(R.string.crop_custom_hue),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(
                                                                Color.Red,
                                                                Color.Yellow,
                                                                Color.Green,
                                                                Color.Cyan,
                                                                Color.Blue,
                                                                Color.Magenta,
                                                                Color.Red
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                        Slider(
                                            value = hsv[0],
                                            onValueChange = { hue ->
                                                val sat = hsv[1].coerceAtLeast(0.75f)
                                                val value = hsv[2].coerceAtLeast(0.88f)
                                                blurTintColor = android.graphics.Color.HSVToColor(
                                                    floatArrayOf(hue.coerceIn(0f, 360f), sat, value)
                                                )
                                            },
                                            valueRange = 0f..360f,
                                            modifier = Modifier.testTag("tint_hue_slider")
                                        )
                                        Text(
                                            stringResource(R.string.crop_tint_intensity, (blurTintIntensity * 100).toInt()),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Slider(
                                            value = blurTintIntensity,
                                            onValueChange = { blurTintIntensity = it },
                                            valueRange = 0f..1f,
                                            modifier = Modifier.testTag("tint_intensity_slider")
                                        )
                                    }

                                    BlurTintMode.RAINBOW -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            BlurRainbowStyle.values().forEach { style ->
                                                FilterChip(
                                                    selected = blurRainbowStyle == style,
                                                    onClick = { blurRainbowStyle = style },
                                                    label = { Text(stringResource(blurRainbowStyleLabelRes(style)), style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.testTag("rainbow_style_${style.name.lowercase()}")
                                                )
                                            }
                                        }
                                        Text(
                                            stringResource(R.string.crop_rainbow_intensity, (blurTintIntensity * 100).toInt()),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Slider(
                                            value = blurTintIntensity,
                                            onValueChange = { blurTintIntensity = it },
                                            valueRange = 0f..1f,
                                            modifier = Modifier.testTag("tint_intensity_slider")
                                        )
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.crop_zoom_value, String.format(Locale.US, "%.1f", zoom)),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.width(12.dp))
                            Slider(
                                value = zoom,
                                onValueChange = { zoom = it },
                                valueRange = 1f..3f,
                                modifier = Modifier.weight(1f).testTag("crop_zoom_slider")
                            )
                        }
                        Text(
                            stringResource(
                                R.string.crop_parallax_value,
                                (parallaxAmount * 100).toInt(),
                                with(density) { leftPreviewTravelPx.toDp().value.roundToInt() },
                                with(density) { rightPreviewTravelPx.toDp().value.roundToInt() }
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = parallaxAmount,
                            onValueChange = {
                                parallaxAmount = it
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.testTag("crop_parallax_slider")
                        )
                        Text(
                            stringResource(R.string.crop_parallax_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            OutlinedButton(
                                onClick = { panX = 0f; panY = 0f; zoom = 1f },
                                contentPadding = ButtonDefaults.TextButtonContentPadding
                            ) { Text(stringResource(R.string.crop_pos_center), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(onClick = { panY = 1f }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                                Text(stringResource(R.string.crop_pos_top), style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = { panY = -1f }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                                Text(stringResource(R.string.crop_pos_bottom), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    if (isAlreadyDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = if (isAlreadyDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(stringResource(R.string.crop_already_dark), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        stringResource(R.string.crop_already_dark_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isAlreadyDark,
                                onCheckedChange = { isAlreadyDark = it },
                                modifier = Modifier.testTag("toggle_is_already_dark")
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

private val TINT_SWATCH_COLORS = listOf(
    0xFF38BDF8.toInt(), // Ice Cyan (Win7 Aero signature)
    0xFF6366F1.toInt(), // Royal Indigo
    0xFF10B981.toInt(), // Emerald Mint
    0xFFF43F5E.toInt(), // Coral Rose
    0xFFF59E0B.toInt(), // Warm Amber
    0xFFA855F7.toInt(), // Orchid Violet
    0xFF1E293B.toInt(), // Deep Obsidian Slate
    0xFFF8FAFC.toInt()  // Pure Frost Pearl
)

private fun blurStyleIcon(style: BlurStyle): ImageVector = when (style) {
    BlurStyle.SMOOTH_GAUSSIAN -> Icons.Default.BlurOn
    BlurStyle.WIN7_AERO -> Icons.Default.AutoAwesome
    BlurStyle.MODERN_ACRYLIC -> Icons.Default.Grain
    BlurStyle.SILKY_VIGNETTE -> Icons.Default.Vignette
    BlurStyle.BOKEH_BLOOM -> Icons.Default.LensBlur
    BlurStyle.PRISM_REFRACTION -> Icons.Default.Flare
}

private fun Color.luminanceCompat(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue

