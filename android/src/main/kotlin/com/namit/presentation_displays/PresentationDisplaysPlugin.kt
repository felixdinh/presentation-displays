package com.namit.presentation_displays

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.annotation.NonNull
import com.google.gson.Gson
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

private const val PLUGIN_TAG = "PresentationDisplaysPlugin"

/**
 * PresentationDisplaysPlugin
 *
 * Note: Legacy v1 registrar-based registration was removed. This plugin relies on
 * the Flutter v2 embedding APIs only.
 */
class PresentationDisplaysPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler {

  private lateinit var channel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private var flutterEngineChannel: MethodChannel? = null
  private var context: Context? = null
  private val activePresentations = mutableMapOf<String, PresentationDisplay>()

  override fun onAttachedToEngine(
      @NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding
  ) {
    // keep static instance reference for callbacks from PresentationDisplay
    instance = this

    channel = MethodChannel(flutterPluginBinding.binaryMessenger, viewTypeId)
    channel.setMethodCallHandler(this)

    eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, viewTypeEventsId)
    displayManager =
        flutterPluginBinding.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as
            DisplayManager
    val displayConnectedStreamHandler = DisplayConnectedStreamHandler(displayManager)
    eventChannel.setStreamHandler(displayConnectedStreamHandler)
  }

  companion object {
    private const val viewTypeId = "presentation_displays_plugin"
    private const val viewTypeEventsId = "presentation_displays_plugin_events"
    private var displayManager: DisplayManager? = null
    // plugin instance for callbacks
    internal var instance: PresentationDisplaysPlugin? = null

    // v1 embedding `registerWith` removed
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
    
    activePresentations.values.forEach { presentation ->
      dismissPresentationSafely(presentation)
    }
    activePresentations.clear()
    
    instance = null
  }

  // Called by PresentationDisplay when the secondary FlutterView is attached and ready.
  internal fun notifyPresentationReady(tag: String) {
    try {
      channel.invokeMethod("presentationReady", mapOf("routerName" to tag))
    } catch (t: Throwable) {
      Log.w(PLUGIN_TAG, "Failed to notify presentationReady: ${t.message}")
    }
  }

  // Safely dismiss a presentation with proper cleanup
  private fun dismissPresentationSafely(presentation: PresentationDisplay) {
    try {
      Log.d(PLUGIN_TAG, "Dismissing presentation for tag: ${presentation.tag}")
      presentation.dismiss()
    } catch (e: Exception) {
      Log.e(PLUGIN_TAG, "Error dismissing presentation for tag ${presentation.tag}: ${e.message}", e)
    }
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "showPresentation" -> {
        try {
          var displayId: Int? = null
          var tag: String? = null
          when (val args = call.arguments) {
            is Map<*, *> -> {
              displayId = (args["displayId"] as? Number)?.toInt()
              tag = args["routerName"] as? String
            }
            is String -> {
              val obj = JSONObject(args)
              displayId = obj.getInt("displayId")
              tag = obj.getString("routerName")
            }
          }

          if (displayId == null || tag == null) {
            result.error("INVALID_ARGUMENTS", "displayId or routerName missing", null)
            return
          }

          val display = displayManager?.getDisplay(displayId)
          if (display != null) {
            val safeTag = tag // Create a local non-nullable reference
            val flutterEngine = createFlutterEngine(safeTag)
            flutterEngine?.let {
              // keep a reference to the last-used engine channel for backward compatibility
              flutterEngineChannel =
                  MethodChannel(it.dartExecutor.binaryMessenger, "${viewTypeId}_engine")
              
              // THAY ĐỔI: Tạo và lưu presentation vào Map
              val presentation = context?.let { it1 -> PresentationDisplay(it1, safeTag, display) }
              presentation?.let { pres ->
                activePresentations[safeTag] = pres
                pres.show()
              }
              result.success(true)
            }
                ?: result.error("FLUTTER_ENGINE_ERROR", "Failed to create FlutterEngine for tag: $safeTag", null)
          } else {
            result.error("DISPLAY_NOT_FOUND", "Can't find display with displayId: $displayId", null)
          }
        } catch (e: Exception) {
          Log.e(PLUGIN_TAG, "Error in showPresentation: ${e.message}", e)
          result.error("SHOW_PRESENTATION_ERROR", e.message ?: "Unknown error occurred", null)
        }
      }
      "prewarmEngine" -> {
        try {
          var tag: String? = null
          when (val args = call.arguments) {
            is Map<*, *> -> tag = args["routerName"] as? String
            is String -> {
              val obj = JSONObject(args)
              tag = obj.optString("routerName")
            }
          }
          val actualTag = tag ?: "presentation"
          val engine = createFlutterEngine(actualTag)
          if (engine != null) result.success(true) else result.success(false)
        } catch (e: Exception) {
          Log.e(PLUGIN_TAG, "Error in prewarmEngine: ${e.message}", e)
          result.error("PREWARM_ERROR", e.message ?: "Unknown error", null)
        }
      }
      "hidePresentation" -> {
        try {
          var displayId: Int? = null
          var routerName: String? = null
          
          when (val args = call.arguments) {
            is Map<*, *> -> {
              displayId = (args["displayId"] as? Number)?.toInt()
              routerName = args["routerName"] as? String
            }
            is String -> {
              // Backward compatibility: treat as routerName
              routerName = args
            }
          }
          
          val success = when {
            // Priority 1: Both specified - exact match
            displayId != null && routerName != null -> {
              val presentation = activePresentations[routerName]
              if (presentation?.display?.displayId == displayId) {
                dismissPresentationSafely(presentation)
                activePresentations.remove(routerName)
                true
              } else {
                Log.w(PLUGIN_TAG, "Mismatch: routerName=$routerName, displayId=$displayId")
                false
              }
            }
            
            // Priority 2: routerName only
            routerName != null -> {
              activePresentations[routerName]?.let {
                dismissPresentationSafely(it)
                activePresentations.remove(routerName)
                true
              } ?: false
            }
            
            // Priority 3: displayId only
            displayId != null -> {
              val presentation = activePresentations.values.find { 
                it.display.displayId == displayId 
              }
              presentation?.let {
                dismissPresentationSafely(it)
                activePresentations.remove(it.tag)
                true
              } ?: false
            }
            
            // Priority 4: Hide all
            else -> {
              val count = activePresentations.size
              activePresentations.values.forEach { presentation ->
                dismissPresentationSafely(presentation)
              }
              activePresentations.clear()
              count > 0
            }
          }
          
          result.success(success)
        } catch (e: Exception) {
          Log.e(PLUGIN_TAG, "Error in hidePresentation: ${e.message}", e)
          result.error("HIDE_PRESENTATION_ERROR", e.message ?: "Unknown error occurred", null)
        }
      }
      "listDisplay" -> {
        try {
          val listJson = ArrayList<DisplayJson>()
          val category = call.arguments as String?

          val displays = displayManager?.getDisplays(category)
          if (displays != null) {
            for (display: Display in displays) {
              val d = DisplayJson.fromDisplay(display)
              listJson.add(d)
            }
          }
          result.success(Gson().toJson(listJson))
        } catch (e: Exception) {
          Log.e(PLUGIN_TAG, "Error in listDisplay: ${e.message}", e)
          result.error("LIST_DISPLAY_ERROR", e.message ?: "Unknown error occurred", null)
        }
      }
      "transferDataToPresentation" -> {
        try {
          // Support envelope { "routerName": "tag", "payload": ... }
          when (val args = call.arguments) {
            is Map<*, *> -> {
              val router = args["routerName"] as? String
              val payload = args["payload"] ?: args
              if (router != null) {
                val engine = FlutterEngineCache.getInstance().get(router)
                if (engine != null) {
                  val channelForEngine = MethodChannel(engine.dartExecutor.binaryMessenger, "${viewTypeId}_engine")
                  channelForEngine.invokeMethod("DataTransfer", payload)
                  result.success(true)
                } else {
                  // router specified but engine not found
                  result.success(false)
                }
              } else {
                // no router specified: fallback to last engine channel
                if (flutterEngineChannel != null) {
                  flutterEngineChannel?.invokeMethod("DataTransfer", payload)
                  result.success(true)
                } else {
                  result.success(false)
                }
              }
            }
            else -> {
              if (flutterEngineChannel != null) {
                flutterEngineChannel?.invokeMethod("DataTransfer", call.arguments)
                result.success(true)
              } else {
                result.success(false)
              }
            }
          }
        } catch (e: Exception) {
          Log.e(PLUGIN_TAG, "Error in transferDataToPresentation: ${e.message}", e)
          result.success(false)
        }
      }
      "hideAllPresentations" -> {
        try {
          val count = activePresentations.size
          activePresentations.values.forEach { presentation ->
            dismissPresentationSafely(presentation)
          }
          activePresentations.clear()
          result.success(count)
        } catch (e: Exception) {
          Log.e(PLUGIN_TAG, "Error in hideAllPresentations: ${e.message}", e)
          result.error("HIDE_ALL_PRESENTATIONS_ERROR", e.message ?: "Unknown error occurred", null)
        }
      }
      "getActivePresentations" -> {
        try {
          val activeTags = activePresentations.keys.toList()
          result.success(activeTags)
        } catch (e: Exception) {
          Log.e(PLUGIN_TAG, "Error in getActivePresentations: ${e.message}", e)
          result.error("GET_ACTIVE_PRESENTATIONS_ERROR", e.message ?: "Unknown error occurred", null)
        }
      }
    }
  }

  private fun createFlutterEngine(tag: String): FlutterEngine? {
    if (context == null) return null
    if (FlutterEngineCache.getInstance().get(tag) == null) {
      val flutterEngine = FlutterEngine(context!!)
      flutterEngine.navigationChannel.setInitialRoute(tag)
      FlutterInjector.instance().flutterLoader().startInitialization(context!!)
      val path = FlutterInjector.instance().flutterLoader().findAppBundlePath()
      val entrypoint = DartExecutor.DartEntrypoint(path, "secondaryDisplayMain")
      flutterEngine.dartExecutor.executeDartEntrypoint(entrypoint)
      flutterEngine.lifecycleChannel.appIsResumed()
      // Cache the FlutterEngine to be used by FlutterActivity.
      FlutterEngineCache.getInstance().put(tag, flutterEngine)
    }
    return FlutterEngineCache.getInstance().get(tag)
  }

  override fun onDetachedFromActivity() {}

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    this.context = binding.activity
    displayManager = context?.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
  }

  override fun onDetachedFromActivityForConfigChanges() {}
}

class DisplayConnectedStreamHandler(private var displayManager: DisplayManager?) :
    EventChannel.StreamHandler {
  private var sink: EventChannel.EventSink? = null
  private var handler: Handler? = null

  private val displayListener =
      object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
          sink?.success(1)
        }

        override fun onDisplayRemoved(displayId: Int) {
          sink?.success(0)
        }

        override fun onDisplayChanged(p0: Int) {}
      }

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    sink = events
    handler = Handler(Looper.getMainLooper())
    displayManager?.registerDisplayListener(displayListener, handler)
  }

  override fun onCancel(arguments: Any?) {
    sink = null
    handler = null
    displayManager?.unregisterDisplayListener(displayListener)
  }
}
