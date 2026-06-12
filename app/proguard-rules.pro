# Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

-repackageclasses
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute SourceFile
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,InnerClasses,EnclosingMethod,Signature

# Keep libxposed entry point. The class name is loaded from META-INF/xposed/java_init.list.
-keep class cn.martinkay.technicspods.hook.HookEntry { *; }

# Hook classes run inside host processes and are reached from HookEntry at runtime.
-keep class cn.martinkay.technicspods.hook.** { *; }

# Parcelable/data classes are shared through broadcast extras across processes.
-keep class cn.martinkay.technicspods.utils.miuiStrongToast.data.** { *; }
