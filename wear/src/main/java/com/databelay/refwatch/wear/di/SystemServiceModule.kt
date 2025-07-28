package com.databelay.refwatch.wear.di

import android.content.Context
import android.os.Vibrator
import androidx.core.content.ContextCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SystemServiceModule {

    @Provides
    @Singleton
    fun provideVibrator(@ApplicationContext context: Context): Vibrator? {
        return ContextCompat.getSystemService(context, Vibrator::class.java)
    }
}
