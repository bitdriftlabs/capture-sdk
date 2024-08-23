# Session Replay

Session replay is a system that allows an app to send screen elements from the Loop SDKs in a compact binary form. It works by traversing the view hierarchy and sending the absolute position / size of every element. There are a few heuristics in place to pick the right views and position them correctly. This logic is described in the next section.

## Heuristics on the client

There are a few challenges we need to get right to get a meaningful representation of the screen:

1. **Infer and tag the type of the views (e.g. Labels, Images, etc)**: The client tries to infer the type, mainly by looking at the class name as well as the class kind (eg. on iOS subclasses of UILabel will be tagged as labels). One important goal of this system is to make this task very generic as well as customizable.
1. **Ignore views that do not provide any meaningful visualization**: Most of the views in the hierarchy do not actually provide any rendering content. Recognizing these cases can be tricky and the SDK tries to do a best effort to ignore them. As an example, it ignores views with no bitmap layers and transparent backgrounds.
1. **Provide the right positioning and size depending on the type, not just the view rect**: When you have views like "labels" or "images", the position you really care about is the rendered content not the view itself (e.g. Think about a label that has the width of the full screen, its text alignment is `centered` and the text is ABC).
1. **Masking the views for what's shown in the screen**: Parent views can mask child views so we want to clip the descendants recursively.
1. **Serializing the information into a compact binary form**: Once the right views are recognized, the SDK packs the information into a binary format that at most takes 9 bytes per view and a minimum of 5.

1. _TODO: Remove views that are completely covered by others._

## Visualization Customization

Customers can provide types for their custom views that are not necessarily infered by the SDK. At Lyft, for example, we want to assume that `MapboxMapView` is a map view and we want to ignore all subviews. In order to do this, one can do:

```swift
replay.add(knownClass: "MapboxMapView", type: AnnotatedViewType(.map, recurse: false))
```

Additionally, we support more complex customizations. The following example would tag all descendants of CenteredLabel as label and provide the right frame:

```swift
extension CenteredLabel: ReplayIdentifiable {
    final func identify(frame: inout CGRect) -> AnnotatedViewType? {
        guard let text = self.text, !text.isEmpty else {
            return .ignored
        }

        frame.size = text.boundingRect(with: self.frame.size,
                                       attributes: [.font: self.font],
                                       context: nil).size

        frame.origin.x += ((self.frame.size.width / 2) - (frame.size.width / 2)).rounded()
        frame.origin.y += ((self.frame.size.height / 2) - (frame.size.height / 2)).rounded()
        return AnnotatedViewType(.label, recurse: false)
    }
}
```

## Binary format

The binary PDU is dynamic in size and defined as follows:

- The first byte is used for the `type` and a bitmask which defines the size in bytes of the elements as follows:
   - The 4 most significant bits are use to define if x, y, width, height (in that order) need to take 2 bytes (1) or just 1 (0).
   - The 4 least significant bits are use for the view type. This means that the maximum type size is b1111 (ie 15 types)
- The next byte (or two) is the x position relative to the axis of the window (not its superview)
- The next byte (or two) is the y position relative to the axis of the window (not its superview)
- The next byte (or two) is the width size
- The next byte (or two) is the height size

##### Example 1

```
(x, y) = (0, 0)
(width, height) = (0, 0)
type = 0 (label is id=0)

| 0x00 | 0x00 | 0x00 | 0x00 | 0x00 |
  type    x       y    width height
```

##### Example 2


```
(x, y) = (256, 0) # Note `x` would be 0x100 in hex
(width, height) = (0, 0)
type = 0 (label is id=0)

| 0x80 | 0x01 | 0x00 | 0x00 | 0x00 | 0x00 |
  type    x       x      y    width height
```
