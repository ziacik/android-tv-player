# Remote direct-channel catalog

## Goal

Allow direct HLS channels to be added, removed, or corrected by editing one
public `channels.json` in this repository, without producing a new APK. Move
the existing direct entries and the channels replaced by Free-TV/IPTV links out
of Kotlin provider definitions.

## Source and format

The app downloads `channels.json` from the public `raw.githubusercontent.com`
URL for the repository's default branch. The exact same file is copied into the
APK as its initial offline catalogue during the Gradle build, avoiding a second
source of truth and allowing first launch without a network connection.

```json
{
  "version": 1,
  "channels": [
    {
      "id": "paprika-tv",
      "name": "PAPRIKA TV",
      "url": "http://88.212.15.19/live/test_parpika_tv_sd_hevc/playlist.m3u8",
      "epg": { "iptvOrg": "PaprikaTV.cz" }
    }
  ]
}
```

`id`, `name`, and an absolute `http` or `https` URL are required. IDs must be
unique. Unknown fields and invalid entries are ignored. Android cleartext is
enabled globally for direct HTTP playlists, so HTTP streams are allowed but are
not integrity-protected.

## Catalogue contents

The JSON carries:

- the six current direct entries, with Paprika changed to the supplied
  `test_parpika_tv_sd_hevc` URL;
- direct Free-TV/IPTV replacements for Markíza, Doma, Dajto, Markíza Krimi,
  Markíza Klasik; JEDNOTKA, DVOJKA, STVR :24, STVR ŠPORT; JOJ, JOJ Plus, JOJ
  Krimi/Wau, JOJ ŠPORT, JOJKO, JOJ 24, JOJ Family, JOJ Cinema, CS Film, CS
  History, and CS Mystery; ČT1, ČT2, ČT24, ČT Sport; Nova Cinema; and CNN
  Prima News;
- separate `ČT :D` and `ČT art` entries, replacing the time-switched `ČT
  :D/ART` provider channel.

JOJ ŠPORT 2 is removed. SVET NARUBY remains a bundled Sweet.tv channel and
continues to use its existing resolver.

The Markíza login integration is removed completely: its provider, resolver,
HTTP/cookie client, credential persistence/provider, credentials UI, and the
credentials-required UI state no longer exist. The five named Markíza channels
remain available only through the JSON links.

## Catalogue behaviour

The ordered runtime catalogue consists of bundled provider channels plus the
JSON entries in file order. Valid downloaded content replaces the locally
cached JSON atomically. On download or parsing failure, the app uses the last
valid cache; if there is none, it uses the copied-in APK catalogue. The player
therefore remains usable in every failure path.

The first version has no in-app editor or manual refresh; a JSON update takes
effect on the next application launch. Channel numbers follow the runtime
order, so catalogue edits can change the numbers of following channels.

## Code boundaries

Replace the enum-only channel source with a catalogue that exposes an ordered
list and lookup by storage key/number. Adapt persistence, channel up/down,
numeric selection, overlay numbering, EPG lookup, and tests to use the
catalogue rather than enum `entries` or `ordinal`.

Keep non-direct provider resolvers only where still needed: JOJ ŠPORT 2 is
removed; SVET NARUBY keeps Sweet.tv. The remote downloader, JSON parser, and
cache are isolated behind a small catalogue source interface and testable
without a network connection.

## Verification

Unit tests cover parsing, invalid/duplicate entries, HTTP and HTTPS URLs,
cache fallback, seed-catalogue fallback, ordering, numeric selection, and
retained Sweet.tv navigation. Resolver and UI tests confirm the Markíza
credential path is absent. Build, install, launch, and direct playback checks
verify the final catalogue on the TV.
