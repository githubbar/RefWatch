---
description: Verify, commit, push and tag a new RefWatch release
---

Cut a release of RefWatch. Version argument (e.g. `1.2.2`) is optional: $ARGUMENTS

Work through these in order and stop at the first failure — do not continue past a
red step, and do not push a tag for code that did not build.

## 1. Check the tree

Run `git status --short` and `git log origin/main..HEAD --oneline`.
Summarise what is about to ship. If the tree is clean and nothing is unpushed,
say so and stop — there is nothing to release.

## 2. Pick the version

Run `git fetch --tags`, `git tag --sort=-v:refname | head -5` and
`git ls-remote --tags origin`.

The new tag must not already exist locally or on the remote. A tag that has already
produced a GitHub Release — or whose version code has been uploaded to Play, even if Play
rejected it — must never be reused. Pick the next number instead.

Version codes come from `refWatchVersionCode` in each module's `build.gradle.kts`:
`400_000_000 + major*1_000_000 + minor*10_000 + patch*100 + variant` (wear 1, mobile 0).
Minor and patch may each go up to 99. The code only has to be larger than the last one
uploaded, which any higher version guarantees.

Update the fallback `appVersionName` (`?: "X.Y.Z"`) in both `wear/build.gradle.kts` and
`mobile/build.gradle.kts` to the new version, so Android Studio builds match the tag.

## 3. Verify and build signed bundles locally

```
./gradlew :wear:validateDebugScreenshotTest :mobile:assembleDebug :wear:bundleRelease :mobile:bundleRelease -PversionName=X.Y.Z
```

`validateDebugScreenshotTest` is the guard against text being clipped at large Wear
font scales — the reason Play has rejected this app many times. If it fails, render
the current output with `./gradlew :wear:updateDebugScreenshotTest`, look at the PNGs
under `wear/build/outputs/screenshotTest-results/preview/debug/rendered/`, and decide
whether the change is an intended redesign (accept the new baselines) or a regression
(fix the layout). Never accept new baselines just to make the check pass. If the change
adds a screen or dialog, it must be added to `FontScaleAudit.kt` first (see CLAUDE.md).

The release bundles are signed locally when the `refwatch*` signing properties are set
in `~/.gradle/gradle.properties`. If they are missing, the output is named `-unsigned`;
tell the user to add them there — never ask for the password in the conversation.

Check the signing key and version code of each bundle:

```
& "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe" -printcert -jarfile wear/build/outputs/bundle/release/wear-release.aab
```

The SHA1 must be `A2:91:2C:C8:86:4C:4C:3E:5A:C0:0B:8B:C0:F7:DC:59:90:C3:C7:33`, the Play
upload certificate. Anything else — in particular `6B:AF:C7:99:...`, the stray
`refwatch-release.jks` — will be rejected at upload. The version code is in the merged
release manifest under `<module>/build/intermediates/**/release/**/AndroidManifest.xml`.

Copy both bundles to `dist/` (gitignored) as
`refwatch-<module>-release-vX.Y.Z-local.aab`.

## 4. Commit and push main

Stage the source changes, any updated screenshot baselines and the version bump. Write
a message that says what changed and why, in the repository's existing style. Then:

```
git push origin main
```

## 5. Tag and push

```
git tag -a vX.Y.Z -m "<short summary>"
git push origin vX.Y.Z
```

Pushing the tag is the outward-facing step: it triggers `.github/workflows/build.yml`,
which builds and signs release APKs and AABs and publishes a public GitHub Release.
**The user must have explicitly asked for the tag to be pushed** (e.g. "tag and push"),
or confirm before it. Everything before this point is local and reversible; this is not.

## 6. Wait for the build

Give the user the run URL immediately, then poll until the tag's run finishes (several
minutes) — use a background command rather than polling by hand.

```
curl -s "https://api.github.com/repos/githubbar/RefWatch/actions/runs?per_page=5"
```

Watch the run whose `head_branch` is the tag (the `main` push starts a separate debug
run — ignore it). Wait for `"status": "completed"`. If `conclusion` is not `success`,
list the failed steps from `.../actions/runs/<id>/jobs` and report them. A failure at
**Sign release artifacts** means the `ANDROID_KEYSTORE_*` secrets no longer match the
keystore. The local bundles from step 3 are still valid for upload either way. `gh` is not
installed; a failed run is re-run from its page with **Re-run failed jobs**, which reuses
the tag and picks up updated secrets.

Do not claim the Release exists until the run has completed; it is created by the last
step of the workflow.

## 7. Fetch and check the CI bundles

```
curl -s https://api.github.com/repos/githubbar/RefWatch/releases/tags/vX.Y.Z
```

Download the two `.aab` assets from their `browser_download_url` into `dist/` and run the
same `keytool -printcert` check. If either is not `A2:91:2C:...`, say so plainly and point
the user at the local bundles instead.

Tell the user which file goes where:

- `refwatch-wear-release-vX.Y.Z.aab` → Play Console, Wear OS app
- `refwatch-mobile-release-vX.Y.Z.aab` → Play Console, phone app

## Notes

- The user is on Windows and pastes into PowerShell. Give commands in PowerShell form
  (`& "path\to\exe" args`, `$env:VAR`), not bash form, whenever they will run them.
- `JAVA_HOME` is not set globally; see CLAUDE.md.
