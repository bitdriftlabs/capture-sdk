# Copilot review instructions

For any Swift C bridge or Android JNI change, apply the FFI and ABI safety requirements in the
repository's `AGENTS.md`. Flag a mismatch between an export, its declaration, and its caller as
release-blocking, even when the affected parameter is unused.
