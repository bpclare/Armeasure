# AR Measure: rules for this repo

The global rules (`~/.claude/CLAUDE.md`, master copy in bpclare/workingwithme) apply here and win over any general
rule in this file that disagrees. This file keeps only what is specific to this repo.

- Android only, one user (Bryan). Native Kotlin + Compose + ARCore; no server, no database, no account.
- Everything is stored on the phone (the app's files, JSON and JPEGs); export is a ZIP to Downloads or the share sheet.
- `main` is the release branch: every push that touches more than `.md` files runs the tests in
  `.github/workflows/release.yml` and publishes a signed release `vN` (N = run number + 1). The installed app offers it
  on launch. Docs-only pushes (`.md`) run no CI.
- Every build must be signed with `app/release.keystore` (password: repo secret `KEYSTORE_PASSWORD`), or the update
  fails to install over the phone's copy. Never replace the key.
- Tests: `./gradlew testDebugUnitTest` (the logic, and Back on every screen with Robolectric). AR itself can only be tried on
  the phone; say so instead of claiming it works.
- Decisions: `docs/decisions.md`. Steps only Bryan can do: `docs/for-bryan.md`. Handoff: `HANDOFF.md`.
