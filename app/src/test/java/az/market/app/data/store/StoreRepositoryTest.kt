package az.market.app.data.store

import az.market.app.data.remote.BackendConfig
import az.market.app.data.remote.SupabaseProvider
import az.market.app.data.remote.createMarketSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the real supabase-kt client against a mocked PostgREST HTTP endpoint. */
class StoreRepositoryTest {
    private val anonKey = Base64.getUrlEncoder().withoutPadding().let {
        it.encodeToString("{}".toByteArray()) + "." + it.encodeToString("""{"role":"anon"}""".toByteArray()) + ".sig"
    }
    private val config = BackendConfig("https://test-project.supabase.co", anonKey)
    private val requests = mutableListOf<HttpRequestData>()

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    private fun TestScope.repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        SupabaseStoreSettingsRepository(
            provider = SupabaseProvider(config) {
                createMarketSupabaseClient(it, engine = MockEngine { req -> requests += req; handler(req) }, withAuth = false)
            },
            io = StandardTestDispatcher(testScheduler),
        )

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test fun connectedStatusUsesRealRestCallsWithAnonKey() = runTest {
        val repo = repository { req ->
            when (req.url.encodedPath) {
                "/rest/v1/app_settings" -> respond("[" + fixture("app_settings.json") + "]", HttpStatusCode.OK, json)
                "/rest/v1/categories" -> respond(fixture("categories.json"), HttpStatusCode.OK, json)
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val status = repo.checkBackend()
        assertTrue("expected Connected but was $status", status is BackendStatus.Connected)
        status as BackendStatus.Connected
        assertEquals("Market", status.storeName)
        assertEquals(13, status.categoryCount)

        val categoriesRequest = requests.single { it.url.encodedPath == "/rest/v1/categories" }
        assertEquals(anonKey, categoriesRequest.headers["apikey"])
        assertTrue(categoriesRequest.url.parameters["order"]!!.startsWith("sort_order.asc"))
    }

    @Test fun serverErrorIsReportedAsUnreachable() = runTest {
        val repo = repository { respond("""{"message":"boom"}""", HttpStatusCode.InternalServerError, json) }
        assertTrue(repo.checkBackend() is BackendStatus.Unreachable)
    }

    @Test fun missingConfigurationIsReportedClearly() = runTest {
        val repo = SupabaseStoreSettingsRepository(
            SupabaseProvider(BackendConfig("", "")) { error("must not be created") },
            StandardTestDispatcher(testScheduler),
        )
        assertEquals(BackendStatus.NotConfigured, repo.checkBackend())
        assertEquals(StoreSettings(), repo.observeSettings().first())
    }

    @Test fun settingsFallBackToDefaultsWhenServerFails() = runTest {
        val repo = SupabaseStoreSettingsRepository(
            SupabaseProvider(config) {
                createMarketSupabaseClient(it, engine = MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }, withAuth = false)
            },
            StandardTestDispatcher(testScheduler),
        )
        assertEquals(StoreSettings(), repo.observeSettings().first())
    }

    @Test fun hexColorParsing() {
        assertEquals(0xFF0E7C5A, parseHexColor("#0E7C5A"))
        assertEquals(null, parseHexColor("red"))
        assertEquals(null, parseHexColor("#12345"))
    }
}
