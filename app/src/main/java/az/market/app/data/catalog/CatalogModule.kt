package az.market.app.data.catalog

import az.market.app.data.customer.CustomerRepository
import az.market.app.data.customer.SupabaseCustomerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogModule {
    @Binds abstract fun bindCatalogRepository(impl: SupabaseCatalogRepository): CatalogRepository
    @Binds abstract fun bindCustomerRepository(impl: SupabaseCustomerRepository): CustomerRepository
}
