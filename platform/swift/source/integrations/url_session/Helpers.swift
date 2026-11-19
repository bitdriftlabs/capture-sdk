// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

func exchangeInstanceMethod(class klass: AnyClass, selector: Selector, with toSelector: Selector) {
    guard let method = class_getInstanceMethod(klass, selector) else {
        return kLogger.log(
            level: .error,
            message: "method replacing field: failed to find \(selector) in \(klass) class"
        )
    }

    guard let toMethod = class_getInstanceMethod(klass, toSelector) else {
        return kLogger.log(
            level: .error,
            message: "method replacing field: failed to find \(toSelector) in \(klass) class"
        )
    }

    method_exchangeImplementations(method, toMethod)
}

func exchangeClassMethod(class klass: AnyClass, selector: Selector, with toSelector: Selector) {
    guard let method = class_getClassMethod(klass, selector) else {
        return
    }

    guard let toMethod = class_getClassMethod(klass, toSelector) else {
        return
    }

    method_exchangeImplementations(method, toMethod)
}
