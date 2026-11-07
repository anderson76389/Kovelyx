# The separate test APK links original Kotlin names after target optimization.
# Applied only to the instrumented optimized variant, never the distributed APK.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.tracing.** { *; }
# Compose's external test APK calls original AndroidX names directly.
-keep class androidx.** { *; }
# Public entry points referenced directly by the separate smoke-test APK.
-keep class com.kovelyx.app.core.ui.** { *; }
-keep class com.kovelyx.app.features.player.** { *; }
-keep class com.kovelyx.app.features.notifications.** { *; }
-keep class com.kovelyx.display.** { *; }
