# Copilot review instructions

## FFI and ABI boundary

Treat all Swift C bridge and Android JNI changes as release-blocking ABI work. For every changed
entry point, verify that the Rust export exactly matches its declaration and caller:

- C ABI: `extern "C"` parameter count, order, nullability, and types must match
  `CaptureRustBridge.h` and Swift call sites.
- JNI: the Rust export parameters must match the Kotlin `external` declaration and its callers.
  Never add unused trailing Rust parameters; they still change the ABI.
- Swift Foundation values crossing the C header must use the corresponding Objective-C object type
  (for example, nullable `NSString *` for `String?`), not `const void *`.
- Public Swift/Objective-C/Kotlin/Java APIs must retain existing overloads and binary symbols when
  adding an argument. Default arguments alone do not preserve JVM or Swift library-evolution ABI.

Flag any mismatch immediately, even if the parameter is currently unused. Verify relevant Swift
and Android bridge builds/tests after the implementation is complete.
