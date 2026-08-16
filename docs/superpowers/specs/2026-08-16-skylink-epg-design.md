# Skylink XMLTV EPG Design

## Goal

Use ordered XMLTV sources so that Markiza and JOJ programmes resolve from
their Skylink IDs while channels absent from Skylink can still use iptv-org.

## Design

Each source defines its download URL, cache file and map from `TvChannel` to
that source's XMLTV channel ID. `CachedXmltvEpgRepository` queries sources in
order and returns the first programme covering the requested instant. Each
source downloads and caches independently; a download, cache or parse failure
only skips that source and continues to the next one. The parser accepts both
the uncompressed Skylink XML document and iptv-org's gzip payload.

The configured priority is Skylink first and iptv-org second. Skylink IDs are
used for its available channels, including Markiza and JOJ. Iptv-org remains a
fallback for channels that Skylink does not carry. If neither source covers a
channel, the existing fallback remains: channel title only and no programme
timeline. Native STVR programme metadata remains authoritative while valid.

## Verification

Unit tests cover source-priority lookup, fallback after a missing programme or
failed source, independent cache use, uncompressed XML parsing, and Markiza/
JOJ Skylink IDs. The Android JVM unit suite and debug build must pass.
