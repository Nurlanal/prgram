package az.market.app.data.auth

import io.github.jan.supabase.auth.user.UserSession
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Same AES-GCM scheme as the Keystore cipher, with a software key (the Keystore is not available on the JVM). */
private class SoftwareAesGcm : SessionCipher {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return c.iv + c.doFinal(plain)
    }
    override fun decrypt(sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12))
        return c.doFinal(sealed, 12, sealed.size - 12)
    }
}

private class MemoryStore : BlobStore {
    var value: String? = null
    override fun read() = value
    override fun write(value: String) { this.value = value }
    override fun clear() { value = null }
}

@OptIn(kotlin.time.ExperimentalTime::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptedSessionManagerTest {
    private val session = UserSession(
        accessToken = "eyJhbGciOiJIUzI1NiJ9.ACCESS-TOKEN-SECRET.sig",
        refreshToken = "REFRESH-TOKEN-SECRET",
        expiresIn = 3600,
        tokenType = "bearer",
        user = null,
    )

    @Test fun sessionRoundTripsAndIsEncryptedAtRest() = runTest {
        val store = MemoryStore()
        val manager = EncryptedSessionManager(store, SoftwareAesGcm())
        manager.saveSession(session)

        val stored = store.value!!
        assertFalse("access token stored in clear text", stored.contains("ACCESS-TOKEN-SECRET"))
        assertFalse("refresh token stored in clear text", stored.contains("REFRESH-TOKEN-SECRET"))

        val loaded = manager.loadSession()!!
        assertEquals(session.accessToken, loaded.accessToken)
        assertEquals(session.refreshToken, loaded.refreshToken)
    }

    @Test fun undecryptableBlobIsDiscarded() = runTest {
        val store = MemoryStore()
        EncryptedSessionManager(store, SoftwareAesGcm()).saveSession(session)
        // A different key (e.g. keystore wiped after a reset) cannot read it → treated as signed out.
        val other = EncryptedSessionManager(store, SoftwareAesGcm())
        assertNull(other.loadSession())
        assertNull(store.value)
    }

    @Test fun deleteRemovesTheSession() = runTest {
        val store = MemoryStore()
        val manager = EncryptedSessionManager(store, SoftwareAesGcm())
        manager.saveSession(session)
        manager.deleteSession()
        assertNull(store.value)
        assertNull(manager.loadSession())
    }
}
