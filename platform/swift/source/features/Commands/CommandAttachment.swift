// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

@_spi(ExperimentalAPI) public struct CommandAttachment: Equatable, Sendable {
    /// The in-memory payload, or empty data when `fileURL` is present.
    public let data: Data
    public let mimeType: String
    public let fileURL: URL?

    public init(data: Data, mimeType: String) {
        self.data = data
        self.mimeType = mimeType
        self.fileURL = nil
    }

    /// Creates a file-backed attachment without reading it. Returning the attachment transfers
    /// ownership of this dedicated local file to the SDK. Do not modify or reuse its path.
    ///
    /// - parameter url:      The dedicated local file to transfer when the attachment is returned.
    /// - parameter mimeType: The MIME type of the file contents.
    ///
    /// - returns: An attachment whose data accessor is empty and whose source is the file URL.
    public static func file(url: URL, mimeType: String) -> CommandAttachment {
        CommandAttachment(fileURL: url, mimeType: mimeType)
    }

    private init(fileURL: URL, mimeType: String) {
        self.data = Data()
        self.mimeType = mimeType
        self.fileURL = fileURL.isFileURL ? fileURL.standardizedFileURL : fileURL
    }

    func discardOwnedFile(using fileManager: FileManager = .default) {
        guard let fileURL, fileURL.isFileURL,
              (try? fileURL.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true
        else { return }
        try? fileManager.removeItem(at: fileURL)
    }
}
