# ProGuard rules for minimal Android project

# TensorFlow Lite - keep GPU delegate classes and JNI loaders
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**
