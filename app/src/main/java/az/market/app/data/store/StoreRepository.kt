package az.market.app.data.store

import az.market.app.data.remote.AppSettingsDto
import az.market.app.data.remote.CategoryDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Read access to public store data. All calls go through RLS as `anon` / the signed-in user. */
class StoreRemoteDataSource(private val client: SupabaseClient) {

    suspend fun fetchSettings(): StoreSettings =
        client.from(TABLE_SETTINGS).select().decodeSingle<AppSettingsDto>().toDomain()

    suspend fun fetchCategories(): List<Category> =
        client.from(TABLE_CATEGORIES)
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList<CategoryDto>()
            .map { Category(it.id, it.name, it.slug, it.imageUrl, it.sortOrder) }

    /** Live updates of app_settings via Supabase Realtime (e.g. admin changes the brand color). */
    fun settingsUpdates(): Flow<StoreSettings> = callbackFlow {
        val channel = client.channel("app-settings")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = TABLE_SETTINGS
        }
        val job = launch { changes.map { it.decodeRecord<AppSettingsDto>().toDomain() }.collect { send(it) } }
        channel.subscribe()
        awaitClose {
            job.cancel()
            launch { client.realtime.removeChannel(channel) }
        }
    }

    companion object {
        const val TABLE_SETTINGS = "app_settings"
        const val TABLE_CATEGORIES = "categories"
    }
}
