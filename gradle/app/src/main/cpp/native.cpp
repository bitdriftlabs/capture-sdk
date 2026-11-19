#include <jni.h>
#include <string.h>

// Leave this public so that the compiler can't elide the null ptr access.
char *invalid_ptr = nullptr;

static void trigger_sefgault() {
    *invalid_ptr = 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_bitdrift_gradleexample_MainActivity_stringFromJNI(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("This is a string coming via JNI");
}

extern "C"
JNIEXPORT void JNICALL
Java_io_bitdrift_gradleexample_FirstFragment_triggerSegfault(JNIEnv *env, jobject thiz) {
    trigger_sefgault();
}
