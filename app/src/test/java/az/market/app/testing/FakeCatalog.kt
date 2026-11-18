package az.market.app.testing

import az.market.app.data.auth.AuthError
import az.market.app.data.auth.AuthException
import az.market.app.data.catalog.CatalogRepository
import az.market.app.data.catalog.HomeFeed
import az.market.app.data.catalog.HomeFeedDto
import az.market.app.data.catalog.ProductCard
import az.market.app.data.catalog.ProductSort
import az.market.app.data.catalog.toDomain
import az.market.app.data.customer.CustomerRepository
import az.market.app.data.customer.DeliveryAddress
import az.market.app.data.remote.MarketJson

/** The real home feed exported from the test database (supabase/tests/run_local_tests.sh). */
fun fixtureHomeFeed(): HomeFeed {
    val text = requireNotNull(FakeCatalogRepository::class.java.classLoader?.getResource("fixtures/home_feed.json")).readText()
    return MarketJson.decodeFromString<HomeFeedDto>(text).toDomain()
}

class FakeCatalogRepository(var feed: HomeFeed = fixtureHomeFeed()) : CatalogRepository {
    var failWith: AuthError? = null
    val productCalls = mutableListOf<String>()
    var products: List<ProductCard> = feed.newArrivals
    var pageSize: Int? = null

    override suspend fun homeFeed(): HomeFeed {
        failWith?.let { throw AuthException(it) }
        return feed
    }

    override suspend fun products(
        search: String?, categoryId: Long?, discountedOnly: Boolean, sort: ProductSort, limit: Int, offset: Int,
    ): List<ProductCard> {
        productCalls += "search=$search cat=$categoryId disc=$discountedOnly sort=${sort.server} limit=$limit offset=$offset"
        failWith?.let { throw AuthException(it) }
        val matching = if (search == null) products else products.filter { it.name.contains(search, ignoreCase = true) }
        return matching.drop(offset).take(pageSize ?: limit)
    }
}

class FakeCustomerRepository : CustomerRepository {
    var addresses = mutableListOf(
        DeliveryAddress("a1", "Ev", "Nizami küç. 12", "Qəbələ", isDefault = true),
        DeliveryAddress("a2", "İş", "Bakı küç. 5", "Qəbələ", isDefault = false),
    )
    var cart = 3
    var fail = false
    override suspend fun addresses(): List<DeliveryAddress> { if (fail) throw AuthException(AuthError.NETWORK); return addresses.toList() }
    override suspend fun setDefaultAddress(addressId: String) {
        addresses = addresses.map { it.copy(isDefault = it.id == addressId) }.toMutableList()
    }
    override suspend fun cartItemCount(): Int { if (fail) throw AuthException(AuthError.NETWORK); return cart }
}
