# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

-keep class com.example.roomie.data.model.** { *; }
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

-dontwarn com.squareup.okhttp3.**
-dontwarn okhttp3.**
-dontwarn okio.**

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembernames class * {
    java.lang.Object readObject(java.io.ObjectInputStream);
    void writeObject(java.io.ObjectOutputStream);
}
