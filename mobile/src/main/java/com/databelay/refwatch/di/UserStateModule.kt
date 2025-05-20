package com.databelay.refwatch.di
import com.databelay.refwatch.auth.AuthRepository // Import AuthRepository
import com.databelay.refwatch.auth.AuthViewModel
import com.google.firebase.auth.FirebaseUser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent // Scope to ViewModel lifecycle
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Qualifier

// Optional: Qualifier if you had multiple String Flows
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserIdFlow

@OptIn(ExperimentalCoroutinesApi::class)
@Module
@InstallIn(ViewModelComponent::class) // This flow is needed by ViewModels
object UserStateModule {

    // This provides a Flow<String?> representing the current user's ID.
    // It depends on AuthViewModel to get the FirebaseUser flow.
    @Provides
    @ViewModelScoped // The provided Flow will live as long as the ViewModel asking for it
    @UserIdFlow // Use the qualifier
    fun provideUserIdFlow(authRepository: AuthRepository): Flow<String?> { // Now depends on AuthRepository
        // Hilt provides authRepository from RepositoryModule
        return authRepository.currentUserFlow.map { firebaseUser ->
            firebaseUser?.uid
        }
    }
}