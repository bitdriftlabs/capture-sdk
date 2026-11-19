// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import UIKit

final class PreviousRunTerminationObserver {
    private let store: BDPreviousRunInfoRepository
    private let notificationCenter: NotificationCenter
    private var token: NSObjectProtocol?

    init(
        store: BDPreviousRunInfoRepository,
        notificationCenter: NotificationCenter = .default
    ) {
        self.store = store
        self.notificationCenter = notificationCenter
    }

    deinit {
        if let token {
            self.notificationCenter.removeObserver(token)
        }
    }

    func start() {
        guard self.token == nil else {
            return
        }

        self.token = self.notificationCenter.addObserver(
            forName: UIApplication.willTerminateNotification,
            object: nil,
            queue: nil
        ) { [weak self] _ in
            self?.store.markTerminating()
        }
    }
}
