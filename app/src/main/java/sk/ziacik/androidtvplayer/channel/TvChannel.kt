package sk.ziacik.androidtvplayer.channel

enum class EpgSourceId { OPEN_EPG, SKYLINK }

data class TvChannel(
    val storageKey: String,
    val stvrId: String? = null,
    val displayName: String,
    val provider: ChannelProvider,
    val providerValue: String? = null,
    val epgIds: Map<EpgSourceId, String> = emptyMap(),
) {
    fun next(): TvChannel {
        val index = entries.indexOfFirst { it.storageKey == storageKey }
        return entries[if (index >= 0) (index + 1) % entries.size else 0]
    }

    fun previous(): TvChannel {
        val index = entries.indexOfFirst { it.storageKey == storageKey }
        return entries[if (index >= 0) (index - 1 + entries.size) % entries.size else entries.lastIndex]
    }

    val ordinal: Int
        get() = entries.indexOfFirst { it.storageKey == storageKey }.takeIf { it >= 0 } ?: 0

    companion object {
        val JEDNOTKA = TvChannel("jednotka", "1", "JEDNOTKA", ChannelProvider.STVR, epgIds = mapOf(EpgSourceId.SKYLINK to "7a55634018be710a62bbf1750443a199"))
        val DVOJKA = TvChannel("dvojka", "2", "DVOJKA", ChannelProvider.STVR, epgIds = mapOf(EpgSourceId.SKYLINK to "584c86a108172efc9c27af06eb3d4652"))
        val MARKIZA = TvChannel("markiza", displayName = "MARKÍZA", provider = ChannelProvider.MARKIZA, epgIds = mapOf(EpgSourceId.SKYLINK to "336e46bf4276e77a716e494c6285d5db"))
        val STVR_24 = TvChannel("stvr-24", "3", "STVR :24", ChannelProvider.STVR, epgIds = mapOf(EpgSourceId.SKYLINK to "6a5461ba82cabcb95db5b344e6440e15"))
        val STVR_SPORT = TvChannel("stvr-sport", "15", "STVR ŠPORT", ChannelProvider.STVR, epgIds = mapOf(EpgSourceId.SKYLINK to "09fc492d319c8813389e11c167b3a053"))
        val JOJ = TvChannel("joj", displayName = "JOJ", provider = ChannelProvider.JOJ, providerValue = "joj", epgIds = mapOf(EpgSourceId.SKYLINK to "84a2364ade7443e6d6afe03f9aa2361a"))
        val JOJ_PLUS = TvChannel("joj-plus", displayName = "JOJ PLUS", provider = ChannelProvider.JOJ, providerValue = "plus", epgIds = mapOf(EpgSourceId.SKYLINK to "2aa59e1b0cb34399f6ffc387e52a437b"))
        val JOJ_KRIMI = TvChannel("joj-krimi", displayName = "JOJ KRIMI", provider = ChannelProvider.JOJ, providerValue = "wau", epgIds = mapOf(EpgSourceId.SKYLINK to "d4bf459e0c7e39dff6a5dbf3c7c76432"))
        val JOJ_SPORT = TvChannel("joj-sport", displayName = "JOJ ŠPORT", provider = ChannelProvider.JOJ, providerValue = "jojsport", epgIds = mapOf(EpgSourceId.SKYLINK to "eacbd3c220de39cfa7e7e4506192b1b2"))
        val JOJ_SPORT_2 = TvChannel("joj-sport-2", displayName = "JOJ ŠPORT 2", provider = ChannelProvider.JOJ, providerValue = "jojsport2", epgIds = mapOf(EpgSourceId.SKYLINK to "755362842dc7526d01a6706c66dd1ec5"))
        val JOJ_FAMILY = TvChannel("joj-family", displayName = "JOJ FAMILY", provider = ChannelProvider.JOJ, providerValue = "family")
        val JOJKO = TvChannel("jojko", displayName = "JOJKO", provider = ChannelProvider.JOJ, providerValue = "jojko", epgIds = mapOf(EpgSourceId.SKYLINK to "c562602b114a9614998fbb29142f4017"))
        val JOJ_24 = TvChannel("joj-24", displayName = "JOJ 24", provider = ChannelProvider.JOJ, providerValue = "joj24", epgIds = mapOf(EpgSourceId.SKYLINK to "ce6596c6d0663e92b84184a09fa033a0"))
        val JOJ_CINEMA = TvChannel("joj-cinema", displayName = "JOJ CINEMA", provider = ChannelProvider.JOJ, providerValue = "jojsport", epgIds = mapOf(EpgSourceId.SKYLINK to "d214870fb6ac73b8b55d7767af74aef9"))
        val CS_FILM = TvChannel("cs-film", displayName = "CS FILM", provider = ChannelProvider.JOJ, providerValue = "csfilm")
        val CS_HISTORY = TvChannel("cs-history", displayName = "CS HISTORY", provider = ChannelProvider.JOJ, providerValue = "cshistory")
        val CS_MYSTERY = TvChannel("cs-mystery", displayName = "CS MYSTERY", provider = ChannelProvider.JOJ, providerValue = "csmystery")
        val DOMA = TvChannel("doma", displayName = "DOMA", provider = ChannelProvider.MARKIZA)
        val DAJTO = TvChannel("dajto", displayName = "DAJTO", provider = ChannelProvider.MARKIZA)
        val MARKIZA_KRIMI = TvChannel("markiza-krimi", displayName = "MARKÍZA KRIMI", provider = ChannelProvider.MARKIZA)
        val MARKIZA_KLASIK = TvChannel("markiza-klasik", displayName = "MARKÍZA KLASIK", provider = ChannelProvider.MARKIZA)
        val CT_1 = TvChannel("ct-1", displayName = "ČT1", provider = ChannelProvider.CT, providerValue = "CH_1")
        val CT_2 = TvChannel("ct-2", displayName = "ČT2", provider = ChannelProvider.CT, providerValue = "CH_2")
        val CT_24 = TvChannel("ct-24", displayName = "ČT24", provider = ChannelProvider.CT, providerValue = "CH_24")
        val CT_SPORT = TvChannel("ct-sport", displayName = "ČT SPORT", provider = ChannelProvider.CT, providerValue = "CH_4")
        val CT_D_ART = TvChannel("ct-d-art", displayName = "ČT :D/ART", provider = ChannelProvider.CT)
        val NOVA_CINEMA = TvChannel("nova-cinema", displayName = "NOVA CINEMA", provider = ChannelProvider.NOVA)
        val CNN_PRIMA_NEWS = TvChannel("cnn-prima-news", displayName = "CNN PRIMA NEWS", provider = ChannelProvider.CNN_PRIMA_NEWS)
        val WATERBEAR = TvChannel("waterbear", displayName = "WATERBEAR", provider = ChannelProvider.DIRECT)
        val LOVE_NATURE = TvChannel("love-nature", displayName = "LOVE NATURE", provider = ChannelProvider.DIRECT, epgIds = mapOf(EpgSourceId.SKYLINK to "87a9a0429e3edc255adbb8601cfddc0a"))
        val GUSTO_TV = TvChannel("gusto-tv", displayName = "GUSTO TV", provider = ChannelProvider.DIRECT)
        val TASTEMADE = TvChannel("tastemade", displayName = "TASTEMADE", provider = ChannelProvider.DIRECT)
        val TV5MONDE_CHEFS = TvChannel("tv5monde-chefs", displayName = "TV5MONDE CHEFS", provider = ChannelProvider.DIRECT)
        val PAPRIKA_TV = TvChannel("paprika-tv", displayName = "PAPRIKA TV", provider = ChannelProvider.DIRECT)
        val SVET_NARUBY = TvChannel("svet-naruby", displayName = "SVET NARUBY", provider = ChannelProvider.SWEET_TV, providerValue = "3257")
        val MEN_WITH_THE_POT = TvChannel("men-with-the-pot", displayName = "MEN WITH THE POT", provider = ChannelProvider.SWEET_TV, providerValue = "2648")
        val sweetTvChannels = listOf(MEN_WITH_THE_POT, SVET_NARUBY)
        @Volatile var entries = listOf(JEDNOTKA, DVOJKA, MARKIZA, STVR_24, STVR_SPORT, JOJ, JOJ_PLUS, JOJ_KRIMI, JOJ_SPORT, JOJ_SPORT_2, JOJ_FAMILY, JOJKO, JOJ_24, JOJ_CINEMA, CS_FILM, CS_HISTORY, CS_MYSTERY, DOMA, DAJTO, MARKIZA_KRIMI, MARKIZA_KLASIK, CT_1, CT_2, CT_24, CT_SPORT, CT_D_ART, NOVA_CINEMA, CNN_PRIMA_NEWS, WATERBEAR, LOVE_NATURE, GUSTO_TV, TASTEMADE, TV5MONDE_CHEFS, PAPRIKA_TV) + sweetTvChannels
            private set
        fun setRuntimeEntries(channels: List<TvChannel>) { entries = channels }
        fun fromChannelNumber(number: Int): TvChannel? = entries.getOrNull(number - 1)
        fun fromStorageKey(key: String?): TvChannel = entries.firstOrNull { it.storageKey == key } ?: JEDNOTKA
    }
}

enum class ChannelProvider { STVR, MARKIZA, JOJ, CT, TA3, NOVA, CNN_PRIMA_NEWS, SWEET_TV, DIRECT }
