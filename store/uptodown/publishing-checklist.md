# Uptodown publishing checklist

## One-time signing setup

1. Generate and permanently archive the production signing key locally. Do not use the debug keystore used by `deploy.sh`.
2. Add these GitHub Actions repository secrets:
   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
3. Keep an offline backup of the keystore and passwords. Every future update must use the same signing identity.

Example local key generation:

```sh
keytool -genkeypair -v \
  -keystore kanalik-release.jks \
  -alias kanalik \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Encode it for the GitHub secret on Linux:

```sh
base64 -w0 kanalik-release.jks
```

## Build the publishable APK

Run the `Release APK` workflow from GitHub Actions. For the first release use:

- version name: `0.1.0`
- version code: `1`

The workflow runs unit tests/lint, builds a production-signed release, verifies the APK signature, verifies that `assets/channels.json` is not packaged, uploads the APK as a workflow artifact and creates/updates GitHub release `v0.1.0`.

For every later Uptodown update, increase `version_code`. Never reuse a lower or equal Android version code for a newer release.

## Uptodown Developers Console

Upload the signed APK and copy values from `metadata.yml`. Upload the committed current-brand assets `icon-512.png` and `feature-1024x500.png`, add the English description and changelog, then add at least four real screenshots.

The remaining account-specific values cannot safely be filled in by the repository:

- public support/contact email
- organization nationality
- optional official website/social profiles

These belong to the Uptodown developer account rather than the APK.
