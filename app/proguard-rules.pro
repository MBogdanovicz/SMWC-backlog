# Rules for the release build (R8). Most libraries (Compose, Ktor, OkHttp, Coil, kotlinx
# serialization, Play Services) ship their own rules; only what they cannot know is here.

# Line numbers in crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Ktor and OkHttp reference optional classes that are not on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
