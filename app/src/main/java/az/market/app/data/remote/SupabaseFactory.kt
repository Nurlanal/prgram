package az.market.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.json.Json

/** JSON settings shared by every Supabase call: tolerant to new server columns. */
val MarketJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Builds the single Supabase client of the app.
 * @param engine injectable HTTP engine (tests use Ktor's MockEngine; production uses OkHttp).
 * @param withAuth Auth needs Android storage for the session; JVM tests can switch it off.
 */
fun createMarketSupabaseClient(
    config: BackendConfig,
    engine: HttpClientEngine? = null,
    withAuth: Boolean = true,
): SupabaseClient {
    require(config.isConfigured) { "Supabase is not configured" }
    return createSupabaseClient(supabaseUrl = config.url.trimEnd('/'), supabaseKey = config.anonKey) {
        defaultSerializer = KotlinXSerializer(MarketJson)
        if (engine != null) httpEngine = engine
        if (withAuth) install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}
