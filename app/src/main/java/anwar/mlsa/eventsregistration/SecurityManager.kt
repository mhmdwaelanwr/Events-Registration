package anwar.mlsa.eventsregistration

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityManager {
    private const val PREFS_NAME = "secure_prefs"

    private fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Seeds the encrypted storage with values from BuildConfig if they haven't been saved yet.
     * This moves the keys from the compiled code (where they are vulnerable) into 
     * Keystore-backed encrypted storage on the first run.
     */
    fun seedConfigIfNeeded(context: Context) {
        val prefs = getEncryptedPrefs(context)
        if (!prefs.contains("HEDERA_PRIVATE_KEY")) {
            prefs.edit().apply {
                putString("HEDERA_ACCOUNT_ID", BuildConfig.HEDERA_ACCOUNT_ID)
                putString("HEDERA_PRIVATE_KEY", BuildConfig.HEDERA_PRIVATE_KEY)
                putString("HEDERA_TOPIC_ID", BuildConfig.HEDERA_TOPIC_ID)
                putString("BASE_URL", BuildConfig.BASE_URL)
                apply()
            }
        }
    }

    fun getConfig(context: Context, key: String): String {
        val prefs = getEncryptedPrefs(context)
        // Try to get from encrypted prefs first, fallback to BuildConfig if not seeded yet
        return prefs.getString(key, null) ?: when (key) {
            "HEDERA_ACCOUNT_ID" -> BuildConfig.HEDERA_ACCOUNT_ID
            "HEDERA_PRIVATE_KEY" -> BuildConfig.HEDERA_PRIVATE_KEY
            "HEDERA_TOPIC_ID" -> BuildConfig.HEDERA_TOPIC_ID
            "BASE_URL" -> BuildConfig.BASE_URL
            else -> ""
        }
    }
}
