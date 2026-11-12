# ---- Apex AI / R8 configuration -------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep the whole app package. R8 shrinking plus reflection (Gson, tool registry,
# SpeechRecognizer callbacks) is the classic cause of a release build that dies
# on launch while the debug build is fine. Library code is still shrunk and
# optimised; our own classes keep their names and members.
-keep class com.apex.ai.** { *; }
-keepclassmembers class com.apex.ai.** { *; }

# Gson-serialised models must keep field names
-keep class com.apex.ai.data.model.** { *; }
-keepclassmembers class com.apex.ai.data.model.** { <fields>; }
-keep class com.apex.ai.llm.dto.** { *; }

# OkHttp / Tink internals
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# Kotlin metadata used by reflection in tool registry
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
