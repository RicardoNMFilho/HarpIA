# ProGuard rules for minimal Android project

# TensorFlow Lite - keep GPU delegate classes and JNI loaders
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**
 
 # MindSpore Lite 1.9 - keep Java API and JNI loaders
 -keep class com.mindspore.** { *; }
 -dontwarn com.mindspore.**
