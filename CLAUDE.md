# RefWatch

Wear OS soccer referee watch app (`wear/`) with a phone companion (`mobile/`) and a
shared `common/` module. Both app modules use the application ID
`com.databelay.refwatch` and ship under one Play listing.

## Environment

- Windows. The user runs commands in **PowerShell** — give commands in PowerShell form
  (`& "C:\path\to.exe" args`, `$env:VAR`), not bash form.
- `JAVA_HOME` is not set globally. For Gradle from a shell:
  `$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"`
- adb: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`
- If a Gradle task fails with `FileSystemException ... classes.jar ... used by another
  process`, Android Studio's daemon holds the file. `./gradlew --stop` and retry.

## Font scaling is the recurring failure

Play rejected this app **twice** for "texts are cut off when a large font size is
selected". Wear OS font scales top out at **1.24**, and the tightest device is a
**192dp small round** screen.

Before changing any Wear layout, render it and look at it:

```
./gradlew :wear:updateDebugScreenshotTest   # writes PNGs + baselines
./gradlew :wear:validateDebugScreenshotTest # fails on any layout change
```

Rendered output lands in
`wear/build/outputs/screenshotTest-results/preview/debug/rendered/`.
`wear/src/screenshotTest/kotlin/FontScaleAudit.kt` covers every screen at 1.24.
Reading those PNGs found four clipped screens that reasoning about the code had missed
— do not substitute inspection of the source for looking at the render.

Things that bit us, worth checking in new layouts:

- **Dialogs are not rendered unless you render them.** A Wear `Dialog` / `AlertDialog`
  opens its own window, which preview rendering does not capture, so the audit passed for
  ten Play rejections while the confirmation dialogs clipped their buttons. Split each
  dialog into a `*Content` composable (built on `AlertDialogContent`) and add it to
  `FontScaleAudit.kt`.
- `AlertDialogDefaults.ConfirmButton` / `DismissButton` are **fixed-size icon buttons**
  (63x54dp, 60dp). Never put a text label in them — use the icon and make the label the
  `contentDescription`. Labelled choices go in full-width `Button`s.
- `ConfirmationDialog` caps its text at 3 non-scrolling lines and `AlertDialog` titles at 3.
  Anything longer belongs in an `AlertDialog` text slot, which scrolls.
- The watch also has a **Bold text** setting that previews cannot simulate; check it on a
  device.

- `ScreenScaffold`'s `contentPadding` already reserves **10% of screen height** top and
  bottom. Adding another `vertical =` padding on top of it eats half a small screen.
- On a round screen, `fillMaxWidth()` content near the top or bottom has its ends cut
  off by the bezel. Inset to roughly `0.82f` there.
- A single long word (`"Yellow"`) cannot wrap, so a narrow button clips it mid-word
  with no way for the user to reveal it. Prefer shape/colour over a label, or give the
  label real width.
- `Picker` fades its edges by `PickerDefaults.GradientRatio`; over a short viewport that
  fade reaches the selected value and reads as clipped text. Set `gradientRatio = 0f`.
- Size text containers from the text (`fontSize.toDp()`), not fixed dp, so they grow
  with the font scale.
- `maxLines` without `overflow = TextOverflow.Ellipsis` clips mid-glyph — always pair
  them.
- Wear Compose has **no `TextField`**. Free text goes through the system input activity
  (`RemoteInputIntentHelper`, see `TextInput.kt`); bounded numbers use a `Picker`.

## Signing — there are TWO keys, and only one is correct

**The Play upload key** is a PKCS#12 keystore at `C:\Users\oleyk\keys_for_android_studio`
(no file extension — `find -name "*.jks"` will not see it), alias **`key0`**.
Fingerprint `SHA1: A2:91:2C:C8:86:4C:4C:3E:5A:C0:0B:8B:C0:F7:DC:59:90:C3:C7:33`. This is
what Android Studio's Generate Signed Bundle wizard uses; the path and alias are
remembered in `.idea/workspace.xml` under `KEY_STORE_PATH` / `KEY_ALIAS`.

**`C:\Users\oleyk\keystores\refwatch-release.jks`** (alias `refwatch`, RSA 4096) is a
*different*, later-generated key with fingerprint `SHA1: 6B:AF:C7:99:...`. Play rejects
anything signed with it ("Your Android App Bundle is signed with the wrong key"). The
`ANDROID_KEYSTORE_*` GitHub secrets once held it, so the v1.1.8 and v1.1.9 release assets
are unusable. **Do not use this key.**

The secrets were re-encoded from the upload key (alias `key0`; for this PKCS#12 keystore
the store and key passwords are the same) and CI signing was confirmed correct from
v1.2.1 on. The v1.2.0 tag run failed at signing and published no Release. CI and local
builds now produce equivalent, uploadable bundles — but still check the fingerprint,
because a secret that drifts from the keystore fails silently into the wrong key.

Local signing: both modules have a `signingConfig` that activates only when
`refwatchStoreFile` / `refwatchStorePassword` / `refwatchKeyAlias` /
`refwatchKeyPassword` are set in `~/.gradle/gradle.properties`. Those properties are
absent on CI, so `canSignLocally` is false there and the workflow's own signing step
still applies.

Never put keystore credentials in a file inside the repository, and never ask the user
to paste them into a conversation — point them at `~/.gradle/gradle.properties`.

A signed local build drops the `-unsigned` suffix from the output filename. Verify which
key was actually used before uploading:

```
& "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe" -printcert -jarfile <file.aab>
```

## Releasing

Versions come from the git tag: CI passes `-PversionName=${tag#v}`, and
`refWatchVersionCode` in each module's `build.gradle.kts` derives the code from it.

The formula is `400_000_000 + major*1_000_000 + minor*10_000 + patch*100 + variant`
(wear variant 1, mobile 0), so v1.2.1 is `401020101` / `401020100`. Minor and patch get
two digits each. The 400_000_000 floor keeps every code above the old scheme's last
published code (`361190001`); the old scheme gave minor and patch one digit each and made
`1.1.10` collide with `1.2.0`. Play requires unique, strictly increasing codes, so a tag
can never be reused for a new upload — bump the version instead. Play has rejected
`401020001` (v1.2.0).

Use `/release` for the full sequence. The short version: verify, commit, push `main`,
then tag and push the tag — the tag push is what publishes a public GitHub Release, so
confirm before it.

The workflow (`.github/workflows/build.yml`) gates the release build on
`:wear:validateDebugScreenshotTest`. Its unit-test job is deliberately non-gating, so a
red unit test does not block a release.

## Games reach the watch through Firestore, not the Data Layer

The watch reads `users/{uid}/games` with its own Firestore listener. Do not push game lists
over the Wear Data Layer: a DataItem is limited to ~100 KB, and games carry GPS and heart-rate
history, so a full list failed at 3.7 MB. Only sign-in (`/phone_user_id`), settings
(`/settings`) and single game updates from the watch use the Data Layer.

## Settings

`collectPositionInfo` and `logGoalScorer` are owned by the **phone** Settings screen and
pushed to the watch over the data layer at `/settings`. The watch caches them in its own
prefs and `WearGameViewModel` observes prefs for changes. A new synced setting needs its
path added to the `DATA_CHANGED` intent-filter in `wear/src/main/AndroidManifest.xml`,
or the watch is simply never told.
