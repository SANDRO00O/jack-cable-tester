# Jack Cable Tester

An Android app that tests the health of a 3.5mm audio cable (or a phone's
whole audio path — DAC, jack, cable, ADC) by sending an FSK-modulated audio
signal from one device and decoding it on the other, checking every packet
against a CRC16.

## How it works

1. **Transmitter**: generates a test file of sequential packets (magic bytes,
   sequence number, 32-byte payload, CRC16) and encodes each byte as an
   FSK tone pair (space = 2400 Hz, mark = 4800 Hz, 1200 baud) over the
   headphone output.
2. **Receiver**: records audio from the mic input, runs a Goertzel filter to
   detect the two tones, decodes bits back into bytes, and validates each
   packet's CRC and payload against the same test file.
3. The receiver reports valid packets, CRC errors, payload mismatches, and
   missed packets, plus an overall cable quality score.

You can run both roles on the same phone with a physical cable, or on two
phones for a wireless (cable + adapter) transmission test.

## Building

Standard Android Gradle project — open in Android Studio and run, or build
from the command line:

```
./gradlew assembleRelease
```

Release builds are signed via a keystore, which is expected outside of
version control. Set `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`
as environment variables (e.g. in CI), or place `my-upload-key.jks` in the
project root.

This project has no committed Gradle wrapper — CI installs Gradle directly
via `gradle/actions/setup-gradle`, so there's nothing to keep in sync.

### CI

Three workflows under `.github/workflows/`:

- **`generate-keystore.yml`** — run this **once**, manually. It generates a
  release keystore with random passwords on the runner and pushes
  `KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` straight to this
  repo's secrets via the GitHub API. Nothing sensitive is printed or saved
  as an artifact. Requires one bootstrap secret you create yourself first:

  1. On github.com (works fine from a phone browser, no PC needed): go to
     **Settings → Developer settings → Personal access tokens → Fine-grained
     tokens → Generate new token**. Scope it to this repository only, and
     under Repository permissions grant **Secrets: Read and write**.
  2. Add that token as a repo secret named `GH_PAT` (repo **Settings →
     Secrets and variables → Actions → New repository secret**).
  3. Run **Actions → Generate Release Keystore → Run workflow**.

  After this, `GH_PAT` isn't needed anymore — you can delete it, or leave
  it in case you want to rotate the keystore later. Note: since the token
  can write secrets, only add it if you trust this repo's workflow files
  (i.e. you just reviewed them, which you're doing now).

- **`debug-build.yml`** — runs on every push, builds an unsigned debug APK,
  uploads it as a workflow artifact. No secrets required.
- **`release-build.yml`** — manual only (`workflow_dispatch`), builds a
  signed release APK using the secrets above and publishes it as a GitHub
  Release.

## License

MIT — see [LICENSE](LICENSE).
