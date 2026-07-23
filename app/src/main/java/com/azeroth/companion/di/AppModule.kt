package com.azeroth.companion.di

import android.content.Context
import androidx.room.Room
import com.azeroth.companion.core.database.AppDatabase
import com.azeroth.companion.core.time.AnchorCalibrator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "azeroth.db").build()

    @Provides
    fun provideTaskStateDao(db: AppDatabase) = db.taskStateDao()

    @Provides
    fun provideCalibrationDao(db: AppDatabase) = db.calibrationDao()

    @Provides
    fun provideCharacterDao(db: AppDatabase) = db.characterDao()

    @Provides
    fun provideSnapshotDao(db: AppDatabase) = db.snapshotDao()

    @Provides
    @Singleton
    fun provideAnchorCalibrator() = AnchorCalibrator()
}
