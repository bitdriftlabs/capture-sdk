# Test API Improvement Demo

## What Changed?

We've created a new **Kotlin-idiomatic test API** that makes integration tests cleaner, safer, and more maintainable.

## Quick Demo: Before vs After

### ❌ Old Way (Verbose, Manual Cleanup)

```kotlin
class CaptureLoggerTest {
    private lateinit var logger: LoggerImpl
    private var testServerPort: Int? = null  // Easy to forget cleanup!
    
    @Before
    fun setUp() {
        CaptureJniLibrary.load()
        testServerPort = CaptureTestJniLibrary.startTestApiServer(-1)
        logger = buildLogger()
    }
    
    @After
    fun teardown() {
        if (testServerPort != null) {  // Manual null check
            CaptureTestJniLibrary.stopTestApiServer()
            testServerPort = null
        }
    }
    
    @Test
    fun test_logger_end_to_end() {
        // Magic number check for errors
        val streamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(streamId).isNotEqualTo(-1)
        
        // Long method names
        CaptureTestJniLibrary.configureAggressiveContinuousUploads(streamId)
        
        logger.log(LogLevel.DEBUG) { "test log" }
        
        // Skip SDK internal logs manually
        val sdkConfigured = CaptureTestJniLibrary.nextUploadedLog()
        assertThat(sdkConfigured.message).isEqualTo("SDKConfigured")
        
        var log = CaptureTestJniLibrary.nextUploadedLog()
        if (log.fields.containsKey("_battery_val")) {
            log = CaptureTestJniLibrary.nextUploadedLog()
        }
        if (log.fields.containsKey("_is_memory_low")) {
            log = CaptureTestJniLibrary.nextUploadedLog()
        }
        
        // Verbose assertions
        assertThat(log.level).isEqualTo(LogLevel.DEBUG.value)
        assertThat(log.message).isEqualTo("test log")
    }
}
```

**Problems:**
- 😢 Manual cleanup code in `@After`
- 😢 Nullable state (`testServerPort: Int?`)
- 😢 Magic numbers (`-1`, `streamId`)
- 😢 Manual log skipping logic
- 😢 Verbose assertions
- 😢 Blocking calls (no async)

### ✅ New Way (Clean, Type-Safe, Auto-Cleanup)

```kotlin
class CaptureLoggerTestNew {
    @Test
    fun test_logger_end_to_end_new_api() = runTest {
        // Server automatically started AND stopped!
        TestApiServer.start { server ->
            logger = buildLogger(apiBaseURL = server.url)
            
            // Clean DSL for stream operations
            server.withStream { stream ->
                stream.configureAggressiveUploads()
                
                logger.log(LogLevel.DEBUG) { "test log" }
                
                // Automatically skips SDK internal logs!
                val logs = server.collectLogsUntil { it.message == "test log" }
                val testLog = logs.last()
                
                // Clean assertions
                testLog.assertLevel(LogLevel.DEBUG)
                testLog.assertMessage("test log")
            }
        } // Server automatically stopped here!
    }
}
```

**Benefits:**
- 😍 Automatic cleanup (no `@Before`/`@After`)
- 😍 Type-safe stream IDs
- 😍 Smart log collection
- 😍 Clean assertions
- 😍 Kotlin coroutines
- 😍 Fluent DSL

## Key Features

### 1. Automatic Resource Management

```kotlin
TestApiServer.start { server ->
    // Server running
    // Do your test
} // Server automatically stopped, even if exception thrown!
```

No more forgotten `stopTestApiServer()` calls!

### 2. Type Safety

```kotlin
// Old: Just an Int, could mix up with other integers
val streamId: Int = CaptureTestJniLibrary.awaitNextApiStream()

// New: Type-safe value class
val stream: TestApiStream = server.awaitNextStream()
stream.configureAggressiveUploads()  // Clear what this operates on!
```

### 3. Fluent DSL

```kotlin
// Old: Imperative, verbose
val streamId = CaptureTestJniLibrary.awaitNextApiStream()
CaptureTestJniLibrary.configureAggressiveContinuousUploads(streamId)
CaptureTestJniLibrary.sendConfigurationUpdate(streamId)
val success = CaptureTestJniLibrary.awaitConfigurationAck(streamId, 5000)
check(success) { "Config ack timed out" }

// New: Fluent, chainable
server.withStream { stream ->
    stream.configureAggressiveUploads()
          .sendConfigurationUpdate()
          .assertConfigurationAcked()
}
```

### 4. Smart Log Collection

```kotlin
// Collect until condition met
val logs = server.collectLogsUntil { log ->
    log.message == "test log"
}

// Collect specific count
val firstFive = server.collectLogs(5)

// Get next one
val next = server.nextUploadedLog()
```

### 5. Better Assertions

```kotlin
// Old: Verbose
assertThat(log.level).isEqualTo(LogLevel.DEBUG.value)
assertThat(log.message).isEqualTo("test")
assertThat(log.fields.containsKey("foo")).isTrue()

// New: Concise
log.assertLevel(LogLevel.DEBUG)
log.assertMessage("test")
assertThat(log.hasFields("foo")).isTrue()

// Or all at once
log.assertMatches(
    level = LogLevel.DEBUG,
    message = "test",
    requiredFields = setOf("foo", "bar")
)
```

### 6. Kotlin Coroutines

```kotlin
@Test
fun my_test() = runTest {  // Coroutine test scope
    TestApiServer.start { server ->
        // Suspend functions work naturally
        val stream = server.awaitNextStream()
        stream.assertHandshakeReceived()
        
        // Can use delay, async, etc.
        delay(100.milliseconds)
    }
}
```

## File Structure

```
platform/jvm/capture/src/test/kotlin/io/bitdrift/capture/testapi/
├── README.md                  # Comprehensive documentation
├── TypeSafeValues.kt         # ServerPort, StreamId, ServerConfig
├── ResultTypes.kt            # HandshakeResult, StreamCloseResult, etc.
├── TestApiStream.kt          # Stream-scoped operations
└── TestApiServer.kt          # Main server API + UploadedLog extensions
```

## Migration Path

We're not breaking anything! The old API still works:

1. **Phase 1**: New tests use new API
2. **Phase 2**: Gradually migrate existing tests
3. **Phase 3**: Eventually deprecate old API

## Examples

See `CaptureLoggerTestNew.kt` for:
- ✅ Basic end-to-end test
- ✅ Global fields test
- ✅ Multiple streams test
- ✅ All patterns in action

## Why This Matters

### Developer Experience
- ⏱️ **Faster to write tests** - Less boilerplate
- 🐛 **Fewer bugs** - Automatic cleanup prevents leaks
- 📖 **Easier to read** - Clear intent
- 🔧 **Easier to maintain** - Consistent patterns

### Code Quality
- 🎯 **Type safety** - Compile-time checks
- 🧪 **More reliable** - No forgotten cleanup
- 🔄 **Composable** - Easy to build complex scenarios
- 📚 **Self-documenting** - DSL shows intent

### Alignment
- ✅ Matches modern Kotlin conventions
- ✅ Similar to iOS async/await pattern
- ✅ Consistent with industry best practices
- ✅ Future-proof (coroutines are standard)

## Try It Out!

1. Look at `testapi/README.md` for full documentation
2. Check `CaptureLoggerTestNew.kt` for examples
3. Try converting one of your tests!

## Questions?

The new API is designed to be intuitive, but if you have questions:
- Check the README for examples
- Look at the converted test
- The code has comprehensive KDoc comments

Happy testing! 🎉
