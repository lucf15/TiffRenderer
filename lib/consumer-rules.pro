# Native method names/signatures must survive so JNI RegisterNatives can find them.
-keepclasseswithmembernames class com.github.lucf15.tiffrenderer.** {
    native <methods>;
}
