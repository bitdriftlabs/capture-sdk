// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.commands;

import androidx.annotation.OptIn;
import io.bitdrift.capture.Capture;
import io.bitdrift.capture.commands.CommandHandle;
import io.bitdrift.capture.commands.CommandResult;
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi;
import kotlin.Unit;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exercises the Java-friendly, CompletableFuture-based {@code registerCommand} overload -- the
 * Java counterpart to the Kotlin suspend demo in {@code MainViewModel}. Deliberately plain Java,
 * not Kotlin calling a Java-friendly signature, to actually prove this API works from Java.
 */
@OptIn(markerClass = ExperimentalBitdriftApi.class)
public final class JavaCommandsDemo {

    private static final String COMMAND_KEY = "flip_flag_java";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static volatile CommandHandle flipFlagCommand;
    private static volatile boolean flagEnabled = false;

    private JavaCommandsDemo() {
    }

    public static boolean isRegistered() {
        return flipFlagCommand != null;
    }

    public static void register() {
        if (flipFlagCommand != null) {
            return;
        }
        flipFlagCommand = Capture.Logger.registerCommand(COMMAND_KEY, (scope, arg) -> {
            boolean previous = flagEnabled;
            CompletableFuture<CommandResult> future = new CompletableFuture<>();
            EXECUTOR.execute(() -> {
                try {
                    Thread.sleep(200); // stands in for a real async network/DB call
                    flagEnabled = !previous;
                    future.complete(scope.success(null, Map.of(
                            "from", String.valueOf(previous),
                            "to", String.valueOf(flagEnabled))));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(e);
                }
            });
            return future;
        });
    }

    public static void unregister() {
        CommandHandle command = flipFlagCommand;
        if (command != null) {
            command.unregister();
            flipFlagCommand = null;
        }
    }

    public interface ResultCallback {
        void onResult(CommandResult result);
    }

    /** Stands in for the debugger/workflow actually invoking the command -- see Capture.Logger. */
    public static void invoke(ResultCallback callback) {
        Capture.Logger.invokeCommandForTesting(COMMAND_KEY, "dark_mode", result -> {
            callback.onResult(result);
            return Unit.INSTANCE; // Function1<CommandResult, Unit> from Java needs this
        });
    }
}
