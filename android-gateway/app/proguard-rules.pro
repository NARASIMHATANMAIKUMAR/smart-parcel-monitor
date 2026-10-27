# ProGuard Rules for Smart Parcel Gateway
-keep class com.smartparcel.gateway.data.** { *; }
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
}
