package com.databelay.refwatch.wear.di


import android.app.Application
import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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