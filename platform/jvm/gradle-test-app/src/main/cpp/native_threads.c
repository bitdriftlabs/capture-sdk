// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#define NATIVE_THREAD_STACK_SIZE (64 * 1024)

static __attribute__((noinline)) void perfetto_native_cpu_workload(jlong duration_ms) {
  struct timespec current;
  clock_gettime(CLOCK_MONOTONIC, &current);
  int64_t deadline = (int64_t)current.tv_sec * 1000000000 + current.tv_nsec + duration_ms * 1000000;
  volatile uint64_t accumulator = 1;
  do {
    for (int iteration = 0; iteration < 10000; iteration++) {
      accumulator = accumulator * 1664525 + 1013904223;
    }
    clock_gettime(CLOCK_MONOTONIC, &current);
  } while ((int64_t)current.tv_sec * 1000000000 + current.tv_nsec < deadline);
}

JNIEXPORT void JNICALL
Java_io_bitdrift_gradletestapp_diagnostics_fatalissues_NativeThreads_runCpuWorkload(JNIEnv *env, jclass clazz, jlong duration_ms) {
  (void)env;
  (void)clazz;
  if (duration_ms > 0 && duration_ms <= 4000) {
    perfetto_native_cpu_workload(duration_ms);
  }
}

static __attribute__((noinline)) void perfetto_native_allocation_workload(jlong duration_ms) {
  struct timespec current;
  clock_gettime(CLOCK_MONOTONIC, &current);
  int64_t deadline = (int64_t)current.tv_sec * 1000000000 + current.tv_nsec + duration_ms * 1000000;
  void *allocations[64] = {0};
  size_t index = 0;
  do {
    free(allocations[index]);
    allocations[index] = malloc(32768);
    if (allocations[index] != NULL) {
      memset(allocations[index], (int)index, 32768);
    }
    index = (index + 1) % 64;
    usleep(1000);
    clock_gettime(CLOCK_MONOTONIC, &current);
  } while ((int64_t)current.tv_sec * 1000000000 + current.tv_nsec < deadline);
  for (size_t i = 0; i < 64; i++) {
    free(allocations[i]);
  }
}

JNIEXPORT void JNICALL
Java_io_bitdrift_gradletestapp_diagnostics_fatalissues_NativeThreads_runAllocationWorkload(JNIEnv *env, jclass clazz, jlong duration_ms) {
  (void)env;
  (void)clazz;
  if (duration_ms > 0 && duration_ms <= 1000) {
    perfetto_native_allocation_workload(duration_ms);
  }
}

static void *block_forever(void *arg) {
  (void)arg;
  for (;;) {
    pause();
  }
  return NULL;
}

JNIEXPORT jint JNICALL
Java_io_bitdrift_gradletestapp_diagnostics_fatalissues_NativeThreads_spawn(JNIEnv *env, jclass clazz, jint count) {
  (void)env;
  (void)clazz;

  pthread_attr_t attr;
  pthread_attr_init(&attr);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
  pthread_attr_setstacksize(&attr, NATIVE_THREAD_STACK_SIZE);

  jint created = 0;
  for (jint i = 0; i < count; i++) {
    pthread_t thread;
    if (pthread_create(&thread, &attr, block_forever, NULL) != 0) {
      break;
    }
    char name[16];
    snprintf(name, sizeof(name), "native-%d", i);
    pthread_setname_np(thread, name);
    created++;
  }

  pthread_attr_destroy(&attr);
  return created;
}
