# Keep the JavaScript bridge methods callable from the WebView.
-keepclassmembers class az.bakurunner.game.GameBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
