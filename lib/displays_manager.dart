import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:presentation_displays/display.dart';
import 'package:presentation_displays/secondary_display.dart';

const _listDisplay = "listDisplay";
const _showPresentation = "showPresentation";
const _hidePresentation = "hidePresentation";
const _transferDataToPresentation = "transferDataToPresentation";
const _prewarmEngine = "prewarmEngine";
const _hideAllPresentations = "hideAllPresentations";
const _getActivePresentations = "getActivePresentations";

/// Display category: secondary display.
/// <p>
/// This category can be used to identify secondary displays that are suitable for
/// use as presentation displays such as HDMI or Wireless displays.  Applications
/// may automatically project their content to presentation displays to provide
/// richer second screen experiences.
/// </p>
/// <p>
/// Use the following methods to query the real display area:
/// [DisplayManager.getDisplays], [DisplayManager.getNameByDisplayId],
/// [DisplayManager.getNameByIndex], [DisplayManager.showSecondaryDisplay],
/// [DisplayManager.transferDataToPresentation], [DisplayManager.hideSecondaryDisplay]
/// </p>
///
/// [DisplayManager.getDisplays]
///
const String DISPLAY_CATEGORY_PRESENTATION =
    "android.hardware.display.category.PRESENTATION";

/// Provide you with the method for you to work with [SecondaryDisplay].
class DisplayManager {
  final _displayMethodChannelId = "presentation_displays_plugin";
  final _displayEventChannelId = "presentation_displays_plugin_events";

  late MethodChannel? _displayMethodChannel;
  late EventChannel? _displayEventChannel;

  DisplayManager() {
    _displayMethodChannel = MethodChannel(_displayMethodChannelId);
    _displayEventChannel = EventChannel(_displayEventChannelId);
  }

  /// Gets all currently valid logical displays of the specified category.
  /// <p>
  /// When there are multiple displays in a category the returned displays are sorted
  /// of preference.  For example, if the requested category is
  /// [DISPLAY_CATEGORY_PRESENTATION] and there are multiple secondary display
  /// then the displays are sorted so that the first display in the returned array
  /// is the most preferred secondary display.  The application may simply
  /// use the first display or allow the user to choose.
  /// </p>
  ///
  /// [category] The requested display category or null to return all displays.
  /// @return An array containing all displays sorted by order of preference.
  ///
  /// See [DISPLAY_CATEGORY_PRESENTATION]
  Future<List<Display>?> getDisplays({String? category}) async {
    final dynamic raw = await _displayMethodChannel?.invokeMethod(_listDisplay, category);
    List<dynamic> origins = [];

    if (raw is List) {
      origins = raw;
    } else if (raw is String) {
      origins = jsonDecode(raw) ?? [];
    }

    List<Display> displays = [];
    for (var element in origins) {
      // element may already be a Map or a decoded JSON object
      if (element is Map) {
        displays.add(displayFromJson(Map<String, dynamic>.from(element)));
      } else {
        final map = jsonDecode(jsonEncode(element));
        displays.add(displayFromJson(map as Map<String, dynamic>));
      }
    }
    return displays;
  }

  /// Gets the name of the display by [displayId] of [getDisplays].
  /// <p>
  /// Note that some displays may be renamed by the user.
  /// [category] The requested display category or null to return all displays.
  /// See [DISPLAY_CATEGORY_PRESENTATION]
  /// </p>
  ///
  /// @return The display's name.
  /// May be null.
  Future<String?> getNameByDisplayId(int displayId, {String? category}) async {
    List<Display> displays = await getDisplays(category: category) ?? [];

    String? name;
    for (var element in displays) {
      if (element.displayId == displayId) name = element.name;
    }
    return name;
  }

  /// Gets the name of the display by [index] of [getDisplays].
  /// <p>
  /// Note that some displays may be renamed by the user.
  /// [category] The requested display category or null to return all displays.
  /// see [DISPLAY_CATEGORY_PRESENTATION]
  /// </p>
  ///
  /// @return The display's name
  /// May be null.
  Future<String?> getNameByIndex(int index, {String? category}) async {
    List<Display> displays = await getDisplays(category: category) ?? [];
    String? name;
    if (index >= 0 && index <= displays.length) name = displays[index].name;
    return name;
  }

  /// Creates a new secondary display that is attached to the specified display
  /// <p>
  /// Before displaying a secondary display, please define the UI you want to display in the [Route].
  /// If we can't find the router name, the secondary display a blank screen
  /// [displayId] The id of display to which the secondary display should be attached.
  /// [routerName] The screen you want to display on the secondary display.
  /// </P>
  ///
  /// return [Future<bool>] about the status has been display or not
  Future<bool?>? showSecondaryDisplay(
      {required int displayId, required String routerName}) async {
    return await _displayMethodChannel?.invokeMethod<bool?>(_showPresentation, <String, dynamic>{
      'displayId': displayId,
      'routerName': routerName,
    });
  }

  /// Hides secondary display that is attached to the specified display
  /// <p>
  /// [displayId] The id of display to which the secondary display should be attached.
  /// [routerName] The router name of the presentation to hide.
  /// If both are provided, will hide the exact match.
  /// If only routerName is provided, will hide by router name.
  /// If only displayId is provided, will hide by display ID.
  /// If neither is provided, will hide all presentations.
  /// </P>
  ///
  /// return [Future<bool>] about the status has been display or not
  Future<bool?>? hideSecondaryDisplay({int? displayId, String? routerName}) async {
    final Map<String, dynamic> args = {};
    if (displayId != null) args['displayId'] = displayId;
    if (routerName != null) args['routerName'] = routerName;
    
    return await _displayMethodChannel?.invokeMethod<bool?>(_hidePresentation, args);
  }

  /// Hides all secondary displays and returns them to default state
  /// <p>
  /// This method ensures that all secondary displays are properly dismissed
  /// and returned to their default state (usually showing the system wallpaper
  /// or the last app that was displayed).
  /// </p>
  ///
  /// return [Future<bool>] true if all presentations were successfully hidden
  Future<bool?>? hideAllSecondaryDisplays() async {
    return await _displayMethodChannel?.invokeMethod<bool?>(_hidePresentation, {});
  }

  /// Transfer data to a secondary display
  /// <p>
  /// Transfer data from main screen to a secondary display
  /// Consider using [arguments] for cases where a particular run-time type is expected. Consider using String when that run-time type is Map or JSONObject.
  /// </p>
  /// <p>
  /// Main Screen
  ///
  /// ```dart
  /// DisplayManager displayManager = DisplayManager();
  /// ...
  /// static Future<void> transferData(Song song) async {
  ///   displayManager.transferDataToPresentation(<String, dynamic>{
  ///         'id': song.id,
  ///         'title': song.title,
  ///         'artist': song.artist,
  ///       });
  /// }
  /// ```
  /// Secondary display
  ///
  /// ```dart
  /// class _SecondaryScreenState extends State<SecondaryScreen> {
  ///   @override
  ///   Widget build(BuildContext context) {
  ///       return PresentationDisplay(
  ///        callback: (argument) {
  ///          Song.fromJson(argument)
  ///       },
  ///       child: Center()
  ///     );
  ///   }
  /// }
  /// ```
  /// Class Song
  ///
  /// ```dart
  /// class Song {
  ///   Song(this.id, this.title, this.artist);
  ///
  ///   final String id;
  ///   final String title;
  ///   final String artist;
  ///
  ///   static Song fromJson(dynamic json) {
  ///     return Song(json['id'], json['title'], json['artist']);
  ///   }
  /// }
  /// ```
  /// </p>
  ///
  /// return [Future<bool>] the value to determine whether or not the data has been transferred successfully
  Future<bool?>? transferDataToPresentation(dynamic arguments) async {
    return await _displayMethodChannel?.invokeMethod<bool?>(
        _transferDataToPresentation, arguments);
  }

  /// Prewarm a FlutterEngine for a given [routerName]. This creates and starts
  /// a cached engine on the native side using the same entrypoint
  /// (`secondaryDisplayMain`) and initial route used when showing a presentation.
  ///
  /// Use this to reduce UI jank when presenting a secondary display by
  /// preparing the engine ahead of time.
  Future<bool?>? prewarmEngine({required String routerName}) async {
    return await _displayMethodChannel?.invokeMethod<bool?>(_prewarmEngine,
        <String, dynamic>{'routerName': routerName});
  }

  /// Hides all active presentations
  /// <p>
  /// This will dismiss all currently active secondary displays.
  /// </p>
  ///
  /// return [Future<int>] the number of presentations that were hidden
  Future<int?>? hideAllPresentations() async {
    return await _displayMethodChannel?.invokeMethod<int?>(_hideAllPresentations);
  }

  /// Gets the list of currently active presentation router names
  /// <p>
  /// This returns the router names of all presentations that are currently active.
  /// </p>
  ///
  /// return [Future<List<String>>] list of active router names
  Future<List<String>?>? getActivePresentations() async {
    return await _displayMethodChannel?.invokeMethod<List<String>?>(_getActivePresentations);
  }

  /// Subscribe to the stream to get notifications about connected / disconnected displays
  /// Streams [1] for new connected display and [0] for disconnected display
  Stream<int?>? get connectedDisplaysChangedStream {
    return _displayEventChannel?.receiveBroadcastStream().cast();
  }
}
