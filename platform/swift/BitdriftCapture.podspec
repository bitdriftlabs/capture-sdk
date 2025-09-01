Pod::Spec.new do |s|
    s.name = 'BitdriftCapture'
    s.summary = "A dynamic observability SDK for mobile developers."
    s.version = '0.18.6'

    s.homepage = 'https://bitdrift.io'
    s.license = {
      :type => "BITDRIFT SOFTWARE DEVELOPMENT KIT LICENSE AGREEMENT",
      :file => "LICENSE.txt"
    }

    s.authors = {
      'Bitdrift, Inc.' => 'info@bitdrift.io',
      'Rafal Augustyniak' => 'rafal@bitdrift.io',
      'Miguel Angel Juarez Lopez' => 'miguel@bitdrift.io',
    }

    s.documentation_url = 'https://docs.bitdrift.io'
    s.social_media_url = 'https://twitter.com/bitdriftio'

    s.module_name = 'Capture'

    s.platform = :ios, '15.0'
    s.swift_versions = ['6.0.0']
    s.frameworks = [
      'Network',
      'UIKit',
      'CoreTelephony'
    ]

    s.source = { http: "https://dl.bitdrift.io/sdk/ios/capture-0.18.6/Capture.zip" }
    s.preserve_paths = ['NOTICE.txt']
    s.vendored_frameworks = 'Capture.xcframework'
  end
