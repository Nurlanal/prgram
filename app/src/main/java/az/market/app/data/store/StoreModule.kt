package az.market.app.data.store

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StoreModule {
    @Binds
    abstract fun bindStoreSettingsRepository(impl: SupabaseStoreSettingsRepository): StoreSettingsRepository
}
