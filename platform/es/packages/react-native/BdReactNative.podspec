require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))
folly_compiler_flags = '-DFOLLY_NO_CONFIG -DFOLLY_MOBILE=1 -DFOLLY_USE_LIBCPP=1 -Wno-comma -Wno-shorten-64-to-32'
capture_version = '0.52.0'
capture_manifest = File.join(__dir__, 'ios/vendor/capture.json')
unless File.file?(capture_manifest)
  raise 'Bundled Capture.xcframework is missing. Prepare the React Native release package before installing pods.'
end
unless JSON.parse(File.read(capture_manifest))['version'] == capture_version
  raise 'Bundled Capture.xcframework does not match the required Capture SDK version.'
end


Pod::Spec.new do |s|
  s.name         = "BdReactNative"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => [min_ios_version_supported, '15.0'].max_by { |v| Gem::Version.new(v) } }
  s.source       = { :http => "https://registry.npmjs.org/@bitdrift/react-native/-/react-native-#{s.version}.tgz" }

  s.source_files = "ios/*.{h,m,mm,swift}"
  s.vendored_frameworks = "ios/vendor/Capture.xcframework"
  s.preserve_paths = ["ios/vendor/LICENSE", "ios/vendor/capture.json"]
  s.frameworks = ['Network', 'UIKit', 'CoreTelephony']
  s.libraries = ['c++']

  # Use install_modules_dependencies helper to install the dependencies if React Native version >=0.71.0.
  # See https://github.com/facebook/react-native/blob/febf6b7f33fdb4904669f99d795eba4c0f95d7bf/scripts/cocoapods/new_architecture.rb#L79.
  if respond_to?(:install_modules_dependencies, true)
    install_modules_dependencies(s)
  else
  s.dependency "React-Core"

  # Don't install the dependencies when we run `pod install` in the old architecture.
  if ENV['RCT_NEW_ARCH_ENABLED'] == '1' then
    s.compiler_flags = folly_compiler_flags + " -DRCT_NEW_ARCH_ENABLED=1"
    s.pod_target_xcconfig    = {
        "HEADER_SEARCH_PATHS" => "\"$(PODS_ROOT)/boost\"",
        "OTHER_CPLUSPLUSFLAGS" => "-DFOLLY_NO_CONFIG -DFOLLY_MOBILE=1 -DFOLLY_USE_LIBCPP=1",
        "CLANG_CXX_LANGUAGE_STANDARD" => "c++17"
    }
    s.dependency "React-Codegen"
    s.dependency "RCT-Folly"
    s.dependency "RCTRequired"
    s.dependency "RCTTypeSafety"
    s.dependency "ReactCommon/turbomodule/core"
   end
  end
end
