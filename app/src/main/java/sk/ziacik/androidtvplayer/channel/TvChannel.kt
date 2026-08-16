package sk.ziacik.androidtvplayer.channel

enum class EpgSourceId {
    SKYLINK,
    IPTV_ORG,
}

enum class TvChannel(
    val storageKey: String,
    val stvrId: String?,
    val displayName: String,
    val provider: ChannelProvider,
    val providerValue: String? = null,
    val epgIds: Map<EpgSourceId, String> = emptyMap(),
) {
    JEDNOTKA("jednotka", "1", "JEDNOTKA", ChannelProvider.STVR, epgIds = mapOf(EpgSourceId.SKYLINK to "7a55634018be710a62bbf1750443a199", EpgSourceId.IPTV_ORG to "JEDNOTKA.cz")),
    DVOJKA("dvojka", "2", "DVOJKA", ChannelProvider.STVR, epgIds = mapOf(EpgSourceId.SKYLINK to "584c86a108172efc9c27af06eb3d4652", EpgSourceId.IPTV_ORG to "DVOJKA.cz")),
    MARKIZA(
        "markiza",
        null,
        "MARKÍZA",
        ChannelProvider.MARKIZA,
        "https://www.markiza.sk/live/1-markiza",
        epgIds = mapOf(EpgSourceId.SKYLINK to "336e46bf4276e77a716e494c6285d5db", EpgSourceId.IPTV_ORG to "MARKÍZA.cz"),
    ),
    STVR_24("stvr-24", "3", "STVR :24", ChannelProvider.STVR, epgIds = mapOf(EpgSourceId.SKYLINK to "6a5461ba82cabcb95db5b344e6440e15", EpgSourceId.IPTV_ORG to "STV_24.cz")),
    STVR_SPORT("stvr-sport", "15", "STVR ŠPORT", ChannelProvider.STVR, epgIds = mapOf(EpgSourceId.SKYLINK to "09fc492d319c8813389e11c167b3a053", EpgSourceId.IPTV_ORG to "RTVSSPORT.cz")),
    STVR_LIVE_O("stvr-live-o", "4", "LIVE :O", ChannelProvider.STVR),
    STVR_LIVE("stvr-live", "6", "LIVE STVR", ChannelProvider.STVR),
    NRSR("nrsr", "5", "LIVE NRSR", ChannelProvider.STVR),
    JOJ("joj", null, "JOJ", ChannelProvider.JOJ, "joj", mapOf(EpgSourceId.SKYLINK to "84a2364ade7443e6d6afe03f9aa2361a", EpgSourceId.IPTV_ORG to "JOJ.cz")),
    JOJ_PLUS("joj-plus", null, "JOJ PLUS", ChannelProvider.JOJ, "plus", mapOf(EpgSourceId.SKYLINK to "2aa59e1b0cb34399f6ffc387e52a437b", EpgSourceId.IPTV_ORG to "JOJPLUS.cz")),
    JOJ_KRIMI("joj-krimi", null, "JOJ KRIMI", ChannelProvider.JOJ, "wau", mapOf(EpgSourceId.SKYLINK to "d4bf459e0c7e39dff6a5dbf3c7c76432", EpgSourceId.IPTV_ORG to "WAU.cz")),
    JOJ_SPORT("joj-sport", null, "JOJ ŠPORT", ChannelProvider.JOJ, "jojsport", mapOf(EpgSourceId.SKYLINK to "eacbd3c220de39cfa7c855755d1291cf", EpgSourceId.IPTV_ORG to "JOJŠPORT.cz")),
    JOJ_SPORT_2("joj-sport-2", null, "JOJ ŠPORT 2", ChannelProvider.JOJ, "jojsport2", mapOf(EpgSourceId.SKYLINK to "755362842dc7526d01a6706c66dd1ec5", EpgSourceId.IPTV_ORG to "JOJŠPORT2.cz")),
    JOJ_FAMILY("joj-family", null, "JOJ FAMILY", ChannelProvider.JOJ, "family", mapOf(EpgSourceId.IPTV_ORG to "JOJFAMILY.cz")),
    JOJKO("jojko", null, "JOJKO", ChannelProvider.JOJ, "jojko", mapOf(EpgSourceId.SKYLINK to "c562602b114a9614998fbb29142f4017", EpgSourceId.IPTV_ORG to "JOJKO.cz")),
    JOJ_24("joj-24", null, "JOJ 24", ChannelProvider.JOJ, "joj24", mapOf(EpgSourceId.SKYLINK to "ce6596c6d0663e92b84184a09fa033a0", EpgSourceId.IPTV_ORG to "JOJ24.cz")),
    JOJ_CINEMA("joj-cinema", null, "JOJ CINEMA", ChannelProvider.JOJ, "jojcinema", mapOf(EpgSourceId.SKYLINK to "d214870d446a92f77571e873cfb90083", EpgSourceId.IPTV_ORG to "JOJCINEMA.cz")),
    CS_FILM("cs-film", null, "CS FILM", ChannelProvider.JOJ, "csfilm", mapOf(EpgSourceId.SKYLINK to "5a70949544b2800b09499525e005a6c5", EpgSourceId.IPTV_ORG to "CSFILM,MINI.cz")),
    CS_HISTORY("cs-history", null, "CS HISTORY", ChannelProvider.JOJ, "cshistory", mapOf(EpgSourceId.SKYLINK to "e960740fb6ac73b8b55d7767af74aef9", EpgSourceId.IPTV_ORG to "CSHistory.cz")),
    CS_MYSTERY("cs-mystery", null, "CS MYSTERY", ChannelProvider.JOJ, "csmystery", mapOf(EpgSourceId.SKYLINK to "e4854cf51b10c03d9c4ceb9a2c55dc7f", EpgSourceId.IPTV_ORG to "CSMystery.cz")),
    DOMA("doma", null, "DOMA", ChannelProvider.MARKIZA, "https://www.markiza.sk/live/3-doma", mapOf(EpgSourceId.SKYLINK to "2499ae6dc5ab84ae26d72ddf3cfe2147", EpgSourceId.IPTV_ORG to "DOMA.cz")),
    DAJTO("dajto", null, "DAJTO", ChannelProvider.MARKIZA, "https://www.markiza.sk/live/2-dajto", mapOf(EpgSourceId.SKYLINK to "8a70396a7c6f6894cb24a3d30f24e5dd", EpgSourceId.IPTV_ORG to "DAJTO.cz")),
    MARKIZA_KRIMI("markiza-krimi", null, "MARKÍZA KRIMI", ChannelProvider.MARKIZA, "https://www.markiza.sk/live/22-krimi", mapOf(EpgSourceId.SKYLINK to "aa3d3d3842d31ad8771ae3c0b9c36b93", EpgSourceId.IPTV_ORG to "MARKIZA_KRIMI.cz")),
    MARKIZA_KLASIK("markiza-klasik", null, "MARKÍZA KLASIK", ChannelProvider.MARKIZA, "https://www.markiza.sk/live/44-klasik", mapOf(EpgSourceId.SKYLINK to "3bfc51074799dc287b11188784cad123", EpgSourceId.IPTV_ORG to "MarkizaKlasik.cz")),
    CT_1("ct-1", null, "ČT1", ChannelProvider.CT, "CH_1", mapOf(EpgSourceId.SKYLINK to "8be503ba653db9521d189238b85779ea", EpgSourceId.IPTV_ORG to "ČT1.cz")),
    CT_2("ct-2", null, "ČT2", ChannelProvider.CT, "CH_2", mapOf(EpgSourceId.SKYLINK to "ad7510730e0284d393651d5fdb351701", EpgSourceId.IPTV_ORG to "ČT2.cz")),
    CT_24("ct-24", null, "ČT24", ChannelProvider.CT, "CH_24", mapOf(EpgSourceId.SKYLINK to "d8372cf312342c51a7e7e4506192b1b2", EpgSourceId.IPTV_ORG to "ČT24.cz")),
    CT_SPORT("ct-sport", null, "ČT SPORT", ChannelProvider.CT, "CH_4", mapOf(EpgSourceId.SKYLINK to "1bc1f61784f6d0048e7d53cdb3d257ca", EpgSourceId.IPTV_ORG to "ČTsport.cz")),
    CT_D_ART("ct-d-art", null, "ČT :D/ART", ChannelProvider.CT, epgIds = mapOf(EpgSourceId.SKYLINK to "24cbbb52506f29852ba10e724d82e8d4", EpgSourceId.IPTV_ORG to "ČT:D/ČTart.cz")),
    TA3("ta3", null, "TA3", ChannelProvider.TA3, epgIds = mapOf(EpgSourceId.SKYLINK to "76ec128b489da6c0e8b34681cba69914", EpgSourceId.IPTV_ORG to "TA3.cz")),
    NOVA_CINEMA("nova-cinema", null, "NOVA CINEMA", ChannelProvider.NOVA, epgIds = mapOf(EpgSourceId.SKYLINK to "67ec56c6158e47151726a025a62a1a29", EpgSourceId.IPTV_ORG to "NOVACINEMA.cz")),
    CNN_PRIMA_NEWS("cnn-prima-news", null, "CNN PRIMA NEWS", ChannelProvider.CNN_PRIMA_NEWS, epgIds = mapOf(EpgSourceId.SKYLINK to "0e08d2e2bbfc45c17e13f8be100c2118", EpgSourceId.IPTV_ORG to "CNNPRIMANEWS.cz")),
    SZTS("szts", null, "SZTŠ", ChannelProvider.DIRECT, "https://dash2.antik.sk/live/tanecnesutaze/index.m3u8"),
    WILD_EARTH("wild-earth", null, "WILD EARTH", ChannelProvider.DIRECT, "https://dqga3jatxofgx.cloudfront.net/WildEarth.m3u8"),
    WATERBEAR("waterbear", null, "WATERBEAR", ChannelProvider.DIRECT, "https://waterbear-waterbear-rakuten.amagi.tv/playlist.m3u8"),
    LOVE_NATURE("love-nature", null, "LOVE NATURE", ChannelProvider.DIRECT, "https://aegis-cloudfront-1.tubi.video/6d6d0f24-8445-4b4c-bdf6-44f9e38beaa4/playlist.m3u8", mapOf(EpgSourceId.SKYLINK to "87a9a0429e3edc255adbb8601cfddc0a", EpgSourceId.IPTV_ORG to "LoveNature.cz")),
    GUSTO_TV("gusto-tv", null, "GUSTO TV", ChannelProvider.DIRECT, "https://563f72af.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLWV1X0d1c3RvVFZfSExT/playlist.m3u8"),
    TASTEMADE("tastemade", null, "TASTEMADE", ChannelProvider.DIRECT, "https://tastemade-tdint-rakuten.amagi.tv/playlist.m3u8"),
    TV5MONDE_CHEFS("tv5monde-chefs", null, "TV5MONDE CHEFS", ChannelProvider.DIRECT, "https://tvf-tv5ch.otteravision.com/tvf/tv5ch/tv5ch.m3u8"),
    BBC_FOOD("bbc-food", null, "BBC FOOD", ChannelProvider.DIRECT, "https://d1e9r0b71zfwk7.cloudfront.net/playlist.m3u8");

    fun next(): TvChannel = entries[(ordinal + 1) % entries.size]

    fun previous(): TvChannel = entries[(ordinal - 1 + entries.size) % entries.size]

    companion object {
        fun fromChannelNumber(number: Int): TvChannel? =
            entries.getOrNull(number - 1)

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
