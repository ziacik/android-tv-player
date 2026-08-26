# Svet naruby Channel Design

## Goal

Add the SWEET.TV FAST channel `Svet naruby` to the Android TV player without
requiring or storing SWEET.TV credentials.

## Stream resolution

- Add `ChannelProvider.SWEET_TV` and catalogue entry `TvChannel.SVET_NARUBY`.
- Store SWEET.TV channel id `3257` in `providerValue`.
- Resolve the stream with an anonymous JSON request to
  `https://api.sweet.tv/TvService/OpenStream.json`.
- Send `without_auth=true`, request only `HTTP_HLS`, and reject non-OK or
  non-HLS responses.
- Pass the browser-compatible SWEET.TV request headers to Media3 together with
  the fresh HLS URL.

## Recovery

The API returns a temporary URL. When Media3 reports a playback error for a
SWEET.TV channel, the controller performs a fresh resolve and reload instead
of immediately showing the terminal error. Other providers retain their
current error behaviour.

## Testing

- Verify the anonymous request body, endpoint, headers, and response parsing.
- Verify invalid responses become `StreamResolveException`.
- Verify provider routing and the 41-channel catalogue order.
- Verify SWEET.TV playback errors trigger a fresh resolve while other provider
  errors remain terminal.

