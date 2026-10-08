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

/// Flattens [rects] into `[type, x, y, width, height]` tuples.
Int32List flattenReplayRects(List<ReplayRect> rects) {
  final values = Int32List(rects.length * 5);
  var i = 0;
  for (final rect in rects) {
    values[i++] = rect.type.value;
    values[i++] = rect.x;
    values[i++] = rect.y;
    values[i++] = rect.width;
    values[i++] = rect.height;
  }
  return values;
}

/// Walks the Flutter render tree and produces [ReplayRect] entries
/// for each visible widget, classifying them by type.
class FlutterReplayCapture {
  /// Capture the current widget tree and return encoded binary data.
  static Uint8List captureScreen() => encodeReplayRects(captureRects());

  /// Capture the current widget tree as a list of rects in logical pixels,
  /// relative to the Flutter view.
  ///
  /// When [includeRoot] is true the first rect spans the whole view so the
  /// renderer knows the overall wireframe dimensions.
  static List<ReplayRect> captureRects({bool includeRoot = true}) {
    final rects = <ReplayRect>[];

    if (includeRoot) {
      final window = WidgetsBinding.instance.platformDispatcher.views.first;
      final screenSize = window.physicalSize / window.devicePixelRatio;
      rects.add(
        ReplayRect(
          type: ReplayType.view,
          x: 0,
          y: 0,
          width: screenSize.width.round(),
          height: screenSize.height.round(),
        ),
      );
    }

    final rootElement = WidgetsBinding.instance.rootElement;
    if (rootElement != null) {
      _ReplayWalker(rects).walk(rootElement);
    }
    return rects;
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
      return widget.value ? ReplayType.switchOn : ReplayType.switchOff;
    }

    // Card / elevated Material (dialogs, bottom sheets, etc.) / Scaffold
    if (widget is Card || widget is Scaffold) {
      return ReplayType.view;
    }
    if (widget is Material &&
        widget.elevation > 0 &&
        widget.type != MaterialType.transparency) {
      return ReplayType.view;
    }

    return null;
  }
}

/// Walks the element tree, emitting rects only for what is actually painted:
/// subtrees that are hidden (zero opacity, offstage, covered overlay entries)
/// are skipped, rects are mapped through paint transforms and clipped to
/// their ancestors' paint clips.
class _ReplayWalker {
  _ReplayWalker(this.rects);

  final List<ReplayRect> rects;
  final Set<RenderObject> _hidden = Set.identity();
  final Set<RenderObject> _emitted = Set.identity();

  static const _controlTypes = {
    ReplayType.button,
    ReplayType.textInput,
    ReplayType.switchOn,
    ReplayType.switchOff,
  };

  void walk(Element root) => _walk(root, Rect.largest, insideControl: false);

  void _walk(Element element, Rect clip, {required bool insideControl}) {
    if (element is RenderObjectElement) {
      final renderObject = element.renderObject;
      if (_hidden.contains(renderObject) || !renderObject.attached) return;
      final parent = renderObject.parent;
      if (parent != null) {
        if (!parent.paintsChild(renderObject)) return;
        final parentClip = parent.describeApproximatePaintClip(renderObject);
        if (parentClip != null) {
          clip = clip.intersect(
            MatrixUtils.transformRect(parent.getTransformTo(null), parentClip),
          );
          if (clip.isEmpty) return;
        }
      }
    }

    if (element.widget is Overlay) {
      _hideOffstageOverlayEntries(element);
    }

    var skipChildren = false;
    final type = FlutterReplayCapture._classifyElement(element);
    if (type != null && !(insideControl && type == ReplayType.view)) {
      final renderObject = element.renderObject;
      if (renderObject is RenderBox &&
          renderObject.hasSize &&
          _emitted.add(renderObject)) {
        final bounds = MatrixUtils.transformRect(
          renderObject.getTransformTo(null),
          Offset.zero & renderObject.size,
        ).intersect(clip);
        if (bounds.width >= 1 && bounds.height >= 1) {
          rects.add(
            ReplayRect(
              type: type,
              x: bounds.left.round(),
              y: bounds.top.round(),
              width: bounds.width.round(),
              height: bounds.height.round(),
            ),
          );
        }
      }
      skipChildren = type == ReplayType.textInput;
      insideControl = insideControl || _controlTypes.contains(type);
    }

    if (skipChildren) return;
    final childInsideControl = insideControl;
    element.visitChildren(
      (child) => _walk(child, clip, insideControl: childInsideControl),
    );
  }

  /// The overlay only paints its onstage entries, which are the children it
  /// visits for semantics.
  void _hideOffstageOverlayEntries(Element overlay) {
    final theater = overlay.renderObject;
    if (theater == null) return;
    final onstage = Set<RenderObject>.identity();
    theater.visitChildrenForSemantics(onstage.add);
    theater.visitChildren((child) {
      if (!onstage.contains(child)) _hidden.add(child);
    });
  }
}
