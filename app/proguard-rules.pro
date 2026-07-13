# WorkManager / Room R8 fix
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>(android.content.Context);
}

-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# Keep WorkManager workers
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# HiveMQ / Netty / RxJava ProGuard Rules
-dontwarn io.netty.**
-dontwarn com.hivemq.client.internal.netty.**
-dontwarn org.conscrypt.**
-dontwarn org.eclipse.jetty.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn io.reactivex.**
-dontwarn io.reactivex.rxjava3.**
-dontwarn org.reactivestreams.**
-dontwarn javax.util.concurrent.**
-dontwarn java.lang.instrument.**
-dontwarn javax.inject.**

-keep class com.hivemq.client.** { *; }
-keep class io.netty.** { *; }
-keep class org.slf4j.** { *; }
-keep class io.reactivex.** { *; }
-keep class io.reactivex.rxjava3.** { *; }
-keep class org.reactivestreams.** { *; }
-keep class javax.inject.** { *; }

# Netty / JCTools specific
-keepclassmembers class io.netty.** {
    private static final sun.misc.Unsafe UNSAFE;
}
-keepclassmembers class com.hivemq.client.internal.netty.** {
    private static final sun.misc.Unsafe UNSAFE;
}

# JCTools uses reflection/Unsafe to access fields by name (e.g., consumerIndex, producerIndex)
# We must keep all fields in JCTools classes to prevent R8 from renaming them.
-keep class io.netty.util.internal.shaded.org.jctools.** { *; }
-keepclassmembers class io.netty.util.internal.shaded.org.jctools.** {
    <fields>;
}

-keep class com.hivemq.client.internal.netty.util.internal.shaded.org.jctools.** { *; }
-keepclassmembers class com.hivemq.client.internal.netty.util.internal.shaded.org.jctools.** {
    <fields>;
}

# If JCTools is used as a direct dependency (not shaded)
-keep class org.jctools.** { *; }
-keepclassmembers class org.jctools.** {
    <fields>;
}
-keep class io.netty.handler.ssl.ReferenceCountedOpenSslEngine { *; }
-keep class com.hivemq.client.internal.netty.handler.ssl.ReferenceCountedOpenSslEngine { *; }
-keep class io.netty.util.ResourceLeakDetector { *; }
-keep class com.hivemq.client.internal.netty.util.ResourceLeakDetector { *; }
-keep class io.netty.util.internal.logging.** { *; }
-keep class com.hivemq.client.internal.netty.util.internal.logging.** { *; }

# HiveMQ internal Dagger factories
-keep class com.hivemq.client.internal.**_Factory { *; }
-keep class com.hivemq.client.internal.mqtt.handler.publish.outgoing.MqttOutgoingQosHandler { *; }

# Ensure these aren't stripped if used via reflection
-keep class com.hivemq.client.internal.mqtt.codec.decoder.** { *; }
-keep class com.hivemq.client.internal.mqtt.codec.encoder.** { *; }

# Breezy Weather Data Sharing
-keep class org.breezyweather.datasharing.** { *; }

# Wearable API
-keep class com.google.android.gms.wearable.** { *; }
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }

# LibADB Android
-keep class io.github.muntashirakon.adb.** { *; }
-keep class android.sun.security.** { *; }
-keep class org.conscrypt.** { *; }
