package com.example.mapsproject.di

import android.content.Context
import androidx.room.Room
import com.example.mapsproject.db.LatLangRoomDb
import com.example.mapsproject.db.listOfLatLangDoa
import com.example.mapsproject.repo.LatLngRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object Instances {

    @Provides
    @Singleton
    fun provideDB(@ApplicationContext context: Context) : LatLangRoomDb = Room.databaseBuilder(
        context,
        LatLangRoomDb::class.java,
        "latLangDB",
    ).build()

    @Provides
    @Singleton
    fun provideLatLangDao( latLangRoomDb: LatLangRoomDb) : listOfLatLangDoa = latLangRoomDb.latLangDoa



    @Provides
    @Singleton
    fun provideRepo(langDoa: listOfLatLangDoa) : LatLngRepository = LatLngRepository(langDoa)
}