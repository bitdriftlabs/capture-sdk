// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SpriteKit

extension Replay {
    func traverse(into buffer: inout Data, view: SKView, parentPosition: CGPoint, clipTo: CGRect) {
        guard let scene = view.scene else { return }

        var children: [(CGFloat, CGRect)] = []
        var queue = view.scene?.children.map { ($0, 0, 0, 0.0) } ?? []
        while let (node, index, parentIndex, parentZ) = queue.popLast() {
            let ignoreNode =
                (node.alpha < 0.1 || node.isHidden || node.frame.isEmpty || node.frame.isInfinite
                    || node.frame.isNull || node.frame.width < 0.1 || node.frame.height < 0.1)

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
                    children.append(
                        (
                            (parentZ + node.zPosition) * 1000 + CGFloat(parentIndex) * 100 + CGFloat(index),
                            frame
                        )
                    )
                }
            }

            node.children.enumerated().forEach {
                queue.append(($1, $0, parentIndex + 1, parentZ + node.zPosition))
            }
        }

        for (_, frame) in children.sorted(by: { a, b in a.0 <= b.0 }) {
            rectToBytes(type: .sprite, buffer: &buffer, frame: frame.intersection(clipTo))
        }
    }
}
