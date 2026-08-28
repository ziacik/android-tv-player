# Player OSD during switching and recovery

## Goal

Replace the centred loading and error panels with the existing broadcast-style
OSD, so a channel transition or failed stream still identifies the selected
channel and its current programme.

## User experience

- While resolving or preparing a selected channel, keep the last rendered video
  frame visible and render the normal OSD for the target channel. It shows
  channel information and the EPG programme/timeline when that information is
  available, plus a subtle `Prepínam…` status.
- On a playback or resolver failure, keep the last rendered video frame visible
  and render that same OSD. It displays a concise recovery status and the time
  of the next automatic attempt. There is no centred error panel or retry
  button.
- If EPG data is unavailable, show the channel and status without inventing a
  programme interval or timeline.
- DPAD centre/OK triggers an immediate retry and resets the backoff. Selecting
  another channel cancels any pending retry for the former channel.

## Recovery policy

Ordinary resolver and playback failures retry after 1, 2, 4, 8, 16, and 32
seconds, then every 60 seconds until playback succeeds, the user retries, the
channel changes, or the controller is released. A successful playback state
resets the sequence.

`Unavailable` remains a meaningful runtime state for STVR: the STVR resolver
returns it when its programme metadata marks `internetAllowed` false. It uses
the same OSD and preserved frame. When its programme end time is known, the
next retry is scheduled immediately after that end; otherwise it uses the
ordinary 1-to-60-second backoff. This does not change the provider's content
restriction; it only changes presentation and recovery timing.

## Architecture

- Extend UI-state data so resolving, preparing, error, and unavailable states
  can carry programme metadata obtained from EPG for the target channel, while
  retaining the error/retry display information required by the OSD.
- Make `PlayerOverlayModel` the common presentation model for ready,
  transition, error, and unavailable UI states. It must retain the existing
  timeline validity rules.
- Move retry scheduling into one controller-owned policy for ordinary errors;
  preserve cancellation and stale-callback guards already used for loads and
  channel switches.
- Preserve the dedicated credential-entry screen; it requires user input and is
  outside this OSD/retry change.

## Verification

- Unit-test retry delays, reset after success/manual retry, and cancellation on
  channel switch and release.
- Unit-test OSD-model content for resolving, error, and unavailable states,
  including absent EPG data and a valid programme interval.
- Update Compose/device tests to assert that the loading, error, and
  unavailable panels are absent and the normal OSD/status is present.
- Run the focused JVM tests, assemble the debug app, and smoke-test on a TV for
  channel transition and a forced stream error. Build/install checks do not
  prove live playback recovery.
