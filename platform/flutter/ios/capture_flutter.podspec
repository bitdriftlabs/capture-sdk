Pod::Spec.new do |s|
  s.name             = 'capture_flutter'
  s.version          = '0.22.16'
  s.summary          = 'Flutter plugin for the Bitdrift Capture SDK'
  s.description      = 'Official Flutter plugin for the Bitdrift Capture SDK, providing logging, session management, and session replay.'
  s.homepage         = 'https://bitdrift.io'
  s.license          = { :type => 'MIT' }
  s.author           = { 'Bitdrift' => 'support@bitdrift.io' }
  s.source           = { :path => '.' }
  s.source_files     = 'Classes/**/*'
  s.dependency 'Flutter'
  s.dependency 'BitdriftCapture', '~> 0.22'
  s.static_framework = true
  s.platform         = :ios, '15.0'
  s.swift_version    = '5.9'
end
