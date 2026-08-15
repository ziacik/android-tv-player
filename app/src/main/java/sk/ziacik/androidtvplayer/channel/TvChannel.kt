package sk.ziacik.androidtvplayer.channel

enum class TvChannel(
    val storageKey: String,
    val stvrId: String?,
    val displayName: String,
    val provider: ChannelProvider,
    val providerValue: String? = null,
) {
    JEDNOTKA("jednotka", "1", "JEDNOTKA", ChannelProvider.STVR),
    DVOJKA("dvojka", "2", "DVOJKA", ChannelProvider.STVR),
    MARKIZA("markiza", null, "MARKÍZA", ChannelProvider.MARKIZA, "https://www.markiza.sk/live/1-markiza"),
    STVR_24("stvr-24", "3", "STVR :24", ChannelProvider.STVR),
    STVR_SPORT("stvr-sport", "15", "STVR ŠPORT", ChannelProvider.STVR),
    STVR_LIVE_O("stvr-live-o", "4", "LIVE :O", ChannelProvider.STVR),
    STVR_LIVE("stvr-live", "6", "LIVE STVR", ChannelProvider.STVR),
    NRSR("nrsr", "5", "LIVE NRSR", ChannelProvider.STVR),
    JOJ("joj", null, "JOJ", ChannelProvider.JOJ, "joj"),
    JOJ_PLUS("joj-plus", null, "JOJ PLUS", ChannelProvider.JOJ, "plus"),
    JOJ_KRIMI("joj-krimi", null, "JOJ KRIMI", ChannelProvider.JOJ, "wau"),
    JOJ_SPORT("joj-sport", null, "JOJ ŠPORT", ChannelProvider.JOJ, "jojsport"),
    JOJ_SPORT_2("joj-sport-2", null, "JOJ ŠPORT 2", ChannelProvider.JOJ, "jojsport2"),
    JOJ_FAMILY("joj-family", null, "JOJ FAMILY", ChannelProvider.JOJ, "family"),
    JOJKO("jojko", null, "JOJKO", ChannelProvider.JOJ, "jojko"),
    JOJ_24("joj-24", null, "JOJ 24", ChannelProvider.JOJ, "joj24"),
    JOJ_CINEMA("joj-cinema", null, "JOJ CINEMA", ChannelProvider.JOJ, "jojcinema"),
    CS_FILM("cs-film", null, "CS FILM", ChannelProvider.JOJ, "csfilm"),
    CS_HISTORY("cs-history", null, "CS HISTORY", ChannelProvider.JOJ, "cshistory"),
    CS_MYSTERY("cs-mystery", null, "CS MYSTERY", ChannelProvider.JOJ, "csmystery"),
    DOMA("doma", null, "DOMA", ChannelProvider.MARKIZA, "https://www.markiza.sk/live/3-doma"),
    DAJTO("dajto", null, "DAJTO", ChannelProvider.MARKIZA, "https://www.markiza.sk/live/2-dajto"),
    MARKIZA_KRIMI("markiza-krimi", null, "MARKÍZA KRIMI", ChannelProvider.MARKIZA, "https://www.markiza.sk/live/22-krimi"),
    MARKIZA_KLASIK("markiza-klasik", null, "MARKÍZA KLASIK", ChannelProvider.MARKIZA, "https://www.markiza.sk/live/44-klasik"),
    CT_1("ct-1", null, "ČT1", ChannelProvider.CT, "CH_1"),
    CT_2("ct-2", null, "ČT2", ChannelProvider.CT, "CH_2"),
    CT_24("ct-24", null, "ČT24", ChannelProvider.CT, "CH_24"),
    CT_SPORT("ct-sport", null, "ČT SPORT", ChannelProvider.CT, "CH_4"),
    CT_D_ART("ct-d-art", null, "ČT :D/ART", ChannelProvider.CT),
    TA3("ta3", null, "TA3", ChannelProvider.TA3),
    NOVA_CINEMA("nova-cinema", null, "NOVA CINEMA", ChannelProvider.NOVA),
    CNN_PRIMA_NEWS("cnn-prima-news", null, "CNN PRIMA NEWS", ChannelProvider.CNN_PRIMA_NEWS),
    SZTS("szts", null, "SZTŠ", ChannelProvider.DIRECT, "https://dash2.antik.sk/live/tanecnesutaze/index.m3u8");

    fun next(): TvChannel = entries[(ordinal + 1) % entries.size]

    fun previous(): TvChannel = entries[(ordinal - 1 + entries.size) % entries.size]

    companion object {
        fun fromStorageKey(key: String?): TvChannel =
            entries.firstOrNull { it.storageKey == key } ?: JEDNOTKA
    }
}

enum class ChannelProvider {
    STVR,
    MARKIZA,
    JOJ,
    CT,
    TA3,
    NOVA,
    CNN_PRIMA_NEWS,
    DIRECT,
}
