# Decisions

Bryan's answers, newest last. A later answer names what it replaces.

| Date | Question | Answer (his words where given) |
|---|---|---|
| Nov 17, 2026 | What to build | "take contrains ar measurement tool and camera controls and build it out in a separate app to measure things like a vehicle seat to get measurements for a cover" |
| Nov 17, 2026 | Where data lives | "store everything locally no need for a database": measurements and photos on the phone |
| Nov 17, 2026 | How it ships | "Can it just be a GitHub repo and an apk I install from git?" with automatic updates: a public repo, an APK per GitHub release, the app checks for a newer release on launch and offers INSTALL |
| Nov 17, 2026 | Phones and users | "just android just me using it". So native Android (ARCore), not a web page; no accounts |
| Nov 17, 2026 | What it measures | "it won't just be for car seats that's just one example and one of the first things I want to try": general items, car seat as the first checklist |
| Nov 17, 2026 | Repo name | "measure", then created as "Armeasure" (replaces "measure") |
| Nov 18, 2026 | Bring the app in line with the new global rules (theme follows the phone, full-screen editors, restorable deletes, in-app help, commit shown, no dashes) | "Fix all now" |
| Nov 18, 2026 | Mockups for those rule fixes | "Build straight" (no mockup round for rule fixes) |
| Nov 18, 2026 | Checklists | "Editable in settings": add, rename, reorder, remove steps; make new checklists; car seat and box become starting copies |
| Nov 18, 2026 | Fold | "Both": AR works folded or open, turned any way |
| Nov 19, 2026 | New global rule: nothing goes live until "ship" | "Wait for ship". Work collects on the `next` branch; `main` (which publishes the phone update) moves only when Bryan says ship. Replaces "every push to main releases" |
| Nov 19, 2026 | New global rule: checks use zero Actions minutes | "Keep GitHub building updates". The repo is public, so the release build on Actions costs no minutes; tests still run in the session first |
| Nov 19, 2026 | New global rule: normal-case labels, build number in the menu | "When next touched". Each screen changes the next time it is worked on for another reason |
