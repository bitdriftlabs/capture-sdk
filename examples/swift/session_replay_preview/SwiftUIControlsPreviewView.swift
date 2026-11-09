// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import MapKit
import SwiftUI

// Used to showcase SwiftUI elements.
struct SwiftUIControlsPreviewView: View {
    @State var isToggled = false
    @State var text = "'TextEditor' element"
    @State var selectedText = "Value 1"
    @State var selectedDate = Date()

    init() {}

    var body: some View {
        VStack {
            Spacer()
            VStack {
                Text("'Text' element")
                    .background(Color.yellow)
                Label("'Label' element", systemImage: "heart.fill")
                    .background(Color.yellow)
                TextField("'TextField' element", text: self.$text)
                    .background(Color.yellow)
                TextEditor(text: self.$text)
                Image(systemName: "star.fill")
                Button("Bordered 'Button' element") {}
                    .buttonStyle(.bordered)
                Button("Bordered 'Button' element with Background") {}
                    .background(Color.yellow)
                    .buttonStyle(.bordered)
                // swiftlint:disable:next use_static_string_url_init force_unwrapping
                Link("'Link' element", destination: URL(string: "https://bitdrift.io")!)
                    .background(Color.yellow)
                Toggle(isOn: self.$isToggled) {
                    Text("Hello World").background(Color.yellow)
                }
                Picker(
                    selection: self.$selectedText,
                    label: Text("'Picker' element"),
                    content: {
                        Text("Value 1").tag(0)
                        Text("Value 2").tag(1)
                    }
                )
                DatePicker(
                    selection: self.$selectedDate,
                    in: Date.distantPast...Date.distantFuture,
                    displayedComponents: .date,
                    label: { Text("Due Date") }
                )
                if #available(iOS 17, *) {
                    Map()
                }
            }
        }
    }
}

struct SessionReplayPlaygroundView_Previews: PreviewProvider {
    static var previews: some View {
        SwiftUIControlsPreviewView()
    }
}
