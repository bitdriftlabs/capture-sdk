// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class ScreenshotCommand(
    private val capture: ((ByteArray) -> Unit) -> Unit,
) {
    private val lock = Any()
    private var pending: CancellableContinuation<CommandResult>? = null

    suspend fun execute(): CommandResult =
        suspendCancellableCoroutine { completion ->
            synchronized(lock) {
                if (pending != null) {
                    completion.resume(
                        CommandResult.Error("screenshot capture already in progress", code = CommandErrorCode.CommandAlreadyExecuting),
                    )
                    return@suspendCancellableCoroutine
                }
                pending = completion
            }
            try {
                capture { bytes ->
                    finish(
                        completion,
                        if (bytes.isNotEmpty()) {
                            CommandResult.Success(CommandAttachment(bytes, "image/jpeg"))
                        } else {
                            CommandResult.Error("invalid or unavailable screenshot")
                        },
                    )
                }
            } catch (_: Exception) {
                finish(completion, CommandResult.Error("screenshot capture failed"))
            }
        }

    private fun finish(
        completion: CancellableContinuation<CommandResult>,
        result: CommandResult,
    ) {
        val accepted =
            synchronized(lock) {
                if (pending !== completion) {
                    false
                } else {
                    pending = null
                    true
                }
            }
        if (accepted) completion.resume(result)
    }
}
