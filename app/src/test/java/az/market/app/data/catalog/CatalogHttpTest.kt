package az.market.app.data.catalog

import az.market.app.data.customer.SupabaseCustomerRepository
import az.market.app.data.remote.BackendConfig
import az.market.app.data.remote.SupabaseProvider
import az.market.app.data.remote.createMarketSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real supabase-kt Postgrest client against a mocked PostgREST API. */
class CatalogHttpTest {
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private val anon = enc.encodeToString("{}".toByteArray()) + "." + enc.encodeToString("""{"role":"anon"}""".toByteArray()) + ".c2ln"
    private val requests = mutableListOf<HttpRequestData>()
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun provider(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        SupabaseProvider(BackendConfig("https://demo.supabase.co", anon)) {
            createMarketSupabaseClient(it, engine = MockEngine { r -> requests += r; handler(r) }, withAuth = false)
        }

    private fun HttpRequestData.jsonBody(): JsonObject =
        Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()) as JsonObject

    @Test fun homeFeedIsOneRpcCall() = runTest {
        val repo = SupabaseCatalogRepository(provider { respond(fixture("home_feed.json"), HttpStatusCode.OK, json) })
        val feed = repo.homeFeed()
        val r = requests.single()
        assertEquals("/rest/v1/rpc/home_feed", r.url.encodedPath)
        assertEquals("POST", r.method.value)
        assertEquals("10", r.jsonBody()["p_section_limit"]!!.jsonPrimitive.content)
        assertEquals("Çay Azərçay 100q", feed.discounted.first().name)
    }

    @Test fun searchSendsTrimmedQueryAndPaging() = runTest {
        val repo = SupabaseCatalogRepository(provider { respond("[]", HttpStatusCode.OK, json) })
        repo.products(search = "  seker ", sort = ProductSort.PRICE_ASC, limit = 40, offset = 40)
        val body = requests.single().jsonBody()
        assertEquals("/rest/v1/rpc/catalog_products", requests.single().url.encodedPath)
        assertEquals("seker", body["p_search"]!!.jsonPrimitive.content)
        assertEquals("price_asc", body["p_sort"]!!.jsonPrimitive.content)
        assertEquals("40", body["p_offset"]!!.jsonPrimitive.content)
        assertTrue("blank category must not be sent", !body.containsKey("p_category_id"))
    }

    @Test fun customerHeaderQueries() = runTest {
        val repo = SupabaseCustomerRepository(provider { r ->
            when {
                r.url.encodedPath.endsWith("/delivery_addresses") -> respond(
                    """[{"id":"a1","label":"Ev","address_line":"Nizami küç. 12","settlement":"Qəbələ","is_default":true}]""", HttpStatusCode.OK, json)
                r.url.encodedPath.endsWith("/cart_items") -> respond("", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ContentRange to listOf("*/4")))
                else -> respond("", HttpStatusCode.NoContent, json)
            }
        })
        assertEquals("Nizami küç. 12", repo.addresses().single().addressLine)
        assertEquals(4, repo.cartItemCount())
        repo.setDefaultAddress("a1")
        val addressReq = requests.first { it.url.encodedPath.endsWith("/delivery_addresses") }
        assertTrue(addressReq.url.parameters["select"]!!.contains("address_line"))
        val countReq = requests.first { it.url.encodedPath.endsWith("/cart_items") }
        assertTrue(countReq.headers["Prefer"]!!.contains("count=exact"))
        assertEquals("a1", requests.last().jsonBody()["p_address_id"]!!.jsonPrimitive.content)
    }
}
