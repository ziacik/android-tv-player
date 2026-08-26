# Programme timeline layout

## Goal

Make the EPG programme interval legible in the player OSD and keep the
current-time label visually stable during its one-second updates.

## Layout

The timeline is a single bounded programme interval. Its start and end times
sit directly above the left and right ends of the bar. Short vertical end ticks
at those positions make the bounds explicit. The current time is also above
the bar, horizontally centred on the progress marker and clamped inside the
available width. The bar has sufficient vertical separation from all labels,
so no time label overlaps it.

## Stability

The measured width of the current-time label is retained while its timestamp
updates. A new second therefore changes only the text and marker position; it
does not first render at the marker with a zero-width measurement and then
jump to its centred position.

## Scope and verification

This changes only OSD presentation. EPG interval calculation, playback, and
control behaviour remain unchanged. Compose tests cover the three labels and
the programme-boundary markers; unit tests and a debug APK build verify the
change.
