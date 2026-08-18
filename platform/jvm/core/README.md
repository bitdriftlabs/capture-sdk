# Host-JVM JNI tests

`//platform/jvm/core:capture_core_host_jvm_jni_test` runs Rust unit tests against Bazel's selected
desktop JDK.
It starts that JDK through `jni::JavaVM::new`, enables HotSpot's `-Xcheck:jni`, and fails when
HotSpot reports a JNI diagnostic. The wrapper sets `JAVA_HOME` to Bazel's JDK before the JVM starts.
Run it with:

```bash
./bazelw test //platform/jvm/core:capture_core_host_jvm_jni_test --test_output=streamed
```

## Suitable coverage

Use these tests for Rust JNI utilities whose inputs and outputs can be built from standard Java
types, such as `java.lang.String`, primitive/object arrays, and `java.util` collections. They are
particularly useful for conversion code, local/global reference lifetime handling, pending
exception cleanup, method signatures, and JavaVM thread attachment.

Use the shared `crate::test_jvm::with_env()` helper rather than creating another VM. It provides
one process-wide JVM and ensures every test runs with the same JNI checking configuration.

## Android-only coverage

Keep tests on an Android emulator when they require Android framework classes, Kotlin application
classes, JNI exports loaded through `System.loadLibrary`, Android class loading, lifecycle or
threading behavior, ABI packaging, or ART-specific behavior. Passing the host-JVM tests is useful
evidence of generic JNI correctness, but it is not a substitute for ART CheckJNI coverage.
