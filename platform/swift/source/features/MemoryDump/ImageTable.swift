// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureMemoryCore

/// The segment map of the loaded images, surveyed once per dump and shared by the stages.
final class ImageTable {
    let pointer: OpaquePointer

    init?() {
        guard let pointer = bdmem_image_table_create() else {
            return nil
        }
        self.pointer = pointer
    }

    deinit {
        bdmem_image_table_destroy(self.pointer)
    }

    /// The binary's name only: paths carry the app container and user directories.
    static func binaryName(_ path: String) -> String {
        path.split(separator: "/").last.map(String.init) ?? path
    }
}
