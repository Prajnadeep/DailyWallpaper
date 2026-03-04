# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Keep model classes
-keep class com.dailywallpaper.data.model.** { *; }
