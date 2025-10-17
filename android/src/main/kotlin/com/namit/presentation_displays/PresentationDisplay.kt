package com.namit.presentation_displays

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.ViewGroup
import android.widget.FrameLayout
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngineCache

class PresentationDisplay(context: Context, val tag: String, display: Display) :
    Presentation(context, display) {

    private var flutterView: FlutterView? = null
    private var flutterEngine: io.flutter.embedding.engine.FlutterEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val flContainer = FrameLayout(context)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        flContainer.layoutParams = params

        setContentView(flContainer)

        flutterView = FlutterView(context)
        flContainer.addView(flutterView, params)
        flutterEngine = FlutterEngineCache.getInstance().get(tag)
        if (flutterEngine != null) {
            flutterView?.attachToFlutterEngine(flutterEngine!!)
            // Notify plugin that the presentation's FlutterView is attached and ready
            PresentationDisplaysPlugin.instance?.notifyPresentationReady(tag)
        } else {
            Log.e("PresentationDisplay", "Can't find the FlutterEngine with cache name $tag")
        }
    }

    override fun dismiss() {
        try {
            // Detach FlutterView from engine before dismissing
            flutterView?.let { view ->
                flutterEngine?.let { engine ->
                    view.detachFromFlutterEngine()
                }
            }
            
            // Clear references
            flutterView = null
            flutterEngine = null
            
            // Call parent dismiss
            super.dismiss()
            
            Log.d("PresentationDisplay", "Presentation dismissed successfully for tag: $tag")
        } catch (e: Exception) {
            Log.e("PresentationDisplay", "Error during dismiss: ${e.message}", e)
            // Still call parent dismiss even if cleanup fails
            super.dismiss()
        }
    }
}
