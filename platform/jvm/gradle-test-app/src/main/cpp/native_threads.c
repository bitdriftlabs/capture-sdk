// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <unistd.h>

#define NATIVE_THREAD_STACK_SIZE (64 * 1024)

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
