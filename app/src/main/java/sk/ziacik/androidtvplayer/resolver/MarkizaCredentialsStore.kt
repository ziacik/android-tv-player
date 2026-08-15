package sk.ziacik.androidtvplayer.resolver

import android.content.Context

interface MarkizaCredentialsStore {
    fun load(): MarkizaCredentials?

    fun save(credentials: MarkizaCredentials)
}

class SharedPreferencesMarkizaCredentialsStore(context: Context) : MarkizaCredentialsStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): MarkizaCredentials? {
        val email = preferences.getString(EMAIL_KEY, null)?.trim().orEmpty()
        val password = preferences.getString(PASSWORD_KEY, null).orEmpty()
        return MarkizaCredentials(email, password).takeIf {
            it.email.isNotBlank() && it.password.isNotBlank()
        }
    }

    override fun save(credentials: MarkizaCredentials) {
        preferences.edit()
            .putString(EMAIL_KEY, credentials.email.trim())
            .putString(PASSWORD_KEY, credentials.password)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "markiza_credentials"
        const val EMAIL_KEY = "email"
        const val PASSWORD_KEY = "password"
    }
}
