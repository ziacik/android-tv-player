# Android TV Player – cinematic overlay redesign

## Goal

Replace the MVP controls with a polished cinematic overlay while preserving the existing fullscreen STVR Jednotka player and its direct D-pad controls. The overlay must show truthful live-window information, expose the current program, cover as little video as practical, and replace STVR's rights-restriction slate with a useful application-owned state.

The approved visual direction is **A – Cinematic**.

## Scope

- Redesign the playback overlay.
- Show current program metadata from the existing STVR response.
- Show a real live-window timeline and the viewer's delay from live.
- Keep direct 10-second seeking and the existing remote-control model.
- Detect programs marked unavailable for internet distribution.
- Automatically retry after a restricted program ends.
- Verify the result on the Philips 58PUS8545/12 (TPM191E).

Additional channels, an EPG browser, archive playback, accounts, alternative stream providers, and tuner integration remain out of scope.

## Visual design

The video remains fullscreen. When controls are visible, a soft black-to-transparent gradient occupies no more than the lower third of the frame. There is no opaque full-width panel.

The overlay contains, from top to bottom:

1. a small uppercase channel label, `JEDNOTKA · NAŽIVO`;
2. the current program title in a larger semibold style, truncated to one line;
3. a thin live-window timeline with a filled portion and a visible current-position marker;
4. the delay from live on the left and `LIVE` on the right;
5. compact pill controls for `↶ 10`, play/pause, and `10 ↷`, with a live action aligned right.

The focused action uses a white background, dark icon, subtle scale increase, and soft shadow. Unfocused actions use translucent white. Red is reserved for the live-status dot. Disabled seeking remains visible but uses reduced contrast. The layout uses density-independent dimensions and remains within TV safe areas at 1080p and 4K.

## Remote interaction

The current direct-control behavior remains unchanged:

- `OK` while the overlay is hidden shows it.
- `OK` while play/pause is focused toggles playback.
- `←` immediately seeks back exactly 10 seconds.
- `→` immediately seeks forward exactly 10 seconds, bounded by the live window.
- `↑` focuses play/pause.
- `↓` focuses the live action.
- `OK` on the live action returns to the live edge.
- `Back` hides a visible overlay; a second `Back` exits.

Every handled remote action shows the overlay and restarts its four-second auto-hide timer.

## Program and stream model

The resolver will parse the following non-secret metadata already present in the STVR JSON response:

- program title, composed as `series: subtitle` when both fields are present, then falling back to `series`, `titleorig`, and finally `title`;
- program start and stop timestamps, preferring the epoch-millisecond `timestart` and `timestop` fields and using the ISO date fields only as fallback;
- the `internet` availability flag.

Resolution will return one of two explicit results:

- **Playable**: program metadata plus the existing tokenized `StreamSource`;
- **Unavailable**: program metadata and the announced end time, without loading the HLS source.

Only an explicit, case-insensitive `internet: "N"` produces `Unavailable`. `Y` and missing or unknown values continue through the playable path when a valid HLS source exists, preserving playback if STVR omits the flag.

The tokenized stream URL remains confined to the resolver/player boundary and must never appear in UI state, normal logs, screenshots, or committed fixtures.

## Live timeline

The player layer will expose a small immutable playback snapshot containing current position, live-window duration, seekability, playing state, and live offset when Media3 provides it.

While the overlay is visible, the UI refreshes the snapshot once per second. Timeline progress is `currentPosition / windowDuration`, clamped to `0..1`. Delay from live uses Media3's live offset when available; otherwise it falls back to `windowDuration - currentPosition`. Values below ten seconds are treated as live to avoid a permanently misleading small delay caused by normal streaming latency.

At the live edge, the right-hand action shows a red dot and `NAŽIVO`. Away from the live edge, it becomes an actionable `NA LIVE`, and the left label shows the delay as `−mm:ss` or `−h:mm:ss`.

If Media3 reports that the item is not seekable or does not provide a meaningful live window, the timeline is shown as an inactive track, delay text is omitted, and the seek controls are visually disabled.

## Restricted-program state

If STVR reports `internet: "N"`, the application will not start the replacement HLS slate. It displays a calm, application-owned screen containing:

- `Tento program nie je dostupný online`;
- the program title;
- the program's announced end time, rendered in the TV's local timezone as the time when the application will retry;
- a `Skúsiť znova` action.

When the program end timestamp is in the future, one retry is scheduled for two seconds after that timestamp. If the timestamp is missing or already past and the response is still unavailable, automatic retries occur no more often than once per minute. Manual retry remains available immediately. Retry jobs are cancelled when superseded or when the player screen is released, preventing duplicate requests and lifecycle leaks.

Network, parsing, and playback failures continue to use a separate concise error state with `Skúsiť znova`; they must not be mislabeled as rights restrictions.

## Component boundaries

- `StvrResolver` parses program availability and returns the explicit playable/unavailable result.
- `PlayerController` owns resolution, playback state, retry scheduling, and immutable playback snapshots.
- `Media3PlayerPort` supplies live-window metrics and performs playback operations.
- `PlayerScreen` maps remote events to controller actions and coordinates overlay visibility.
- `PlayerOverlay` renders only the supplied UI model and contains no networking or player logic.

These boundaries keep the visual layer replaceable and allow future providers or channels without coupling them to Compose.

## Verification

Automated tests will cover:

- parsing playable and internet-restricted STVR responses;
- preservation of secret stream URLs outside UI state and diagnostics;
- timeline progress, live threshold, delay formatting, and missing-window behavior;
- exact and bounded 10-second seeks;
- automatic retry scheduling, cancellation, and manual retry;
- remote mappings and overlay auto-hide reset behavior;
- UI semantics for playing, paused, behind-live, live, unavailable, and error states.

Final device verification will build and install a fresh debug APK, launch it on the Philips TPM191E, exercise all D-pad commands, confirm that the overlay hides after four seconds, and capture screenshots for both normal playback and the restricted-program state.

## Acceptance criteria

- The implemented overlay visibly matches the approved Cinematic direction.
- Program title and live status reflect the current STVR response and Media3 state.
- Timeline and delay values come from real playback data rather than static decoration.
- Restricted programs never display STVR's raw replacement slate when `internet: "N"` is known.
- Playback automatically resumes after a restricted program when STVR makes the next program available.
- Existing playback, retry, lifecycle cleanup, and D-pad behavior remain functional on the target Philips TV.
