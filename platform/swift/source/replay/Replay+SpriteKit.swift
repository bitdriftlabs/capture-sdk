// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SpriteKit

extension Replay {
    /**
     * This function is used to traverse the view hierarchy of a SpriteKit scene and
     * collect the frames of all visible nodes. It takes a buffer to store the frames,
     * the SKView to traverse, the parent position of the nodes, and a clipping rectangle
     * to limit the area of interest.
     *
     * - parameter buffer:         The buffer to store the frames of the nodes.
     * - parameter view:           The SKView to traverse.
     * - parameter parentPosition: The parent position of the nodes.
     * - parameter clipTo:         The clipping rectangle to limit the area of interest.
     */
    func traverse(into buffer: inout Data, view: SKView, clipTo: CGRect) {
        guard let scene = view.scene else { return }

        var children: [(z: CGFloat, annotated: AnnotatedView)] = []
        var queue = [(scene as SKNode, 0, 0, 0.0, clipTo)]
        while let (node, index, parentIndex, nodeZ, clipTo) = queue.popLast() {
            let annotated = node.annotated(in: view, from: scene)

            if annotated.type != .ignore {
                if annotated.frame.intersects(clipTo) {
                    var clipped = annotated
                    clipped.frame = annotated.frame.intersection(clipTo)
                    let childIndexOffset = CGFloat(parentIndex) * 100 + CGFloat(index)
                    children.append((z: (nodeZ + node.zPosition) * 1000 + childIndexOffset, clipped))
                }
            }

            if !annotated.recurse {
                continue
            }

            for (i, child) in node.children.enumerated() {
                let clipTo = node is SKCropNode ? annotated.frame : clipTo
                queue.append((child, i, parentIndex + 1, nodeZ + node.zPosition, clipTo))
            }
        }

        for (_, annotated) in children.sorted(by: { $0.z <= $1.z }) {
            rectToBytes(type: annotated.type, buffer: &buffer, frame: annotated.frame)
        }
    }
}

private extension SKNode {
    func annotated(in view: SKView, from scene: SKScene) -> AnnotatedView {
        let pointInScene = self.parent?.convert(self.frame.origin, to: scene)
        let frameInScene = CGRect(origin: pointInScene ?? self.frame.origin, size: self.frame.size)
        let frameInView = (view as UIView).convert(frameInScene, from: scene as UICoordinateSpace)

        if self.alpha < 0.1 || self.isHidden {
            return .ignored
        }

        var annotatedView: AnnotatedView?
        if let identifiable = self as? ReplayIdentifiable {
            annotatedView = identifiable.identify(frame: frameInView)
        }

        if let identifiable = self as? ReplaySpriteKitIdentifiable {
            annotatedView = identifiable.identifySpriteKit(frame: frameInView)
        }

        return annotatedView ?? .skipped
    }
}

// MARK: - SpriteKit default indentification

/// Internal protocol to provide SpriteKit identifications for known subclasses. This is split from
/// `ReplayIdentifiable` mainly because we want consumers with subclasses of `SpriteKit` types to be able to
/// define their types independently of our heuristic.
protocol ReplaySpriteKitIdentifiable where Self: SKNode {
    /// This method works the same way as `ReplayIdentifiable.identify` refer to that for more information.
    ///
    /// - parameter frame: See `ReplayIdentifiable.identify`.
    ///
    /// - returns: See `ReplayIdentifiable.identify`.
    func identifySpriteKit(frame: CGRect) -> AnnotatedView?
}

extension SKSpriteNode: ReplaySpriteKitIdentifiable {
    func identifySpriteKit(frame: CGRect) -> AnnotatedView? { AnnotatedView(.sprite, frame: frame) }
}

extension SKShapeNode: ReplaySpriteKitIdentifiable {
    func identifySpriteKit(frame: CGRect) -> AnnotatedView? { AnnotatedView(.sprite, frame: frame) }
}

extension SKEmitterNode: ReplaySpriteKitIdentifiable {
    func identifySpriteKit(frame: CGRect) -> AnnotatedView? { AnnotatedView(.sprite, frame: frame) }
}

extension SKLabelNode: ReplaySpriteKitIdentifiable {
    func identifySpriteKit(frame: CGRect) -> AnnotatedView? { AnnotatedView(.label, frame: frame) }
}

extension SKVideoNode: ReplaySpriteKitIdentifiable {
    func identifySpriteKit(frame: CGRect) -> AnnotatedView? { AnnotatedView(.image, frame: frame) }
}

extension SKTileMapNode: ReplaySpriteKitIdentifiable {
    func identifySpriteKit(frame: CGRect) -> AnnotatedView? { AnnotatedView(.image, frame: frame) }
}
