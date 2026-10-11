# Garmin Phase 5: Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish RefWatch for Garmin in the Connect IQ store, then release the phone app with
its Garmin section pointing at the store listing.

**Architecture:** The phone's Garmin section is already on `main` (phases 3–4) but has never
shipped (the last tag, v1.2.3, predates it). Task 1 hides it behind a store URL that is `null`
until Garmin approves the watch app, so phone releases (such as the Wear OS 4 change) can go out
first. Tasks 2–4 produce the `.iq` package, the store listing kit and the policy text. Tasks 5–6
are the user's steps on Garmin's and Google's sites. Task 7 runs after approval: set the URL and
release the phone app.

**Tech Stack:** Monkey C / Connect IQ SDK 9.2.0 (`monkeyc -e`), PowerShell (`garmin/build.ps1`,
`garmin/tools/sim.ps1`), Kotlin + Compose (mobile), JUnit + Truth, GitHub Pages (`docs/`).

**Spec:** `docs/superpowers/specs/2026-10-07-garmin-app-design.md` (Build order step 5, Phone
app, Risks).

## Global Constraints

- "plan the phone release to follow, not precede, the watch app's approval, so the Garmin
  section never points at an app that isn't available." (spec, Risks)
- "If the listing later mentions Garmin, only in the description ("Works with Garmin watches"),
  never the title or icon. Review the Data safety form before the release." (spec, Phone app)
- "No new Android permissions. Ships as a phone-only update." (spec, Phone app)
- "the app version (resources/strings/version.xml, bumped at each release)" (spec, Screens)
- Memory: stay under 60% of the fēnix 5X watch-app limit (1275.5 kB → about 765 kB); fēnix 7S
  and epix Pro limit 763.6 kB → about 458 kB (garmin/README.md).
- The Connect IQ developer key at `%USERPROFILE%\keys_for_garmin\developer_key.der` signs every
  store upload; updates must use the same key. Never copy it into the repo.
- Products: `fenix5x`, `fenix7s`, `fenix7`, `epix2pro47mm`, `fr265` (manifest.xml). No change in
  this phase.
- American English in all text ("color", not "colour").
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Pushing `main` publishes `docs/` (the live privacy policy) through GitHub Pages; pushing a tag
  publishes a GitHub Release. Both need the user's explicit go-ahead.
- Accounts, terms acceptance, uploads to Garmin and Play Console forms are the user's to do.
  Claude prepares the files and the text.

## Review Focus

1. **A user with a linked watch updates the phone before approval.** The section must still show
   their watch with Unlink, even though the store URL is null. Test: `showsWhenDevicesLinked`
   in Task 1.
2. **Before approval, a new user must see no Garmin section at all.** That includes no "Link"
   button, which would lead nowhere. Test: `hiddenWithoutUrlOrDevices` in Task 1.
3. **The `.iq` package has a debug build in it, or no build for a product.** The export must use
   `-r` and contain every manifest product. Check: Task 2 Step 4 lists the package.
4. **The fēnix 7S or epix is over its memory budget in the release build.** That gets it rejected
   or it crashes on a real watch. Check: Task 2 Step 5.
5. **The listing promises something the watch can't do without the phone app.** For example,
   game lists without an account. The description must say what needs a RefWatch account.
   Check: Task 3 listing review.

---

### Task 1: Hide the phone's Garmin section until the store URL is set

**Files:**
- Create: `mobile/src/main/java/com/databelay/refwatch/data/garmin/GarminStore.kt`
- Modify: `mobile/src/main/java/com/databelay/refwatch/screens/GarminLinkSection.kt`
- Test: `mobile/src/test/java/com/databelay/refwatch/data/garmin/GarminStoreTest.kt`

**Interfaces:**
- Produces: `object GarminStore { val STORE_URL: String?; fun showSection(storeUrl: String?, linkedDevices: Int): Boolean }`.
  Task 7 sets `STORE_URL`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.databelay.refwatch.data.garmin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GarminStoreTest {
    @Test
    fun hiddenWithoutUrlOrDevices() {
        assertThat(GarminStore.showSection(storeUrl = null, linkedDevices = 0)).isFalse()
    }

    @Test
    fun showsWhenDevicesLinked() {
        assertThat(GarminStore.showSection(storeUrl = null, linkedDevices = 1)).isTrue()
    }

    @Test
    fun showsOnceStoreUrlIsSet() {
        assertThat(GarminStore.showSection(storeUrl = "https://apps.garmin.com/apps/x", linkedDevices = 0)).isTrue()
    }

    @Test
    fun blankUrlCountsAsUnset() {
        assertThat(GarminStore.showSection(storeUrl = " ", linkedDevices = 0)).isFalse()
    }
}
```

- [ ] **Step 2: Run the test and confirm that it fails**

Run (PowerShell): `$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"; .\gradlew :mobile:testDebugUnitTest --tests "*GarminStoreTest"`
Expected: compilation fails with `Unresolved reference: GarminStore`.

- [ ] **Step 3: Implement**

`GarminStore.kt`:

```kotlin
package com.databelay.refwatch.data.garmin

/** Where the phone sends people to install RefWatch on a Garmin watch. */
object GarminStore {
    /**
     * The Connect IQ store page of the watch app. Null until Garmin approves the app: the Garmin
     * section stays hidden from people who have not linked a watch, so it never points at an app
     * they cannot install.
     */
    val STORE_URL: String? = null

    fun showSection(storeUrl: String?, linkedDevices: Int): Boolean =
        !storeUrl.isNullOrBlank() || linkedDevices > 0
}
```

In `GarminLinkSection.kt`, gate the content and add the store link to the idle state. The
`GarminLinkSection` composable becomes:

```kotlin
@Composable
fun GarminLinkSection(viewModel: GarminLinkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!GarminStore.showSection(GarminStore.STORE_URL, state.devices.size)) return
    val context = LocalContext.current
    GarminLinkContent(
        state = state,
        storeUrl = GarminStore.STORE_URL,
        onOpenStore = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: ActivityNotFoundException) {
                Log.e("GarminLinkSection", "No app can open $url", e)
            }
        },
        onLink = viewModel::requestCode,
        onUnlink = viewModel::unlink
    )
}
```

`GarminLinkContent` gains `storeUrl: String?` and `onOpenStore: (String) -> Unit` parameters
(placed after `state`). In its `else` branch, the idle state, add this after the description
text and before the Link button:

```kotlin
            if (!storeUrl.isNullOrBlank()) {
                TextButton(onClick = { onOpenStore(storeUrl) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Get RefWatch for Garmin in the Connect IQ store")
                }
            }
```

Add the imports `android.content.ActivityNotFoundException`, `android.content.Intent`,
`android.net.Uri`, `android.util.Log`, `androidx.compose.foundation.layout.PaddingValues` and
`androidx.compose.ui.platform.LocalContext`. Update both `@Preview` functions to pass
`storeUrl = "https://apps.garmin.com/apps/preview"` (idle preview) or `null` (code preview), and
`onOpenStore = {}`.

- [ ] **Step 4: Run the tests and confirm that they pass**

Run: `.\gradlew :mobile:testDebugUnitTest --tests "com.databelay.refwatch.data.garmin.*" :mobile:assembleDebug`
Expected: `BUILD SUCCESSFUL`. GarminStoreTest (4), GarminLinkViewModelTest and
GarminLinkParsingTest all pass.

- [ ] **Step 5: Check on the phone**

Check `ro.product.model` first: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 4ec8f1c7 shell getprop ro.product.model`
must print `CPH2655`. Then run `.\gradlew :mobile:installDebug`.

Open Settings on an account that has a linked 5X. Expected: the Garmin section shows the watch
with Unlink and no store link. If the user can spare a second account, they can also sign in
with one that has no linked watch. Expected: no Garmin section.

- [ ] **Step 6: Commit**

```
git add mobile/src/main/java/com/databelay/refwatch/data/garmin/GarminStore.kt mobile/src/main/java/com/databelay/refwatch/screens/GarminLinkSection.kt mobile/src/test/java/com/databelay/refwatch/data/garmin/GarminStoreTest.kt
git commit -m "Hide the Garmin section until the watch app is in the Connect IQ store"
```

After this commit, a phone release no longer depends on Garmin's review. The Wear OS 4 change
(bc00f4b) can ship whenever the user wants.

---

### Task 2: Export the store package

**Files:**
- Modify: `garmin/build.ps1`
- Modify: `garmin/resources/strings/version.xml`
- Modify: `garmin/README.md` (new "Release to the Connect IQ store" section)

**Interfaces:**
- Produces: `.\garmin\build.ps1 -Export` writes `garmin\bin\RefWatch.iq`. This is the release
  build for every product in `manifest.xml`, signed with the developer key.

- [ ] **Step 1: Add `-Export` to `build.ps1`**

Add `[switch]$Export` to `param(...)` and an example line to the help block:

```
  .\garmin\build.ps1 -Export               # store package (.iq) for every product in manifest.xml
```

Insert this before the `$out = ...` line:

```powershell
if ($Export) {
    # monkeyc -e packages every product in manifest.xml into one .iq for the Connect IQ store.
    # Always a release build: the store must never get (:debug) code or test fixtures.
    $iq = "$root\bin\RefWatch.iq"
    New-Item -ItemType Directory -Force "$root\bin" | Out-Null
    $ErrorActionPreference = "Continue"
    & "$bin\monkeyc.bat" -e -r -w -o $iq -f "$root\monkey.jungle" -y $Key 2>&1 | ForEach-Object { Write-Host "$_" }
    $exportExit = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($exportExit -ne 0) { throw "monkeyc export failed (exit $exportExit); see the compiler output above" }
    Write-Host "Store package: $iq"
    return
}
```

- [ ] **Step 2: Bump the version**

`garmin/resources/strings/version.xml`: `0.4.0` → `1.0.0`.

- [ ] **Step 3: Run the unit tests and export**

Run: `.\garmin\build.ps1 -Test` (expected: `PASSED`, 101 tests). Then run `.\garmin\build.ps1 -Export`.
Expected: `Store package: ...\garmin\bin\RefWatch.iq` and exit 0.

- [ ] **Step 4: Check the package contents**

The `.iq` is a zip. List it:

```powershell
Copy-Item garmin\bin\RefWatch.iq $env:TEMP\RefWatch.zip -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::OpenRead("$env:TEMP\RefWatch.zip").Entries | Select-Object FullName, Length
```

Expected: a `manifest.xml` and one `.prg` per product, for all five products. If a product is
missing, stop and report it. If the layout differs from this, record the actual layout in the
README section.

- [ ] **Step 5: Check memory in release builds**

For each of `fenix5x`, `fenix7s` and `epix2pro47mm`, run `.\garmin\build.ps1 -Device <id> -Release -Run`.
Start a quick match, open Team actions and read the simulator's status-bar memory figure. A
capture works for this: `& "C:\Users\oleyk\AndroidStudioProjects\RefWatch\garmin\tools\sim.ps1" -Device <id> -Out $env:TEMP\mem-<id>.png`.
Expected: fēnix 5X ≤ 765 kB, fēnix 7S and epix ≤ 458 kB. Record the three figures in the README.

- [ ] **Step 6: README section**

Add "## Release to the Connect IQ store" to `garmin/README.md` after "Sideload to a watch":

```markdown
## Release to the Connect IQ store
1. Bump `resources/strings/version.xml`.
2. `.\garmin\build.ps1 -Test`, then `.\garmin\build.ps1 -Export` → `garmin\bin\RefWatch.iq`
   (release build, every product in `manifest.xml`).
3. Upload it at https://apps-developer.garmin.com (Upload an app, or Upload new version on the
   existing app). Sign every version with the same developer key; a new key means a new app.
4. Listing text, icon and screenshots are in `garmin/store/`.
```

- [ ] **Step 7: Commit**

```
git add garmin/build.ps1 garmin/resources/strings/version.xml garmin/README.md
git commit -m "Export the Garmin store package and set the watch app to 1.0.0"
```

---

### Task 3: Store listing kit

**Files:**
- Create: `garmin/store/listing.md`
- Create: `garmin/store/icon-512.png`
- Create: `garmin/store/screenshots/<device>-<n>-<screen>.png`

- [ ] **Step 1: Store icon**

Resize `mobile/src/main/ic_launcher-playstore.png` (the Play icon) to 512×512 PNG at
`garmin/store/icon-512.png`, using System.Drawing in PowerShell with HighQualityBicubic. Do not
add Garmin's name or logo, or any Garmin-like artwork.

- [ ] **Step 2: Screenshots**

Use the release build in the simulator: `.\garmin\build.ps1 -Device <id> -Release -Run`. Capture
with `sim.ps1 -Device <id> -Click ... -Out ...`. Use `-Wait 1500` or more, because the simulator
lags.

Capture both `fenix7` (current look) and `fenix5x` (MIP, the oldest supported watch):
1. start menu with a synced game, using the linked simulator setup from phase 4 (or Quick match
   only if not linked)
2. match screen in the 1st half with a score
3. match screen in added time (`+0:xx` shown)
4. team actions (Goal / Yellow / Red)
5. full time summary

Crop each capture to the watch face. Save as `garmin/store/screenshots/fenix7-1-start.png` and
so on. Open every PNG and check it: no clipped text, and no debug or test content.

- [ ] **Step 3: Listing text**

Write `garmin/store/listing.md` with the following content. Before saving, check every claim
against the app as built.

```markdown
# Connect IQ store listing — RefWatch

**App name:** RefWatch
**Type / category:** Watch app — Sports (or the nearest category the dashboard offers)
**Support email:** alexleykin+refwatch@gmail.com
**Privacy policy:** https://githubbar.github.io/RefWatch/privacy-policy.html
**Price:** Free

## Description

RefWatch is a match timer for soccer referees.

- Big match clock for each half, with added time counted past regulation and a buzz every
  30 seconds until you end the half
- Pause and resume, half-time countdown, and kick-off that switches sides for the 2nd half
- Goals and cards with shirt numbers, and an optional goal scorer
- Game log, undo of the last event, and a full-time summary
- Records the match as an activity (heart rate and GPS stay in your Garmin account); you can
  turn this off
- A match survives an app restart or a watch reboot

Quick match works on its own, with no phone and no account.

With a free RefWatch account, you can also link the watch to the RefWatch Android phone app
(Settings → Garmin watch) to:
- see your upcoming games on the watch, with teams, colors and timings
- send each finished match back to the phone, where it appears in your game history

Buttons: START pauses and resumes. UP and DOWN open Home and Away team actions. Hold UP or press
BACK for the match menu.

## What's new (1.0.0)

First release.

## Permissions (as the store shows them)

- Fit: save the match as an activity
- Positioning: GPS track of the activity
- Communications: game sync with your RefWatch account through Garmin Connect
```

- [ ] **Step 4: Commit**

```
git add garmin/store
git commit -m "Add the Connect IQ store listing text, icon and screenshots"
```

---

### Task 4: Privacy policy covers the Garmin watch app itself

The Garmin linking section already exists (phase 3). It describes the server side only. Add a
description of what the watch app keeps and where its activity goes.

**Files:**
- Modify: `docs/privacy-policy.md`

- [ ] **Step 1: Edit**

1. Change the first paragraph's opening sentence to: "RefWatch is a soccer referee app for Wear
   OS and Garmin watches with a companion Android phone app."
2. Append this paragraph to the end of the "### Garmin watches" subsection:

```markdown
The RefWatch app on a Garmin watch keeps the current match, your upcoming games and up to 10
finished matches in the watch's own storage until they are sent to your account. If you turn on
"Record activity", the watch saves each match as an activity in your Garmin account. Its heart
rate and GPS data go to Garmin, under Garmin's privacy policy, and are not sent to us. Without
a linked account, the Garmin app sends us nothing.
```

3. Change **Effective date** to the commit date (for example October 11, 2026).

- [ ] **Step 2: Check the wording**

Check each sentence against the code. Watch storage: `MatchStore` (current match and up to 10
finished matches) and `GameCache`. Uploads: `UploadQueue.body` sends no heart rate or GPS.
Activity recording is in `Recorder.mc`. Remove any sentence the code does not support.

- [ ] **Step 3: Commit (do not push)**

```
git add docs/privacy-policy.md
git commit -m "Describe the Garmin watch app in the privacy policy"
```

Pushing `main` updates the live policy through GitHub Pages. Ask the user before pushing. It is
fine to push it before the store submission, because the listing links to it.

---

### Task 5: Developer account, upload and review (user)

Claude does not create accounts, accept agreements or upload for the user. Give the user these
steps and wait.

- [ ] **Step 1:** The user signs in at https://apps-developer.garmin.com with a Garmin account
  (18+), and accepts the Connect IQ developer agreement.
- [ ] **Step 2:** They choose Upload an app and select `garmin\bin\RefWatch.iq`. After
  validation, they fill in the fields from `garmin/store/listing.md` and upload the icon and
  screenshots from `garmin/store/`.
- [ ] **Step 3: Beta test.** While the app is in review, the dashboard lets the user download and
  install it. They install it on the 5X through Garmin Connect, not by sideloading. Then they
  check:
  - The settings open in Garmin Connect: Pairing code, Record activity and Log goal scorer.
    Sideloaded builds could not test this.
  - Linking with a code entered in Garmin Connect works.
  - A synced game shows on the watch.
  - A match uploads.
  - The activity appears in Garmin Connect.
- [ ] **Step 4:** They submit for review. After that, they wait for Garmin's email, which can
  take days.
- [ ] **Step 5:** They tell Claude the outcome. If approved, they also give the store URL
  (`https://apps.garmin.com/apps/<id>`). If rejected, they give Garmin's reason. Fix the reason
  in a new task, bump `version.xml`, re-export and re-upload.

---

### Task 6: Review Play's Data safety form (user, with Claude's ruling)

- [ ] **Step 1: Claude's ruling** (recorded here, for the user to confirm or override)

The phone app collects nothing new for Garmin. The Garmin watch record (model, link time, last
seen, token hash) is written by Cloud Functions from requests sent by the Garmin watch, not by
the Android app. The phone only reads that record back to list linked watches. The Garmin watch
receives the user's game data, but Play does not count this as sharing: the user asks for the
transfer, and it goes to the user's own device through Garmin. So the form's answers do not
change.

The form needs a change only if the Garmin record should count as Android-app data. If so, add
"Device or other IDs: collected, App functionality, not shared".

- [ ] **Step 2:** The user opens Play Console → App content → Data safety. They compare it with
  the ruling, and edit and submit only if they disagree with it.

---

### Task 7: After approval — store link, docs and phone release

Run this only after Task 5 Step 5 reports that the app was approved and gives the store URL.

**Files:**
- Modify: `mobile/src/main/java/com/databelay/refwatch/data/garmin/GarminStore.kt`
- Modify: `garmin/README.md`, `docs/superpowers/specs/2026-10-07-garmin-app-design.md`

> **Timing:** Garmin's approval publishes the watch app at once, while the phone app's Garmin
> section only appears after the phone release clears Play review. Build and submit the phone
> release to Play the same day Garmin approves (Task 7 Steps 1-5 back to back).

- [ ] **Step 1:** Set `val STORE_URL: String? = "<store URL from the user>"`.
- [ ] **Step 2:** Run `.\gradlew :mobile:testDebugUnitTest --tests "com.databelay.refwatch.data.garmin.*"`. Expected: pass.
  Check the model with adb (`CPH2655`), then run `.\gradlew :mobile:installDebug`.
  On the phone, open Settings → Garmin watch → the store link. Expected: the RefWatch store page
  opens.
- [ ] **Step 3: Docs.**
  - Spec Build order step 5: add "Done (<date>); Connect IQ store: <URL>".
  - `garmin/README.md`: add "Phase 5 results (release), <date>", with the store URL, the
    approved version, the memory figures from Task 2, and the beta-test results from Task 5.
  - Update the memory file `platform-expansion.md`: Garmin app published.
- [ ] **Step 4: Commit:** "Link the phone's Garmin section to the Connect IQ store".
- [ ] **Step 5: Phone release.** Run `/release`. It bumps the version, builds and checks both
  bundles (SHA1 `A2:91:2C:...`), commits and pushes `main`, then tags. Push the tag only after
  the user confirms. The user uploads `refwatch-mobile-release-vX.Y.Z.aab` (and the wear bundle,
  if the Wear OS 4 change has not shipped yet) in Play Console.
- [ ] **Step 6 (optional, the user's call):** add "Works with Garmin watches" to the Play
  description only, never the title or the icon.

---

## Later (not in this phase)

These come from the spec and the phase 4 notes:
- extra time, penalties and analytics
- GPS and heart-rate upload
- Instinct layouts
- a Garmin CI build
- an API 33 device test of the Wear OS 4 change
- the RestrictedApi lint errors at `HealthServicesManager.kt:125`
- the menu focus jumping to the top after a rebuild
- persisting "Link again" across restarts
- `syncStatus` unit tests
