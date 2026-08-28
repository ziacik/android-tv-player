# Stable player OSD layout

## Goal

Keep the player OSD at a fixed height while a channel is switching, recovering
from a stream failure, or has no EPG interval.

## Layout

- The channel line contains only its number and name, for example
  `2 · DVOJKA`; it does not include `NAŽIVO`.
- The right-hand, fixed-width pill is the sole state indicator. It shows
  `NAŽIVO` during normal playback, `PREPÍNAM…` during a transition, and a
  concise recovery/restriction status for unavailable/error states.
- A programme status is never inserted below the programme title.
- The timeline row is always reserved. With valid programme timestamps it keeps
  start/end labels, progress, and the moving current-time pill. Without EPG it
  renders the same-height neutral track with only the current time centred and
  no fabricated start/end times or progress.

## Verification

Add model and Compose assertions for no-EPG timeline layout and each state-pill
label. Build the app and visually confirm on the TV that switching, errors,
and EPG availability no longer move the controls vertically.
