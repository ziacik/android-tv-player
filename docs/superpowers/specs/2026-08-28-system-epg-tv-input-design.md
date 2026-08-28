# Kanálik system EPG TV input design

## Goal

Expose Kanálik's runtime channel catalogue and XMLTV schedule in the Philips
system TV guide. Selecting a Kanálik channel there must play it directly in the
system TV application, without opening Kanálik's Compose activity.

## Scope

Implement a regular Android `TvInputService` backed by the existing channel
catalogue, stream resolvers, Media3 dependencies, and XMLTV EPG sources.

The system-TV integration owns only its rows in Android's TV provider. The
existing standalone Kanálik player remains available and continues to use the
same catalogue and resolver behavior.

## Architecture

### Shared runtime dependencies

Extract application construction currently located in `MainActivity` into an
app-scoped factory. Both the activity and TV input service obtain:

- the versioned `ChannelCatalogRepository` and runtime `ChannelCatalog`;
- `ChannelResolver`, including the existing provider-specific resolvers;
- `CachedXmltvEpgRepository` using Open-EPG with Skylink fallback; and
- a refresh operation that updates the runtime catalogue.

The factory does not own a Media3 player. Each UI activity and each active TV
input session owns and releases its own player.

### System guide synchronizer

`SystemEpgSynchronizer` maps each `TvChannel` to one row in
`TvContract.Channels` for Kanálik's input ID. It stores the stable
`storageKey` as its internal provider ID/data and uses the catalogue order as
the displayed channel number.

It maps the XMLTV programmes for each channel into `TvContract.Programs` with
title, optional description, and UTC start/end milliseconds. It upserts rows
by the Kanálik channel/programme identities and deletes only stale rows owned
by this input. It never modifies tuner, HDMI, or another app's rows.

Synchronization runs when Android requests program initialization, when the
TV input service starts, after a successful catalogue refresh, and through
scheduled background work. A failure keeps the last successfully published
guide and is retried later.

### Direct TV playback

`KanálikTvInputService` declares the Android TV input service and creates a
`KanálikTvInputSession` for the system TV application.

When the system tunes a channel URI, the session resolves the stored
`storageKey` to the current runtime catalogue channel, resolves its stream
with the shared `ChannelResolver`, and creates a session-local Media3 player.
The player renders to the `Surface` supplied by Android and reports normal
tuning/video availability callbacks to the system.

Changing channels, losing the surface, stopping playback, or releasing the
session cancels outstanding resolution and releases the session player. A
stream-resolution failure or online-restricted programme reports video
unavailable; it never starts Kanálik's activity and never crashes the system
TV application.

## Manifest and user behavior

The manifest registers the TV input service with `BIND_TV_INPUT`, required
TV-input metadata, and the receiver/permissions Android requires to initialize
channel and programme data. The input is identifiable as Kanálik in the
system's source list. The user may need to enable the new source once in TV
settings; the implementation does not attempt to change that user choice.

## Data lifecycle

1. The service loads the bundled/cached catalogue and publishes its channels.
2. It refreshes catalogue and EPG data off the main thread.
3. It publishes valid programme intervals for mapped channels and preserves the
   previous provider rows if a refresh fails.
4. A system-TV tune reads the channel identity from the provider row and
   resolves against the current catalogue, so reordered catalogue positions do
   not break playback.

## Testing and verification

- Unit-test channel/provider mapping, upserts, stale-row deletion scoping, and
  programme UTC timestamps.
- Unit-test session tuning for direct streams, resolver failure, and restricted
  programmes with a fake player/session surface.
- Build and install the APK on the Philips TPM191E.
- Verify with Android TV input/provider diagnostics that Kanálik is registered
  and only its own channel/programme rows exist.
- On the physical TV, enable Kanálik, open the native guide, verify programme
  display, choose a channel, and confirm video renders in the system TV app.

## Non-goals

- Replacing the Philips tuner, modifying Philips-owned guide rows, recording,
  timeshift, or parental-control implementation.
- Altering the existing Kanálik Compose player UI or channel catalogue format
  beyond data needed to publish system-guide metadata.
