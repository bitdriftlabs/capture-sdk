// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import HelloWorldCrashSupport

enum CrashCategory: String, CaseIterable {
    case swiftRuntime = "Swift Runtime"
    case signal = "Signal"
    case thread = "Thread"
    case exception = "Exception"
    case memory = "Memory"

    var subtitle: String {
        switch self {
        case .swiftRuntime:
            return "Swift traps and runtime failures."
        case .signal:
            return "Signals and low-level process termination paths."
        case .thread:
            return "Threading failures and watchdog-like hangs."
        case .exception:
            return "Objective-C and C++ exception scenarios."
        case .memory:
            return "Memory exhaustion and allocator corruption."
        }
    }
}

protocol Crash: AnyObject {
    var category: CrashCategory { get }
    var title: String { get }
    var crashDescription: String { get }
    func trigger() -> Never
}

final class CrashRegistry {
    static let shared = CrashRegistry()

    private let crashes: [any Crash] = [
        ForceUnwrapCrash(),
        ArrayOutOfBoundsCrash(),
        StackOverflowCrash(),
        FatalErrorCrash(),
        AssertionCrash(),
        PreconditionCrash(),
        IntegerOverflowCrash(),
        DivisionByZeroCrash(),
        AbortCrash(),
        NullPointerCrash(),
        SIGSEGVCrash(),
        SIGBUSCrash(),
        SIGILLCrash(),
        SIGFPECrash(),
        StackSmashCrash(),
        DeadlockCrash(),
        AsyncSafeThreadCrash(),
        ObjCExceptionCrash(),
        CXXExceptionCrash(),
        ObjCMsgSendCrash(),
        UnrecognizedSelectorCrash(),
        KVOCrash(),
        ReleasedObjectCrash(),
        CorruptMallocCrash(),
        OOMKillCrash(),
    ]

    func crashes(in category: CrashCategory) -> [any Crash] {
        self.crashes.filter { $0.category == category }
    }
}

final class ForceUnwrapCrash: Crash {
    let category: CrashCategory = .swiftRuntime
    let title = "Force unwrap nil"
    let crashDescription = "Force-unwrap an Optional<Int> that is nil. Swift traps with EXC_BAD_INSTRUCTION."

    func trigger() -> Never {
        let value: Int? = nil
        _ = value!
        fatalError("unreachable")
    }
}

final class ArrayOutOfBoundsCrash: Crash {
    let category: CrashCategory = .swiftRuntime
    let title = "Array out of bounds"
    let crashDescription = "Access index 10 on a 3-element array. Swift traps with EXC_BAD_INSTRUCTION."

    func trigger() -> Never {
        let array = [1, 2, 3]
        _ = array[10]
        fatalError("unreachable")
    }
}

final class StackOverflowCrash: Crash {
    let category: CrashCategory = .swiftRuntime
    let title = "Stack overflow"
    let crashDescription = "Infinite recursion exhausts the stack. Crashes with EXC_BAD_ACCESS (SIGSEGV) or EXC_BAD_INSTRUCTION."

    func trigger() -> Never {
        hello_world_crash_stack_overflow()
        fatalError("unreachable")
    }
}

final class FatalErrorCrash: Crash {
    let category: CrashCategory = .swiftRuntime
    let title = "fatalError()"
    let crashDescription = "Call fatalError() with a message. Produces EXC_BAD_INSTRUCTION with the message in the crash report."

    func trigger() -> Never {
        fatalError("Triggered by hello_world")
    }
}

final class AssertionCrash: Crash {
    let category: CrashCategory = .swiftRuntime
    let title = "Assertion failure"
    let crashDescription = "Call assertionFailure(). Only crashes in debug builds; in release, use preconditionFailure instead."

    func trigger() -> Never {
        assertionFailure("Triggered by hello_world")
        fatalError("unreachable")
    }
}

final class PreconditionCrash: Crash {
    let category: CrashCategory = .swiftRuntime
    let title = "Precondition failure"
    let crashDescription = "Call preconditionFailure(). Crashes in both debug and release builds."

    func trigger() -> Never {
        preconditionFailure("Triggered by hello_world")
    }
}

final class IntegerOverflowCrash: Crash {
    let category: CrashCategory = .swiftRuntime
    let title = "Integer overflow"
    let crashDescription = "Add 1 to Int.max via checked arithmetic. Swift traps with EXC_BAD_INSTRUCTION."

    func trigger() -> Never {
        var value = Int.max
        value = value &+ Int.random(in: 1...1)
        let (result, overflow) = value.addingReportingOverflow(1)
        if overflow {
            _ = value + 1
        }
        _ = result
        fatalError("unreachable")
    }
}

final class DivisionByZeroCrash: Crash {
    let category: CrashCategory = .swiftRuntime
    let title = "Division by zero"
    let crashDescription = "Divide an integer by zero. Swift traps with EXC_BAD_INSTRUCTION (not a floating-point exception)."

    func trigger() -> Never {
        let zero = Int.random(in: 0..<1)
        let result = 1 / zero
        _ = result
        fatalError("unreachable")
    }
}

final class AbortCrash: Crash {
    let category: CrashCategory = .signal
    let title = "Call abort()"
    let crashDescription = "Call abort() to send SIGABRT to the process."

    func trigger() -> Never {
        abort()
    }
}

final class NullPointerCrash: Crash {
    let category: CrashCategory = .signal
    let title = "Dereference NULL pointer"
    let crashDescription = "Write to address 0x0 via an UnsafeMutablePointer. Produces EXC_BAD_ACCESS (SIGSEGV)."

    func trigger() -> Never {
        let ptr = UnsafeMutablePointer<UInt>(bitPattern: 0)
        ptr!.pointee = 1
        fatalError("unreachable")
    }
}

final class SIGSEGVCrash: Crash {
    let category: CrashCategory = .signal
    let title = "Raise SIGSEGV"
    let crashDescription = "Explicitly raise SIGSEGV (segmentation fault) via raise(3)."

    func trigger() -> Never {
        raise(SIGSEGV)
        fatalError("unreachable")
    }
}

final class SIGBUSCrash: Crash {
    let category: CrashCategory = .signal
    let title = "Raise SIGBUS"
    let crashDescription = "Explicitly raise SIGBUS (bus error) via raise(3)."

    func trigger() -> Never {
        raise(SIGBUS)
        fatalError("unreachable")
    }
}

final class SIGILLCrash: Crash {
    let category: CrashCategory = .signal
    let title = "Raise SIGILL"
    let crashDescription = "Explicitly raise SIGILL (illegal instruction) via raise(3)."

    func trigger() -> Never {
        raise(SIGILL)
        fatalError("unreachable")
    }
}

final class SIGFPECrash: Crash {
    let category: CrashCategory = .signal
    let title = "Raise SIGFPE"
    let crashDescription = "Explicitly raise SIGFPE (floating-point exception) via raise(3)."

    func trigger() -> Never {
        raise(SIGFPE)
        fatalError("unreachable")
    }
}

final class StackSmashCrash: Crash {
    let category: CrashCategory = .signal
    let title = "Stack smash"
    let crashDescription = "Writes past a stack buffer to corrupt the stack canary. Produces SIGABRT with a low-level stack trace."

    func trigger() -> Never {
        hello_world_crash_stack_smash()
        fatalError("unreachable")
    }
}

final class DeadlockCrash: Crash {
    let category: CrashCategory = .thread
    let title = "Main thread deadlock"
    let crashDescription = "Dispatch sync onto the main queue from the main thread. Produces a watchdog timeout (0x8badf00d) in production."

    func trigger() -> Never {
        DispatchQueue.main.sync {}
        fatalError("unreachable")
    }
}

final class AsyncSafeThreadCrash: Crash {
    let category: CrashCategory = .thread
    let title = "Crash inside async-safe thread"
    let crashDescription = "Trigger a crash from a background thread to verify the reporter captures threads other than main."

    func trigger() -> Never {
        let semaphore = DispatchSemaphore(value: 0)
        DispatchQueue.global().async {
            let ptr = UnsafeMutablePointer<UInt>(bitPattern: 0)
            ptr!.pointee = 1
            semaphore.signal()
        }
        semaphore.wait()
        fatalError("unreachable")
    }
}

final class ObjCExceptionCrash: Crash {
    let category: CrashCategory = .exception
    let title = "Objective-C exception"
    let crashDescription = "Throw an uncaught NSException."

    func trigger() -> Never {
        hello_world_crash_objc_exception()
        fatalError("unreachable")
    }
}

final class CXXExceptionCrash: Crash {
    let category: CrashCategory = .exception
    let title = "C++ exception"
    let crashDescription = "Throw an uncaught C++ std::runtime_error."

    func trigger() -> Never {
        hello_world_crash_cxx_exception()
        fatalError("unreachable")
    }
}

final class ObjCMsgSendCrash: Crash {
    let category: CrashCategory = .exception
    let title = "objc_msgSend to deallocated object"
    let crashDescription = "Send a message to a deallocated Objective-C object to force EXC_BAD_ACCESS."

    func trigger() -> Never {
        hello_world_crash_objc_msg_send()
        fatalError("unreachable")
    }
}

final class UnrecognizedSelectorCrash: Crash {
    let category: CrashCategory = .exception
    let title = "Unrecognized selector"
    let crashDescription = "Send an Objective-C selector that the object does not implement."

    func trigger() -> Never {
        hello_world_crash_unrecognized_selector()
        fatalError("unreachable")
    }
}

final class KVOCrash: Crash {
    let category: CrashCategory = .exception
    let title = "KVO misuse"
    let crashDescription = "Remove an observer that was never registered to trigger an Objective-C exception."

    func trigger() -> Never {
        hello_world_crash_kvo()
        fatalError("unreachable")
    }
}

final class ReleasedObjectCrash: Crash {
    let category: CrashCategory = .exception
    let title = "Released object / corrupted isa"
    let crashDescription = "Corrupt an Objective-C object's isa pointer and then message it."

    func trigger() -> Never {
        hello_world_crash_released_object()
        fatalError("unreachable")
    }
}

final class CorruptMallocCrash: Crash {
    let category: CrashCategory = .exception
    let title = "Corrupt malloc metadata"
    let crashDescription = "Write outside an allocation to corrupt allocator metadata before freeing it."

    func trigger() -> Never {
        hello_world_crash_corrupt_malloc()
        fatalError("unreachable")
    }
}

final class OOMKillCrash: Crash {
    let category: CrashCategory = .memory
    let title = "OOM kill (jetsam)"
    let crashDescription = "Allocates 4 MB chunks in a loop until jetsam terminates the process. This usually appears in MetricKit or jetsam diagnostics, not a classic crash report."

    func trigger() -> Never {
        var buckets: [[UInt8]] = []
        while true {
            buckets.append(Array(repeating: 0, count: 4 * 1024 * 1024))
        }
    }
}
