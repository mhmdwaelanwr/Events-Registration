# Retrofit 2 rules
# احتفظ بالـ Signatures والـ Annotations اللازمة لعمل الـ Reflection والـ Generics
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, InnerClasses, EnclosingMethod, Exceptions

# احتفظ بواجهات (Interfaces) الخاصة بـ Retrofit وطرقها (Methods)
-keep @retrofit2.http.GET interface * { <methods>; }
-keep @retrofit2.http.POST interface * { <methods>; }
-keep @retrofit2.http.PUT interface * { <methods>; }
-keep @retrofit2.http.DELETE interface * { <methods>; }
-keep @retrofit2.http.PATCH interface * { <methods>; }
-keep @retrofit2.http.HEAD interface * { <methods>; }
-keep @retrofit2.http.OPTIONS interface * { <methods>; }
-keep @retrofit2.http.HTTP interface * { <methods>; }

# منع حذف أسماء البارامترات في دوال الـ interface
-keepparameternames

# قواعد Gson لضمان تحويل البيانات بشكل صحيح
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# احتفظ بجميع موديلات البيانات (Data Models)
-keep class anwar.mlsa.eventsregistration.data.** { *; }

# احتفظ بجميع كلاسات الشبكة (Network interfaces)
-keep interface anwar.mlsa.eventsregistration.network.** { *; }
-keep class anwar.mlsa.eventsregistration.network.** { *; }

# Callbacks and Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keep class kotlin.coroutines.Continuation

# قواعد إضافية لـ OkHttp و Retrofit
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class retrofit2.Response

# Hedera SDK, Grpc, and Protobuf Rules
-keep class com.hedera.hashgraph.** { *; }
-keep class io.grpc.** { *; }
-keep class com.google.protobuf.** { *; }

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    *** getDefaultInstance();
    *** newBuilder();
    <fields>;
}

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite$Builder {
    <methods>;
}

-dontwarn com.hedera.hashgraph.**
-dontwarn io.grpc.**
-dontwarn com.google.protobuf.**
