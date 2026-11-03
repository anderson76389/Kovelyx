# The separate test APK links original Kotlin names after target optimization.
# Applied only to the instrumented optimized variant, never the distributed APK.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.tracing.** { *; }
