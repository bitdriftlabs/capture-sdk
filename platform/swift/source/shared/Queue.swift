// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// An extension with convenience to use methods and properties for working with `DispatchQueue`s.
///
/// The overall idea for the use of queues in the framework is to have a small number of top level serial
/// queues that other queues specify as their target. This follows some of the best practices shared by Apple
/// engineers in [Moderninzing Grand Central Usage](https://developer.apple.com/wwdc17/706) WWDC session.
///
/// A various other resources where used when determining the actual setup of used queues (their
/// initialization parameters). In the order of their relevance:
///  1. https://gist.github.com/tclementdev/6af616354912b0347cdf6db159c37057
///  2. https://www.fadel.io/blog/posts/ios-performance-tips-you-probably-didnt-know/
///  3. https://mjtsai.com/blog/2021/03/16/underused-and-overused-gcd-patterns/
extension DispatchQueue {
    /// A root queue to be targeted by any queue that wants to perform basic (simple, short-lived) processing
    /// tasks.
    static let `default` = DispatchQueue(
        label: "io.bitdrift.default",
        qos: .utility,
        /// Per https://twitter.com/pedantcoder/status/1370080183051382786?s=20 it's good to always set
        /// `autoreleaseFrequency` attribute so we do it here.
        autoreleaseFrequency: .workItem
    )

    /// A root queue to be used for heavy (long) operations such as IO operations
    static let heavy = DispatchQueue(
        label: "io.bitdrift.io",
        qos: .utility,
        /// Per https://twitter.com/pedantcoder/status/1370080183051382786?s=20 it's good to always set
        /// `autoreleaseFrequency` attribute so we do it here.
        autoreleaseFrequency: .workItem
    )

    /// A root queue to be used for network operations performed by the SDK itself.
    static let network = DispatchQueue(
        label: "io.bitdrift.network",
        qos: .utility,
        autoreleaseFrequency: .workItem
    )

    /// Creates a new serial queue with a label that's a result of concatenating the label of a target queue
    /// and the
    /// provided label suffix.
    ///
    /// Having a lot of queues, all with distinct queue labels may make debugging of issues easier as queue
    /// labels are displayed prominently in various places in Xcode itself and in services such as Bugsnag.
    /// For this
    /// reason, and because of the fact that queues created with the method call should be "cheap" as they all
    /// target
    /// an underlying queue it's recommended to create a new queue for each separate part of the system
    /// i.e., a listener or an attribute monitor.
    ///
    ///
    /// - parameter labelSuffix: The suffix to use as the suffix of the created label. The label of the
    ///                          created queue
    ///                          follows the following format: `[target.label].[labelSuffix]`.
    /// - parameter target:      The queue to target.
    /// - parameter qos:         The qos that created queue should use.
    ///
    /// - returns: The created serial queue.
    static func serial(
        withLabelSuffix labelSuffix: String,
        target: DispatchQueue,
        qos: DispatchQoS = .utility
    ) -> DispatchQueue {
        return DispatchQueue(
            label: "\(target.label).\(labelSuffix)",
            qos: qos,
            target: target
        )
    }
}

/// An extension with convenience to use methods for working with `OperationQueue`s.
extension OperationQueue {
    private final class OperationQueue: Foundation.OperationQueue {
        /// A strong reference to the underlying queue. The public interface of `Foundation.OperationQueue`
        /// specifies its underlying queue as a non-retaining property and by capturing a strong reference in
        /// here we make our APIs easier to use.
        let retainedUnderlyingQueue: DispatchQueue

        init(underlyingQueue: DispatchQueue) {
            self.retainedUnderlyingQueue = underlyingQueue
            super.init()
            self.underlyingQueue = underlyingQueue
        }
    }

    /// Creates a new serial queue with a label that's a result of concatenating the label of a target queue
    /// and the
    /// provided label suffix.
    ///
    /// Having a lot of queues, all with distinct queue labels may make debugging of issues easier as queue
    /// labels are displayed prominently in various places in Xcode itself and in services such as Bugsnag.
    /// For this
    /// reason, and because of the fact that queues created with the method call should be "cheap" as they all
    /// target
    /// an underlying queue it's recommended to create a new queue for each separate part of the system
    /// i.e., a listener or an attribute monitor.
    ///
    ///
    /// - parameter labelSuffix: The suffix to use as the suffix of the created label. The label of the
    ///                          created queue
    ///                          follows the following format: `[target.label].[labelSuffix]`.
    /// - parameter target:      The queue to target.
    /// - parameter qos:         The qos that created queue should use.
    ///
    /// - returns: The created serial queue.
    static func serial(
        withLabelSuffix labelSuffix: String,
        target: DispatchQueue,
        qos: DispatchQoS = .utility
    ) -> Foundation.OperationQueue {
        return OperationQueue(
            underlyingQueue: DispatchQueue(
                label: "\(target.label).\(labelSuffix)",
                qos: qos,
                target: target
            )
        )
    }
}
