-keep class com.leotv.launcher.SkyView { <init>(...); }
-keep class com.leotv.launcher.GlassView { <init>(...); }
-keep class com.leotv.launcher.PetalShowerView { <init>(...); }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn **
