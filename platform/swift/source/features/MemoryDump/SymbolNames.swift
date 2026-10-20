// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Readable type names for the heap census, using only public API.
enum SymbolNames {
    /// Demangles a Swift class name by reflecting its class.
    ///
    /// - parameter rawName:      The name the ObjC runtime reports.
    /// - parameter classPointer: A class pointer validated by the census. An unvalidated pointer
    ///                           crashes.
    /// - parameter demangle:     When false, the name is returned as reported.
    ///
    /// - returns: The readable name, or `rawName` when it is not a mangled Swift class.
    static func swiftClassName(_ rawName: String, classPointer: UInt, demangle: Bool) -> String {
        guard demangle, classPointer != 0, Self.isMangledSwiftClass(rawName) else {
            return rawName
        }
        let readable = String(reflecting: unsafeBitCast(classPointer, to: AnyClass.self))
        return readable.isEmpty ? rawName : Self.removingContextAddresses(readable)
    }

    /// Demangles an Itanium C++ type name.
    ///
    /// - parameter rawName:  The mangled RTTI name.
    /// - parameter demangle: When false, the name is returned as reported.
    ///
    /// - returns: The readable name, or `rawName` when it cannot be demangled.
    static func cxxTypeName(_ rawName: String, demangle: Bool) -> String {
        guard demangle, let cxaDemangle = Self.cxaDemangle else {
            return rawName
        }
        var status: Int32 = 0
        return rawName.withCString { mangled in
            guard let demangled = cxaDemangle(mangled, nil, nil, &status) else {
                return rawName
            }
            defer { free(demangled) }
            return status == 0 ? String(cString: demangled) : rawName
        }
    }

    // `_Tt` is the mangling of ObjC-visible Swift entities, `$s` the stable one. Only classes (`C`)
    // and generic classes (`GC`) can be reflected.
    private static func isMangledSwiftClass(_ name: String) -> Bool {
        if name.hasPrefix("$s") {
            return true
        }
        guard name.hasPrefix("_Tt") else {
            return false
        }
        let kind = name.dropFirst(3)
        return kind.hasPrefix("C") || kind.hasPrefix("GC")
    }

    // The runtime names types declared in a private or anonymous context with an address, as in
    // `SwiftUI.(unknown context at $1d44c9bfc).Box`. The address identifies nothing off device.
    private static func removingContextAddresses(_ name: String) -> String {
        name.replacingOccurrences(
            of: #"\(unknown context at \$[0-9a-fA-F]+\)\."#,
            with: "",
            options: .regularExpression
        )
    }

    private typealias CxaDemangle = @convention(c) (
        UnsafePointer<CChar>?,
        UnsafeMutablePointer<CChar>?,
        UnsafeMutablePointer<Int>?,
        UnsafeMutablePointer<Int32>?
    ) -> UnsafeMutablePointer<CChar>?

    private static let cxaDemangle: CxaDemangle? = {
        guard let symbol = dlsym(UnsafeMutableRawPointer(bitPattern: -2), "__cxa_demangle") else {
            return nil
        }
        return unsafeBitCast(symbol, to: CxaDemangle.self)
    }()
}
