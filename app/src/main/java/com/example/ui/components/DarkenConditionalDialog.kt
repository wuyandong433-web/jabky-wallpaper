package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DarkenConfig
import com.example.data.model.DarkenTriggerMode
import com.example.data.model.WallpaperAppState
import com.example.ui.darkenTriggerLabelRes
import com.example.util.ImageProcessingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private enum class DarkenScheduleTimeField {
    START,
    END
}

@Composable
fun DarkenConditionalDialog(
    appState: WallpaperAppState,
    isSystemDarkMode: Boolean,
    loadPageThumbnail: suspend (Int) -> Bitmap?,
    onDismiss: () -> Unit,
    onConfirm: (DarkenConfig, Map<Int, Boolean>) -> Unit
) {
    val initialDarken = remember(appState.darken) { appState.darken.normalized() }
    var draftDarken by remember(appState.darken) { mutableStateOf(initialDarken) }
    var draftTargetBrightness by remember(appState.darken.targetBrightness) {
        mutableFloatStateOf(
            initialDarken.targetBrightness.coerceIn(
                DarkenConfig.MIN_TARGET_BRIGHTNESS,
                DarkenConfig.MAX_TARGET_BRIGHTNESS
            )
        )
    }
    val draftAlreadyDark = remember(appState.pages) {
        mutableStateMapOf<Int, Boolean>().apply {
            appState.pages.forEach { put(it.pageIndex, it.isAlreadyDark) }
        }
    }
    var showDarkenedPreview by remember { mutableStateOf(true) }
    var editingTimeField by remember { mutableStateOf<DarkenScheduleTimeField?>(null) }

    val baseThumbnails = remember { mutableStateMapOf<Int, Bitmap>() }
    val previewThumbnails = remember { mutableStateMapOf<Int, Bitmap>() }

    LaunchedEffect(appState.pages, appState.activePresetId) {
        val loaded = withContext(Dispatchers.IO) {
            appState.pages.mapNotNull { page ->
                if (page.isBlank) null
                else loadPageThumbnail(page.pageIndex)?.let { page.pageIndex to it }
            }
        }
        baseThumbnails.clear()
        loaded.forEach { (pageIndex, bmp) ->
            baseThumbnails[pageIndex] = bmp
        }
    }

    val alreadyDarkSnapshot = draftAlreadyDark.toMap()
    LaunchedEffect(
        baseThumbnails.toMap(),
        draftTargetBrightness,
        draftDarken.autoDetectDark,
        showDarkenedPreview,
        alreadyDarkSnapshot
    ) {
        if (baseThumbnails.isEmpty()) {
            previewThumbnails.clear()
            return@LaunchedEffect
        }
        // Slight debounce only when thumbnails are already populated so slider stays fluid
        if (previewThumbnails.isNotEmpty()) {
            delay(18)
        }
        val rendered = withContext(Dispatchers.Default) {
            baseThumbnails.mapValues { (pageIndex, baseBmp) ->
                val markedDark = alreadyDarkSnapshot[pageIndex] == true
                if (!showDarkenedPreview || markedDark) {
                    baseBmp
                } else {
                    ImageProcessingUtils.createNormalizedDarkenedCopy(
                        srcBmp = baseBmp,
                        targetBrightness = draftTargetBrightness,
                        skipIfAlreadyBelowTarget = draftDarken.autoDetectDark
                    )
                }
            }
        }
        previewThumbnails.clear()
        previewThumbnails.putAll(rendered)
    }

    SettingEditorDialog(
        title = stringResource(R.string.darken_conditions_title),
        icon = Icons.Default.Brightness4,
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(
                draftDarken.copy(targetBrightness = draftTargetBrightness).normalized(),
                draftAlreadyDark.toMap()
            )
        },
        dialogTestTag = "darken_conditions_editor_dialog"
    ) {
        // Unified multi-page brightness normalization preview
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("darken_multi_page_preview")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.darken_multi_preview_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        onClick = { showDarkenedPreview = !showDarkenedPreview },
                        shape = CircleShape,
                        color = if (showDarkenedPreview) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("darken_preview_compare_toggle")
                    ) {
                        Text(
                            text = stringResource(
                                if (showDarkenedPreview) {
                                    R.string.darken_preview_mode_darkened
                                } else {
                                    R.string.darken_preview_mode_original
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (showDarkenedPreview) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                val pages = appState.pages.sortedBy { it.pageIndex }
                val useCompactScroll = pages.size > 4
                Row(
                    modifier = if (useCompactScroll) {
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pages.forEach { page ->
                        val markedDark = draftAlreadyDark[page.pageIndex] ?: page.isAlreadyDark
                        val previewBmp = previewThumbnails[page.pageIndex] ?: baseThumbnails[page.pageIndex]
                        val tileModifier = if (useCompactScroll) {
                            Modifier.width(76.dp)
                        } else {
                            Modifier.weight(1f)
                        }
                        Column(
                            modifier = tileModifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !page.isBlank) {
                                    draftAlreadyDark[page.pageIndex] = !markedDark
                                }
                                .testTag("darken_preview_page_${page.pageIndex}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(9f / 16f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (previewBmp != null && !previewBmp.isRecycled) {
                                    Image(
                                        bitmap = previewBmp.asImageBitmap(),
                                        contentDescription = stringResource(
                                            R.string.studio_page_title,
                                            page.pageIndex + 1
                                        ),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = if (page.isBlank) {
                                            stringResource(R.string.studio_source_empty)
                                        } else {
                                            stringResource(
                                                R.string.darken_preview_page_short,
                                                page.pageIndex + 1
                                            )
                                        },
                                        color = Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (markedDark) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.72f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DarkMode,
                                            contentDescription = stringResource(R.string.crop_already_dark),
                                            tint = Color(0xFFFACC15),
                                            modifier = Modifier
                                                .padding(3.dp)
                                                .size(12.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.darken_preview_page_short,
                                        page.pageIndex + 1
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                if (markedDark) {
                                    Text(
                                        text = stringResource(R.string.darken_preview_page_dark_chip),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.darken_preview_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Single target brightness setting (audio-loudness-normalization style)
        EditorSliderControl(
            label = stringResource(R.string.darken_target_brightness_title),
            valueText = stringResource(
                R.string.percent_value,
                (draftTargetBrightness * 100f).roundToInt()
            ),
            value = draftTargetBrightness,
            onValueChange = { draftTargetBrightness = it },
            valueRange = DarkenConfig.MIN_TARGET_BRIGHTNESS..DarkenConfig.MAX_TARGET_BRIGHTNESS,
            sliderTestTag = "target_brightness_slider"
        )
        Text(
            text = stringResource(R.string.darken_target_brightness_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        EditorSwitchRow(
            title = stringResource(R.string.darken_smart_detect_title),
            description = stringResource(R.string.darken_smart_detect_desc),
            checked = draftDarken.autoDetectDark,
            onCheckedChange = { draftDarken = draftDarken.copy(autoDetectDark = it) },
            switchTestTag = "toggle_auto_detect_dark"
        )

        EditorSwitchRow(
            title = stringResource(R.string.darken_auto_title),
            description = stringResource(R.string.darken_auto_desc),
            checked = draftDarken.enabled,
            onCheckedChange = { draftDarken = draftDarken.copy(enabled = it) },
            switchTestTag = "toggle_darken_enabled"
        )

        AnimatedVisibility(visible = draftDarken.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.darken_trigger_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                DarkenTriggerMode.values().forEach { mode ->
                    val selected = draftDarken.triggerMode == mode
                    val modeTag = when (mode) {
                        DarkenTriggerMode.SCHEDULE_ONLY -> "darken_mode_schedule_only"
                        DarkenTriggerMode.DARK_MODE_ONLY -> "darken_mode_dark_mode_only"
                        DarkenTriggerMode.BOTH_OR -> "darken_mode_both_or"
                        DarkenTriggerMode.BATTERY_SAVER_ONLY -> "darken_mode_battery_saver_only"
                    }
                    val modeIcon = when (mode) {
                        DarkenTriggerMode.SCHEDULE_ONLY -> Icons.Default.Schedule
                        DarkenTriggerMode.DARK_MODE_ONLY -> Icons.Default.Brightness4
                        DarkenTriggerMode.BOTH_OR -> Icons.Default.AutoAwesome
                        DarkenTriggerMode.BATTERY_SAVER_ONLY -> Icons.Default.BatterySaver
                    }
                    val startFmt = formatDarkenScheduleTime(
                        draftDarken.scheduleStartHour,
                        draftDarken.scheduleStartMinute
                    )
                    val endFmt = formatDarkenScheduleTime(
                        draftDarken.scheduleEndHour,
                        draftDarken.scheduleEndMinute
                    )
                    val modeSubtitle = when (mode) {
                        DarkenTriggerMode.SCHEDULE_ONLY -> stringResource(
                            R.string.darken_mode_schedule_daily,
                            startFmt,
                            endFmt
                        )
                        DarkenTriggerMode.DARK_MODE_ONLY -> if (isSystemDarkMode) {
                            stringResource(R.string.darken_mode_dark_mode_active)
                        } else {
                            stringResource(R.string.darken_mode_dark_mode_only_desc)
                        }
                        DarkenTriggerMode.BOTH_OR -> stringResource(
                            R.string.darken_mode_both_or_desc,
                            startFmt,
                            endFmt
                        )
                        DarkenTriggerMode.BATTERY_SAVER_ONLY ->
                            stringResource(R.string.darken_mode_battery_only_desc)
                    }

                    EditorRadioOptionRow(
                        title = stringResource(darkenTriggerLabelRes(mode)),
                        description = modeSubtitle,
                        icon = modeIcon,
                        selected = selected,
                        onClick = { draftDarken = draftDarken.copy(triggerMode = mode) },
                        rowTestTag = modeTag
                    )
                }

                AnimatedVisibility(visible = draftDarken.triggerMode.usesSchedule) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.darken_schedule_window),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedCard(
                                onClick = { editingTimeField = DarkenScheduleTimeField.START },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .testTag("darken_schedule_start_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.darken_start_time),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatDarkenScheduleTime(
                                            draftDarken.scheduleStartHour,
                                            draftDarken.scheduleStartMinute
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedCard(
                                onClick = { editingTimeField = DarkenScheduleTimeField.END },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .testTag("darken_schedule_end_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.darken_end_time),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatDarkenScheduleTime(
                                            draftDarken.scheduleEndHour,
                                            draftDarken.scheduleEndMinute
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        val sameStartAndEnd =
                            draftDarken.scheduleStartHour == draftDarken.scheduleEndHour &&
                                draftDarken.scheduleStartMinute == draftDarken.scheduleEndMinute
                        if (sameStartAndEnd) {
                            Text(
                                text = stringResource(R.string.darken_same_time_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            val isOvernight =
                                (draftDarken.scheduleStartHour * 60 + draftDarken.scheduleStartMinute) >
                                    (draftDarken.scheduleEndHour * 60 + draftDarken.scheduleEndMinute)
                            Text(
                                text = if (isOvernight) {
                                    stringResource(R.string.darken_overnight_hint)
                                } else {
                                    stringResource(R.string.darken_sameday_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!draftDarken.triggerMode.usesBatterySaver) {
                    EditorSwitchRow(
                        title = stringResource(R.string.darken_also_on_battery_title),
                        description = stringResource(R.string.darken_also_on_battery_desc),
                        checked = draftDarken.darkenOnBatterySaver,
                        onCheckedChange = {
                            draftDarken = draftDarken.copy(darkenOnBatterySaver = it)
                        },
                        switchTestTag = "toggle_darken_on_battery_saver"
                    )
                }
            }
        }
    }

    editingTimeField?.let { field ->
        val initialHour = if (field == DarkenScheduleTimeField.START) {
            draftDarken.scheduleStartHour
        } else {
            draftDarken.scheduleEndHour
        }
        val initialMinute = if (field == DarkenScheduleTimeField.START) {
            draftDarken.scheduleStartMinute
        } else {
            draftDarken.scheduleEndMinute
        }
        DarkenScheduleTimePickerDialog(
            title = if (field == DarkenScheduleTimeField.START) {
                stringResource(R.string.darken_pick_start_title)
            } else {
                stringResource(R.string.darken_pick_end_title)
            },
            initialHour = initialHour,
            initialMinute = initialMinute,
            onDismiss = { editingTimeField = null },
            onConfirm = { hour, minute ->
                draftDarken = if (field == DarkenScheduleTimeField.START) {
                    draftDarken.copy(scheduleStartHour = hour, scheduleStartMinute = minute)
                } else {
                    draftDarken.copy(scheduleEndHour = hour, scheduleEndMinute = minute)
                }
                editingTimeField = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkenScheduleTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = true
    )
    var useKeyboardInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { useKeyboardInput = !useKeyboardInput },
                    modifier = Modifier.testTag("darken_time_picker_mode_toggle")
                ) {
                    Icon(
                        imageVector = if (useKeyboardInput) {
                            Icons.Default.Schedule
                        } else {
                            Icons.Default.Keyboard
                        },
                        contentDescription = if (useKeyboardInput) {
                            stringResource(R.string.darken_switch_clock_cd)
                        } else {
                            stringResource(R.string.darken_switch_keyboard_cd)
                        }
                    )
                }
            }
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (useKeyboardInput) {
                    TimeInput(state = timePickerState)
                } else {
                    TimePicker(state = timePickerState)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) },
                modifier = Modifier.testTag("darken_time_picker_confirm")
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("darken_time_picker_cancel")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun formatDarkenScheduleTime(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
