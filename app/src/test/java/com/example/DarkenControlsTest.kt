package com.example

import android.app.Application
import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.darken.ExtraDimDetector
import com.example.data.model.BatterySaverConfig
import com.example.data.model.BatterySaverTrigger
import com.example.data.model.DarkenConfig
import com.example.data.model.DarkenTriggerMode
import com.example.data.model.OriginalMedia
import com.example.data.model.PageItem
import com.example.data.repository.WallpaperRepository
import com.example.render.DarkenTransitionController
import com.example.service.WallpaperStatusBarColorPolicy
import com.example.ui.WallpaperViewModel
import com.example.ui.screens.EffectsScreen
import com.example.ui.screens.MediaEditorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ImageProcessingUtils
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DarkenControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun legacyDarkenTriggerModesMigrateToEnabledAndTriggerMode() {
        val darkTheme = DarkenConfig.fromJson(JSONObject().put("triggerMode", "DARK_THEME"))
        assertTrue(darkTheme.enabled)
        assertEquals(DarkenTriggerMode.DARK_MODE_ONLY, darkTheme.triggerMode)
        assertTrue(darkTheme.useDarkMode)
        assertFalse(darkTheme.useSchedule)

        val scheduled = DarkenConfig.fromJson(JSONObject().put("triggerMode", "SCHEDULED"))
        assertTrue(scheduled.enabled)
        assertEquals(DarkenTriggerMode.SCHEDULE_ONLY, scheduled.triggerMode)
        assertFalse(scheduled.useDarkMode)
        assertTrue(scheduled.useSchedule)

        val always = DarkenConfig.fromJson(JSONObject().put("triggerMode", "ALWAYS"))
        assertTrue(always.enabled)
        assertEquals(DarkenTriggerMode.BOTH_OR, always.triggerMode)
        assertTrue(always.useDarkMode)
        assertTrue(always.useSchedule)

        val extraDim = DarkenConfig.fromJson(JSONObject().put("triggerMode", "EXTRA_DIM"))
        assertFalse(extraDim.enabled)
        assertFalse(extraDim.useDarkMode)
        assertFalse(extraDim.useSchedule)

        val off = DarkenConfig.fromJson(JSONObject().put("triggerMode", "OFF"))
        assertFalse(off.enabled)

        val legacyBoth = DarkenConfig.fromJson(
            JSONObject()
                .put("useDarkMode", true)
                .put("useSchedule", true)
        )
        assertTrue(legacyBoth.enabled)
        assertEquals(DarkenTriggerMode.BOTH_OR, legacyBoth.triggerMode)

        val legacyNeither = DarkenConfig.fromJson(
            JSONObject()
                .put("useDarkMode", false)
                .put("useSchedule", false)
        )
        assertFalse(legacyNeither.enabled)
    }

    @Test
    fun triggerModesEvaluateScheduleOnlyDarkModeOnlyAndBothOr() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val disabled = DarkenConfig(
            enabled = false,
            triggerMode = DarkenTriggerMode.BOTH_OR,
            scheduleStartHour = 20,
            scheduleStartMinute = 30,
            scheduleEndHour = 7,
            scheduleEndMinute = 15
        )
        assertFalse(
            ExtraDimDetector.isDimConditionActive(
                context,
                disabled,
                isDarkModeOverride = true,
                hourOfDay = 22,
                minute = 0
            )
        )

        val darkModeOnly = disabled.copy(enabled = true, triggerMode = DarkenTriggerMode.DARK_MODE_ONLY)
        assertTrue(ExtraDimDetector.isDimConditionActive(context, darkModeOnly, isDarkModeOverride = true, hourOfDay = 12, minute = 0))
        assertFalse(ExtraDimDetector.isDimConditionActive(context, darkModeOnly, isDarkModeOverride = false, hourOfDay = 22, minute = 0))

        val scheduleOnlyOvernight = disabled.copy(enabled = true, triggerMode = DarkenTriggerMode.SCHEDULE_ONLY)
        assertTrue(ExtraDimDetector.isDimConditionActive(context, scheduleOnlyOvernight, isDarkModeOverride = true, hourOfDay = 20, minute = 30))
        assertTrue(ExtraDimDetector.isDimConditionActive(context, scheduleOnlyOvernight, isDarkModeOverride = false, hourOfDay = 23, minute = 45))
        assertTrue(ExtraDimDetector.isDimConditionActive(context, scheduleOnlyOvernight, isDarkModeOverride = false, hourOfDay = 7, minute = 14))
        assertFalse(ExtraDimDetector.isDimConditionActive(context, scheduleOnlyOvernight, isDarkModeOverride = true, hourOfDay = 12, minute = 0))
        assertFalse(ExtraDimDetector.isDimConditionActive(context, scheduleOnlyOvernight, isDarkModeOverride = false, hourOfDay = 7, minute = 15))
        assertFalse(ExtraDimDetector.isDimConditionActive(context, scheduleOnlyOvernight, isDarkModeOverride = false, hourOfDay = 20, minute = 29))

        val sameDaySchedule = DarkenConfig(
            enabled = true,
            triggerMode = DarkenTriggerMode.SCHEDULE_ONLY,
            scheduleStartHour = 9,
            scheduleStartMinute = 15,
            scheduleEndHour = 17,
            scheduleEndMinute = 45
        )
        assertTrue(ExtraDimDetector.isScheduledActive(sameDaySchedule, 9, 15))
        assertTrue(ExtraDimDetector.isScheduledActive(sameDaySchedule, 13, 0))
        assertFalse(ExtraDimDetector.isScheduledActive(sameDaySchedule, 17, 45))
        assertFalse(ExtraDimDetector.isScheduledActive(sameDaySchedule, 8, 59))

        val identicalTimes = sameDaySchedule.copy(scheduleEndHour = 9, scheduleEndMinute = 15)
        assertFalse(ExtraDimDetector.isScheduledActive(identicalTimes, 9, 15))

        val bothOr = scheduleOnlyOvernight.copy(triggerMode = DarkenTriggerMode.BOTH_OR)
        // Active if Dark Mode is on even outside scheduled hours
        assertTrue(ExtraDimDetector.isDimConditionActive(context, bothOr, isDarkModeOverride = true, hourOfDay = 12, minute = 0))
        // Active if inside scheduled hours even when Dark Mode is off
        assertTrue(ExtraDimDetector.isDimConditionActive(context, bothOr, isDarkModeOverride = false, hourOfDay = 22, minute = 10))
        // Inactive only when both signals are off
        assertFalse(ExtraDimDetector.isDimConditionActive(context, bothOr, isDarkModeOverride = false, hourOfDay = 12, minute = 0))
    }

    @Test
    fun darkenTransitionControllerAnimatesAround550MsAndSupportsReversal() {
        assertEquals(550, DarkenTransitionController.DEFAULT_DURATION_MS)
        val controller = DarkenTransitionController()
        assertEquals(0f, controller.currentMix, 0.0001f)
        assertFalse(controller.isActive)

        controller.snapTo(true)
        assertEquals(1f, controller.currentMix, 0.0001f)
        assertFalse(controller.isActive)

        controller.transitionTo(darkened = false)
        assertTrue(controller.isActive)
        assertEquals(0f, controller.targetMix, 0.0001f)

        controller.step(275_000_000L) // halfway through 550ms
        val midDownMix = controller.currentMix
        assertTrue("Expected smooth halfway mix near 0.5, got $midDownMix", midDownMix in 0.45f..0.55f)
        assertTrue(controller.isActive)

        // Reverse direction mid-flight without snapping
        controller.transitionTo(darkened = true)
        assertEquals(midDownMix, controller.currentMix, 0.0001f)
        assertEquals(1f, controller.targetMix, 0.0001f)
        assertTrue(controller.isActive)

        controller.step(100_000_000L)
        assertTrue(controller.currentMix > midDownMix)
        assertTrue(controller.currentMix < 1f)

        controller.step(250_000_000L)
        assertEquals(1f, controller.currentMix, 0.0001f)
        assertFalse(controller.isActive)
    }

    @Test
    fun perceptualNormalizationEqualizesVaryingAndUnevenWallpapersAndSkipsDark() {
        val brightBmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.rgb(235, 235, 235))
        }
        val midBmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.rgb(135, 140, 145))
        }
        val unevenBmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            c.drawColor(Color.rgb(45, 50, 55))
            c.drawRect(0f, 0f, 80f, 40f, Paint().apply { color = Color.rgb(245, 240, 235) })
        }
        val darkBmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.rgb(20, 22, 24))
        }

        val target = 0.24f
        val normBright = ImageProcessingUtils.createNormalizedDarkenedCopy(brightBmp, target, true)
        val normMid = ImageProcessingUtils.createNormalizedDarkenedCopy(midBmp, target, true)
        val normUneven = ImageProcessingUtils.createNormalizedDarkenedCopy(unevenBmp, target, true)
        val normDark = ImageProcessingUtils.createNormalizedDarkenedCopy(darkBmp, target, true)

        val brightAnalysis = ImageProcessingUtils.analyzePerceptualBrightness(normBright)
        val midAnalysis = ImageProcessingUtils.analyzePerceptualBrightness(normMid)
        val unevenAnalysis = ImageProcessingUtils.analyzePerceptualBrightness(normUneven)

        assertTrue(
            "Expected normalized bright (${brightAnalysis.effectiveBrightness}) and mid (${midAnalysis.effectiveBrightness}) to converge",
            abs(brightAnalysis.effectiveBrightness - midAnalysis.effectiveBrightness) < 0.08f
        )
        assertTrue(
            "Expected normalized bright (${brightAnalysis.effectiveBrightness}) near target $target",
            brightAnalysis.effectiveBrightness in 0.16f..0.34f
        )
        assertTrue(
            "Expected uneven highlight compressed below 0.55, got ${unevenAnalysis.p95Brightness}",
            unevenAnalysis.p95Brightness < 0.55f
        )
        assertEquals(Color.rgb(20, 22, 24), normDark.getPixel(40, 40))

        brightBmp.recycle()
        midBmp.recycle()
        unevenBmp.recycle()
        darkBmp.recycle()
        normBright.recycle()
        normMid.recycle()
        normUneven.recycle()
        normDark.recycle()
    }

    @Test
    fun wallpaperColorsPolicyNeverMarksAlreadyDarkOrDarkenedPagesAsLightText() {
        val whiteBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.WHITE)
        }

        // Regression test: an Already dark page must NEVER receive HINT_SUPPORTS_DARK_TEXT (black icons)
        val alreadyDarkColors = WallpaperStatusBarColorPolicy.computeWallpaperColors(
            activePage = PageItem(pageIndex = 0, isAlreadyDark = true),
            darkenActive = true,
            shouldDarkenActivePage = false,
            targetBrightness = 0.22f,
            bitmapProvider = { whiteBmp }
        )
        assertEquals(0, alreadyDarkColors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue((alreadyDarkColors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_THEME) != 0)
        }

        // Even when darken is active on a non-dark page, dark-surface status bar hints stay white icons
        val darkenedColors = WallpaperStatusBarColorPolicy.computeWallpaperColors(
            activePage = PageItem(pageIndex = 1, isAlreadyDark = false),
            darkenActive = true,
            shouldDarkenActivePage = true,
            targetBrightness = 0.22f,
            bitmapProvider = { whiteBmp }
        )
        assertEquals(0, darkenedColors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT)

        // Bright page when not darkened and not flagged Already dark preserves light-wallpaper dark-text hint
        val brightColors = WallpaperStatusBarColorPolicy.computeWallpaperColors(
            activePage = PageItem(pageIndex = 2, isAlreadyDark = false),
            darkenActive = false,
            shouldDarkenActivePage = false,
            targetBrightness = 0.22f,
            bitmapProvider = { whiteBmp }
        )
        assertTrue((brightColors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0)

        whiteBmp.recycle()
    }

    @Test
    fun repositoryNormalizesCachesAndInvalidatesDarkenedBitmapsByTargetBrightness() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = WallpaperRepository.getInstance(context)

        val croppedDir = File(context.filesDir, "cropped").apply { mkdirs() }
        val brightSource = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.rgb(220, 220, 220))
        }
        FileOutputStream(File(croppedDir, "test_bright_page.jpg")).use { out ->
            brightSource.compress(Bitmap.CompressFormat.JPEG, 98, out)
        }
        brightSource.recycle()

        val mediumSource = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.rgb(135, 135, 135))
        }
        FileOutputStream(File(croppedDir, "test_medium_page.jpg")).use { out ->
            mediumSource.compress(Bitmap.CompressFormat.JPEG, 98, out)
        }
        mediumSource.recycle()

        repository.updateState { state ->
            state.copy(
                pages = listOf(
                    PageItem(
                        pageIndex = 0,
                        croppedFileName = "test_bright_page.jpg",
                        isAlreadyDark = false,
                        isBlank = false
                    ),
                    PageItem(
                        pageIndex = 1,
                        croppedFileName = "test_bright_page.jpg",
                        isAlreadyDark = true,
                        isBlank = false
                    ),
                    PageItem(
                        pageIndex = 2,
                        croppedFileName = "test_medium_page.jpg",
                        isAlreadyDark = false,
                        isBlank = false
                    )
                ),
                darken = DarkenConfig(
                    enabled = true,
                    triggerMode = DarkenTriggerMode.DARK_MODE_ONLY,
                    autoDetectDark = true,
                    targetBrightness = 0.26f
                )
            )
        }

        val normalBmp = repository.getCroppedBitmap(0, 100, 200, darkened = false)
        assertNotNull(normalBmp)
        val darkenedBright = repository.getCroppedBitmap(0, 100, 200, darkened = true)
        assertNotNull(darkenedBright)
        val darkenedMedium = repository.getCroppedBitmap(2, 100, 200, darkened = true)
        assertNotNull(darkenedMedium)

        val normalRed = Color.red(normalBmp!!.getPixel(50, 100))
        val darkBrightRed = Color.red(darkenedBright!!.getPixel(50, 100))
        val darkMediumRed = Color.red(darkenedMedium!!.getPixel(50, 100))
        assertTrue("Expected normal ($normalRed) brighter than darkened ($darkBrightRed)", darkBrightRed < normalRed - 80)
        assertTrue(
            "Expected bright ($darkBrightRed) and medium ($darkMediumRed) normalized close together",
            abs(darkBrightRed - darkMediumRed) <= 30
        )

        val diskCacheFile = repository.getDarkenedCacheFileForTesting(0, 100, 200)
        assertNotNull(diskCacheFile)
        assertTrue("Expected darkened cache file to be persisted at ${diskCacheFile?.path}", diskCacheFile!!.exists())

        // Page 1 flagged as already dark should NOT be darkened when autoDetectDark is enabled
        val alreadyDarkResult = repository.getCroppedBitmap(1, 100, 200, darkened = true)
        assertNotNull(alreadyDarkResult)
        assertTrue(Color.red(alreadyDarkResult!!.getPixel(50, 100)) > 180)

        // Updating global targetBrightness should invalidate darkened cache and regenerate a darker cached file while pruning old one
        val prevGeneration = repository.darkenedBitmapGeneration
        repository.updateState { state ->
            state.copy(darken = state.darken.copy(targetBrightness = 0.12f))
        }
        assertTrue(repository.darkenedBitmapGeneration > prevGeneration)

        val darkerBmp = repository.getCroppedBitmap(0, 100, 200, darkened = true)!!
        val darkerRed = Color.red(darkerBmp.getPixel(50, 100))
        assertTrue("Expected lower targetBrightness ($darkerRed) darker than 0.26f ($darkBrightRed)", darkerRed < darkBrightRed - 15)
        val newDiskCacheFile = repository.getDarkenedCacheFileForTesting(0, 100, 200)!!
        assertTrue(newDiskCacheFile.exists())
        assertFalse("Expected old cache file to be pruned", diskCacheFile.exists())
    }

    @Test
    fun batterySaverDarkenTriggerAndSupplementalToggleEvaluateAndRoundTripJson() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val batteryOnly = DarkenConfig(
            enabled = true,
            triggerMode = DarkenTriggerMode.BATTERY_SAVER_ONLY
        )
        assertTrue(batteryOnly.useBatterySaver)
        assertFalse(batteryOnly.useDarkMode)
        assertFalse(batteryOnly.useSchedule)
        assertFalse(
            ExtraDimDetector.isDimConditionActive(
                context,
                batteryOnly,
                isDarkModeOverride = true,
                isBatterySaverActive = false
            )
        )
        assertTrue(
            ExtraDimDetector.isDimConditionActive(
                context,
                batteryOnly,
                isDarkModeOverride = false,
                isBatterySaverActive = true
            )
        )

        val supplemental = DarkenConfig(
            enabled = true,
            triggerMode = DarkenTriggerMode.DARK_MODE_ONLY,
            darkenOnBatterySaver = true
        )
        assertTrue(supplemental.useBatterySaver)
        assertTrue(
            ExtraDimDetector.isDimConditionActive(
                context,
                supplemental,
                isDarkModeOverride = false,
                isBatterySaverActive = true
            )
        )

        val roundTrip = DarkenConfig.fromJson(supplemental.toJson())
        assertEquals(DarkenTriggerMode.DARK_MODE_ONLY, roundTrip.triggerMode)
        assertTrue(roundTrip.darkenOnBatterySaver)
        assertTrue(roundTrip.enabled)

        val batteryOnlyRoundTrip = DarkenConfig.fromJson(batteryOnly.toJson())
        assertEquals(DarkenTriggerMode.BATTERY_SAVER_ONLY, batteryOnlyRoundTrip.triggerMode)
        assertTrue(batteryOnlyRoundTrip.useBatterySaver)
    }

    @Test
    fun effectsScreenProvidesStandaloneBatterySaverCardWithoutDarkenCard() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = WallpaperViewModel(app)
        viewModel.setAppLanguage("en")
        viewModel.updateBatterySaverConfig(BatterySaverConfig(enabled = false))

        composeRule.setContent {
            MyApplicationTheme {
                EffectsScreen(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()

        // Darken settings moved out to the top-left Conditional Menu
        composeRule.onNodeWithTag("darken_settings_card").assertDoesNotExist()
        composeRule.onNodeWithTag("dim_factor_slider").assertDoesNotExist()

        // Standalone Battery Saver card opens popup editor, supports Cancel & Confirm
        composeRule.onNodeWithTag("battery_saver_settings_card_edit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battery_saver_editor_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_battery_saver").performScrollTo().performClick()
        composeRule.onNodeWithTag("editor_cancel_button").performClick()
        composeRule.waitForIdle()
        assertFalse(viewModel.appState.value.batterySaver.enabled)

        composeRule.onNodeWithTag("battery_saver_settings_card_edit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("toggle_battery_saver").performScrollTo().performClick()
        composeRule.onNodeWithTag("battery_saver_trigger_EITHER").performScrollTo().performClick()
        composeRule.onNodeWithTag("editor_confirm_button").performClick()
        composeRule.waitForIdle()

        assertTrue(viewModel.appState.value.batterySaver.enabled)
        assertEquals(BatterySaverTrigger.EITHER, viewModel.appState.value.batterySaver.triggerMode)
    }

    @Test
    fun topBarConditionalMenusManageDarkenMultiPagePreviewAndSettingsDiagnostics() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = WallpaperViewModel(app)
        viewModel.setAppLanguage("en")
        viewModel.updateDarkenConfig(
            DarkenConfig(
                enabled = false,
                triggerMode = DarkenTriggerMode.DARK_MODE_ONLY,
                targetBrightness = 0.24f,
                scheduleStartHour = 20,
                scheduleStartMinute = 0,
                scheduleEndHour = 7,
                scheduleEndMinute = 0
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                MainAppScaffold(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()

        // Studio tab: Darken Conditional Menu visible, Diagnostics Conditional Menu hidden
        composeRule.onNodeWithTag("conditional_menu_darken").assertIsDisplayed()
        composeRule.onNodeWithTag("conditional_menu_diagnostics").assertDoesNotExist()

        // Open Darken Conditional Menu -> verify multi-page preview + single brightness slider, Cancel preserves state
        composeRule.onNodeWithTag("conditional_menu_darken").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("darken_conditions_editor_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("darken_multi_page_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("darken_preview_compare_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("target_brightness_slider").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_darken_enabled").performScrollTo().performClick()
        composeRule.onNodeWithTag("editor_cancel_button").performClick()
        composeRule.waitForIdle()
        assertFalse(viewModel.appState.value.darken.enabled)

        // Re-open Darken Conditional Menu, edit target brightness, page 0 dark badge, trigger mode, schedule, and Confirm
        composeRule.onNodeWithTag("conditional_menu_darken").performClick()
        composeRule.waitForIdle()
        val initialPage0Dark = viewModel.appState.value.pages.first { it.pageIndex == 0 }.isAlreadyDark
        composeRule.onNodeWithTag("darken_preview_page_0").performClick()
        composeRule.onNodeWithTag("target_brightness_slider")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { action ->
                action(0.16f)
            }
        composeRule.onNodeWithTag("toggle_darken_enabled").performScrollTo().performClick()
        composeRule.onNodeWithTag("darken_mode_battery_saver_only").performScrollTo().performClick()
        composeRule.onNodeWithTag("darken_mode_schedule_only").performScrollTo().performClick()
        composeRule.onNodeWithTag("darken_schedule_start_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("darken_mode_dark_mode_only").performScrollTo().performClick()
        composeRule.onNodeWithTag("toggle_darken_on_battery_saver").performScrollTo().performClick()
        composeRule.onNodeWithTag("darken_mode_both_or").performScrollTo().performClick()

        composeRule.onNodeWithTag("darken_schedule_start_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("darken_time_picker_mode_toggle").performClick()
        composeRule.onNodeWithContentDescription("for hour").performTextReplacement("21")
        composeRule.onNodeWithContentDescription("for minutes").performTextReplacement("45")
        composeRule.onNodeWithTag("darken_time_picker_confirm").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor_confirm_button").performClick()
        composeRule.waitForIdle()

        val updated = viewModel.appState.value.darken
        assertTrue(updated.enabled)
        assertEquals(DarkenTriggerMode.BOTH_OR, updated.triggerMode)
        assertTrue(updated.darkenOnBatterySaver)
        assertEquals(21, updated.scheduleStartHour)
        assertEquals(45, updated.scheduleStartMinute)
        assertEquals(0.16f, updated.targetBrightness, 0.02f)
        assertEquals(!initialPage0Dark, viewModel.appState.value.pages.first { it.pageIndex == 0 }.isAlreadyDark)

        // Switch to Behavior & Media tabs -> neither contextual menu button is shown
        composeRule.onNodeWithTag("tab_behavior").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("conditional_menu_darken").assertDoesNotExist()
        composeRule.onNodeWithTag("conditional_menu_diagnostics").assertDoesNotExist()

        composeRule.onNodeWithTag("tab_media").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("conditional_menu_darken").assertDoesNotExist()
        composeRule.onNodeWithTag("conditional_menu_diagnostics").assertDoesNotExist()

        // Switch to Settings tab -> Darken menu hidden, Diagnostics Conditional Menu appears in top bar and opens Diagnostics popup
        composeRule.onNodeWithTag("tab_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("conditional_menu_darken").assertDoesNotExist()
        composeRule.onNodeWithTag("conditional_menu_diagnostics").assertIsDisplayed()
        composeRule.onNodeWithTag("conditional_menu_diagnostics").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostics_conditional_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_dialog_close_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostics_conditional_dialog").assertDoesNotExist()
    }

    @Test
    fun mergedPreviewTogglesBetweenSingleAndFullAndPersistsSplitDivider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalsDir = File(context.filesDir, "originals").apply { mkdirs() }
        val fileName = "test_page_settings_media.jpg"
        val bmp = Bitmap.createBitmap(120, 240, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.rgb(180, 190, 210))
        }
        FileOutputStream(File(originalsDir, fileName)).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        bmp.recycle()

        val media = OriginalMedia(
            id = "media_test_0",
            fileName = fileName,
            dateAdded = 1000L,
            width = 120,
            height = 240,
            averageLuminance = 0.75f,
            isDarkDetected = false
        )
        val initialPage = PageItem(
            pageIndex = 0,
            originalId = media.id,
            isAlreadyDark = false
        )
        var savedPage: PageItem? = null
        val reportedSplits = mutableListOf<Float>()

        composeRule.setContent {
            MyApplicationTheme {
                MediaEditorScreen(
                    media = media,
                    page = initialPage,
                    pageCount = 3,
                    isSaving = false,
                    initialSplitFraction = 0.40f,
                    onSplitFractionChange = { reportedSplits.add(it) },
                    onDismiss = {},
                    onSave = { savedPage = it }
                )
            }
        }

        composeRule.waitForIdle()

        // Per-page darken preview & dim intensity slider were removed
        composeRule.onNodeWithTag("toggle_preview_darkened").assertDoesNotExist()
        composeRule.onNodeWithTag("page_dim_factor_slider").assertDoesNotExist()

        // Merged preview starts in Full & Parallax Bounds mode; clicking toggles to Single Page mode
        composeRule.onNodeWithTag("crop_merged_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("crop_parallax_bounds_preview", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("crop_merged_preview").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crop_parallax_bounds_preview", useUnmergedTree = true).assertDoesNotExist()

        // Clicking mode pill switches back to Full & Parallax Bounds mode
        composeRule.onNodeWithTag("crop_preview_mode_pill").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crop_parallax_bounds_preview", useUnmergedTree = true).assertIsDisplayed()

        // Drag split divider and verify persistence callback receives updated fraction
        composeRule.onNodeWithTag("crop_split_divider").assertIsDisplayed()
        composeRule.onNodeWithTag("crop_split_divider").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 120f)
        }
        composeRule.waitForIdle()
        assertTrue("Expected split fraction callback after dragging divider", reportedSplits.isNotEmpty())
        assertTrue("Expected increased split fraction > 0.40f, got ${reportedSplits.last()}", reportedSplits.last() > 0.40f)

        // Drag split divider upward as well
        composeRule.onNodeWithTag("crop_split_divider").performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 180f)
        }
        composeRule.waitForIdle()
        assertTrue("Expected decreased split fraction after upward swipe", reportedSplits.last() < 0.42f)

        // Toggle Already dark and save
        composeRule.onNodeWithTag("toggle_is_already_dark").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crop_confirm_button").performClick()

        assertNotNull(savedPage)
        assertTrue(savedPage!!.isAlreadyDark)
    }
}
