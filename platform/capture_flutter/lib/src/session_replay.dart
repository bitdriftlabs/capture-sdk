import 'dart:typed_data';
import 'package:flutter/material.dart';

/// Matches the native Capture SDK ReplayType enum values.
enum ReplayType {
  label(0),
  button(1),
  textInput(2),
  image(3),
  view(4),
  backgroundImage(5),
  switchOn(6),
  switchOff(7),
  map(8),
  chevron(9),
  transparentView(10),
  keyboard(11),
  webView(12);

  final int value;
  const ReplayType(this.value);
}

/// A single rect to be serialized in the replay binary format.
class ReplayRect {
  final ReplayType type;
  final int x;
  final int y;
  final int width;
  final int height;

  const ReplayRect({
    required this.type,
    required this.x,
    required this.y,
    required this.width,
    required this.height,
  });
}

/// Encodes a list of [ReplayRect] into the Capture SDK binary format.
///
/// Format per rect (5-9 bytes):
///   Byte 0: [mask_x][mask_y][mask_w][mask_h][type 3..0]
///   Bytes 1+: x, y, width, height (each 1 or 2 bytes based on mask)
Uint8List encodeReplayRects(List<ReplayRect> rects) {
  final buffer = BytesBuilder(copy: false);
  for (final rect in rects) {
    int byte0 = rect.type.value & 0x0F;
    final values = [rect.x, rect.y, rect.width, rect.height];
    final encoded = <int>[];

    for (int i = 0; i < 4; i++) {
      if (values[i] > 255 || values[i] < 0) {
        byte0 |= (1 << (7 - i));
        encoded.add((values[i] >> 8) & 0xFF);
        encoded.add(values[i] & 0xFF);
      } else {
        encoded.add(values[i] & 0xFF);
      }
    }

    buffer.addByte(byte0);
    buffer.add(encoded);
  }
  return buffer.toBytes();
}

/// Walks the Flutter render tree and produces [ReplayRect] entries
/// for each visible widget, classifying them by type.
class FlutterReplayCapture {
  /// Capture the current widget tree and return encoded binary data.
  static Uint8List captureScreen() {
    final rects = <ReplayRect>[];

    // Add root screen bounds as the first rect so the native renderer
    // knows the overall wireframe dimensions.
    final window = WidgetsBinding.instance.platformDispatcher.views.first;
    final screenSize = window.physicalSize / window.devicePixelRatio;
    rects.add(ReplayRect(
      type: ReplayType.view,
      x: 0,
      y: 0,
      width: screenSize.width.round(),
      height: screenSize.height.round(),
    ));

    final rootElement = WidgetsBinding.instance.rootElement;
    if (rootElement != null) {
      _walkElement(rootElement, rects);
    }
    return encodeReplayRects(rects);
  }

  /// Widget types whose subtrees should NOT be walked after classification,
  /// because their children are internal implementation details.
  static const _opaqueTypes = {ReplayType.textInput};

  static void _walkElement(Element element, List<ReplayRect> rects) {
    final renderObject = element.renderObject;
    bool skipChildren = false;

    if (renderObject is RenderBox && renderObject.hasSize) {
      final type = _classifyElement(element);
      if (type != null) {
        final offset = _getGlobalOffset(renderObject);
        if (offset != null) {
          final size = renderObject.size;
          rects.add(ReplayRect(
            type: type,
            x: offset.dx.round(),
            y: offset.dy.round(),
            width: size.width.round(),
            height: size.height.round(),
          ));
          if (_opaqueTypes.contains(type)) {
            skipChildren = true;
          }
        }
      }
    }

    if (!skipChildren) {
      element.visitChildren((child) {
        _walkElement(child, rects);
      });
    }
  }

  static Offset? _getGlobalOffset(RenderBox box) {
    try {
      return box.localToGlobal(Offset.zero);
    } catch (_) {
      return null;
    }
  }

  /// Classify a Flutter Element into a ReplayType based on its widget.
  ///
  /// Only classifies user-facing "leaf" widgets to avoid duplicates from
  /// internal composition (e.g. ElevatedButton contains InkWell contains
  /// Material — we only want one rect for the button).
  static ReplayType? _classifyElement(Element element) {
    final widget = element.widget;

    // Text — only RichText (the leaf renderer).
    if (widget is RichText) {
      return ReplayType.label;
    }

    // Buttons — only top-level button widgets.
    if (widget is ElevatedButton ||
        widget is TextButton ||
        widget is OutlinedButton ||
        widget is IconButton ||
        widget is FloatingActionButton) {
      return ReplayType.button;
    }

    // Text inputs
    if (widget is EditableText) {
      return ReplayType.textInput;
    }

    // Images
    if (widget is Image || widget is RawImage) {
      return ReplayType.image;
    }

    // Switches
    if (widget is Switch) {
      return ReplayType.switchOn;
    }

    // Card / elevated Material (dialogs, bottom sheets, etc.) / Scaffold
    if (widget is Card || widget is Scaffold) {
      return ReplayType.view;
    }
    if (widget is Material && widget.elevation > 0) {
      return ReplayType.view;
    }

    return null;
  }
}
