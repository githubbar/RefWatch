package com.databelay.refwatch.wear.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(app: Application): SharedPreferences {
        // Use the same name as in mobile if they are intended to be synced, 
        // but here they are local to the device unless synced via DataClient.
        // For now, let's just provide it.
        return app.getSharedPreferences(app.packageName + "_prefs", Context.MODE_PRIVATE)
    }
}
