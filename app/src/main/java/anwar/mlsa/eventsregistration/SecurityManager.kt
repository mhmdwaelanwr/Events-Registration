package anwar.mlsa.eventsregistration

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityManager {
    private const val PREFS_NAME = "secure_prefs"
    private const val IS_AUTHORIZED_KEY = "is_authorized"
    private const val IS_MASTER_KEY_USED = "is_master_key_used"
    private const val LAST_AUTHORIZED_KEY = "last_authorized_key"

    private fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun seedConfigIfNeeded(context: Context) {
        val prefs = getEncryptedPrefs(context)
        if (!prefs.contains("HEDERA_PRIVATE_KEY")) {
            prefs.edit().apply {
                putString("HEDERA_ACCOUNT_ID", BuildConfig.HEDERA_ACCOUNT_ID)
                putString("HEDERA_PRIVATE_KEY", BuildConfig.HEDERA_PRIVATE_KEY)
                putString("HEDERA_TOPIC_ID", BuildConfig.HEDERA_TOPIC_ID)
                putString("BASE_URL", BuildConfig.BASE_URL)
                putString("APP_ACCESS_KEY", BuildConfig.APP_ACCESS_KEY)
                putString("REMOTE_CONFIG_URL", BuildConfig.REMOTE_CONFIG_URL)
                apply()
            }
        }
    }

    fun getConfig(context: Context, key: String): String {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(key, null) ?: when (key) {
            "HEDERA_ACCOUNT_ID" -> BuildConfig.HEDERA_ACCOUNT_ID
            "HEDERA_PRIVATE_KEY" -> BuildConfig.HEDERA_PRIVATE_KEY
            "HEDERA_TOPIC_ID" -> BuildConfig.HEDERA_TOPIC_ID
            "BASE_URL" -> BuildConfig.BASE_URL
            "APP_ACCESS_KEY" -> BuildConfig.APP_ACCESS_KEY
            "REMOTE_CONFIG_URL" -> BuildConfig.REMOTE_CONFIG_URL
            else -> ""
        }
    }

    fun isAuthorized(context: Context): Boolean = getEncryptedPrefs(context).getBoolean(IS_AUTHORIZED_KEY, false)

    fun isMasterKeyUsed(context: Context): Boolean = getEncryptedPrefs(context).getBoolean(IS_MASTER_KEY_USED, false)

    fun getLastKey(context: Context): String = getEncryptedPrefs(context).getString(LAST_AUTHORIZED_KEY, "") ?: ""

    fun setAuthorized(context: Context, authorized: Boolean, isMaster: Boolean = false, key: String = "") {
        getEncryptedPrefs(context).edit().apply {
            putBoolean(IS_AUTHORIZED_KEY, authorized)
            putBoolean(IS_MASTER_KEY_USED, isMaster)
            putString(LAST_AUTHORIZED_KEY, key)
            apply()
        }
    }
}
