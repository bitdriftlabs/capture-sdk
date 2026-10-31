// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands;

/** Host-JVM double for the private Kotlin dispatcher JNI contract. */
public interface ICommandDispatcher {
  void dispatch(
      long invocationId,
      String key,
      String commandId,
      String sessionId,
      String[] argumentNames,
      int[] argumentTypes,
      Object[] argumentValues);

  void cancel(long invocationId);

  final class Recording implements ICommandDispatcher {
    public long dispatched;
    public long cancelled;
    public int cancelCount;
    public boolean throwOnDispatch;
    public boolean throwOnCancel;

    @Override
    public void dispatch(
        long invocationId,
        String key,
        String commandId,
        String sessionId,
        String[] argumentNames,
        int[] argumentTypes,
        Object[] argumentValues) {
      dispatched = invocationId;
      if (throwOnDispatch) {
        throw new IllegalStateException("dispatch failed");
      }
    }

    @Override
    public void cancel(long invocationId) {
      cancelled = invocationId;
      cancelCount++;
      if (throwOnCancel) {
        throw new IllegalStateException("cancel failed");
      }
    }
  }
}
