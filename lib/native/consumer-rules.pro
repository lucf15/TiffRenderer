# Native method names/signatures must survive so JNI RegisterNatives can find them.
-keepclasseswithmembernames class io.github.lucf15.tiffrenderer.** {
    native <methods>;
}

-keep class io.github.lucf15.tiffrenderer.NativeTiffLimitException { <init>(java.lang.String); }
