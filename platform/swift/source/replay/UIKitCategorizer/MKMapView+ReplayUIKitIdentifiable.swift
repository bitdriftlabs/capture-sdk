// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import MapKit

// This works for both MapKit from UIKit and Map from SwiftUI as the latter uses
// `_MapKit_SwiftUI._SwiftUIMKMapView` class which inherits from `MKMapView`.
extension MKMapView: ReplayUIKitIdentifiable {
    final func identifyUIKit(frame: CGRect) -> AnnotatedView? {
        return AnnotatedView(.map, recurse: false, frame: frame)
    }
}
