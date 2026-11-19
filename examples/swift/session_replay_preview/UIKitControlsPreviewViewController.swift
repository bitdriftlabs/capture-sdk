// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import MapKit
import UIKit

// Used to showcase UIKit controls and standard views.
final class UIKitControlsPreviewViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()

        self.edgesForExtendedLayout = UIRectEdge()

        let label = UILabel()
        label.text = "'label' with some text"

        let textField = UITextField()
        textField.text = "'UITextField' with some text"

        let textView = UITextView()
        textView.text = "'UITextView' with some text"

        let button = UIButton(type: .roundedRect)
        button.setTitle("'Button'", for: .normal)
        button.backgroundColor = .lightGray
        button.translatesAutoresizingMaskIntoConstraints = false

        let buttonContainerView = UIView()
        buttonContainerView.addSubview(button)
        button.centerXAnchor.constraint(equalTo: buttonContainerView.centerXAnchor).isActive = true
        button.centerYAnchor.constraint(equalTo: buttonContainerView.centerYAnchor).isActive = true
        buttonContainerView.heightAnchor.constraint(equalToConstant: 44).isActive = true

        let imageView = UIImageView(image: UIImage(systemName: "star.fill"))
        imageView.translatesAutoresizingMaskIntoConstraints = false

        let imageContainerView = UIView()
        imageContainerView.addSubview(imageView)

        imageView.centerXAnchor.constraint(equalTo: imageContainerView.centerXAnchor).isActive = true
        imageView.centerYAnchor.constraint(equalTo: imageContainerView.centerYAnchor).isActive = true
        imageContainerView.heightAnchor.constraint(equalToConstant: 44).isActive = true

        let stackView = UIStackView(
            arrangedSubviews: [
                label,
                textField,
                textView,
                imageContainerView,
                buttonContainerView,
            ]
        )
        stackView.axis = .vertical
        stackView.alignment = .fill
        stackView.translatesAutoresizingMaskIntoConstraints = false

        let mapView = MKMapView()
        mapView.translatesAutoresizingMaskIntoConstraints = false

        self.view.backgroundColor = .white
        self.view.addSubview(stackView)
        self.view.addSubview(mapView)

        stackView.topAnchor.constraint(equalTo: self.view.topAnchor).isActive = true
        stackView.leadingAnchor.constraint(equalTo: self.view.leadingAnchor).isActive = true
        stackView.trailingAnchor.constraint(equalTo: self.view.trailingAnchor).isActive = true

        stackView.bottomAnchor.constraint(equalTo: mapView.topAnchor).isActive = true

        mapView.leadingAnchor.constraint(equalTo: self.view.leadingAnchor).isActive = true
        mapView.trailingAnchor.constraint(equalTo: self.view.trailingAnchor).isActive = true
        mapView.bottomAnchor.constraint(equalTo: self.view.bottomAnchor).isActive = true
        mapView.heightAnchor.constraint(equalToConstant: 200).isActive = true
    }
}
