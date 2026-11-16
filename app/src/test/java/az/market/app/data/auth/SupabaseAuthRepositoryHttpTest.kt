package az.market.app.data.auth

import az.market.app.data.admin.CourierAdminRepository
import az.market.app.data.admin.CourierCreationError
import az.market.app.data.admin.CourierCreationException
import az.market.app.data.admin.NewCourier
import az.market.app.data.remote.BackendConfig
import az.market.app.data.remote.SupabaseProvider
import az.market.app.data.remote.createMarketSupabaseClient
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real supabase-kt Auth / Postgrest / Functions clients against a mocked Supabase HTTP API,
 * so request paths, bodies and error mapping are verified exactly as they would be sent to the server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseAuthRepositoryHttpTest {
    private class Recorded(val method: String, val path: String, val query: String, val body: JsonObject?, val auth: String?)

    private class MemorySessionManager : SessionManager {
        var session: UserSession? = null
        override suspend fun saveSession(session: UserSession) { this.session = session }
        override suspend fun loadSession() = session
        override suspend fun deleteSession() { session = null }
    }

    private val enc = Base64.getUrlEncoder().withoutPadding()
    private fun jwt(payload: String) = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray()) + "." +
        enc.encodeToString(payload.toByteArray()) + ".c2ln"

    private val anonKey = jwt("""{"role":"anon"}""")
    private val userToken = jwt("""{"sub":"u1","role":"authenticated","aal":"aal1","exp":4102444800}""")
    private val user = """{"id":"u1","aud":"authenticated","role":"authenticated","phone":"994501234567",
        "app_metadata":{"provider":"phone"},"user_metadata":{},"created_at":"2026-11-16T00:00:00Z"}"""
    private val sessionJson = """{"access_token":"$userToken","token_type":"bearer","expires_in":3600,
        "refresh_token":"refresh-1","user":$user}"""

    private val requests = mutableListOf<Recorded>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val sessions = MemorySessionManager()

    private fun setup(handler: suspend MockRequestHandleScope.(Recorded) -> HttpResponseData): Pair<SupabaseAuthRepository, SupabaseProvider> {
        val provider = SupabaseProvider(BackendConfig("https://demo.supabase.co", anonKey)) { config ->
            createMarketSupabaseClient(
                config,
                engine = MockEngine { req -> val r = record(req); requests += r; handler(r) },
                sessionManager = sessions,
                androidLifecycle = false,
            )
        }
        val gateway = SupabaseAuthGateway(provider.client!!)
        return SupabaseAuthRepository(provider, gateway, AuthFlowState()) to provider
    }

    private fun record(req: HttpRequestData): Recorded {
        val bytes = (req.body as? OutgoingContent.ByteArrayContent)?.bytes()
        val body = bytes?.decodeToString()?.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it) as? JsonObject }
        return Recorded(req.method.value, req.url.encodedPath, req.url.encodedQuery, body, req.headers[HttpHeaders.Authorization])
    }

    private suspend fun expectError(expected: AuthError, block: suspend () -> Unit) {
        try { block(); fail("expected $expected") } catch (e: AuthException) { assertEquals(expected, e.error) }
    }

    @Test fun phoneSignInUsesPasswordGrantAndStoresTheSession() = runTest {
        val (repo, _) = setup { respond(sessionJson, HttpStatusCode.OK, json) }
        repo.signInWithPhone("+994501234567", "Market2026")
        val r = requests.single { it.path == "/auth/v1/token" }
        assertEquals("POST", r.method)
        assertEquals("grant_type=password", r.query)
        assertEquals("+994501234567", r.body!!["phone"]!!.jsonPrimitive.content)
        assertEquals("Market2026", r.body["password"]!!.jsonPrimitive.content)
        assertEquals("refresh-1", sessions.session?.refreshToken)
    }

    @Test fun wrongPasswordIsMappedToInvalidCredentials() = runTest {
        val (repo, _) = setup {
            respond("""{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}""", HttpStatusCode.BadRequest, json)
        }
        expectError(AuthError.INVALID_CREDENTIALS) { repo.signInWithPhone("+994501234567", "bad") }
        assertEquals(null, sessions.session)
    }

    @Test fun registrationSendsProfileAndAddressAsSignUpData() = runTest {
        val (repo, _) = setup { respond(user, HttpStatusCode.OK, json) }
        repo.registerCustomer(CustomerRegistration(" Leyla ", "Həsənova", "+994551234567", "Market2026", "Nizami küç. 12", "Qəbələ"))
        val r = requests.single { it.path == "/auth/v1/signup" }
        val data = r.body!!["data"]!!.jsonObject
        assertEquals("+994551234567", r.body["phone"]!!.jsonPrimitive.content)
        assertEquals("Leyla", data["first_name"]!!.jsonPrimitive.content)
        assertEquals("Nizami küç. 12", data["address_line"]!!.jsonPrimitive.content)
        assertEquals("Qəbələ", data["settlement"]!!.jsonPrimitive.content)
        assertFalse("role must never be sent from the app", data.containsKey("role"))
    }

    @Test fun registrationErrors() = runTest {
        val (repo, _) = setup {
            respond("""{"code":422,"error_code":"phone_exists","msg":"Phone number already registered"}""",
                HttpStatusCode.UnprocessableEntity, json)
        }
        expectError(AuthError.PHONE_EXISTS) {
            repo.registerCustomer(CustomerRegistration("A", "B", "+994551234567", "Market2026", "Ünvan 1", "Kənd"))
        }
    }

    @Test fun smsCodeVerification() = runTest {
        val (repo, _) = setup { r ->
            if (r.path == "/auth/v1/verify") respond(sessionJson, HttpStatusCode.OK, json)
            else respond("{}", HttpStatusCode.OK, json)
        }
        repo.verifyRegistrationCode("+994551234567", " 123456 ")
        val r = requests.first { it.path == "/auth/v1/verify" }
        assertEquals("sms", r.body!!["type"]!!.jsonPrimitive.content)
        assertEquals("123456", r.body["token"]!!.jsonPrimitive.content)
        assertTrue(sessions.session != null)
    }

    @Test fun wrongSmsCode() = runTest {
        val (repo, _) = setup {
            respond("""{"code":403,"error_code":"otp_expired","msg":"Token has expired or is invalid"}""", HttpStatusCode.Forbidden, json)
        }
        expectError(AuthError.INVALID_OTP) { repo.verifyRegistrationCode("+994551234567", "000000") }
    }

    @Test fun passwordResetNeverCreatesUsersAndDoesNotRevealUnknownNumbers() = runTest {
        val (repo, _) = setup {
            respond("""{"code":422,"error_code":"otp_disabled","msg":"Signups not allowed for otp"}""", HttpStatusCode.UnprocessableEntity, json)
        }
        repo.requestPasswordResetCode("+994509999999", null) // no exception → same UI as for a real account
        val r = requests.single { it.path == "/auth/v1/otp" }
        assertEquals(false, r.body!!["create_user"]!!.jsonPrimitive.boolean)
    }

    @Test fun passwordResetRateLimitIsReported() = runTest {
        val (repo, _) = setup {
            respond("""{"code":429,"error_code":"over_sms_send_rate_limit","msg":"rate limit"}""", HttpStatusCode.TooManyRequests, json)
        }
        expectError(AuthError.RATE_LIMITED) { repo.requestPasswordResetCode("+994501234567", null) }
    }

    @Test fun adminFormRejectsNonAdminAccountsAndSignsThemOut() = runTest {
        val (repo, _) = setup { r ->
            when (r.path) {
                "/auth/v1/token" -> respond(sessionJson, HttpStatusCode.OK, json)
                "/rest/v1/rpc/get_my_account" -> respond(
                    """{"user_id":"u1","role":"customer","is_active":true,"first_name":"A","last_name":"B"}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NoContent, json)
            }
        }
        expectError(AuthError.NOT_ADMIN_ACCOUNT) { repo.signInAdmin("someone@market.az", "Market2026") }
        assertEquals("someone@market.az", requests.first { it.path == "/auth/v1/token" }.body!!["email"]!!.jsonPrimitive.content)
        assertTrue("account must be read with the user's token",
            requests.single { it.path == "/rest/v1/rpc/get_my_account" }.auth == "Bearer $userToken")
        assertEquals(null, sessions.session)
    }

    @Test fun networkFailureIsReportedAsNetwork() = runTest {
        val (repo, _) = setup { throw java.io.IOException("no route to host") }
        expectError(AuthError.NETWORK) { repo.signInWithPhone("+994501234567", "Market2026") }
    }

    @Test fun courierCreationCallsTheEdgeFunctionWithTheAdminToken() = runTest {
        val (repo, provider) = setup { r ->
            when (r.path) {
                "/auth/v1/token" -> respond(sessionJson, HttpStatusCode.OK, json)
                "/functions/v1/admin-create-courier" -> respond("""{"user_id":"new-courier"}""", HttpStatusCode.Created, json)
                else -> respond("{}", HttpStatusCode.OK, json)
            }
        }
        repo.signInWithPhone("+994501234567", "Market2026")
        val id = CourierAdminRepository(provider).createCourier(NewCourier("Orxan", "M", "+994707654321", "Kuryer2026", null))
        assertEquals("new-courier", id)
        val call = requests.single { it.path == "/functions/v1/admin-create-courier" }
        assertEquals("Bearer $userToken", call.auth)
        assertEquals("+994707654321", call.body!!["phone"]!!.jsonPrimitive.content)
    }

    @Test fun courierCreationErrorsFromTheFunction() = runTest {
        for ((status, expected) in listOf(
            HttpStatusCode.Conflict to CourierCreationError.PHONE_EXISTS,
            HttpStatusCode.Forbidden to CourierCreationError.ADMIN_MFA_REQUIRED,
            HttpStatusCode.BadRequest to CourierCreationError.INVALID_INPUT,
        )) {
            val (_, provider) = setup { respond("""{"error":"x"}""", status, json) }
            try {
                CourierAdminRepository(provider).createCourier(NewCourier("A", "B", "+994707654321", "Kuryer2026"))
                fail("expected $expected")
            } catch (e: CourierCreationException) {
                assertEquals(expected, e.reason)
            }
        }
    }
}
