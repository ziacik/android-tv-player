package sk.ziacik.androidtvplayer.channel

import android.content.Context

class SharedPreferencesChannelStore(context: Context) : ChannelStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(catalog: ChannelCatalog): TvChannel =
        catalog.fromStorageKey(preferences.getString(SELECTED_CHANNEL_KEY, null))

    override fun save(channel: TvChannel) {
        preferences.edit().putString(SELECTED_CHANNEL_KEY, channel.storageKey).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "player_preferences"
        const val SELECTED_CHANNEL_KEY = "selected_channel"
    }
}
