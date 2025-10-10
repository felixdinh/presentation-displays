package com.namit.presentation_displays

import android.view.Display
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class DisplayJson(
    @SerializedName("displayId")
    val displayId: Int,
    @SerializedName("flags")
    val flags: Int,
    @SerializedName("rotation")
    val rotation: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("width")
    val width: Int,
    @SerializedName("height")
    val height: Int,
    @SerializedName("densityDpi")
    val densityDpi: Int,
    @SerializedName("refreshRate")
    val refreshRate: Float,
    @SerializedName("isPresentation")
    val isPresentation: Boolean,
    @SerializedName("isExternal")
    val isExternal: Boolean,
    @SerializedName("state")
    val state: Int
) {
    companion object {
        fun fromDisplay(display: Display): DisplayJson {
            val metrics = android.util.DisplayMetrics()
            display.getMetrics(metrics)
            
            return DisplayJson(
                displayId = display.displayId,
                flags = display.flags,
                rotation = display.rotation,
                name = display.name ?: "Unknown Display",
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
                refreshRate = display.refreshRate,
                isPresentation = (display.flags and Display.FLAG_PRESENTATION) != 0,
                // Consider external if it's not the default primary display (id 0).
                // Using FLAG_ROUND is incorrect for detecting external displays.
                isExternal = display.displayId != 0,
                state = if (display.state == Display.STATE_ON) 1 else 0
            )
        }
    }
}