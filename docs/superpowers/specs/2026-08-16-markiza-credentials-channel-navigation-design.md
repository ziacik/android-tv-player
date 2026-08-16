# Markiza credentials channel navigation design

## Goal

When the Markiza credentials panel is visible, the user can still change
channels with CH+/CH- and enter a channel number with the remote. Back-button
behaviour remains unchanged.

## Design

`PlayerScreen` will not globally discard remote KeyUp events while the player
is in `CredentialsRequired`. Instead, it will permit only the navigation
commands that select another channel:

- `ChannelUp` calls `controller.channelUp()`.
- `ChannelDown` calls `controller.channelDown()`.
- `NumericDigit` is forwarded to the existing `NumericChannelInput`.

All other commands retain the existing credentials-panel behaviour: the login
form receives its normal focused-input interactions, and Back is not given new
behaviour.

Selecting another channel moves `PlayerController` out of
`CredentialsRequired` into its normal resolving state, so Compose replaces the
credentials panel with the selected channel's state without an additional
dismiss action.

## Error handling

If the next selected channel also requires credentials, the same panel remains
visible for that channel. Invalid numeric entries keep the existing behaviour:
they disappear after the input timeout and select no channel.

## Verification

Add UI-level regression coverage for credentials-required state that proves
CH+/CH- and numeric keys reach the existing channel-selection actions. Preserve
existing checks that the credentials panel is rendered and that other remote
commands are not activated by this change. Run the focused test suite and the
full debug unit-test suite.
