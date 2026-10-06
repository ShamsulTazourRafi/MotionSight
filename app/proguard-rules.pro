# OpenCV's Java bindings are resolved by JNI. Keep their names while allowing
# R8 to shrink unrelated application and AndroidX code.
-keep class org.opencv.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
