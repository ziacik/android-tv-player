# Android TV Player – OSD timing and program EPG

## Goal

Make the playback OSD stay visible long enough to use and show truthful
information about the programme currently being broadcast. This change keeps
the existing single-player architecture, channel switching, and restricted
STVR programme handling.

## Scope

- Ordinary playback actions show the OSD for six seconds.
- `OK` or centre while the OSD is hidden shows it for one minute.
- A subsequent ordinary playback action changes the visible timeout back to
  six seconds.
- The title and timeline describe the current scheduled programme, not the
  live stream's seekable window.
- Add background XMLTV lookup for supported non-STVR channels.
- Retain truthful channel-name fallback and hide the programme timeline when
  no current EPG item is available.

No programme guide screen, archive, reminder, account, or paid EPG service is
added.

## Programme data

`StvrResolver` continues to provide its native programme metadata, including
internet availability. It remains authoritative for STVR channels so that a
restricted programme is never accidentally considered playable by a generic
EPG result.

The application adds an `EpgRepository` backed by the public
`https://iptv-epg.org/files/epg-cz.xml.gz` XMLTV feed. `TvChannel` maps every
supported station to its XMLTV channel ID. The repository reads only the
currently requested channel's programme entries and returns the entry whose
start is at or before the current instant and whose end is after it.

The feed is cached in application storage and considered fresh for six hours.
After that interval, a lookup starts one background refresh. The last
successfully parsed cache remains usable if that refresh fails, including
offline use. A malformed feed, absent station mapping, or absent current
programme is non-fatal: it returns no EPG result.

The selected public feed does not cover every current direct or special live
stream. In these cases, specifically including WaterBear, Wild Earth, Gusto
TV, Tastemade, TV5MONDE Chefs, BBC Food, SZTŠ, and any unmapped special STVR live channel, the
OSD uses the channel name and shows no programme timeline. It does not invent
programme times or derive them from Media3's buffer.

## Playback and update flow

1. A channel resolver immediately supplies the existing stream source and its
   initial metadata, so EPG lookup never delays playback.
2. Once the selected stream reaches ready state, `PlayerController` starts a
   cancellable EPG lookup for the selected channel when native programme data
   lacks a valid programme interval.
3. If the lookup still belongs to the current channel and active load, the
   controller replaces only the ready-state programme metadata. A channel
   switch or release cancels the obsolete lookup.
4. The EPG cache refreshes in the background. A completed refresh updates the
   currently displayed programme if applicable; playback itself is unchanged.

## OSD model and controls

`PlayerOverlayModel` takes an injected current epoch time when it is built.
When programme start and end form a valid interval, its progress is:

`(now - programmeStart) / (programmeEnd - programmeStart)`, clamped to
`0..1`.

When that interval is unavailable, progress is `null`; the view renders no
programme marker. Media3 seekability, stream duration, live offset, delay
label, and the `LIVE` action retain their existing meanings and are not used
to calculate programme progress.

`OverlayController` accepts the visibility duration for each `show` call:

- hidden OSD + centre/enter: 60 seconds;
- channel change, seek, play/pause, LIVE, and focus movement: 6 seconds;
- `Back`: hides OSD immediately;
- numeric channel entry: keeps its independent numeric indicator behaviour.

The program title remains the existing one-line title above the timeline. It
updates when fresh EPG metadata is applied.

## Verification

Unit tests cover:

- six-second versus one-minute visibility and a subsequent ordinary action
  resetting the timeout to six seconds;
- choosing the current XMLTV programme using time-zone-aware XMLTV timestamps;
- malformed, unavailable, stale-cache, unmapped-channel, and no-current-item
  fallback paths;
- cancellation or rejection of an EPG result after a channel/load change;
- program-progress calculation from the scheduled start/end time, including
  before-start and after-end clamping;
- retention of native STVR availability and metadata.

The Android unit-test suite and debug APK build are required before declaring
the implementation complete. Device playback verification remains separate
from build validation because public streams and EPG availability can change.
