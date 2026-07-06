# WorkManager / Room R8 fix
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>(android.content.Context);
}

-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# Keep Wear OS components
-keep class * extends androidx.wear.tiles.TileService {
    public <init>();
}
-keep class * extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService {
    public <init>();
}
