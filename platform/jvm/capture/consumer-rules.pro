# keep exception names
-keepnames class * extends java.lang.Throwable

##---------------Begin: proguard configuration for Kotlin Serialization ----------
-keepattributes *Annotation*

# Keep Kotlinx Serialization library internals
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.serialization.modules.SerializersModule { *; }
-keep class kotlinx.serialization.json.** { *; }

# Keep generated serializers
-keep class **$$serializer { *; }

# Keep classes annotated with @Serializable
-keep @kotlinx.serialization.Serializable class * { *; }
##---------------End: proguard configuration for Kotlin Serialization ----------

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
