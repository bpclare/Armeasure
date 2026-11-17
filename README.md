# AR Measure

An Android app for measuring things in 3D with the phone's camera (ARCore), with photos, all kept on the phone. Built
for car seats first (a cover's measurements), but it measures anything: start an item, follow a checklist or name
each measurement, and export a ZIP to send on.

Started from Contrain's AR window measure (`web/photos/src/measure/` in bpclare/Contrain): the same idea of a ring
at the screen's center where a tap puts a point, the tape's quarter-inch display and parsing, the photo with the
measurement drawn on. Native instead of WebXR because an APK's WebView has no AR, and ARCore's depth finds a point on
any surface (fabric, curves), where the window tool needed a flat wall.

## Measuring

- **LINE**: put the ring on where it starts, tap anywhere; on where it ends, tap. The straight distance in 3D.
- **TRACE**: tap along a curve (over a seat's bolster, round a cushion's front); the pieces are added up, close to a
  tape laid along it.
- **SAVE** keeps the number; **PHOTO + SAVE** keeps the camera's picture with the line and length drawn on. The next
  checklist step comes up, so a whole seat is measured without leaving AR. **UNDO** takes the last point back.
- Each measurement can take a tape reading typed over it (the app shows how far AR was off), be renamed, retaken or
  deleted. Inches (to the ¼) or centimeters, switched on Home.
- Best from 1 to 3 feet away, in good light, moving the phone slowly at first so ARCore can find its place.

## Storage

Items live in the app's own files (`files/items/<id>/item.json` and its photos). No server, no account. **Export**
(the item's ⋯, or SAVE ZIP / SHARE ZIP) writes `summary.txt`, `measurements.csv`, `item.json` and the photos, to
`Downloads/AR Measure/` or the share sheet. Uninstalling the app deletes its items, so export what you want to keep.

## Install and updates

1. On the phone, open the repo's **Releases**, download the latest `measure-N.apk`, open it, and allow installs
   from the browser when Android asks.
2. After that the app checks GitHub when it opens; **Update ready → INSTALL** downloads the new build and Android asks
   once to confirm (the first time, allow installs from AR Measure).

Every push to `main` runs `.github/workflows/release.yml`: the unit tests, then a release `vN` with the signed APK.
The repo is public so the app can read its releases without a login (and public repos' Actions minutes are free).
The key is `app/release.keystore`; its password is the `KEYSTORE_PASSWORD` secret. Keep the password: a build signed
by another key can't update the installed app.

## Building here

JDK 17+, the Android SDK (platform 36). `./gradlew testDebugUnitTest` for the tests, `./gradlew assembleDebug` for a
debug APK, `KEYSTORE_PASSWORD=... ./gradlew assembleRelease -PversionCode=N` for a release one.

## Layout

- `geo/Geometry.kt`: distances, traces, inches and centimeters, tape parsing, the projection. Pure, unit-tested.
- `data/`: the model and its JSON, the on-phone store, the checklists (`Templates.kt`: car seat, box).
- `ar/`: the AR screen (`MeasureActivity`), the frame loop and hit test (`MeasureRenderer`), the camera background,
  and the overlay drawn on the screen and on the photo.
- `ui/`: Home, the item screen, export, the theme.
- `update/Updater.kt`: the GitHub release check, download and install.
