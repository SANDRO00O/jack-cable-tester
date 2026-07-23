# Jack Cable Tester

[![Debug Build](https://github.com/karrarnazim/jack-cable-tester/actions/workflows/debug-build.yml/badge.svg)](https://github.com/karrarnazim/jack-cable-tester/actions/workflows/debug-build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/minSdk-24-blue.svg)](app/build.gradle.kts)

An Android application for diagnosing the health of a 3.5&nbsp;mm audio
cable — and the audio path around it — by transmitting an FSK-modulated
digital signal between two devices (or two ports on the same device) and
verifying it byte-for-byte on arrival.

> Bad audio cables and worn jacks are a common, hard-to-diagnose source of
> data-transfer failures in any hardware that repurposes the analog audio
> path for digital signaling. This tool turns that failure mode into a
> single, repeatable pass/fail measurement.

---

## Table of Contents

- [Overview](#overview)
- [How It Works](#how-it-works)
- [Features](#features)
- [Requirements](#requirements)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Project Structure](#project-structure)
- [Continuous Integration](#continuous-integration)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

Jack Cable Tester measures whether a 3.5&nbsp;mm audio cable — and the full
signal path around it (DAC, jack, cable, ADC) — can carry a clean digital
signal, rather than relying on subjective listening tests. One device plays
an encoded test signal through its headphone output; a second device (or
the same device, looped back through the cable) records it through the
microphone input and checks every packet against a known reference.

The result is an objective, reproducible **Cable Quality Score** and letter
grade (A+ through F), along with a live breakdown of valid packets, CRC
errors, payload mismatches, and dropped packets.

## How It Works

1. **Reference file generation** — the transmitter creates a file of
   sequential test packets. Each packet contains a fixed sync header, a
   sequence number, a 32-byte payload, and a CRC16 checksum.
2. **Transmission** — each packet is encoded as an audio-frequency-shift-keyed
   (AFSK) signal at 1200 baud (space tone: 2400 Hz, mark tone: 4800 Hz,
   48 kHz sample rate) and played through the device's headphone output.
3. **Reception** — the receiving device records audio through its
   microphone input and applies a Goertzel filter to detect the two tones,
   reconstructing bits and bytes in real time.
4. **Verification** — each decoded packet's CRC and payload are checked
   against the original reference file. Results (valid / CRC error /
   mismatch / missed) are tallied live and plotted on a running chart.
5. **Scoring** — the ratio of valid packets to expected packets produces a
   percentage score, translated into a letter grade for quick
   interpretation.

## Features

- Objective, repeatable cable/audio-path quality testing — no subjective
  listening required
- Live packet-level statistics: valid, CRC errors, data mismatches, missed
- Real-time quality chart as packets are received
- Simple A+ through F letter grading, in addition to the raw percentage
- Works with a single device (loopback cable) or two devices
- Configurable test length (10–1000 packets)
- No network permissions, no analytics, no third-party services

## Requirements

- Android 7.0 (API 24) or later
- A 3.5&nbsp;mm audio input/output on the device(s) under test (via a
  headphone jack or a compatible USB-C/Lightning adapter)
- Microphone permission (requested on first launch)

## Getting Started

Clone the repository and open it in Android Studio:

```bash
git clone https://github.com/karrarnazim/jack-cable-tester.git
```

This project intentionally ships **without** a committed Gradle wrapper.
Android Studio will offer to generate one on first sync (`File > Sync
Project with Gradle Files`), or you can build from the command line with a
locally installed Gradle:

```bash
gradle assembleDebug
```

Release builds require a signing keystore, supplied via environment
variables rather than checked into the repository:

```bash
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=********
export KEY_PASSWORD=********
gradle assembleRelease
```

See [Continuous Integration](#continuous-integration) below for how this is
automated in CI without ever storing the keystore in the repository.

## Usage

1. Launch the app on both devices (or once, if testing via loopback).
2. On the **Transmitter**, choose a packet count and generate a reference
   test file (`.jct`).
3. Transfer the reference file to the **Receiver** device if it's a
   different phone.
4. Connect the cable under test between the transmitter's headphone output
   and the receiver's microphone input.
5. Start listening on the Receiver, then start transmission on the
   Transmitter.
6. Read the live results: valid/error/missed packet counts, a running
   quality chart, and a final Cable Quality Score with letter grade.

## Project Structure

```
app/src/main/java/space/karrarnazim/jackcabletester/
├── MainActivity.kt          Navigation host and home screen
├── audio/
│   ├── AudioTransmitter.kt  AFSK signal generation and playback
│   └── AudioReceiver.kt     Signal detection (Goertzel) and decoding
├── data/
│   ├── Models.kt            Test packet / test file data classes
│   ├── TestFileHelper.kt    Reference file read/write, CRC16
│   └── CableGrade.kt        Score-to-letter-grade mapping
└── ui/
    ├── SendScreen.kt        Transmitter screen
    ├── ReceiveScreen.kt     Receiver screen and live results
    ├── QualityChart.kt      Live score chart
    └── theme/               Material 3 theme
```

## Continuous Integration

Three GitHub Actions workflows live under `.github/workflows/`:

| Workflow | Trigger | Purpose |
|---|---|---|
| `debug-build.yml` | Every push | Builds an unsigned debug APK and uploads it as a workflow artifact. No secrets required. |
| `release-build.yml` | Manual (`workflow_dispatch`) | Builds a signed release APK and publishes it as a GitHub Release. |
| `generate-keystore.yml` | Manual, one-time | Generates a release keystore with random passwords and pushes it directly to repository secrets via the GitHub API. Nothing sensitive is printed or stored as an artifact. |

### One-time signing setup

`release-build.yml` expects three repository secrets: `KEYSTORE_BASE64`,
`STORE_PASSWORD`, and `KEY_PASSWORD`. The `generate-keystore.yml` workflow
creates and stores these for you:

1. Create a fine-grained [Personal Access Token](https://github.com/settings/tokens?type=beta)
   scoped to this repository only, with **Secrets: Read and write**
   permission.
2. Add it as a repository secret named `GH_PAT`
   (**Settings → Secrets and variables → Actions**).
3. Run **Actions → Generate Release Keystore → Run workflow**.
4. Optionally remove `GH_PAT` afterward, or keep it to rotate the keystore
   later.

## Contributing

Issues and pull requests are welcome. Please keep the audio protocol
(`AudioTransmitter`/`AudioReceiver`) and file format (`TestFileHelper`)
changes backward-compatible where possible, since a transmitter and
receiver running different app versions should still be able to
interoperate.

## License

See [LICENSE](LICENSE) for details.