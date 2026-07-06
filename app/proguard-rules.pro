# WorkManager / Room R8 fix
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>(android.content.Context);
}

-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# Keep WorkManager workers
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
