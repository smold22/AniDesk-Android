# Keep kotlinx.serialization generated serializers
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
