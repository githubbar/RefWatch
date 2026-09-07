# Releasing RefWatch

CI lives in [`.github/workflows/build.yml`](.github/workflows/build.yml).

| Trigger | What you get |
| --- | --- |
| Push / PR to `main` | Debug APKs (mobile + wear) as a workflow artifact, plus unit-test reports |
| Push a `v*` tag | Signed release APKs + AABs attached to a GitHub Release |
| Manual "Run workflow" | Debug, or release artifacts if you tick `build_release` |

## One-time setup

### 1. Create a release keystore

Keep it **outside** the repo and back it up — losing it means you can never update
the app under the same signing identity.

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\keystores" | Out-Null; & "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "$env:USERPROFILE\keystores\refwatch-release.jks" -alias refwatch -keyalg RSA -keysize 4096 -validity 10000
```

It prompts for a keystore password, a key password (press Enter to reuse the
keystore one) and your name/organisation.

### 2. Encode it for GitHub

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\keystores\refwatch-release.jks")) | Set-Clipboard; Write-Host "Base64 keystore copied to clipboard"
```

### 3. Add the repository secrets

Settings → Secrets and variables → Actions → **New repository secret**
(<https://github.com/githubbar/RefWatch/settings/secrets/actions>):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the clipboard contents from step 2 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `refwatch` |
| `ANDROID_KEY_PASSWORD` | key password |
| `MAPS_API_KEY` | your Google Maps key (optional; CI uses a placeholder without it, and maps stay blank in that build) |

Until `ANDROID_KEYSTORE_BASE64` exists the pipeline still runs, but release APKs
come out **unsigned** and cannot be installed. The release notes say so.

## Cutting a release

Versions come from the tag — no need to edit any `build.gradle.kts`:

```bash
git tag v1.1.8 && git push origin v1.1.8
```

`v1.1.8` produces `versionName` `1.1.8` and `versionCode` `361180000` (mobile) /
`361180001` (wear), following the
[Wear packaging scheme](https://developer.android.com/training/wearables/packaging):

```
36        | 118             | 00           | 00 / 01
targetSdk | product version | build number | multi-APK variant
```

A tag containing a hyphen (`v1.2.0-beta1`) is published as a **pre-release**.

Local builds keep using the fallback version in each module's `build.gradle.kts`.
To reproduce a CI release build locally:

```powershell
.\gradlew.bat :mobile:assembleRelease :wear:assembleRelease -PversionName=1.1.8
```

`-PversionCode=<int>` overrides the derived code if you ever need to hand-pick it.
