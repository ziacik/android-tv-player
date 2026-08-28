package sk.ziacik.androidtvplayer.systemepg

import android.content.ContentResolver
import android.content.ContentValues
import android.content.ComponentName
import android.content.Context
import android.media.tv.TvContract
import android.util.Log
import sk.ziacik.androidtvplayer.channel.ChannelCatalog
import sk.ziacik.androidtvplayer.epg.EpgProgramme

class SystemEpgPublisher(private val context: Context) {
    private val inputId = TvContract.buildInputId(
        ComponentName(context, KanalikTvInputService::class.java),
    )

    suspend fun publish(catalog: ChannelCatalog) {
        val channelUris = publishChannels(catalog)
        publishProgrammes(catalog, channelUris)
    }

    suspend fun publishProgrammes(
        catalog: ChannelCatalog,
        channelUris: Map<String, android.net.Uri>,
    ) {
        Log.i("SystemEpg", "Published ${channelUris.size} channels; loading programme schedules")
        val programmes = SystemEpgScheduleLoader(context.filesDir).load(catalog.channels)
        channelUris.forEach { (storageKey, channelUri) ->
            publishProgrammes(channelUri, programmes[storageKey].orEmpty())
        }
    }

    fun publishChannels(catalog: ChannelCatalog): Map<String, android.net.Uri> {
        val existing = existingChannelIds(context.contentResolver)
        val publishedUris = mutableMapOf<String, android.net.Uri>()
        catalog.channels.forEachIndexed { index, channel ->
            val published = SystemEpgChannel.from(channel, index + 1)
            val values = ContentValues().apply {
                put(TvContract.Channels.COLUMN_INPUT_ID, inputId)
                put(TvContract.Channels.COLUMN_DISPLAY_NAME, published.displayName)
                put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, published.displayNumber)
                put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, published.storageKey)
                put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_OTHER)
                put(TvContract.Channels.COLUMN_SERVICE_TYPE, TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO)
            }
            val channelUri = existing.remove(published.storageKey)?.let { uri ->
                context.contentResolver.update(uri, values, null, null)
                uri
            } ?: requireNotNull(context.contentResolver.insert(TvContract.Channels.CONTENT_URI, values))
            publishedUris[published.storageKey] = channelUri
        }
        existing.values.forEach { uri -> context.contentResolver.delete(uri, null, null) }
        Log.i("SystemEpg", "Channel provider synchronization completed with ${publishedUris.size} rows")
        return publishedUris
    }

    private fun publishProgrammes(channelUri: android.net.Uri, programmes: List<EpgProgramme>) {
        val resolver = context.contentResolver
        resolver.delete(TvContract.buildProgramsUriForChannel(channelUri), null, null)
        programmes.forEach { programme ->
            resolver.insert(
                TvContract.Programs.CONTENT_URI,
                ContentValues().apply {
                    put(TvContract.Programs.COLUMN_CHANNEL_ID, channelUri.lastPathSegment?.toLong())
                    put(TvContract.Programs.COLUMN_TITLE, programme.title)
                    put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, programme.startsAtMs)
                    put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, programme.endsAtMs)
                },
            )
        }
    }

    private fun existingChannelIds(resolver: ContentResolver): MutableMap<String, android.net.Uri> {
        val uri = TvContract.buildChannelsUriForInput(inputId)
        return resolver.query(
            uri,
            arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val key = cursor.getString(1) ?: continue
                    put(key, TvContract.buildChannelUri(cursor.getLong(0)))
                }
            }.toMutableMap()
        } ?: mutableMapOf()
    }
}
