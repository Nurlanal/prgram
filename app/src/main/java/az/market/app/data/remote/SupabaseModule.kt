package az.market.app.data.remote

import az.market.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

/**
 * Holds the Supabase client, or null when the build has no backend configuration
 * (the UI then shows a clear "server not configured" state instead of failing silently).
 */
class SupabaseProvider(val config: BackendConfig, factory: (BackendConfig) -> SupabaseClient) {
    val client: SupabaseClient? by lazy { if (config.isConfigured) factory(config) else null }
}

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun provideBackendConfig(): BackendConfig =
        BackendConfig(url = BuildConfig.SUPABASE_URL, anonKey = BuildConfig.SUPABASE_ANON_KEY)

    @Provides
    @Singleton
    fun provideSupabaseProvider(config: BackendConfig): SupabaseProvider =
        SupabaseProvider(config) { createMarketSupabaseClient(it) }
}
