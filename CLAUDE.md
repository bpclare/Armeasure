# AR Measure: rules for this repo

The global rules (`~/.claude/CLAUDE.md`, master copy in bpclare/workingwithme) apply here and win over any general
rule in this file that disagrees. This file keeps only what is specific to this repo.

- Android only, one user (Bryan). Native Kotlin + Compose + ARCore; no server, no database, no account.
- Everything is stored on the phone (the app's files, JSON and JPEGs); export is a ZIP to Downloads or the share sheet.
- Nothing reaches the phone until Bryan says "ship" (Nov 19). Work is committed and pushed to the `next` branch, with
  tests run here first. On "ship": fast-forward `main` to `next` and push; that push runs
  `.github/workflows/release.yml` (tests, then a signed release `vN`, N = run number + 1), which the installed app
  offers on launch. Before asking him to ship, give him a plain list of what is waiting on `next`. A fix for something
  broken in the field ships at once, and he is told. Docs-only pushes (`.md`) run no CI and may go to `main` directly.
- Release builds stay on GitHub Actions (Bryan, Nov 19: "Keep GitHub building updates"): the repo is public, so they cost
  no minutes.
- Labels: normal case ("Save", not "SAVE") and the build number in the ⋯ menu, applied to each screen the next time
  it is worked on for another reason (Nov 19, "When next touched"). New screens use them from the start.
- Every build must be signed with `app/release.keystore` (password: repo secret `KEYSTORE_PASSWORD`), or the update
  fails to install over the phone's copy. Never replace the key.
- Tests: `./gradlew testDebugUnitTest` (the logic, and Back on every screen with Robolectric). AR itself can only be tried on
  the phone; say so instead of claiming it works.
- Decisions: `docs/decisions.md`. Steps only Bryan can do: `docs/for-bryan.md`. Handoff: `HANDOFF.md`.
