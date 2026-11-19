// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import SwiftUI
import UIKit

struct RepresentedSessionReplayBenchmarkView: UIViewRepresentable {
    typealias UIViewType = SessionReplayBenchmarkView

    func makeUIView(context _: Context) -> SessionReplayBenchmarkView {
        return SessionReplayBenchmarkView()
    }

    func updateUIView(_: SessionReplayBenchmarkView, context _: Context) {}
}

final class SessionReplayBenchmarkView: UIView {
    init() {
        super.init(frame: .zero)
        self.backgroundColor = .lightGray

        for index in 0..<50 {
            self.addSubview(
                UIView(frame: CGRect(x: 10 + index, y: 40 + index, width: 20 + index, height: 20 + index))
            )
        }

        for index in 0..<30 {
            let label = UILabel(
                frame: CGRect(x: 10 + index, y: 40 + index, width: 20 + index, height: 20 + index)
            )
            label.text = "test"
            self.addSubview(label)
        }

        for index in 0..<30 {
            let imageView = UIImageView(
                frame: CGRect(x: 10 + index, y: 40 + index, width: 20 + index, height: 20 + index)
            )
            imageView.image = UIImage(systemName: "multiply.circle.fill")
            self.addSubview(imageView)
        }

        for index in 0..<30 {
            let button = UIButton(
                frame: CGRect(x: 10 + index, y: 40 + index, width: 20 + index, height: 20 + index)
            )
            button.setTitle("test", for: .normal)
            self.addSubview(button)
        }

        let label = UILabel(frame: CGRect(x: 0, y: 0, width: 300, height: 40))
        self.addSubview(label)

        let viewsCount = self.traverse(view: self)

        label.text = "Session Replay Test View: \(viewsCount) views"
    }

    func traverse(view: UIView) -> Int {
        var result = 1 // 1 for current view
        for subview in view.subviews {
            result += self.traverse(view: subview)
        }
        return result
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}
