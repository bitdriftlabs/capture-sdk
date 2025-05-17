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
    func traverse(into buffer: inout Data, view: SKView, parentPosition: CGPoint, clipTo: CGRect) {
        guard let scene = view.scene else { return }

        var children: [(z: CGFloat, frame: CGRect)] = []
        var queue = view.scene?.children.map { ($0, 0, 0, 0.0) } ?? []
        while let (node, index, parentIndex, nodeZ) = queue.popLast() {
            if node.alpha < 0.1 || node.isHidden {
                continue
            }

            let ignoreNode = (
                node.frame.isEmpty || node.frame.isInfinite || node.frame.isNull ||
                node.frame.width < 0.1 || node.frame.height < 0.1
            )

            if !ignoreNode {
                var frame = node.frame
                let middle = scene.convert(node.position, from: node.parent!)
                let plus = CGPoint(x: middle.x + node.frame.width, y: middle.y + node.frame.height)

                frame.origin = view.convert(middle, from: scene)

                let converted = view.convert(plus, from: scene)
                frame.size.width = abs(converted.x - frame.origin.x)
                frame.size.height = abs(converted.y - frame.origin.y)
                frame.origin.x -= frame.size.width * 0.5
                frame.origin.y -= frame.size.height * 0.5

                if frame.intersects(clipTo) {
                    let childIndexOffset = CGFloat(parentIndex) * 100 + CGFloat(index)
                    children.append((z: nodeZ * 1000 + childIndexOffset, frame))
                }
            }

            node.children.enumerated().forEach {
                queue.append(($1, $0, parentIndex + 1, nodeZ + node.zPosition))
            }
        }

        for (_, frame) in children.sorted(by: { $0.z <= $1.z }) {
            rectToBytes(type: .sprite, buffer: &buffer, frame: frame.intersection(clipTo))
        }
    }
}
