package com.example.mapsproject.module

import android.content.Context
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MapsModule {
    @Singleton
    @Provides
    fun provideGMapOptions() : GoogleMapOptions = GoogleMapOptions()


    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

}