package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BatterySaverConfig
import com.example.data.model.BatterySaverTrigger
import com.example.data.model.OffsetEasingStyle
import com.example.data.model.WallpaperAppState
import com.example.power.BatterySaverPolicy
import com.example.power.PowerSnapshot
import com.example.ui.WallpaperViewModel
import com.example.ui.batterySaverTriggerDescRes
import com.example.ui.batterySaverTriggerLabelRes
import com.example.ui.components.DashboardBadge
import com.example.ui.components.DashboardBadgeTone
import com.example.ui.components.DashboardMetric
import com.example.ui.components.EditorRadioOptionRow
import com.example.ui.components.EditorSliderControl
import com.example.ui.components.EditorSwitchRow
import com.example.ui.components.SettingDashboardCard
import com.example.ui.components.SettingEditorDialog
import com.example.ui.offsetEasingStyleLabelRes
import kotlin.math.roundToInt

@Composable
fun EffectsScreen(
    viewModel: WallpaperViewModel
) {
    val appState by viewModel.appState.collectAsState()
    val powerSnapshot by viewModel.powerSnapshot.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MotionBlurDashboardCard(
            enabled = appState.motionBlurEnabled,
            strengthPercent = appState.motionBlurStrengthPercent,
            onCommit = { enabled, strength ->
                viewModel.toggleMotionBlur(enabled)
                viewModel.setMotionBlurStrengthPercent(strength)
            }
        )

        OffsetEasingDashboardCard(
            enabled = appState.offsetEasingEnabled,
            style = appState.offsetEasingStyle,
            linearZonePercent = appState.offsetEasingLinearZonePercent,
            strength = appState.offsetEasingStrength,
            onCommit = { enabled, style, linearZone, strength ->
                viewModel.toggleOffsetEasing(enabled)
                viewModel.setOffsetEasingStyle(style)
                viewModel.setOffsetEasingLinearZonePercent(linearZone)
                viewModel.setOffsetEasingStrength(strength)
            }
        )

        AnimationFrameRateDashboardCard(
            selectedFrameRate = appState.animationFrameRate,
            onCommit = viewModel::setAnimationFrameRate
        )

        DisplayEntryAnimationDashboardCard(
            appState = appState,
            onCommit = { enabled, durationMs, zoomEnabled, zoomScale, fadeEnabled, fadeAlpha, blurEnabled, blurStrength ->
                viewModel.toggleResumeFade(enabled)
                viewModel.setEntryDurationMs(durationMs)
                viewModel.toggleEntryZoom(zoomEnabled)
                viewModel.setEntryZoomStartScale(zoomScale)
                viewModel.toggleEntryBlackFade(fadeEnabled)
                viewModel.setEntryBlackFadeMaxAlpha(fadeAlpha)
                viewModel.toggleEntryBlur(blurEnabled)
                viewModel.setEntryBlurStrength(blurStrength)
            }
        )

        BatterySaverDashboardCard(
            batterySaver = appState.batterySaver,
            normalFrameRate = appState.animationFrameRate,
            powerSnapshot = powerSnapshot,
            onCommit = viewModel::updateBatterySaverConfig
        )
    }
}

@Composable
private fun MotionBlurDashboardCard(
    enabled: Boolean,
    strengthPercent: Int,
    onCommit: (Boolean, Int) -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    val title = stringResource(R.string.behavior_motion_blur_title)

    SettingDashboardCard(
        title = title,
        icon = Icons.Default.BlurOn,
        cardTestTag = "motion_blur_setting",
        editContentDescription = stringResource(R.string.cd_edit_setting, title),
        onEditClick = { showEditor = true },
        badges = listOf(
            DashboardBadge(
                text = if (enabled) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                tone = if (enabled) DashboardBadgeTone.PRIMARY else DashboardBadgeTone.NEUTRAL
            )
        ),
        metrics = listOf(
            DashboardMetric(
                label = stringResource(R.string.behavior_strength_label),
                value = if (enabled) {
                    stringResource(R.string.percent_value, strengthPercent)
                } else {
                    stringResource(R.string.state_off)
                }
            )
        )
    )

    if (showEditor) {
        var draftEnabled by remember(enabled) { mutableStateOf(enabled) }
        var draftStrength by remember(strengthPercent) {
            mutableFloatStateOf(strengthPercent.toFloat())
        }

        SettingEditorDialog(
            title = title,
            icon = Icons.Default.BlurOn,
            onDismiss = { showEditor = false },
            onConfirm = {
                onCommit(draftEnabled, draftStrength.roundToInt())
                showEditor = false
            },
            dialogTestTag = "motion_blur_editor_dialog"
        ) {
            EditorSwitchRow(
                title = title,
                description = stringResource(R.string.behavior_motion_blur_desc),
                checked = draftEnabled,
                onCheckedChange = { draftEnabled = it },
                switchTestTag = "toggle_motion_blur"
            )
            EditorSliderControl(
                label = stringResource(R.string.behavior_strength_label),
                valueText = stringResource(R.string.percent_value, draftStrength.roundToInt()),
                value = draftStrength,
                onValueChange = { draftStrength = it },
                valueRange = 0f..WallpaperAppState.MAX_MOTION_BLUR_STRENGTH_PERCENT.toFloat(),
                sliderTestTag = "motion_blur_strength"
            )
        }
    }
}

@Composable
private fun OffsetEasingDashboardCard(
    enabled: Boolean,
    style: OffsetEasingStyle,
    linearZonePercent: Int,
    strength: Int,
    onCommit: (Boolean, OffsetEasingStyle, Int, Int) -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    val title = stringResource(R.string.behavior_offset_easing_title)

    SettingDashboardCard(
        title = title,
        icon = Icons.Default.Tune,
        cardTestTag = "offset_easing_setting",
        editContentDescription = stringResource(R.string.cd_edit_setting, title),
        onEditClick = { showEditor = true },
        badges = listOf(
            DashboardBadge(
                text = if (enabled) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                tone = if (enabled) DashboardBadgeTone.PRIMARY else DashboardBadgeTone.NEUTRAL
            )
        ),
        metrics = listOf(
            DashboardMetric(
                label = stringResource(R.string.behavior_easing_style_label),
                value = stringResource(offsetEasingStyleLabelRes(style))
            ),
            DashboardMetric(
                label = if (style == OffsetEasingStyle.SYMMETRIC) {
                    stringResource(R.string.behavior_linear_zone_label)
                } else {
                    stringResource(R.string.behavior_linear_portion_label)
                },
                value = stringResource(R.string.percent_value, linearZonePercent)
            ),
            DashboardMetric(
                label = stringResource(R.string.behavior_strength_label),
                value = stringResource(R.string.percent_value, strength)
            )
        )
    )

    if (showEditor) {
        var draftEnabled by remember(enabled) { mutableStateOf(enabled) }
        var draftStyle by remember(style) { mutableStateOf(style) }
        var draftLinearZone by remember(linearZonePercent) {
            mutableFloatStateOf(linearZonePercent.toFloat())
        }
        var draftStrength by remember(strength) { mutableFloatStateOf(strength.toFloat()) }

        SettingEditorDialog(
            title = title,
            icon = Icons.Default.Tune,
            onDismiss = { showEditor = false },
            onConfirm = {
                onCommit(
                    draftEnabled,
                    draftStyle,
                    draftLinearZone.roundToInt(),
                    draftStrength.roundToInt()
                )
                showEditor = false
            },
            dialogTestTag = "offset_easing_editor_dialog"
        ) {
            EditorSwitchRow(
                title = title,
                description = stringResource(R.string.behavior_offset_easing_desc),
                checked = draftEnabled,
                onCheckedChange = { draftEnabled = it },
                switchTestTag = "toggle_offset_easing"
            )
            Text(
                text = stringResource(R.string.behavior_easing_style_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OffsetEasingStyle.values().forEach { easingStyle ->
                    FilterChip(
                        selected = draftStyle == easingStyle,
                        onClick = { draftStyle = easingStyle },
                        label = { Text(stringResource(offsetEasingStyleLabelRes(easingStyle))) },
                        modifier = Modifier.testTag("offset_easing_style_${easingStyle.name}")
                    )
                }
            }
            EditorSliderControl(
                label = if (draftStyle == OffsetEasingStyle.SYMMETRIC) {
                    stringResource(R.string.behavior_linear_zone_label)
                } else {
                    stringResource(R.string.behavior_linear_portion_label)
                },
                valueText = stringResource(R.string.percent_value, draftLinearZone.roundToInt()),
                value = draftLinearZone,
                onValueChange = { draftLinearZone = it },
                valueRange = 0f..100f,
                sliderTestTag = "offset_easing_linear_zone"
            )
            EditorSliderControl(
                label = stringResource(R.string.behavior_strength_label),
                valueText = stringResource(R.string.percent_value, draftStrength.roundToInt()),
                value = draftStrength,
                onValueChange = { draftStrength = it },
                valueRange = 0f..100f,
                sliderTestTag = "offset_easing_strength"
            )
        }
    }
}

@Composable
private fun AnimationFrameRateDashboardCard(
    selectedFrameRate: Int,
    onCommit: (Int) -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    val title = stringResource(R.string.behavior_frame_rate_title)

    SettingDashboardCard(
        title = title,
        icon = Icons.Default.Speed,
        cardTestTag = "animation_frame_rate_card",
        editContentDescription = stringResource(R.string.cd_edit_setting, title),
        onEditClick = { showEditor = true },
        badges = listOf(
            DashboardBadge(
                text = stringResource(R.string.fps_value, selectedFrameRate),
                tone = DashboardBadgeTone.PRIMARY
            )
        ),
        metrics = listOf(
            DashboardMetric(
                label = stringResource(R.string.behavior_target_rate_label),
                value = stringResource(R.string.fps_value, selectedFrameRate)
            )
        )
    )

    if (showEditor) {
        var draftFrameRate by remember(selectedFrameRate) {
            mutableIntStateOf(selectedFrameRate)
        }

        SettingEditorDialog(
            title = title,
            icon = Icons.Default.Speed,
            onDismiss = { showEditor = false },
            onConfirm = {
                onCommit(draftFrameRate)
                showEditor = false
            },
            dialogTestTag = "frame_rate_editor_dialog"
        ) {
            Text(
                text = stringResource(R.string.behavior_frame_rate_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WallpaperAppState.supportedAnimationFrameRates.forEach { frameRate ->
                    FilterChip(
                        selected = draftFrameRate == frameRate,
                        onClick = { draftFrameRate = frameRate },
                        label = { Text(stringResource(R.string.fps_value, frameRate)) },
                        modifier = Modifier.testTag("animation_frame_rate_$frameRate")
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayEntryAnimationDashboardCard(
    appState: WallpaperAppState,
    onCommit: (
        enabled: Boolean,
        durationMs: Int,
        zoomEnabled: Boolean,
        zoomStartScale: Float,
        blackFadeEnabled: Boolean,
        blackFadeMaxAlpha: Float,
        blurEnabled: Boolean,
        blurStrength: Float
    ) -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    val title = stringResource(R.string.behavior_entry_title)

    SettingDashboardCard(
        title = title,
        icon = Icons.Default.AutoAwesome,
        cardTestTag = "resume_transition_card",
        editContentDescription = stringResource(R.string.cd_edit_setting, title),
        onEditClick = { showEditor = true },
        badges = listOf(
            DashboardBadge(
                text = if (appState.resumeFadeEnabled) {
                    stringResource(R.string.state_on)
                } else {
                    stringResource(R.string.state_off)
                },
                tone = if (appState.resumeFadeEnabled) {
                    DashboardBadgeTone.PRIMARY
                } else {
                    DashboardBadgeTone.NEUTRAL
                }
            )
        ),
        metrics = if (appState.resumeFadeEnabled) {
            listOf(
                DashboardMetric(
                    label = stringResource(R.string.behavior_entry_duration_label),
                    value = stringResource(R.string.ms_value, appState.entryDurationMs)
                ),
                DashboardMetric(
                    label = stringResource(R.string.behavior_entry_zoom_title),
                    value = if (appState.entryZoomEnabled) {
                        stringResource(
                            R.string.percent_value,
                            (appState.entryZoomStartScale * 100f).roundToInt()
                        )
                    } else {
                        stringResource(R.string.state_off)
                    }
                ),
                DashboardMetric(
                    label = stringResource(R.string.behavior_entry_fade_title),
                    value = if (appState.entryBlackFadeEnabled) {
                        stringResource(
                            R.string.percent_value,
                            (appState.entryBlackFadeMaxAlpha * 100f).roundToInt()
                        )
                    } else {
                        stringResource(R.string.state_off)
                    }
                ),
                DashboardMetric(
                    label = stringResource(R.string.behavior_entry_blur_title),
                    value = if (appState.entryBlurEnabled) {
                        stringResource(
                            R.string.percent_value,
                            (appState.entryBlurStrength * 100f).roundToInt()
                        )
                    } else {
                        stringResource(R.string.state_off)
                    }
                )
            )
        } else {
            listOf(
                DashboardMetric(
                    label = stringResource(R.string.behavior_entry_duration_label),
                    value = stringResource(R.string.state_off)
                )
            )
        }
    )

    if (showEditor) {
        var draftEnabled by remember(appState.resumeFadeEnabled) {
            mutableStateOf(appState.resumeFadeEnabled)
        }
        var draftDuration by remember(appState.entryDurationMs) {
            mutableFloatStateOf(appState.entryDurationMs.toFloat())
        }
        var draftZoomEnabled by remember(appState.entryZoomEnabled) {
            mutableStateOf(appState.entryZoomEnabled)
        }
        var draftZoomScale by remember(appState.entryZoomStartScale) {
            mutableFloatStateOf(appState.entryZoomStartScale)
        }
        var draftFadeEnabled by remember(appState.entryBlackFadeEnabled) {
            mutableStateOf(appState.entryBlackFadeEnabled)
        }
        var draftFadeAlpha by remember(appState.entryBlackFadeMaxAlpha) {
            mutableFloatStateOf(appState.entryBlackFadeMaxAlpha)
        }
        var draftBlurEnabled by remember(appState.entryBlurEnabled) {
            mutableStateOf(appState.entryBlurEnabled)
        }
        var draftBlurStrength by remember(appState.entryBlurStrength) {
            mutableFloatStateOf(appState.entryBlurStrength)
        }

        SettingEditorDialog(
            title = title,
            icon = Icons.Default.AutoAwesome,
            onDismiss = { showEditor = false },
            onConfirm = {
                onCommit(
                    draftEnabled,
                    draftDuration.roundToInt(),
                    draftZoomEnabled,
                    draftZoomScale,
                    draftFadeEnabled,
                    draftFadeAlpha,
                    draftBlurEnabled,
                    draftBlurStrength
                )
                showEditor = false
            },
            dialogTestTag = "entry_animation_editor_dialog"
        ) {
            EditorSwitchRow(
                title = title,
                description = stringResource(R.string.behavior_entry_desc),
                checked = draftEnabled,
                onCheckedChange = { draftEnabled = it },
                switchTestTag = "toggle_resume_fade"
            )

            AnimatedVisibility(visible = draftEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorSliderControl(
                        label = stringResource(R.string.behavior_entry_duration_label),
                        valueText = stringResource(R.string.ms_value, draftDuration.roundToInt()),
                        value = draftDuration,
                        onValueChange = { draftDuration = it },
                        valueRange = WallpaperAppState.MIN_ENTRY_DURATION_MS.toFloat()..
                            WallpaperAppState.MAX_ENTRY_DURATION_MS.toFloat(),
                        sliderTestTag = "entry_duration_slider"
                    )

                    EntryElementSection(
                        title = stringResource(R.string.behavior_entry_zoom_title),
                        description = stringResource(R.string.behavior_entry_zoom_desc),
                        checked = draftZoomEnabled,
                        onCheckedChange = { draftZoomEnabled = it },
                        switchTestTag = "toggle_entry_zoom"
                    ) {
                        EditorSliderControl(
                            label = stringResource(R.string.behavior_entry_zoom_title),
                            valueText = stringResource(
                                R.string.percent_value,
                                (draftZoomScale * 100).roundToInt()
                            ),
                            value = draftZoomScale,
                            onValueChange = { draftZoomScale = it },
                            valueRange = WallpaperAppState.MIN_ENTRY_ZOOM_START_SCALE..
                                WallpaperAppState.MAX_ENTRY_ZOOM_START_SCALE,
                            sliderTestTag = "entry_zoom_scale_slider"
                        )
                    }

                    EntryElementSection(
                        title = stringResource(R.string.behavior_entry_fade_title),
                        description = stringResource(R.string.behavior_entry_fade_desc),
                        checked = draftFadeEnabled,
                        onCheckedChange = { draftFadeEnabled = it },
                        switchTestTag = "toggle_entry_black_fade"
                    ) {
                        EditorSliderControl(
                            label = stringResource(R.string.behavior_entry_fade_title),
                            valueText = stringResource(
                                R.string.percent_value,
                                (draftFadeAlpha * 100).roundToInt()
                            ),
                            value = draftFadeAlpha,
                            onValueChange = { draftFadeAlpha = it },
                            valueRange = 0.1f..1f,
                            sliderTestTag = "entry_black_fade_slider"
                        )
                    }

                    EntryElementSection(
                        title = stringResource(R.string.behavior_entry_blur_title),
                        description = stringResource(R.string.behavior_entry_blur_desc),
                        checked = draftBlurEnabled,
                        onCheckedChange = { draftBlurEnabled = it },
                        switchTestTag = "toggle_entry_blur"
                    ) {
                        EditorSliderControl(
                            label = stringResource(R.string.behavior_entry_blur_title),
                            valueText = stringResource(
                                R.string.percent_value,
                                (draftBlurStrength * 100).roundToInt()
                            ),
                            value = draftBlurStrength,
                            onValueChange = { draftBlurStrength = it },
                            valueRange = 0.1f..1f,
                            sliderTestTag = "entry_blur_slider"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatterySaverDashboardCard(
    batterySaver: BatterySaverConfig,
    normalFrameRate: Int,
    powerSnapshot: PowerSnapshot,
    onCommit: (BatterySaverConfig) -> Unit
) {
    val isSaverActive = BatterySaverPolicy.isBatterySaverActive(batterySaver, powerSnapshot)
    val effectiveFps = BatterySaverPolicy.effectiveFrameRate(normalFrameRate, batterySaver, powerSnapshot)
    val batteryLabel = powerSnapshot.batteryPercent?.let {
        stringResource(R.string.percent_value, it)
    } ?: "--%"
    val batteryBadgeText = when {
        isSaverActive -> stringResource(R.string.battery_saver_status_saving, batteryLabel, effectiveFps)
        batterySaver.enabled && batterySaver.pauseWhileCharging && powerSnapshot.isCharging ->
            stringResource(R.string.battery_saver_status_charging, batteryLabel, normalFrameRate)
        batterySaver.enabled ->
            stringResource(R.string.battery_saver_status_normal, batteryLabel, normalFrameRate)
        else -> stringResource(R.string.battery_saver_status_off, batteryLabel)
    }
    val batteryBadgeTone = when {
        isSaverActive -> DashboardBadgeTone.TERTIARY
        batterySaver.enabled && batterySaver.pauseWhileCharging && powerSnapshot.isCharging ->
            DashboardBadgeTone.PRIMARY
        else -> DashboardBadgeTone.NEUTRAL
    }

    val batteryMetricValue = if (!batterySaver.enabled) {
        stringResource(R.string.state_off)
    } else {
        when (batterySaver.triggerMode) {
            BatterySaverTrigger.BATTERY_PERCENTAGE -> stringResource(
                R.string.battery_summary_below,
                batterySaver.thresholdPercent,
                batterySaver.reducedFrameRate
            )
            BatterySaverTrigger.SYSTEM_BATTERY_SAVER -> stringResource(
                R.string.battery_summary_system,
                batterySaver.reducedFrameRate
            )
            BatterySaverTrigger.EITHER -> stringResource(
                R.string.battery_summary_either,
                batterySaver.thresholdPercent,
                batterySaver.reducedFrameRate
            )
        }
    }

    val title = stringResource(R.string.battery_saver_title)
    var showEditor by remember { mutableStateOf(false) }

    SettingDashboardCard(
        title = title,
        icon = Icons.Default.BatterySaver,
        cardTestTag = "battery_saver_settings_card",
        editContentDescription = stringResource(R.string.cd_edit_setting, title),
        onEditClick = { showEditor = true },
        badges = listOf(
            DashboardBadge(
                text = batteryBadgeText,
                tone = batteryBadgeTone,
                showDot = true,
                testTag = "battery_saver_status_badge"
            )
        ),
        metrics = listOf(
            DashboardMetric(
                label = stringResource(R.string.darken_summary_battery_saver),
                value = batteryMetricValue
            ),
            DashboardMetric(
                label = stringResource(R.string.battery_pause_charging_title),
                value = if (batterySaver.pauseWhileCharging) {
                    stringResource(R.string.state_on)
                } else {
                    stringResource(R.string.state_off)
                }
            )
        )
    )

    if (showEditor) {
        var draftBattery by remember(batterySaver) { mutableStateOf(batterySaver) }
        var draftThresholdSlider by remember(batterySaver.thresholdPercent) {
            mutableFloatStateOf(batterySaver.thresholdPercent.toFloat())
        }

        SettingEditorDialog(
            title = title,
            icon = Icons.Default.BatterySaver,
            onDismiss = { showEditor = false },
            onConfirm = {
                onCommit(draftBattery.copy(thresholdPercent = draftThresholdSlider.roundToInt()))
                showEditor = false
            },
            dialogTestTag = "battery_saver_editor_dialog"
        ) {
            EditorSwitchRow(
                title = stringResource(R.string.battery_saver_toggle_title),
                description = stringResource(R.string.battery_saver_toggle_desc),
                checked = draftBattery.enabled,
                onCheckedChange = { draftBattery = draftBattery.copy(enabled = it) },
                switchTestTag = "toggle_battery_saver"
            )

            AnimatedVisibility(visible = draftBattery.enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.battery_saver_trigger_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    BatterySaverTrigger.values().forEach { mode ->
                        EditorRadioOptionRow(
                            title = stringResource(batterySaverTriggerLabelRes(mode)),
                            description = stringResource(batterySaverTriggerDescRes(mode)),
                            selected = draftBattery.triggerMode == mode,
                            onClick = { draftBattery = draftBattery.copy(triggerMode = mode) },
                            rowTestTag = "battery_saver_trigger_${mode.name}"
                        )
                    }

                    AnimatedVisibility(
                        visible = draftBattery.triggerMode != BatterySaverTrigger.SYSTEM_BATTERY_SAVER
                    ) {
                        EditorSliderControl(
                            label = stringResource(R.string.battery_threshold_label),
                            valueText = stringResource(
                                R.string.percent_value,
                                draftThresholdSlider.roundToInt()
                            ),
                            value = draftThresholdSlider,
                            onValueChange = { draftThresholdSlider = it },
                            valueRange = BatterySaverConfig.MIN_THRESHOLD_PERCENT.toFloat()..
                                BatterySaverConfig.MAX_THRESHOLD_PERCENT.toFloat(),
                            sliderTestTag = "battery_saver_threshold_slider"
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.battery_reduced_fps_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BatterySaverConfig.supportedReducedFrameRates.forEach { fps ->
                                FilterChip(
                                    selected = draftBattery.reducedFrameRate == fps,
                                    onClick = {
                                        draftBattery = draftBattery.copy(reducedFrameRate = fps)
                                    },
                                    label = { Text(stringResource(R.string.fps_value, fps)) },
                                    modifier = Modifier.testTag("battery_saver_fps_$fps")
                                )
                            }
                        }
                    }

                    EditorSwitchRow(
                        title = stringResource(R.string.battery_pause_charging_title),
                        description = stringResource(
                            R.string.battery_pause_charging_desc,
                            normalFrameRate
                        ),
                        checked = draftBattery.pauseWhileCharging,
                        onCheckedChange = {
                            draftBattery = draftBattery.copy(pauseWhileCharging = it)
                        },
                        switchTestTag = "toggle_battery_saver_pause_charging"
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryElementSection(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String,
    controls: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag(switchTestTag)
                )
            }
            AnimatedVisibility(visible = checked) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    controls()
                }
            }
        }
    }
}
