# keep exception names
-keepnames class * extends java.lang.Throwable

##---------------Begin: proguard configuration for Gson  ----------
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature
# Keep class TypeToken (respectively its generic signature) if present
-if class com.google.gson.reflect.TypeToken
-keep,allowobfuscation class com.google.gson.reflect.TypeToken
# Keep any (anonymous) classes extending TypeToken
-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken
# Keep fields annotated with @SerializedName for classes which are referenced.
# If classes with fields annotated with @SerializedName have a no-args
# constructor keep that as well. Based on
# https://issuetracker.google.com/issues/150189783#comment11.
# See also https://github.com/google/gson/pull/2420#discussion_r1241813541
# for a more detailed explanation.
-if class *
-keepclasseswithmembers,allowobfuscation class <1> {
  @com.google.gson.annotations.SerializedName <fields>;
}
-if class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowobfuscation,allowoptimization class <1> {
  <init>();
}
##---------------End: proguard configuration for Gson  ----------

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
