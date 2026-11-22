# Nexus Micro tight R8 rules (no broad -keep rules)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Strip debug/verbose logs in R8 release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
