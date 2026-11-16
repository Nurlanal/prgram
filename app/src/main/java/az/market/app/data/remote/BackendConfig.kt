package az.market.app.data.remote

/**
 * Supabase connection settings baked in at build time from local.properties / CI env.
 * Only the PUBLIC (anon / publishable) key may ever be here — the build fails otherwise.
 */
data class BackendConfig(val url: String, val anonKey: String) {

    val isConfigured: Boolean
        get() = url.matches(URL_PATTERN) && anonKey.isNotBlank() && !isSecretKey(anonKey)

    companion object {
        private val URL_PATTERN = Regex("^https://[A-Za-z0-9.-]+(:[0-9]+)?/?$")

        /**
         * True for keys that must never ship in an APK: the new-style `sb_secret_…` keys and
         * legacy JWT keys whose payload carries `"role":"service_role"`.
         */
        fun isSecretKey(key: String): Boolean {
            if (key.startsWith("sb_secret_")) return true
            val parts = key.split('.')
            if (parts.size != 3) return false
            val payload = runCatching { decodeBase64Url(parts[1]) }.getOrNull() ?: return false
            return Regex("\"role\"\\s*:\\s*\"service_role\"").containsMatchIn(payload)
        }

        private fun decodeBase64Url(segment: String): String =
            String(java.util.Base64.getUrlDecoder().decode(segment), Charsets.UTF_8)
    }
}
