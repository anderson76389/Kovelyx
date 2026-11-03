# The separate test APK links original Kotlin names after target optimization.
# Applied only to the instrumented optimized variant, never the distributed APK.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.tracing.** { *; }
# Compose's external test APK calls original AndroidX names directly.
-keep class androidx.** { *; }
# Public entry points referenced directly by the separate smoke-test APK.
-keep class com.nuvio.app.core.ui.** { *; }
-keep class com.nuvio.app.features.player.** { *; }
-keep class com.nuvio.app.features.notifications.** { *; }
-keep class com.kovelyx.display.** { *; }
