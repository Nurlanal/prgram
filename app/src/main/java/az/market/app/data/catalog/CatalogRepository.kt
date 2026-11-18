package az.market.app.data.catalog

import az.market.app.data.auth.AuthError
import az.market.app.data.auth.AuthException
import az.market.app.data.auth.toAuthError
import az.market.app.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Read-only catalogue. Every price comes from the server (public.catalog_products / home_feed). */
interface CatalogRepository {
    suspend fun homeFeed(): HomeFeed
    suspend fun products(
        search: String? = null,
        categoryId: Long? = null,
        discountedOnly: Boolean = false,
        sort: ProductSort = ProductSort.POPULAR,
        limit: Int = 40,
        offset: Int = 0,
    ): List<ProductCard>
}

@Singleton
class SupabaseCatalogRepository @Inject constructor(private val provider: SupabaseProvider) : CatalogRepository {

    private suspend inline fun <T> call(block: (io.github.jan.supabase.SupabaseClient) -> T): T {
        val client = provider.client ?: throw AuthException(AuthError.NOT_CONFIGURED)
        return try { block(client) } catch (e: Exception) { throw e as? AuthException ?: AuthException(e.toAuthError(), e) }
    }

    override suspend fun homeFeed(): HomeFeed = call { client ->
        client.postgrest.rpc("home_feed", buildJsonObject { put("p_section_limit", 10) })
            .decodeAs<HomeFeedDto>().toDomain()
    }

    override suspend fun products(
        search: String?,
        categoryId: Long?,
        discountedOnly: Boolean,
        sort: ProductSort,
        limit: Int,
        offset: Int,
    ): List<ProductCard> = call { client ->
        client.postgrest.rpc(
            "catalog_products",
            buildJsonObject {
                search?.trim()?.takeIf { it.isNotEmpty() }?.let { put("p_search", it) }
                categoryId?.let { put("p_category_id", it) }
                put("p_only_discounted", discountedOnly)
                put("p_sort", sort.server)
                put("p_limit", limit)
                put("p_offset", offset)
            },
        ).decodeList<ProductCardDto>().map { it.toDomain() }
    }
}
