require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "BdReactNativeWebView"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]
  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :http => "https://registry.npmjs.org/@bitdrift/react-native-webview/-/react-native-webview-#{s.version}.tgz" }
  s.source_files = "ios/**/*.{h,m,mm}"

  # This package depends heavily on BdReactNative public APIs so both JS packages are released with the same version.
  s.dependency "BdReactNative", ">= #{package["version"]}"
  s.dependency "react-native-webview", ">= 13.0.0"

  if respond_to?(:install_modules_dependencies, true)
    install_modules_dependencies(s)
  else
    s.dependency "React-Core"
  end
end
