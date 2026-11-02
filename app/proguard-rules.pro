# NetBlocker Pro R8 rules
-keepattributes Signature, InnerClasses, EnclosingMethod

# org.json is part of the platform, nothing to keep.

# Material components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
