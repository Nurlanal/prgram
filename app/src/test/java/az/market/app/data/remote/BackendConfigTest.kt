package az.market.app.data.remote

import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendConfigTest {
    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray()) + "." +
            enc.encodeToString(payload.toByteArray()) + ".signature"
    }

    private val anonKey = jwt("""{"iss":"supabase","ref":"abc","role":"anon","iat":1,"exp":2}""")
    private val serviceKey = jwt("""{"iss":"supabase","ref":"abc","role":"service_role","iat":1,"exp":2}""")

    @Test fun anonAndPublishableKeysAreAllowed() {
        assertFalse(BackendConfig.isSecretKey(anonKey))
        assertFalse(BackendConfig.isSecretKey("sb_publishable_abc123"))
    }

    @Test fun serviceRoleAndSecretKeysAreRejected() {
        assertTrue(BackendConfig.isSecretKey(serviceKey))
        assertTrue(BackendConfig.isSecretKey("sb_secret_abc123"))
        assertFalse(BackendConfig("https://abc.supabase.co", serviceKey).isConfigured)
    }

    @Test fun configurationRequiresHttpsUrlAndKey() {
        assertTrue(BackendConfig("https://abc.supabase.co", anonKey).isConfigured)
        assertFalse(BackendConfig("", "").isConfigured)
        assertFalse(BackendConfig("http://abc.supabase.co", anonKey).isConfigured)
        assertFalse(BackendConfig("https://abc.supabase.co", "").isConfigured)
        assertFalse(BackendConfig("https://abc.supabase.co/rest/v1", anonKey).isConfigured)
    }
}
