# Markíza channel

## Scope

Add Markíza as the third channel in the Android TV player. The implementation follows the public Markíza web player's flow: authenticate, open the live page, read its embedded player URL, then resolve the temporary HLS URL.

## Design

`TvChannel` gains `MARKIZA`. A channel resolver routes STVR channels to the existing STVR resolver and Markíza to a dedicated resolver.

The Markíza resolver uses one cookie-preserving HTTP client. It obtains a login form token, posts the user's locally stored Markíza credentials, loads the live page, extracts the embed URL and then its HLS URL. The resulting source carries the User-Agent, Referer, and Origin headers required by the stream.

The player data source forwards source headers for both manifest and media requests.

Credentials are stored only in app-private SharedPreferences. No account is embedded in the APK. When credentials are absent, selecting Markíza shows one small login form; saving it retries the channel immediately. A rejected login uses the normal player error state.

## Verification

Unit tests cover the Markíza request sequence, HLS extraction, required headers, channel ordering, and missing credentials. The app builds and installs on the connected TV for manual playback verification.
