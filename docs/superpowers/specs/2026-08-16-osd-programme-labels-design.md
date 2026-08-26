# Android TV Player – EPG time labels in OSD

## Goal

Make the OSD timeline communicate the scheduled programme rather than stream
latency. The viewer can read when the programme started, what time it is now,
and when the programme ends directly from its EPG interval.

## Timeline

When the ready programme has a valid EPG start/end interval, the OSD shows its
existing progress bar and marker. The timeline labels are:

- start time below the bar's left edge;
- current local time above the current progress marker;
- end time below the bar's right edge.

The current time and marker update once per second while the OSD is visible.
The three labels use the TV's local time format. The current-time label stays
above the bar, while the endpoint labels stay below it, so they remain legible
when the marker is close to either edge.

No Media3 live offset, stream-window duration, or `LIVE` text appears beside
the programme timeline. If no valid programme interval exists, the whole
timeline and its labels remain hidden.

## Channel and transport labels

The channel heading includes its one-based channel number, for example
`1 · JEDNOTKA · NAŽIVO`.

The transport action currently rendered as `NA LIVE` is renamed to `NAŽIVO`.
It continues to call the existing go-live action; only its Slovak wording
changes. The previous red-dot `LIVE` marker at the end of the timeline is
removed.

## Scope and verification

This is a display-only change. EPG selection, scheduled programme updates,
playback control, and channel numbering retain their existing behaviour.

Unit tests cover programme start/end/current-time labels, label hiding without
a programme interval, local-time formatting input, channel-number heading,
and the revised go-live label. The Android unit-test suite and debug APK build
are run before completion. Device playback remains a separate check.
