package az.market.app.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json

/** Symmetric encryption used for the stored session. */
interface SessionCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a non-exportable key in the Android Keystore. The key never leaves the
 * secure hardware / keystore daemon; the stored blob is IV (12 bytes) + ciphertext + tag.
 */
class KeystoreSessionCipher(private val alias: String = "market_session_key") : SessionCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, 12))
        return cipher.doFinal(sealed, 12, sealed.size - 12)
    }
}

/** Minimal key/value storage so the manager can be unit-tested without Android. */
interface BlobStore {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

class SharedPrefsBlobStore(context: Context) : BlobStore {
    private val prefs = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString(KEY, null)
    override fun write(value: String) = prefs.edit { putString(KEY, value) }
    override fun clear() = prefs.edit { remove(KEY) }
    private companion object { const val KEY = "session" }
}

/**
 * Stores the Supabase session (access + refresh token) encrypted at rest.
 * Passwords are never stored anywhere on the device. A blob that cannot be decrypted
 * (e.g. key wiped after a device reset) is discarded, which simply signs the user out.
 */
class EncryptedSessionManager(
    private val store: BlobStore,
    private val cipher: SessionCipher,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        val plain = json.encodeToString(UserSession.serializer(), session).encodeToByteArray()
        store.write(Base64.encodeToString(cipher.encrypt(plain), Base64.NO_WRAP))
    }

    override suspend fun loadSession(): UserSession? {
        val stored = store.read() ?: return null
        return runCatching {
            val plain = cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP))
            json.decodeFromString(UserSession.serializer(), plain.decodeToString())
        }.getOrElse {
            store.clear()
            null
        }
    }

    override suspend fun deleteSession() = store.clear()
}

/**
 * PKCE code verifier storage. The app uses no redirect-based (PKCE/OAuth) flows — registration and recovery
 * use SMS / e-mail codes — so the verifier only needs to live in memory. This also stops supabase-kt from
 * creating its default *unencrypted* settings file on disk.
 */
class MemoryCodeVerifierCache : io.github.jan.supabase.auth.CodeVerifierCache {
    @Volatile private var verifier: String? = null
    override suspend fun saveCodeVerifier(codeVerifier: String) { verifier = codeVerifier }
    override suspend fun loadCodeVerifier(): String? = verifier
    override suspend fun deleteCodeVerifier() { verifier = null }
}
