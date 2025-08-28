# keep exception names
-keepnames class * extends java.lang.Throwable

##---------------Begin: proguard configuration for kotlinx serialization  ----------
# Keep kotlinx serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep serializers
-keep,includedescriptorclasses class kotlinx.serialization.** {
    *** <fields>;
    *** <methods>;
}

# Keep classes with @Serializable annotation
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep serialization metadata
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
##---------------End: proguard configuration for kotlinx serialization  ----------

-keep, includedescriptorclasses class io.bitdrift.capture.IPreferences {
   public <methods>;
}

-keep, includedescriptorclasses class io.bitdrift.capture.error.IErrorReporter {
   public <methods>;
}

-keep, includedescriptorclasses class io.bitdrift.capture.network.ICaptureNetwork {
   public <methods>;
}

-keep, includedescriptorclasses class io.bitdrift.capture.network.ICaptureStream {
   public <methods>;
}

-keep, includedescriptorclasses class io.bitdrift.capture.providers.session.SessionStrategyConfiguration$* {
   public <methods>;
}

-keep, includedescriptorclasses class io.bitdrift.capture.IMetadataProvider {
   public <methods>;
}

-keep, includedescriptorclasses class io.bitdrift.capture.IEventsListenerTarget {
   public <methods>;
}

-keep, includedescriptorclasses class io.bitdrift.capture.IResourceUtilizationTarget {
   public <methods>;
}

-keep, includedescriptorclasses class io.bitdrift.capture.ISessionReplayTarget {
   public <methods>;
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class io.bitdrift.capture.error.ErrorReporterService {
   public <methods>;
}

-keep class io.bitdrift.capture.network.okhttp.OkHttpNetwork {
   public <methods>;
}

-keep class io.bitdrift.capture.StackTraceProvider {
   public <methods>;
}

-keep class io.bitdrift.capture.providers.** { *; }

# Keep the generated tombstone for parsing native crash
-keep class io.bitdrift.capture.** extends com.google.protobuf.GeneratedMessageLite { *; }

-dontwarn android.app.ApplicationStartInfo
-dontwarn kotlin.time.LongSaturatedMathKt
-dontwarn kotlin.time.TimeSource$Monotonic$ValueTimeMark
-dontwarn kotlin.jvm.internal.SourceDebugExtension

# https://github.com/square/okio/issues/1298#issuecomment-1652182091
-dontwarn okio.**

# https://github.com/square/okhttp/issues/6258
# referenced from: void okhttp3.internal.platform
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
