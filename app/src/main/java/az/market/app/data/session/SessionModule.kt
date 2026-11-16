package az.market.app.data.session

import android.content.Context
import az.market.app.data.auth.AuthGateway
import az.market.app.data.auth.AuthRepository
import az.market.app.data.auth.SupabaseAuthGateway
import az.market.app.data.auth.SupabaseAuthRepository
import az.market.app.data.remote.SupabaseProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    abstract fun bindSessionRepository(impl: SupabaseSessionRepository): SessionRepository

    @Binds
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    companion object {
        /** Null when the build has no backend configuration. */
        @Provides
        @Singleton
        fun provideAuthGateway(provider: SupabaseProvider): AuthGateway? = provider.client?.let(::SupabaseAuthGateway)
    }
}
