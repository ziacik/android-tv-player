# Freeview Lightweight Channels Design

## Goal

Add the active, low-effort Freeview playlist channels requested for the Android TV player. The implementation intentionally ports the provider flows from Freeview as they are; long-term stream stability is not a goal of this change.

The final catalogue adds 30 channels while retaining the existing Jednotka, Dvojka, and Markíza entries. The existing remote channel navigation, persisted selection, player lifecycle, and overlay stay unchanged.

## Included Channels

The new ordered channels are:

1. STVR :24
2. STVR Šport
3. STVR Live :O
4. STVR Live STVR
5. Live NRSR
6. JOJ
7. JOJ Plus
8. JOJ Krimi
9. JOJ Šport
10. JOJ Šport 2
11. JOJ Family
12. Jojko
13. JOJ 24
14. JOJ Cinema
15. CS Film
16. CS History
17. CS Mystery
18. Doma
19. Dajto
20. Markíza Krimi
21. Markíza Klasik
22. ČT1
23. ČT2
24. ČT24
25. ČT sport
26. ČT :D/art
27. TA3
28. Nova Cinema
29. CNN Prima News
30. SZTŠ

Music channels (Óčko, Óčko Expres, Óčko Star, Retro Music, and the four Vantage channels) and TV Doktor are excluded. Inactive Freeview playlist entries are also excluded. The Czech Prima channels other than CNN Prima News remain excluded because the plugin delegates them to an unspecified external proxy.

## Architecture

`TvChannel` becomes a complete, ordered static catalogue. Each entry has its persisted key, display label, source kind, and a provider-specific identifier or page URL. This preserves the existing wraparound channel navigation and SharedPreferences selection without adding a channel browser or runtime playlist download.

`ChannelResolver` routes by source kind rather than maintaining a growing `when` list of individual channels. It retains the existing STVR and Markíza resolvers and adds small resolvers for JOJ, ČT, TA3, Nova, CNN Prima News, and a direct HLS source. A shared cancellable OkHttp text/JSON client supplies the GET and JSON POST requests required by those resolvers; the existing cookie-preserving clients remain where their login flow requires them.

Provider behavior follows the Freeview source:

- STVR passes the additional channel IDs through the existing `live5f.json` resolver.
- Markíza maps the four new entries to their live-page URLs and reuses the existing credentials/login/embed/HLS sequence.
- JOJ resolves the channel source through the Tivio source endpoint, falling back to the provider's per-channel HLS URL when necessary.
- ČT calls the live-channel API and uses the returned `streamUrls.main`; `ČT :D/art` selects the appropriate API channel from the local hour as in Freeview.
- TA3 reads its small live-source script and extracts the matching HLS source.
- Nova Cinema reads the provider embed page and extracts its HLS source, including the Freeview request header behavior.
- CNN Prima News reads its public play endpoint and uses its first stream URL.
- SZTŠ is a direct HLS source.

Every successful resolver returns the current channel's display name as live program metadata. The player source continues to carry any provider-required User-Agent, Referer, Origin, or request headers.

## Errors and Playback

Network failures, malformed provider responses, and missing streams become the existing `StreamResolveException`, so `PlayerController` shows its current retryable error state. A resolver never updates the player directly; existing generation checks continue to protect against a response from a channel that the viewer already left.

The existing STVR restriction response remains specific to STVR. Other providers either produce a playable source or the generic error state. No source is persisted: every selection obtains a fresh source when it is resolved.

## Verification

Unit tests cover catalogue order and storage-key lookup, source-kind routing, and each provider's essential response parsing/request sequence. Existing player and remote-navigation tests run unchanged against the enlarged catalogue. The project then runs the Android unit-test task and a debug build; playback is manually checked on-device for a representative channel from each resolver.

## Out of Scope

- Music channels, TV Doktor, inactive playlist entries, and the proxy-dependent Prima channels.
- A channel picker, EPG, logos, favourites, background preloading, multiple players, or a remote playlist update mechanism.
- Improving source reliability, access restrictions, or provider APIs beyond the Freeview implementation.
