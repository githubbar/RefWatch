package com.databelay.refwatch.di


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
    fun provideDataClient(@ApplicationContext context: Context): DataClient {
        return Wearable.getDataClient(context)
    }


    @Provides
    @Singleton
    fun provideMessageClient(@ApplicationContext context: Context): MessageClient {
        return Wearable.getMessageClient(context)
    }

    @Provides
    @Singleton
    fun provideNodeClient(@ApplicationContext context: Context): NodeClient {
        return Wearable.getNodeClient(context)
    }

    // You could also provide MessageClient, CapabilityClient etc. here if needed
}