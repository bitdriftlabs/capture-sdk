// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import MetricKit

@available(iOS 27.0, *)
struct ExtractedThread {
    let name: String?
    let attributed: Bool
    /// Frames ordered outermost (root) first, matching `CallStackFrame.subFrames` descent order.
    let frames: [ExtractedFrame]
    
    init(name: String?, attributed: Bool, frames: [ExtractedFrame]) {
        self.name = name
        self.attributed = attributed
        self.frames = frames
    }
    
    init(dictionary: [String: Any]) {
        self.name = dictionary["name"] as? String
        self.attributed = (dictionary["threadAttributed"] as? Bool) ?? false
        if let framesDictionary = dictionary["callStackRootFrames"] as? [[String: Any]] {
            self.frames = Self.extractFrames(fromDict: framesDictionary.first)
        } else {
            self.frames = .init()
        }
    }
    
    /// Parses the `CallStackTree` from MetricKit v2 payload to an array of  local `ExtractedThread`.
    /// - Parameter tree: the call stack tree from the MetricKit v2 payload.
    /// - Returns: an array with all the threads that could be extracted from the MetricKit payload.
    static func create(fromCallStackTree tree: CallStackTree) -> [ExtractedThread] {
        tree.callStackThreads.map { thread in
            ExtractedThread(
                name: nil,
                attributed: thread.threadAttributed ?? false,
                frames: extractFrames(rootFrames: thread.rootFrames, tree: tree)
            )
        }
    }
    
    /// Parses a dictionary and transforms its contents into an array of local `ExtractedThread`.
    /// - Parameter dictionary: a dictionary containing the infromation of an enhanced callStackTree (similar to MetricKit v1)
    /// - Returns: an array with all the threads that could be extracted from the enhanced dictionary.
    static func create(fromThreadsDictionary dictionary: [String: Any]) -> [ExtractedThread] {
        guard
            let callStackTree = dictionary["callStackTree"] as? [String: Any],
            let callStacks = callStackTree["callStacks"] as? [[String: Any]]
        else {
            return []
        }
        
        return callStacks.map { thread in
            ExtractedThread(dictionary: thread)
        }
    }
}

@available(iOS 27.0, *)
private extension ExtractedThread {
    static func extractFrames(
        rootFrames: ContiguousArray<CallStackFrame>,
        tree: CallStackTree
    ) -> [ExtractedFrame] {
        var frames: [ExtractedFrame] = []
        var current = rootFrames.first
        while let frame = current {
            guard
                let address = frame.address,
                let binaryUUID = frame.binaryUUID,
                let offset = frame.offsetIntoBinaryTextSegment
            else {
                break
            }

            frames.append(ExtractedFrame(
                address: address,
                binaryUUID: binaryUUID.uuidString,
                binaryName: frame.binaryName(from: tree) ?? "",
                offsetIntoBinaryTextSegment: offset
            ))
            current = frame.subFrames.first
        }
        return frames
    }
    
    static func extractFrames(fromDict rootFrame: [String: Any]?) -> [ExtractedFrame] {
        var frames: [ExtractedFrame] = []
        var current = rootFrame
        while let frame = current {
            guard
                let address = frame["address"] as? UInt64,
                let binaryUUID = frame["binaryUUID"] as? String,
                let offset = frame["offsetIntoBinaryTextSegment"] as? UInt64
            else {
                break
            }

            frames.append(ExtractedFrame(
                address: address,
                binaryUUID: binaryUUID,
                binaryName: frame["binaryName"] as? String ?? "",
                offsetIntoBinaryTextSegment: offset
            ))
            current = (frame["subFrames"] as? [[String: Any]])?.first
        }
        return frames
    }
}

struct ExtractedFrame {
    let address: UInt64
    let binaryUUID: String
    let binaryName: String
    let offsetIntoBinaryTextSegment: UInt64
}
