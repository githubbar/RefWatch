package com.databelay.refwatch.wear.di


import android.app.Application
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // These clients can be singletons
object WearableModule {
    @Provides
    @Singleton
    fun provideDataClient(application: Application): DataClient {
        return Wearable.getDataClient(application)
    }
}