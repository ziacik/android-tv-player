# Dvojka Channel Design

**Date:** 2026-08-15

## Goal

Add STVR Dvojka as a second live channel while keeping the existing cinematic player, restricted-program handling, and single-player architecture. Viewers switch channels with the remote's channel-up and channel-down keys, and the app restores the last selected channel after restart.

## Scope

The supported channel catalogue contains exactly two entries:

- Jednotka, STVR API channel id `1`
- Dvojka, STVR API channel id `2`

This change does not add a channel picker screen, EPG, channel logos, numeric-key selection, background preloading, or simultaneous players.

## Channel Model

Introduce a small, ordered channel model containing a stable persistence key, STVR API id, and display label. Channel order is Jednotka followed by Dvojka. Moving next or previous wraps around the catalogue; with two entries either direction switches to the other channel.

The channel is part of every player UI state. Resolving, preparing, ready, unavailable, and error states therefore always describe one unambiguous channel. This prevents the overlay or a delayed callback from displaying metadata for a different selection.

## Stream Resolution

The STVR resolver accepts the selected channel instead of using a fixed live URL. It builds the existing `live5f.json` request with the channel's API id as the `c` query parameter. Landing-page cookie acquisition, user-agent handling, JSON parsing, HLS selection, program metadata, and the explicit `internet: N` restriction remain shared between both channels.

Switching channels cancels any in-flight resolve and restricted-program retry, clears the active program, stops the current player source, and begins a new resolve for the selected channel. Results are applied only to the selection that initiated them. The existing single Media3 player is reused after the new source resolves.

An unavailable program is never loaded into Media3. Its scheduled retry resolves the same selected channel. A manual retry after an error also resolves the current channel.

## Persistence

Persist the stable key of the selected channel in Android `SharedPreferences` as soon as a channel switch is accepted. Read it before constructing the controller on app startup. Missing, invalid, or obsolete stored values fall back to Jednotka.

Persistence sits behind a small interface so controller and storage behavior can be unit-tested without Android framework state. No account, cloud synchronization, database, or migration framework is needed.

## Remote Interaction

Map Android `KEYCODE_CHANNEL_UP` to next channel and `KEYCODE_CHANNEL_DOWN` to previous channel. These commands work whether the cinematic overlay is visible or hidden and take precedence over ordinary overlay navigation.

On a channel command, the screen resets the overlay visibility timer and the controller switches immediately. Existing D-pad behavior remains unchanged:

- left and right seek ten seconds
- up focuses play/pause
- down focuses the live action
- center activates the focused action or shows a hidden overlay
- back hides the overlay before exiting

## Presentation

The channel label in `PlayerOverlayModel` comes from the ready state's channel rather than a hard-coded Jednotka string. Ready playback displays `JEDNOTKA · NAŽIVO` or `DVOJKA · NAŽIVO` while preserving the approved cinematic layout.

While the selected channel is resolving or preparing, show a compact centered status containing `<CHANNEL> · NAČÍTAVAM` and the existing progress indicator. This guarantees immediate channel feedback even when resolution takes longer than the overlay timer.

The unavailable-program panel includes the selected channel label above the existing restriction message, program title, automatic retry time, and focused retry action. The generic error panel also retains the selected channel context. Channel keys remain active from resolving, unavailable, and error states, allowing the viewer to switch away at any time.

## Failure and Concurrency Behavior

- A failed resolve keeps the requested channel selected and persisted.
- Center/enter retries the same channel from unavailable or error state.
- Switching during resolve or scheduled retry cancels work belonging to the old channel.
- Rapid repeated switching cannot allow a stale response or player callback to overwrite the latest channel state.
- A player error is attributed to the channel whose source is active.
- Existing generic diagnostic messages remain free of expiring stream URLs.

## Testing

Unit tests verify:

- channel catalogue order, next/previous wraparound, and stable keys
- Dvojka resolution uses `c=2` and retains the common parser and availability behavior
- startup fallback and persisted-channel restoration
- switching stops the old source, cancels stale resolution and retry work, and loads only the latest channel
- manual and automatic retry remain on the current channel
- channel-up/channel-down mapping works with the overlay both visible and hidden
- overlay models expose the correct channel label

Compose tests on the Philips TV verify:

- ready Dvojka state renders `DVOJKA · NAŽIVO`
- resolving Dvojka renders `DVOJKA · NAČÍTAVAM`
- unavailable Dvojka identifies the channel and retains the Slovak retry presentation

Final acceptance consists of a clean unit-test, lint, and APK build; all connected Compose tests; installation on `192.168.0.200:5555`; physical P+/P− key events in both directions; foreground and crash-log checks; and a screenshot of Dvojka playback or its native restricted-program panel.

## Out of Scope

- More than the two approved channels
- Instant switching through parallel playback
- A full-screen channel catalogue
- EPG browsing or scheduled reminders
- Replacing STVR's public live API
