# Fast Open-EPG Design

## Goal

Make programme metadata available as soon as a channel becomes ready, restore Markíza EPG, and remove IPTV-Org completely.

## Data sources

Open-EPG is the primary XMLTV source. Skylink remains the only fallback for channels with a verified Skylink identifier, including Markíza. The remote `channels.json` carries both mappings and is the runtime source of truth. IPTV-Org and its identifiers, downloader, cache, and tests are removed.

## Lookup flow

For channels without a native programme interval, `PlayerController` starts the EPG lookup immediately after stream resolution and before Media3 reports readiness. If EPG wins the race, Media3's ready state is created with the EPG programme already attached. If playback becomes ready first, the channel appears immediately with its fallback title and is updated when EPG arrives.

The XMLTV repository caches the programme found for each source/channel pair until its end time. Repeated selection during that interval is an in-memory lookup. Feed bytes remain cached using the existing six-hour refresh policy; malformed or empty downloads are not cached.

## Failure behavior

An empty or unavailable Open-EPG feed falls through to Skylink. A late result from an abandoned channel load is ignored. Missing EPG leaves the channel name visible without a fabricated timeline.

## Verification

Unit tests cover early lookup, late-result rejection, per-programme caching, source fallback, and removal of IPTV-Org parsing. A clean unit-test/build run is followed by GitHub publication, TV installation, launch, and log inspection.
