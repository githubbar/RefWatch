package com.databelay.refwatch.di // Or your di package

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent // For application-wide singletons
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Scopes these bindings to the application lifecycle
object FirebaseModule {

    @Provides
    @Singleton // Ensures only one instance of FirebaseAuth is created
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton // Ensures only one instance of FirebaseFirestore is created
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        // Optional: Configure Firestore settings if needed
        // val settings = FirebaseFirestoreSettings.Builder()
        //    .setPersistenceEnabled(true)
        //    .build()
        // firestore.firestoreSettings = settings
        return firestore
    }
}