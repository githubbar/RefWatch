package com.databelay.refwatch.di
import com.databelay.refwatch.data.GameStorageMobile
import com.databelay.refwatch.data.garmin.FirebaseGarminLinkRepository
import com.databelay.refwatch.data.garmin.GarminLinkRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton // GameRepository can be a singleton
    fun provideGameRepository(firestore: FirebaseFirestore): GameStorageMobile {
        // Hilt will automatically provide the FirebaseFirestore instance
        // from FirebaseModule because it knows how to create it.
        return GameStorageMobile(firestore)
    }

    @Provides
    @Singleton
    fun provideGarminLinkRepository(): GarminLinkRepository = FirebaseGarminLinkRepository()

/*    @Provides
    @Singleton
    fun provideAuthRepository(firebaseAuth: FirebaseAuth): AuthRepository = // Provide AuthRepository
        FirebaseAuthRepository(firebaseAuth)*/
}