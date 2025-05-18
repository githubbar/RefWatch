package com.databelay.refwatch.di

import com.databelay.refwatch.games.GameRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent // GameRepository likely a singleton
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton // GameRepository can be a singleton
    fun provideGameRepository(firestore: FirebaseFirestore): GameRepository {
        // Hilt will automatically provide the FirebaseFirestore instance
        // from FirebaseModule because it knows how to create it.
        return GameRepository(firestore)
    }
}