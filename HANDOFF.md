# Handoff: AR Measure

## Identity

Repo bpclare/Armeasure (public). App "AR Measure", package com.bpclare.armeasure. Owner: Bryan, building it through
Claude from his phone. Global rules in bpclare/workingwithme.

## What it is

An Android app that measures things in 3D with the phone camera (Google ARCore) and keeps the numbers and photos on
the phone. Items (a car seat, a cushion, anything) follow a checklist (car seat, box) or free measuring. LINE is two
taps (straight 3D distance); TRACE is taps along a curve, added up. SAVE or PHOTO + SAVE (the camera picture with the
line and length drawn on). A tape reading can be typed over any measurement. Export is a ZIP (summary, CSV, JSON,
photos). Started from Contrain's AR window measure (bpclare/Contrain `web/photos/src/measure/`).

## How to work on it

- JDK 17+ and the Android SDK (platform 36). The cloud container needs the SDK installed: command-line tools from
  dl.google.com into /opt/android-sdk, then `sdkmanager "platforms;android-36" "build-tools;36.0.0"`, and
  `local.properties` with `sdk.dir=/opt/android-sdk` (gitignored).
- `./gradlew testDebugUnitTest` for the tests; `./gradlew assembleDebug` to check it builds.
- Push to `main` to release (see CLAUDE.md). Check the run in Actions and the release's APK afterwards.

## Things that would be got wrong

- The signing key: CI signs with `app/release.keystore` and the KEYSTORE_PASSWORD secret. A different key means the
  phone refuses every update; never regenerate it.
- The update check reads the LATEST release of bpclare/Armeasure. versionCode comes from the tag (vN). A release must
  carry exactly one `.apk` asset. Builds made by hand are versionCode 1; CI's are run number + 1.
- An APK wrapper around a web page cannot do AR (Android's WebView has no WebXR). Stay native.
- Compose BOM 2026.x needs Android Gradle plugin 9.1 and compileSdk 37; the project is on AGP 8.13.2, compileSdk 36,
  BOM 2025.12.01 on purpose. Upgrading is its own step.
- AR accuracy is unproven until Bryan's tape comparison. Don't claim inches of accuracy that haven't been measured.
- The ARCore hit test accepts depth points, planes (inside their outline) and feature points, nearest first.

## Current work

Build 2 is released (Nov 17, 2026). Waiting on Bryan's first real test on a seat.

## In flight / waiting on Bryan

- His first seat test with a tape alongside (docs/for-bryan.md).
- His answers on bringing the app in line with the Nov 18 global rules (see docs/decisions.md once answered).

## Next

Whatever the seat test shows; then the rule fixes he picks.
