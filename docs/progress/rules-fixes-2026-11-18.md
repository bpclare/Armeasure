# Rule fixes and editable checklists, Nov 18, 2026

**Go:** Bryan, Nov 18 (question box): "Fix all now", "Build straight", checklists "Editable in settings", Fold "Both".

**Restore point:** git tag `pre-rules-fixes` (build 2).

## The problem

The app was built before the global rules. It is dark only (hard to read outdoors), edits open as pop-ups, a delete
cannot be undone, there is no help, the build's commit is not shown, and the checklists can only be changed by asking
Claude for an update. On the Fold opened up, the AR screen is locked upright and may not fit.

## The expected result

Bryan opens build 3: it is light or dark as his phone is set. Editing an item's name, a measurement, or a new item is
a full screen with Back returning where he was and nothing typed lost. A deleted item, measurement or photo can be
restored (UNDO right away, or from Trash). A help screen explains measuring. Settings shows the build and commit, the
units, Trash, and the checklists, where he can add, rename, reorder or remove steps and make his own lists. AR works
with the Fold closed or open, upright or sideways.

## Steps

| # | What | State | Commit | Verified | Undo |
|---|---|---|---|---|---|
| 1 | Screens as a stack with Back rules; root Back minimizes | Done | 1e35a81 | BackNavigationTest (root, item, photo, settings, help, trash) | git revert 1e35a81 |
| 2 | Theme follows the phone (light and dark) | Done | 1e35a81 | Builds; to see on the phone | git revert 1e35a81 |
| 3 | Restorable deletes (items, measurements, photos; Trash) | Done | 1e35a81 | DataTest, UNDO and Trash tests | git revert 1e35a81 |
| 4 | Editable checklists in Settings | Done | 1e35a81 | DataTest checklist store; editor Back test | git revert 1e35a81 |
| 5 | Full-screen editors (new item, rename, measurement, AR naming) | Done | 1e35a81 | Back saves tests; guards fail when reverted | git revert 1e35a81 |
| 6 | Settings (units, checklists, Trash, help, build and commit, updates) and Help | Done | 1e35a81 | Back tests; buildLabel on Home and Settings | git revert 1e35a81 |
| 7 | AR any orientation and size (Fold) | Done | 1e35a81 | Builds; only the phone can show AR turning | git revert 1e35a81 |
| 8 | Back tests for every screen and layer; dashes gone | Done | 1e35a81 | 28 tests pass; 3 guards fail when their fix is removed | git revert 1e35a81 |
| 9 | Push, CI, release build 3 verified | Done | 1e35a81 | CI run 2 passed; v3 APK downloaded: signer c30bedad, versionCode 3, commit ac9262b | revert, push |

## How to resume

Read this log, `git log --oneline pre-rules-fixes..`, run `./gradlew testDebugUnitTest`, continue at the first step
not Done. The SDK setup is in HANDOFF.md.

## For Bryan

- Build 3 is out (Nov 18). Open AR Measure, tap INSTALL on Update ready, and check the bottom of Home says
  "Build 3 · ac9262b".
- On the phone (the tests can't): AR folded and opened, upright and sideways; the light theme outdoors.
