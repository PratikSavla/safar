# Keep kotlinx.serialization generated serializers.
-keepclassmembers class app.safar.** { *** Companion; }
-keepclasseswithmembers class app.safar.** { kotlinx.serialization.KSerializer serializer(...); }
