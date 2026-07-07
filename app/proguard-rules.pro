# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Crashlytics: stack traces legibles (el mapping se sube solo) ---
-keepattributes SourceFile,LineNumberTable

# --- Firestore: los modelos se deserializan por reflexión (toObject).
# Sin esto, R8 renombra los campos y TODOS los documentos llegarían vacíos.
-keep class com.mochilapp.mobile.data.** { *; }
-keepattributes Signature, *Annotation*

# --- kotlinx.serialization (Navigation 3 serializa los Destination) ---
-keepattributes InnerClasses
-keep,includedescriptorclasses class com.mochilapp.mobile.**$$serializer { *; }
-keepclassmembers class com.mochilapp.mobile.** {
    *** Companion;
}
-keepclasseswithmembers class com.mochilapp.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Stripe: referencias opcionales que no usamos (evita fallos de R8) ---
-dontwarn com.stripe.android.pushProvisioning.**

# --- Firebase: los ComponentRegistrar se instancian por reflexión con su
# constructor sin argumentos. R8 (full mode) los eliminaba y componentes
# enteros desaparecían en release (Crashlytics, Messaging, Installations...)
# → NPE "FirebaseCrashlytics component is not present" al tocar Telemetry.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}