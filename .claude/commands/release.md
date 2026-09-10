---
description: Verify, commit, push and tag a new RefWatch release
---

Cut a release of RefWatch. Version argument (e.g. `1.1.10`) is optional: $ARGUMENTS

Work through these in order and stop at the first failure — do not continue past a
red step, and do not push a tag for code that did not build.

## 1. Check the tree

Run `git status --short` and `git log origin/main..HEAD --oneline`.
Summarise what is about to ship. If the tree is clean and nothing is unpushed,
say so and stop — there is nothing to release.

## 2. Verify

```
./gradlew :wear:validateDebugScreenshotTest :wear:assembleDebug :mobile:assembleDebug
```

`validateDebugScreenshotTest` is the guard against text being clipped at large Wear
font scales, which is what got this app rejected from Play twice. If it fails, render
the current output with `./gradlew :wear:updateDebugScreenshotTest`, look at the PNGs
under `wear/build/outputs/screenshotTest-results/preview/debug/rendered/`, and decide
whether the change is an intended redesign (accept the new baselines) or a regression
(fix the layout). Never accept new baselines just to make the check pass.

## 3. Pick the version

Run `git tag --sort=-v:refname | head -5` and `git ls-remote --tags origin`.

The new tag must not already exist locally or on the remote. A tag that has already
produced a GitHub Release must never be moved — pick the next number instead.

Confirm the derived versionCode is larger than every code already published. The
formula lives in `wear/build.gradle.kts` (`refWatchVersionCode`); as of v1.1.9 it is
`36 * 10_000_000 + (major*100 + minor*10 + patch) * 10_000 + variant`, which gives
minor and patch a single digit each — so `1.1.10` and `1.2.0` collide on `361200001`.
If the requested version hits that, stop and raise it rather than tagging.

## 4. Commit

Stage the source changes and any updated screenshot baselines. Write a message that
says what changed and why, in the repository's existing style. Then:

```
git push origin main
```

## 5. Tag and push

```
git tag -a vX.Y.Z -m "<short summary>"
git push origin vX.Y.Z
```

Pushing the tag is the outward-facing step: it triggers `.github/workflows/build.yml`,
which builds signed release APKs and AABs for both modules and publishes a public
GitHub Release. **Ask the user to confirm before pushing the tag.** Everything before
this point is local and reversible; this is not.

## 6. Wait for the build

Give the user the run URL immediately, then poll until the tag's run finishes. It takes
several minutes.

```
curl -s "https://api.github.com/repos/githubbar/RefWatch/actions/runs?per_page=3"
```

Watch the run whose `head_branch` is the tag (the `main` push starts a second, separate
debug run — ignore it). Wait for `"status": "completed"`. If `conclusion` is anything
other than `success`, report the failure and stop — do not tell the user to upload
anything.

Do not claim the Release exists until the run has completed; it is created by the last
step of the workflow.

## 7. Fetch the signed bundles

CI signs the artifacts with the release keystore held in GitHub secrets — there is no
release keystore on this machine and neither module declares a `signingConfig`, so a
local `bundleRelease` would be **unsigned** and unusable for Play. Always take the
bundles from the Release rather than building them locally.

```
curl -s https://api.github.com/repos/githubbar/RefWatch/releases/tags/vX.Y.Z
```

Download the two `.aab` assets from their `browser_download_url` into `dist/`
(gitignored).

**Then verify which key signed them.** Checking only that *a* signature exists is not
enough: CI was once configured with a freshly generated keystore rather than the Play
upload key, and produced signed-but-unuploadable bundles for two releases before anyone
noticed.

```
& "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe" -printcert -jarfile dist/<file>.aab
```

The SHA1 must be `A2:91:2C:C8:86:4C:4C:3E:5A:C0:0B:8B:C0:F7:DC:59:90:C3:C7:33`, the Play
upload certificate. Anything else — in particular `6B:AF:C7:99:...`, which is the stray
`refwatch-release.jks` — means the artifact will be rejected at upload. Stop and tell the
user which key was used rather than handing over a bundle that cannot be published.

Tell the user which file goes where:

- `refwatch-mobile-release-vX.Y.Z.aab` → Play Console, phone app
- `refwatch-wear-release-vX.Y.Z.aab` → Play Console, Wear OS app

## Notes

- The user is on Windows and pastes into PowerShell. Give commands in PowerShell form
  (`& "path\to\exe" args`, `$env:VAR`), not bash form, whenever they will run them.
- Two workflow runs fire on a release: one for the `main` push (debug) and one for the
  tag (release). Only the tag run publishes.
