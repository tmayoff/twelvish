/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tylermayoff.android.wearable.twelvish

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.SurfaceHolder
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSetting
import com.tylermayoff.android.wearable.twelvish.data.watchface.ColorStyleIdAndResourceIds
import com.tylermayoff.android.wearable.twelvish.data.watchface.WatchFaceColorPalette.Companion.convertToWatchFaceColorPalette
import com.tylermayoff.android.wearable.twelvish.data.watchface.WatchFaceData
import com.tylermayoff.android.wearable.twelvish.utils.COLOR_STYLE_SETTING
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// Default for how long each frame is displayed at expected frame rate.
private const val FRAME_PERIOD_MS_DEFAULT: Long = 16L

/**
 * Renders watch face via data in Room database. Also, updates watch face state based on setting
 * changes by user via [userStyleRepository.addUserStyleListener()].
 */
class TwelvishWatchCanvasRenderer(
    context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer2<TwelvishWatchCanvasRenderer.AnalogSharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    FRAME_PERIOD_MS_DEFAULT,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
    class AnalogSharedAssets : SharedAssets {
        override fun onDestroy() {
        }
    }

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Represents all data needed to render the watch face. All value defaults are constants. Only
    // three values are changeable by the user (color scheme, ticks being rendered, and length of
    // the minute arm). Those dynamic values are saved in the watch face APIs and we update those
    // here (in the renderer) through a Kotlin Flow.
    private var watchFaceData: WatchFaceData = WatchFaceData()

    var watchFaceColors = convertToWatchFaceColorPalette(
        context,
        watchFaceData.activeColourStyle,
        watchFaceData.ambientColourStyle
    )

    private val textPaint = TextPaint()

    // Default size of watch face drawing area, that is, a no size rectangle. Will be replaced with
    // valid dimensions from the system.
//    private var currentWatchFaceSize = Rect(0, 0, 0, 0)

    init {
        scope.launch {
            currentUserStyleRepository.userStyle.collect { userStyle ->
                updateWatchFaceData(userStyle)
            }
        }
    }

    override suspend fun createSharedAssets(): AnalogSharedAssets {
        return AnalogSharedAssets()
    }

    /*
     * Triggered when the user makes changes to the watch face through the settings activity. The
     * function is called by a flow.
     */
    private fun updateWatchFaceData(userStyle: UserStyle) {
        Log.d(TAG, "updateWatchFace(): $userStyle")

        var newWatchFaceData: WatchFaceData = watchFaceData

        // Loops through user style and applies new values to watchFaceData.
        for (options in userStyle) {
            when (options.key.id.toString()) {
                COLOR_STYLE_SETTING -> {
                    val listOptions =
                        options.value as UserStyleSetting.ListUserStyleSetting.ListOption
                    newWatchFaceData = newWatchFaceData.copy(
                        activeColourStyle = ColorStyleIdAndResourceIds.getColorStyleConfig(
                            listOptions.id.toString()
                        )
                    )
                }
            }
        }

        // Only updates if something changed.
        if (watchFaceData != newWatchFaceData) {
            watchFaceData = newWatchFaceData

            // Applies the user chosen complication color scheme changes. ComplicationDrawables for
            // each of the styles are defined in XML so we need to replace the complication's
            // drawables.
//            for ((_, complication) in complicationSlotsManager.complicationSlots) {
////                ComplicationDrawable.getDrawable(
////                    context,
////                    watchFaceColors.complicationStyleDrawableId
////                )?.let {
////                    (complication.renderer as CanvasComplicationDrawable).drawable = it
////                }
//        }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        scope.cancel("TwelvishWatchCanvasRenderer scope clear() request")
        super.onDestroy()
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: AnalogSharedAssets
    ) {
        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)

        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    private fun GetWordFromNumber(num: Int): String {
        when (num) {
            1 -> return "ONE"
            2 -> return "TWO"
            3 -> return "THREE"
            4 -> return "FOUR"
            5 -> return "FIVE"
            6 -> return "SIX"
            7 -> return "SEVEN"
            8 -> return "EIGHT"
            9 -> return "NINE"
            10 -> return "TEN"
            11 -> return "ELEVEN"
            12 -> return "TWELVE"
        }

        return ""
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: AnalogSharedAssets
    ) {
        val backgroundColor = if (renderParameters.drawMode == DrawMode.AMBIENT) {
            watchFaceColors.ambientBackgroundColor
        } else {
            watchFaceColors.activeBackgroundColor
        }

        canvas.drawColor(backgroundColor)

        var hour: Int = zonedDateTime.toLocalTime().hour
        if (hour > 12) hour -= 12
        val minute = zonedDateTime.toLocalTime().minute

        var prefix = ""
        var postfix = ""
        var hourNumber = GetWordFromNumber(hour)

        when (minute) {
            in 1..4 -> {
                prefix = ""
                postfix = "ish"
            }

            in 5..7 -> {
                prefix = ""
                postfix = " or so"
            }

            in 8..14 -> {
                prefix = "almost a quarter past "
                postfix = ""
            }

            15 -> {
                prefix = "quarter past "
                postfix = ""
            }

            in 16..23 -> {
                prefix = "quarter past "
                postfix = " or so"
            }

            in 24..29 -> {
                prefix = "almost half past "
                postfix = ""
            }

            30 -> {
                prefix = ""
                postfix = " thirty"
            }

            in 31..34 -> {
                prefix = ""
                postfix = " thirtyish"
            }

            in 35..44 -> {
                prefix = "almost a quarter to "
                postfix = ""
                hourNumber = GetWordFromNumber(hour + 1)
            }

            45 -> {
                prefix = ""
                postfix = "forty five "
            }

            in 46..59 -> {
                prefix = "almost "
                postfix = ""
                hourNumber = GetWordFromNumber(hour + 1)
            }
        }

        val clockWordText =
            prefix + hourNumber.lowercase() + postfix

        textPaint.color = Color.WHITE
        textPaint.textSize = 45.0f

        val builder: StaticLayout.Builder =
            StaticLayout.Builder.obtain(
                clockWordText,
                0,
                clockWordText.length,
                textPaint,
                canvas.width - watchFaceData.clockTextPadding
            ).setAlignment(Layout.Alignment.ALIGN_CENTER)

        val layout = builder.build()
        canvas.translate(
            (canvas.width / 2.0f) - (layout.width / 2.0f),
            (canvas.height / 2.0f) - (layout.height / 2.0f)
        )
        layout.draw(canvas)

        // CanvasComplicationDrawable already obeys rendererParameters.
//        drawComplications(canvas, zonedDateTime)
    }

    // ----- All drawing functions -----
//    private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
//        for ((_, complication) in complicationSlotsManager.complicationSlots) {
//            if (complication.enabled) {
//                complication.render(canvas, zonedDateTime, renderParameters)
//            }
//        }
//    }

    companion object {
        private const val TAG = "TwelvishWatchCanvasRenderer"
    }
}
