# Keep the JS interface bridge methods accessible to WebView JS code.
-keepclassmembers class com.dotsquares.mobileapp.DSMobileAppBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Standard Android attributes used by reflection.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
