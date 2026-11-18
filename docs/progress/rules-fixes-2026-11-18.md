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
| 1 | Screens as a stack with Back rules; root Back minimizes | | | | |
| 2 | Theme follows the phone (light and dark) | | | | |
| 3 | Restorable deletes (items, measurements, photos; Trash) | | | | |
| 4 | Editable checklists in Settings | | | | |
| 5 | Full-screen editors (new item, rename, measurement, AR naming) | | | | |
| 6 | Settings (units, checklists, Trash, help, build and commit, updates) and Help | | | | |
| 7 | AR any orientation and size (Fold) | | | | |
| 8 | Back tests for every screen and layer; dashes gone | | | | |
| 9 | Push, CI, release build 3 verified | | | | |

## How to resume

Read this log, `git log --oneline pre-rules-fixes..`, run `./gradlew testDebugUnitTest`, continue at the first step
not Done. The SDK setup is in HANDOFF.md.

## For Bryan

- Nothing needed until build 3 is out; then open the app and tap INSTALL.
