# New Kotlin-Idiomatic Test API

This directory contains the new idiomatic Kotlin test API for integration testing with the Capture SDK test server.

## Overview

The new API provides:

- ✅ **Automatic resource management** - No manual cleanup needed
- ✅ **Kotlin coroutines** - Natural async/await with `suspend` functions
- ✅ **Type safety** - Value classes prevent mixing up integers
- ✅ **Fluent DSL** - Readable, chainable operations
- ✅ **Better assertions** - Clear error messages and helpers
- ✅ **Composable** - Easy to build complex test scenarios

## Quick Comparison

### Old API (Verbose, Error-Prone)

```kotlin
class MyTest {
    private var testServerPort: Int? = null
    
    @Before
    fun setUp() {
        testServerPort = CaptureTestJniLibrary.startTestApiServer(-1)
    }
    
    @After
    fun teardown() {
        if (testServerPort != null) {
            CaptureTestJniLibrary.stopTestApiServer()
            testServerPort = null
        }
    }
    
    @Test
    fun test() {
        val streamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(streamId).isNotEqualTo(-1)
        
        CaptureTestJniLibrary.configureAggressiveContinuousUploads(streamId)
        
        logger.log(LogLevel.DEBUG) { "test" }
        
        val log = CaptureTestJniLibrary.nextUploadedLog()
        assertThat(log.level).isEqualTo(LogLevel.DEBUG.value)
        assertThat(log.message).isEqualTo("test")
    }
}
```

**Problems:**
- ❌ Manual cleanup (easy to forget)
- ❌ Nullable state
- ❌ Magic numbers (`-1` for errors)
- ❌ Blocking calls
- ❌ Verbose assertions

### New API (Clean, Type-Safe)

```kotlin
class MyTest {
    @Test
    fun test() = runTest {
        TestApiServer.start { server ->
            logger = buildLogger(apiBaseURL = server.url)
            
            server.withStream { stream ->
                stream.configureAggressiveUploads()
                
                logger.log(LogLevel.DEBUG) { "test" }
                
                val log = server.collectLogsUntil { it.message == "test" }.last()
                log.assertLevel(LogLevel.DEBUG)
                log.assertMessage("test")
            }
        } // Server automatically stopped
    }
}
```

**Benefits:**
- ✅ Automatic cleanup
- ✅ No nullable state
- ✅ Type-safe stream IDs
- ✅ Coroutine-based
- ✅ Clean assertions

## Core Components

### 1. TestApiServer

Main entry point for test server management.

```kotlin
TestApiServer.start { server ->
    // Server is running, automatically cleaned up when block exits
    val stream = server.awaitNextStream()
    val logs = server.collectLogs(5)
}
```

**Key Methods:**
- `awaitNextStream()` - Wait for SDK to connect
- `nextUploadedLog()` - Get next log from SDK
- `collectLogs(count)` - Collect N logs
- `collectLogsUntil(predicate)` - Collect until condition met
- `withStream { }` - DSL for stream operations

### 2. TestApiStream

Represents an active connection from SDK to test server.

```kotlin
stream.configureAggressiveUploads()
      .sendConfigurationUpdate()
      .awaitConfigurationAck()
```

**Key Methods:**
- `configureAggressiveUploads()` - Enable fast log uploads
- `sendConfigurationUpdate()` - Push config to SDK
- `awaitConfigurationAck()` - Wait for config acknowledgment
- `awaitHandshake()` - Wait for connection handshake
- `awaitClosed()` - Wait for stream to close
- `disableFeature(feature)` - Disable runtime feature

### 3. Type-Safe Values

Prevent mixing up integers:

```kotlin
val port: ServerPort = ServerPort(8080)  // Type-safe port
val streamId: StreamId = StreamId(1)     // Type-safe stream ID

// Compile error: Type mismatch
// val badId: StreamId = port  ❌
```

### 4. UploadedLog Extensions

Helper methods for cleaner assertions:

```kotlin
log.assertLevel(LogLevel.DEBUG)
log.assertMessage("expected message")
log.assertMatches(
    level = LogLevel.INFO,
    message = "test",
    requiredFields = setOf("foo", "bar")
)

// Get field values easily
val foo: String? = log.getStringField("foo")
val hasBar: Boolean = log.hasFields("bar", "baz")
```

## Usage Patterns

### Basic Test

```kotlin
@Test
fun simple_test() = runTest {
    TestApiServer.start { server ->
        logger = buildLogger(apiBaseURL = server.url)
        
        server.withStream { stream ->
            stream.configureAggressiveUploads()
            
            logger.log(LogLevel.INFO) { "hello world" }
            
            val log = server.nextUploadedLog()
            log.assertMessage("hello world")
        }
    }
}
```

### Collecting Multiple Logs

```kotlin
@Test
fun collect_logs() = runTest {
    TestApiServer.start { server ->
        logger = buildLogger(apiBaseURL = server.url)
        
        server.withStream { stream ->
            stream.configureAggressiveUploads()
            
            logger.log(LogLevel.INFO) { "log 1" }
            logger.log(LogLevel.INFO) { "log 2" }
            logger.log(LogLevel.INFO) { "log 3" }
            
            // Collect specific count
            val logs = server.collectLogs(3)
            assertThat(logs).hasSize(3)
            
            // Or collect until condition
            val logsUntil = server.collectLogsUntil { it.message == "log 3" }
            assertThat(logsUntil.last().message).isEqualTo("log 3")
        }
    }
}
```

### Multiple Streams

```kotlin
@Test
fun multiple_streams() = runTest {
    TestApiServer.start { server ->
        logger = buildLogger(apiBaseURL = server.url)
        
        // First stream
        server.withStream { stream1 ->
            stream1.configureAggressiveUploads()
            logger.log(LogLevel.INFO) { "from stream 1" }
            // ... test code
        }
        
        // Second stream (after reconnect)
        server.withStream { stream2 ->
            stream2.configureAggressiveUploads()
            logger.log(LogLevel.INFO) { "from stream 2" }
            // ... test code
        }
    }
}
```

### Advanced: Custom Configuration

```kotlin
@Test
fun custom_config() = runTest {
    TestApiServer.start(
        configure = {
            pingInterval = 100.milliseconds
        }
    ) { server ->
        // Server with custom ping interval
        // ...
    }
}
```

### Testing Stream Lifecycle

```kotlin
@Test
fun stream_lifecycle() = runTest {
    TestApiServer.start { server ->
        logger = buildLogger(apiBaseURL = server.url)
        
        val stream = server.awaitNextStream()
        stream.assertHandshakeReceived()
        
        // Trigger stream close (e.g., via idle timeout)
        // ...
        
        stream.assertClosed(timeout = 5.seconds)
        
        // Verify reconnection
        val stream2 = server.awaitNextStream()
        stream2.assertHandshakeReceived()
    }
}
```

## Migration Guide

### Step 1: Add `runTest` to Test Function

```kotlin
// Before
@Test
fun myTest() {
    // ...
}

// After
@Test
fun myTest() = runTest {
    // ...
}
```

### Step 2: Wrap Test in `TestApiServer.start`

```kotlin
// Before
@Before
fun setUp() {
    testServerPort = CaptureTestJniLibrary.startTestApiServer(-1)
}

@After
fun teardown() {
    if (testServerPort != null) {
        CaptureTestJniLibrary.stopTestApiServer()
    }
}

@Test
fun myTest() {
    // test code
}

// After
@Test
fun myTest() = runTest {
    TestApiServer.start { server ->
        // test code
    }
}
```

### Step 3: Replace Stream ID with Stream Object

```kotlin
// Before
val streamId = CaptureTestJniLibrary.awaitNextApiStream()
assertThat(streamId).isNotEqualTo(-1)
CaptureTestJniLibrary.configureAggressiveContinuousUploads(streamId)

// After
server.withStream { stream ->
    stream.configureAggressiveUploads()
}
```

### Step 4: Replace Log Collection

```kotlin
// Before
val log = CaptureTestJniLibrary.nextUploadedLog()
assertThat(log.level).isEqualTo(LogLevel.DEBUG.value)
assertThat(log.message).isEqualTo("test")

// After
val log = server.nextUploadedLog()
log.assertLevel(LogLevel.DEBUG)
log.assertMessage("test")
```

## Examples

See `CaptureLoggerTestNew.kt` for complete working examples demonstrating:
- Basic end-to-end test
- Global fields test
- Multiple streams test
- Complex log collection patterns

## API Reference

### TestApiServer

| Method | Description |
|--------|-------------|
| `start(config, block)` | Start server with automatic cleanup |
| `awaitNextStream()` | Wait for next SDK connection |
| `nextUploadedLog()` | Get next uploaded log |
| `collectLogs(count)` | Collect N logs |
| `collectLogsUntil(predicate)` | Collect until condition |
| `withStream(block)` | Execute block with stream |

### TestApiStream

| Method | Description |
|--------|-------------|
| `configureAggressiveUploads()` | Enable fast uploads |
| `sendConfigurationUpdate()` | Push config update |
| `awaitConfigurationAck()` | Wait for config ack |
| `assertHandshakeReceived()` | Assert handshake success |
| `assertClosed()` | Assert stream closed |
| `disableFeature(feature)` | Disable runtime feature |

### UploadedLog Extensions

| Method | Description |
|--------|-------------|
| `assertLevel(level)` | Assert log level |
| `assertMessage(message)` | Assert log message |
| `assertMatches(...)` | Assert multiple properties |
| `getStringField(key)` | Get string field value |
| `hasFields(...keys)` | Check field presence |

## Benefits Summary

### For Developers
- ✅ Less boilerplate code
- ✅ Fewer bugs (automatic cleanup)
- ✅ Better IDE support (type safety)
- ✅ Easier to read and maintain

### For Tests
- ✅ More reliable (guaranteed cleanup)
- ✅ Better error messages
- ✅ Faster to write
- ✅ More composable patterns

### For Codebase
- ✅ Consistent patterns across tests
- ✅ Modern Kotlin idioms
- ✅ Future-proof (coroutines standard)
- ✅ Aligns with industry best practices
