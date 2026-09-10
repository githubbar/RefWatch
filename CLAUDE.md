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
*different*, later-generated key with fingerprint `SHA1: 6B:AF:C7:99:...`. It is what the
`ANDROID_KEYSTORE_*` GitHub secrets contain, so **every CI-produced artifact is signed
with the wrong key and Play rejects it** ("Your Android App Bundle is signed with the
wrong key"). v1.1.8 and v1.1.9 release assets are affected.

Until the CI secrets are re-encoded from the upload key, take release bundles from a
**local** build, not from the GitHub Release.

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

**Known bug:** the formula is `major*100 + minor*10 + patch`, giving minor and patch one
digit each, so `1.1.10` and `1.2.0` both yield `361200001`. Play requires unique,
strictly increasing codes. This needs fixing before either version ships.

Use `/release` for the full sequence. The short version: verify, commit, push `main`,
then tag and push the tag — the tag push is what publishes a public GitHub Release, so
confirm before it.

The workflow (`.github/workflows/build.yml`) does **not** currently run
`validateDebugScreenshotTest`, and its unit-test job is deliberately non-gating, so a
red test does not block a release.

## Settings

`collectPositionInfo` and `logGoalScorer` are owned by the **phone** Settings screen and
pushed to the watch over the data layer at `/settings`. The watch caches them in its own
prefs and `WearGameViewModel` observes prefs for changes. A new synced setting needs its
path added to the `DATA_CHANGED` intent-filter in `wear/src/main/AndroidManifest.xml`,
or the watch is simply never told.
