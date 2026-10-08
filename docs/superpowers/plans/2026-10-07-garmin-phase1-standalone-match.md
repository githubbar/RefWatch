# Garmin Phase 1 — Standalone Match Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Connect IQ watch app in `garmin/` that referees a complete quick match — set-up, both halves, halftime, added time, goals, cards, game log, undo, full time — with the match saved after every action, running on the fēnix 5X and newer watches.

**Architecture:** All match rules live in one UI-free class, `MatchState`, which takes the current time as an argument and is unit-tested in the simulator. Screens are Connect IQ `Menu2` menus, `Picker`s and one custom-drawn `MatchView`; they call `MatchState` and persist it through `MatchStore` (Application.Storage) after every change. No sync, recording or pairing in this phase — those are later plans.

**Tech Stack:** Monkey C, Connect IQ SDK (latest, via SDK Manager), `minApiLevel` 3.1.0, PowerShell build script, Connect IQ simulator and unit-test runner.

**Spec:** `docs/superpowers/specs/2026-10-07-garmin-app-design.md` (this plan implements build-order step 1, "Standalone match").

## Global Constraints

- Project lives in `garmin/`; Gradle never sees it.
- `minApiLevel="3.1.0"`; products: `fenix5x`, `fenix7s`, `fenix7`, `epix2pro47mm`, `fr265` (confirm the IDs exist in `%APPDATA%\Garmin\ConnectIQ\Devices` in Task 1; substitute the nearest existing ID if one is named differently, and note it in the commit).
- Phase, team and card strings are exactly the Kotlin enum names: `PRE_GAME`, `FIRST_HALF`, `HALF_TIME`, `SECOND_HALF`, `GAME_ENDED`, `HOME`, `AWAY`, `YELLOW`, `RED`.
- Events are stored in the phone's `GameEvent` JSON shape: keys `eventType` (`GOAL` / `CARD` / `PHASE_CHANGE`), `id`, `timestamp` (wall-clock epoch ms, Double), `gameTimeMillis` (elapsed **within the current period**, Double); GOAL adds `team`, `homeScoreAtTime`, `awayScoreAtTime`, optional `playerNumber`; CARD adds `team`, `playerNumber`, `cardType`; PHASE_CHANGE adds `newPhase`.
- The clock is computed from timestamps (period start, accumulated pause), never by counting ticks.
- Match state is saved to storage after every state change.
- Buttons: START = pause/resume (halftime: start 2nd half; full time: save); UP = Home actions; DOWN = Away actions; hold UP = match menu; BACK = confirm before leaving.
- No bitmaps other than the launcher icon; all user-visible text in `resources/strings/strings.xml`.
- fēnix 5X memory use must stay under 60% of its `watchApp` limit.
- The Connect IQ developer key lives at `%USERPROFILE%\keys_for_garmin\developer_key.der`, never in the repo. Store updates must be signed with the same key, so it must be backed up.
- Commands are PowerShell. Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## File Structure

```
garmin/
  .gitignore                     bin/, keys
  README.md                      setup, build, test, sideload
  build.ps1                      build / unit-test / run in simulator
  manifest.xml                   app id, products, minApiLevel
  monkey.jungle                  source paths
  resources/
    drawables/drawables.xml      launcher icon declaration
    drawables/launcher_icon.png
    strings/strings.xml          every user-visible string
    settings/properties.xml      logGoalScorer property + setting
  source/
    RefWatchApp.mc               AppBase: start-up, initial view (resume or game list)
    util/Clock.mc                wall-clock milliseconds
    util/Ids.mc                  random UUID strings
    util/Format.mc               mm:ss and time-of-day text
    util/Alerts.mc               vibration
    util/Settings.mc             app settings access
    model/MatchState.mc          match rules (no UI, no storage, no clock)
    model/MatchStore.mc          save / load / archive MatchState
    model/GameLog.mc             match-minute labels and log rows
    model/TeamColors.mc          color palette
    ui/Ask.mc                yes/no confirmation helper
    ui/Pickers.mc                number pickers (minutes, player number)
    ui/GameList.mc               start menu
    ui/PreMatch.mc               set-up menu
    ui/MatchView.mc              the match screen (all phases)
    ui/MatchDelegate.mc          match screen buttons
    ui/TeamActions.mc            goal / yellow / red menu
    ui/MatchMenu.mc              end half, log, undo, abandon
    ui/GameLogView.mc            log menu
  test/
    TestSupport.mc               shared test fixture
    MatchStateTimingTest.mc
    MatchStateEventsTest.mc
    MatchStoreTest.mc
    GameLogTest.mc
```

---

### Task 1: Toolchain and project skeleton

**Files:**
- Create: `garmin/.gitignore`, `garmin/README.md`, `garmin/build.ps1`, `garmin/manifest.xml`, `garmin/monkey.jungle`, `garmin/resources/drawables/drawables.xml`, `garmin/resources/drawables/launcher_icon.png`, `garmin/resources/strings/strings.xml`, `garmin/source/RefWatchApp.mc`, `garmin/test/SmokeTest.mc`

**Interfaces:**
- Produces: `.\garmin\build.ps1 [-Device <id>] [-Test] [-Run]`; `getInitialView()` in `RefWatchApp` (replaced in Task 4).

- [ ] **Step 1: User installs the SDK (cannot be automated — needs the user's Garmin login)**

Ask the user to:
1. Create a free Garmin developer account at https://developer.garmin.com/connect-iq/ (Sign In → Create Account).
2. Download and run the **Connect IQ SDK Manager** from https://developer.garmin.com/connect-iq/sdk/, sign in, install the **latest SDK**, and set it as current.
3. In the SDK Manager's **Devices** tab, download: fēnix 5X, fēnix 7S, fēnix 7, epix Pro (Gen 2) 47mm, Forerunner 265.
4. In VS Code, install the **Monkey C** extension (Garmin).

Then verify:

```powershell
Get-ChildItem "$env:APPDATA\Garmin\ConnectIQ\Sdks" -Directory | Select-Object Name
Get-ChildItem "$env:APPDATA\Garmin\ConnectIQ\Devices" -Directory | Select-Object Name
```

Expected: one SDK folder (e.g. `connectiq-sdk-win-8.x.x-...`) and device folders including `fenix5x`. Note the exact device IDs for the manifest.

- [ ] **Step 2: Generate the developer key outside the repo**

```bash
mkdir -p ~/keys_for_garmin && cd ~/keys_for_garmin && \
openssl genrsa -out developer_key.pem 4096 && \
openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key.der -nocrypt && ls -l
```

Expected: `developer_key.pem` and `developer_key.der`. Tell the user to back up this folder — every future Connect IQ store update must be signed with this key.

- [ ] **Step 3: Read the fēnix 5X memory limit**

```powershell
Get-Content "$env:APPDATA\Garmin\ConnectIQ\Devices\fenix5x\compiler.json" | Select-String -Pattern "watchApp" -Context 0,3
```

Record the `watchApp` memory limit (bytes) for the README in Step 6.

- [ ] **Step 4: Create the project files**

`garmin/.gitignore`:
```
bin/
*.der
*.pem
```

`garmin/monkey.jungle`:
```
project.manifest = manifest.xml
base.sourcePath = source;test
```

`garmin/manifest.xml`:
```xml
<?xml version="1.0"?>
<iq:manifest version="3" xmlns:iq="http://www.garmin.com/xml/connectiq">
    <iq:application id="946fcc57eef840c1ad2df79b4f43f30e" type="watch-app" name="@Strings.AppName"
                    entry="RefWatchApp" launcherIcon="@Drawables.LauncherIcon" minApiLevel="3.1.0">
        <iq:products>
            <iq:product id="fenix5x"/>
            <iq:product id="fenix7s"/>
            <iq:product id="fenix7"/>
            <iq:product id="epix2pro47mm"/>
            <iq:product id="fr265"/>
        </iq:products>
        <iq:permissions/>
        <iq:languages>
            <iq:language>eng</iq:language>
        </iq:languages>
        <iq:barrels/>
    </iq:application>
</iq:manifest>
```

`garmin/resources/drawables/drawables.xml`:
```xml
<drawables>
    <bitmap id="LauncherIcon" filename="launcher_icon.png"/>
</drawables>
```

`garmin/resources/strings/strings.xml`:
```xml
<strings>
    <string id="AppName">RefWatch</string>
</strings>
```

Launcher icon, scaled from the Play icon (the compiler rescales it per device):
```powershell
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile("$PWD\ic_launcher-playstore.png")
$bmp = New-Object System.Drawing.Bitmap 70, 70
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($src, 0, 0, 70, 70)
$bmp.Save("$PWD\garmin\resources\drawables\launcher_icon.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose(); $src.Dispose()
```

`garmin/source/RefWatchApp.mc` (temporary view, replaced in Task 4):
```monkeyc
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

class RefWatchApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        return [new HelloView()];
    }
}

class HelloView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        dc.drawText(dc.getWidth() / 2, dc.getHeight() / 2, Graphics.FONT_MEDIUM,
            WatchUi.loadResource(Rez.Strings.AppName) as String,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}
```

`garmin/test/SmokeTest.mc` (proves the test runner works; deleted in Task 2):
```monkeyc
import Toybox.Lang;
import Toybox.Test;

(:test)
function smokeTest(logger as Logger) as Boolean {
    return true;
}
```

`garmin/build.ps1`:
```powershell
<#
.SYNOPSIS
  Build, unit-test or run the RefWatch Connect IQ app.
.EXAMPLE
  .\garmin\build.ps1                       # build for fenix5x
  .\garmin\build.ps1 -Device fenix7 -Run   # build and open in the simulator
  .\garmin\build.ps1 -Test                 # build with unit tests and run them in the simulator
#>
param(
    [string]$Device = "fenix5x",
    [switch]$Test,
    [switch]$Run,
    [string]$Key = "$env:USERPROFILE\keys_for_garmin\developer_key.der"
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$sdkRoot = "$env:APPDATA\Garmin\ConnectIQ\Sdks"
$sdk = Get-ChildItem $sdkRoot -Directory -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
if (-not $sdk) { throw "No Connect IQ SDK in $sdkRoot. Install one with the SDK Manager (see garmin/README.md)." }
$bin = Join-Path $sdk.FullName "bin"
if (-not (Test-Path $Key)) { throw "Developer key not found at $Key (see garmin/README.md)." }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr" }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

New-Item -ItemType Directory -Force "$root\bin" | Out-Null
$out = "$root\bin\RefWatch-$Device.prg"
$compileArgs = @("-o", $out, "-f", "$root\monkey.jungle", "-y", $Key, "-d", $Device, "-w")
if ($Test) { $compileArgs += "-t" }
& "$bin\monkeyc.bat" @compileArgs
if ($LASTEXITCODE -ne 0) { throw "monkeyc failed" }

if ($Test -or $Run) {
    if (-not (Get-Process simulator -ErrorAction SilentlyContinue)) {
        Start-Process "$bin\simulator.exe"
        Start-Sleep -Seconds 6
    }
    $runArgs = @($out, $Device)
    if ($Test) { $runArgs += "-t" }
    $output = & "$bin\monkeydo.bat" @runArgs 2>&1 | Out-String
    Write-Output $output
    # Case-sensitive: the summary line is "PASSED (passed=N, failed=0, errors=0)".
    if ($Test -and (($output -cmatch "FAILED|ERROR") -or ($output -cnotmatch "PASSED"))) {
        throw "Unit tests failed"
    }
}
```

- [ ] **Step 5: Build, run and test**

```powershell
.\garmin\build.ps1
.\garmin\build.ps1 -Run
.\garmin\build.ps1 -Test
```

Expected: the build succeeds (warnings about launcher-icon scaling are fine); the simulator shows a fēnix 5X with "RefWatch" in the centre; the test run prints a summary containing `PASSED`. If the test summary format differs from `PASSED (...)`, adjust the match in `build.ps1` to the real summary text and note it in the commit. If `getInitialView`'s return annotation is rejected by this SDK, use the type the compiler error names.

- [ ] **Step 6: Write `garmin/README.md`**

```markdown
# RefWatch for Garmin (Connect IQ)

Design: `docs/superpowers/specs/2026-10-07-garmin-app-design.md`.

## One-time setup
1. Free Garmin developer account; install the Connect IQ SDK Manager, the latest SDK, and the
   devices fēnix 5X, fēnix 7S, fēnix 7, epix Pro 47mm, Forerunner 265.
2. Developer key at `%USERPROFILE%\keys_for_garmin\developer_key.der` (generated with openssl,
   see the plan). **Back it up** — store updates must be signed with the same key.
3. Java comes from Android Studio's bundled JBR; `build.ps1` sets it.

## Build, test, run
    .\garmin\build.ps1                      # fenix5x .prg in garmin\bin
    .\garmin\build.ps1 -Device fenix7 -Run  # open in the simulator
    .\garmin\build.ps1 -Test                # unit tests in the simulator

## Memory budget
fēnix 5X watch-app limit: <BYTES FROM STEP 3> bytes. Keep peak use under 60%
(simulator: File → View Memory).

## Sideload to a watch
Build for that watch's device ID, connect it by USB, and copy `garmin\bin\RefWatch-<device>.prg`
to the watch's `GARMIN\APPS` folder. Eject; the app appears in the watch's app list.
Sideloaded apps cannot be configured from Garmin Connect; test settings in the simulator
(File → Edit Persistent Storage → Edit Application.Properties).
```

Replace `<BYTES FROM STEP 3>` with the number recorded in Step 3.

- [ ] **Step 7: Commit**

```powershell
git add garmin
git commit -m "Add Connect IQ project skeleton for the Garmin app`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Match timing in `MatchState`

**Files:**
- Create: `garmin/source/util/Clock.mc`, `garmin/source/util/Ids.mc`, `garmin/source/model/MatchState.mc`, `garmin/test/TestSupport.mc`, `garmin/test/MatchStateTimingTest.mc`
- Delete: `garmin/test/SmokeTest.mc`

**Interfaces:**
- Produces:
  - `Clock.init() as Void`, `Clock.nowMs() as Long`
  - `Ids.newId() as String` (36-char UUID-shaped)
  - constants `PHASE_PRE_GAME`, `PHASE_FIRST_HALF`, `PHASE_HALF_TIME`, `PHASE_SECOND_HALF`, `PHASE_GAME_ENDED`, `PHASE_ABANDONED`, `TEAM_HOME`, `TEAM_AWAY`, `CARD_YELLOW`, `CARD_RED`
  - `class MatchState` — `initialize(setup as Dictionary)` with setup keys `id, homeName, awayName, homeColor, awayColor, halfMinutes, halftimeMinutes, kickOffTeam, scheduledStartMs`; public vars `gameId, homeName, awayName, homeColor, awayColor, halfMinutes, halftimeMinutes, kickOffTeam, scheduledStartMs, phase, homeScore, awayScore, events, startedAtMs, periodStartMs, pausedTotalMs, pausedAtMs, regulationAlerted`; methods `kickOff(nowMs)`, `endPeriod(nowMs)`, `pause(nowMs)`, `resume(nowMs)`, `togglePause(nowMs)`, `isPlaying()`, `isPaused()`, `elapsedMs(nowMs) as Long`, `regulationMs() as Long`, `addedMs(nowMs) as Long`, `isPastRegulation(nowMs) as Boolean`, `breakRemainingMs(nowMs) as Long`, `takeRegulationAlert(nowMs) as Boolean`
  - test fixture `testMatch() as MatchState` (30-minute halves, 5-minute break, Home kicks off)

- [ ] **Step 1: Write the failing tests**

Delete `garmin/test/SmokeTest.mc`.

`garmin/test/TestSupport.mc`:
```monkeyc
import Toybox.Lang;

// Shared fixture for unit tests. Not annotated (:test), so the runner does not call it.
function testMatch() as MatchState {
    return new MatchState({
        "id" => "test-game",
        "homeName" => "Eagles",
        "awayName" => "Hawks",
        "homeColor" => 0xFF0000,
        "awayColor" => 0x0055FF,
        "halfMinutes" => 30,
        "halftimeMinutes" => 5,
        "kickOffTeam" => TEAM_HOME,
        "scheduledStartMs" => null
    });
}
```

`garmin/test/MatchStateTimingTest.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.Test;

const T0 = 1000000000000l; // an arbitrary wall-clock start, epoch ms
const MIN = 60000l;

(:test)
function kickOffStartsFirstHalf(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    Test.assertEqual(PHASE_FIRST_HALF, m.phase);
    Test.assertEqual(T0, m.startedAtMs);
    Test.assertEqual(0l, m.elapsedMs(T0));
    Test.assertEqual(1, m.events.size());
    var e = m.events[0];
    Test.assertEqual("PHASE_CHANGE", e["eventType"]);
    Test.assertEqual(PHASE_FIRST_HALF, e["newPhase"]);
    Test.assertEqual(0.0d, e["gameTimeMillis"]);
    return true;
}

(:test)
function elapsedFollowsWallClock(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    Test.assertEqual(90000l, m.elapsedMs(T0 + 90000l));
    return true;
}

(:test)
function pauseFreezesClockAndResumeContinues(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.pause(T0 + MIN);
    Test.assert(m.isPaused());
    Test.assertEqual(MIN, m.elapsedMs(T0 + 3 * MIN));
    m.resume(T0 + 3 * MIN);
    Test.assert(!m.isPaused());
    Test.assertEqual(2 * MIN, m.elapsedMs(T0 + 4 * MIN));
    return true;
}

(:test)
function addedTimeCountsPastRegulation(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    Test.assert(!m.isPastRegulation(T0 + 30 * MIN - 1l));
    Test.assertEqual(0l, m.addedMs(T0 + 29 * MIN));
    Test.assert(m.isPastRegulation(T0 + 30 * MIN));
    Test.assertEqual(MIN, m.addedMs(T0 + 31 * MIN));
    return true;
}

(:test)
function regulationAlertFiresOncePerPeriod(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    Test.assert(!m.takeRegulationAlert(T0 + 29 * MIN));
    Test.assert(m.takeRegulationAlert(T0 + 30 * MIN));
    Test.assert(!m.takeRegulationAlert(T0 + 31 * MIN));
    m.endPeriod(T0 + 32 * MIN);
    Test.assert(m.takeRegulationAlert(T0 + 37 * MIN)); // end of the 5-minute break
    return true;
}

(:test)
function endFirstHalfStartsBreak(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.endPeriod(T0 + 31 * MIN);
    Test.assertEqual(PHASE_HALF_TIME, m.phase);
    Test.assertEqual(5 * MIN, m.breakRemainingMs(T0 + 31 * MIN));
    Test.assertEqual(3 * MIN, m.breakRemainingMs(T0 + 33 * MIN));
    Test.assertEqual(0l, m.breakRemainingMs(T0 + 40 * MIN));
    var e = m.events[m.events.size() - 1];
    Test.assertEqual(PHASE_HALF_TIME, e["newPhase"]);
    Test.assertEqual((31 * MIN).toDouble(), e["gameTimeMillis"]);
    return true;
}

(:test)
function secondHalfFlipsKickOffAndRestartsClock(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.kickOff(T0 + 36 * MIN);
    Test.assertEqual(PHASE_SECOND_HALF, m.phase);
    Test.assertEqual(TEAM_AWAY, m.kickOffTeam);
    Test.assertEqual(0l, m.elapsedMs(T0 + 36 * MIN));
    Test.assertEqual(MIN, m.elapsedMs(T0 + 37 * MIN));
    Test.assertEqual(T0, m.startedAtMs);
    return true;
}

(:test)
function endSecondHalfEndsGame(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.kickOff(T0 + 35 * MIN);
    m.endPeriod(T0 + 66 * MIN);
    Test.assertEqual(PHASE_GAME_ENDED, m.phase);
    Test.assert(!m.isPlaying());
    Test.assert(!m.takeRegulationAlert(T0 + 70 * MIN));
    return true;
}

(:test)
function invalidTransitionsAreIgnored(logger as Logger) as Boolean {
    var m = testMatch();
    m.pause(T0);                       // not started
    Test.assertEqual(PHASE_PRE_GAME, m.phase);
    Test.assert(!m.isPaused());
    m.kickOff(T0);
    m.kickOff(T0 + MIN);               // already playing
    Test.assertEqual(T0, m.periodStartMs);
    m.endPeriod(T0 + 30 * MIN);
    m.pause(T0 + 31 * MIN);            // no pausing the break
    Test.assert(!m.isPaused());
    return true;
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\garmin\build.ps1 -Test`
Expected: compile error — `MatchState` / `TEAM_HOME` / `PHASE_FIRST_HALF` not found.

- [ ] **Step 3: Implement**

`garmin/source/util/Clock.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;

// Wall-clock epoch milliseconds. Time.now() has one-second resolution, so the sub-second part
// comes from System.getTimer(), measured from when the app started. Wall-clock based, so a
// match saved before a restart resumes with the right elapsed time.
module Clock {
    var baseEpochMs as Long = 0l;
    var baseTimer as Number = 0;

    function init() as Void {
        baseEpochMs = Time.now().value().toLong() * 1000l;
        baseTimer = System.getTimer();
    }

    function nowMs() as Long {
        return baseEpochMs + (System.getTimer() - baseTimer).toLong();
    }
}
```

`garmin/source/util/Ids.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.Math;

// Random UUID-shaped ids for games and events. Math.srand is seeded in RefWatchApp.onStart.
module Ids {
    function newId() as String {
        var hex = "0123456789abcdef";
        var id = "";
        for (var i = 0; i < 32; i++) {
            if (i == 8 || i == 12 || i == 16 || i == 20) {
                id += "-";
            }
            var n = Math.rand() % 16;
            id += hex.substring(n, n + 1);
        }
        return id;
    }
}
```

`garmin/source/model/MatchState.mc`:
```monkeyc
import Toybox.Lang;

// Phase, team and card names are the Kotlin enum names in common/.../DataModels.kt, because
// they are uploaded as-is and parsed by the phone app.
const PHASE_PRE_GAME = "PRE_GAME";
const PHASE_FIRST_HALF = "FIRST_HALF";
const PHASE_HALF_TIME = "HALF_TIME";
const PHASE_SECOND_HALF = "SECOND_HALF";
const PHASE_GAME_ENDED = "GAME_ENDED";
// Watch-only: set when a match is discarded so its screen closes. Never stored or uploaded.
const PHASE_ABANDONED = "ABANDONED";
const TEAM_HOME = "HOME";
const TEAM_AWAY = "AWAY";
const CARD_YELLOW = "YELLOW";
const CARD_RED = "RED";

// The rules of a match: periods, the clock, the score and the event log. It never reads the
// clock and never touches storage or the screen; callers pass the current time in, so every
// rule is unit-testable.
//
// The clock is derived from timestamps: elapsed = now - periodStartMs - pausedTotalMs (frozen at
// pausedAtMs while paused). Nothing counts ticks, so a busy or sleeping watch cannot drift.
class MatchState {
    var gameId as String;
    var homeName as String;
    var awayName as String;
    var homeColor as Number;
    var awayColor as Number;
    var halfMinutes as Number;
    var halftimeMinutes as Number;
    var kickOffTeam as String;           // team kicking off the current (or next) half
    var scheduledStartMs as Long or Null;

    var phase as String;
    var homeScore as Number;
    var awayScore as Number;
    var events as Array<Dictionary>;     // GameEvent JSON shape, see the spec
    var startedAtMs as Long or Null;     // wall clock of the first kick-off
    var periodStartMs as Long or Null;   // start of the current half or break
    var pausedTotalMs as Long;
    var pausedAtMs as Long or Null;
    var regulationAlerted as Boolean;    // end-of-period vibration already given

    function initialize(setup as Dictionary) {
        gameId = setup["id"] as String;
        homeName = setup["homeName"] as String;
        awayName = setup["awayName"] as String;
        homeColor = setup["homeColor"] as Number;
        awayColor = setup["awayColor"] as Number;
        halfMinutes = setup["halfMinutes"] as Number;
        halftimeMinutes = setup["halftimeMinutes"] as Number;
        kickOffTeam = setup["kickOffTeam"] as String;
        scheduledStartMs = setup["scheduledStartMs"] as Long or Null;
        phase = PHASE_PRE_GAME;
        homeScore = 0;
        awayScore = 0;
        events = [] as Array<Dictionary>;
        startedAtMs = null;
        periodStartMs = null;
        pausedTotalMs = 0l;
        pausedAtMs = null;
        regulationAlerted = false;
    }

    function isPlaying() as Boolean {
        return phase.equals(PHASE_FIRST_HALF) || phase.equals(PHASE_SECOND_HALF);
    }

    function isPaused() as Boolean {
        return pausedAtMs != null;
    }

    // Pre-game → 1st half, or half time → 2nd half (the other team kicks off).
    function kickOff(nowMs as Long) as Void {
        if (phase.equals(PHASE_PRE_GAME)) {
            startedAtMs = nowMs;
            phase = PHASE_FIRST_HALF;
        } else if (phase.equals(PHASE_HALF_TIME)) {
            kickOffTeam = kickOffTeam.equals(TEAM_HOME) ? TEAM_AWAY : TEAM_HOME;
            phase = PHASE_SECOND_HALF;
        } else {
            return;
        }
        startPeriod(nowMs);
        logPhase(nowMs, 0l);
    }

    // 1st half → half time, or 2nd half → game ended.
    function endPeriod(nowMs as Long) as Void {
        if (!isPlaying()) {
            return;
        }
        var elapsed = elapsedMs(nowMs);
        if (phase.equals(PHASE_FIRST_HALF)) {
            phase = PHASE_HALF_TIME;
            startPeriod(nowMs);
        } else {
            phase = PHASE_GAME_ENDED;
            periodStartMs = null;
            pausedAtMs = null;
            pausedTotalMs = 0l;
        }
        logPhase(nowMs, elapsed);
    }

    function pause(nowMs as Long) as Void {
        if (isPlaying() && pausedAtMs == null) {
            pausedAtMs = nowMs;
        }
    }

    function resume(nowMs as Long) as Void {
        if (pausedAtMs != null) {
            pausedTotalMs += nowMs - (pausedAtMs as Long);
            pausedAtMs = null;
        }
    }

    function togglePause(nowMs as Long) as Void {
        if (isPaused()) {
            resume(nowMs);
        } else {
            pause(nowMs);
        }
    }

    // Time elapsed in the current half or break.
    function elapsedMs(nowMs as Long) as Long {
        if (periodStartMs == null) {
            return 0l;
        }
        var end = nowMs;
        if (pausedAtMs != null) {
            end = pausedAtMs as Long;
        }
        var elapsed = end - (periodStartMs as Long) - pausedTotalMs;
        return elapsed > 0 ? elapsed : 0l;
    }

    // Regulation length of the current half, or of the break at half time.
    function regulationMs() as Long {
        var minutes = phase.equals(PHASE_HALF_TIME) ? halftimeMinutes : halfMinutes;
        return minutes.toLong() * 60000l;
    }

    function isPastRegulation(nowMs as Long) as Boolean {
        return periodStartMs != null && elapsedMs(nowMs) >= regulationMs();
    }

    function addedMs(nowMs as Long) as Long {
        if (!isPlaying()) {
            return 0l;
        }
        var over = elapsedMs(nowMs) - regulationMs();
        return over > 0 ? over : 0l;
    }

    function breakRemainingMs(nowMs as Long) as Long {
        if (!phase.equals(PHASE_HALF_TIME)) {
            return 0l;
        }
        var remaining = regulationMs() - elapsedMs(nowMs);
        return remaining > 0 ? remaining : 0l;
    }

    // True exactly once per half or break, when regulation time is reached.
    function takeRegulationAlert(nowMs as Long) as Boolean {
        if (regulationAlerted || !isPastRegulation(nowMs)) {
            return false;
        }
        regulationAlerted = true;
        return true;
    }

    hidden function startPeriod(nowMs as Long) as Void {
        periodStartMs = nowMs;
        pausedTotalMs = 0l;
        pausedAtMs = null;
        regulationAlerted = false;
    }

    hidden function logPhase(nowMs as Long, gameTimeMs as Long) as Void {
        events.add({
            "eventType" => "PHASE_CHANGE",
            "id" => Ids.newId(),
            "newPhase" => phase,
            "timestamp" => nowMs.toDouble(),
            "gameTimeMillis" => gameTimeMs.toDouble()
        });
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\garmin\build.ps1 -Test`
Expected: summary `PASSED`, 9 tests, 0 failed. If `Test.assertEqual` reports a Long/Number or Double mismatch, make the expected literal the same type (`l` suffix for Long, `d` for Double) rather than changing `MatchState`.

- [ ] **Step 5: Commit**

```powershell
git add garmin
git commit -m "Add match clock and periods to the Garmin MatchState`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Goals, cards, undo and persistence

**Files:**
- Modify: `garmin/source/model/MatchState.mc` (add methods before `hidden function startPeriod`)
- Create: `garmin/source/model/MatchStore.mc`, `garmin/test/MatchStateEventsTest.mc`, `garmin/test/MatchStoreTest.mc`

**Interfaces:**
- Consumes: `MatchState`, `testMatch()`, constants from Task 2.
- Produces:
  - `MatchState.addGoal(team as String, playerNumber as Number or Null, nowMs as Long) as String or Null` (event id; null when not in a half)
  - `MatchState.setPlayerNumber(eventId as String, number as Number) as Void`
  - `MatchState.addCard(team as String, playerNumber as Number, cardType as String, nowMs as Long) as Void`
  - `MatchState.undoLast() as Dictionary or Null`
  - `MatchState.toDict() as Dictionary`, `static MatchState.fromDict(d as Dictionary) as MatchState`
  - `MatchStore.load() as MatchState or Null`, `MatchStore.save(m as MatchState) as Void`, `MatchStore.clear() as Void`, `MatchStore.archive(m as MatchState) as Void`, `MatchStore.finished() as Array<Dictionary>`, const `MatchStore.MAX_FINISHED = 5`

- [ ] **Step 1: Write the failing tests**

`garmin/test/MatchStateEventsTest.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.Test;

(:test)
function goalUpdatesScoreAndLogsEvent(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    var id = m.addGoal(TEAM_HOME, null, T0 + 754000l);
    Test.assert(id != null);
    Test.assertEqual(1, m.homeScore);
    Test.assertEqual(0, m.awayScore);
    var e = m.events[m.events.size() - 1];
    Test.assertEqual("GOAL", e["eventType"]);
    Test.assertEqual(TEAM_HOME, e["team"]);
    Test.assertEqual(754000.0d, e["gameTimeMillis"]);
    Test.assertEqual((T0 + 754000l).toDouble(), e["timestamp"]);
    Test.assertEqual(1, e["homeScoreAtTime"]);
    Test.assertEqual(0, e["awayScoreAtTime"]);
    Test.assert(!e.hasKey("playerNumber"));
    return true;
}

(:test)
function scorerCanBeAddedAfterTheGoal(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    var id = m.addGoal(TEAM_AWAY, null, T0 + MIN);
    m.setPlayerNumber(id as String, 9);
    Test.assertEqual(9, m.events[m.events.size() - 1]["playerNumber"]);
    return true;
}

(:test)
function goalsOnlyDuringAHalf(logger as Logger) as Boolean {
    var m = testMatch();
    Test.assert(m.addGoal(TEAM_HOME, null, T0) == null);
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    Test.assert(m.addGoal(TEAM_HOME, null, T0 + 31 * MIN) == null);
    Test.assertEqual(0, m.homeScore);
    return true;
}

(:test)
function cardIsLoggedWithPlayer(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addCard(TEAM_AWAY, 4, CARD_YELLOW, T0 + 2 * MIN);
    var e = m.events[m.events.size() - 1];
    Test.assertEqual("CARD", e["eventType"]);
    Test.assertEqual(TEAM_AWAY, e["team"]);
    Test.assertEqual(4, e["playerNumber"]);
    Test.assertEqual(CARD_YELLOW, e["cardType"]);
    Test.assertEqual((2 * MIN).toDouble(), e["gameTimeMillis"]);
    return true;
}

(:test)
function cardsAllowedAtHalfTimeButNotBeforeKickOff(logger as Logger) as Boolean {
    var m = testMatch();
    m.addCard(TEAM_HOME, 5, CARD_RED, T0);
    Test.assertEqual(0, m.events.size());
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.addCard(TEAM_HOME, 5, CARD_RED, T0 + 32 * MIN);
    Test.assertEqual("CARD", m.events[m.events.size() - 1]["eventType"]);
    return true;
}

(:test)
function undoRemovesLatestGoalOrCardAndFixesScore(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addGoal(TEAM_HOME, null, T0 + MIN);
    m.addCard(TEAM_AWAY, 4, CARD_YELLOW, T0 + 2 * MIN);
    var removed = m.undoLast();
    Test.assertEqual("CARD", (removed as Dictionary)["eventType"]);
    Test.assertEqual(1, m.homeScore);
    removed = m.undoLast();
    Test.assertEqual("GOAL", (removed as Dictionary)["eventType"]);
    Test.assertEqual(0, m.homeScore);
    Test.assert(m.undoLast() == null);           // only the kick-off phase change is left
    Test.assertEqual(1, m.events.size());
    return true;
}

(:test)
function undoSkipsPhaseChanges(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addGoal(TEAM_AWAY, 7, T0 + MIN);
    m.endPeriod(T0 + 30 * MIN);
    m.undoLast();
    Test.assertEqual(0, m.awayScore);
    Test.assertEqual(PHASE_HALF_TIME, m.phase);
    Test.assertEqual(2, m.events.size());         // FIRST_HALF and HALF_TIME phase changes
    return true;
}

(:test)
function dictRoundTripPreservesState(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addGoal(TEAM_HOME, 10, T0 + MIN);
    m.pause(T0 + 2 * MIN);
    var copy = MatchState.fromDict(m.toDict());
    Test.assertEqual(m.gameId, copy.gameId);
    Test.assertEqual(PHASE_FIRST_HALF, copy.phase);
    Test.assertEqual(1, copy.homeScore);
    Test.assertEqual(2, copy.events.size());
    Test.assert(copy.isPaused());
    Test.assertEqual(2 * MIN, copy.elapsedMs(T0 + 10 * MIN));
    Test.assertEqual(TEAM_HOME, copy.kickOffTeam);
    Test.assertEqual(30, copy.halfMinutes);
    return true;
}
```

`garmin/test/MatchStoreTest.mc`:
```monkeyc
import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

(:test)
function storeSavesAndLoadsCurrentMatch(logger as Logger) as Boolean {
    MatchStore.clear();
    Test.assert(MatchStore.load() == null);
    var m = testMatch();
    m.kickOff(T0);
    m.addCard(TEAM_HOME, 3, CARD_YELLOW, T0 + MIN);
    MatchStore.save(m);
    var loaded = MatchStore.load() as MatchState;
    Test.assertEqual(m.gameId, loaded.gameId);
    Test.assertEqual(2, loaded.events.size());
    MatchStore.clear();
    Test.assert(MatchStore.load() == null);
    return true;
}

(:test)
function archiveKeepsOnlyTheLatestFinishedMatches(logger as Logger) as Boolean {
    Application.Storage.deleteValue(MatchStore.FINISHED_KEY);
    for (var i = 0; i < MatchStore.MAX_FINISHED + 2; i++) {
        var m = testMatch();
        m.gameId = "game-" + i;
        MatchStore.save(m);
        MatchStore.archive(m);
    }
    var finished = MatchStore.finished();
    Test.assertEqual(MatchStore.MAX_FINISHED, finished.size());
    Test.assertEqual("game-2", finished[0]["id"]);
    Test.assertEqual("game-" + (MatchStore.MAX_FINISHED + 1), finished[finished.size() - 1]["id"]);
    Test.assert(MatchStore.load() == null);      // archiving clears the current match
    return true;
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\garmin\build.ps1 -Test`
Expected: compile error — `addGoal`, `MatchStore` not found.

- [ ] **Step 3: Implement**

Add to `MatchState`, immediately before `hidden function startPeriod`:
```monkeyc
    // Returns the goal's event id, or null outside a half. The scorer can be added afterwards
    // with setPlayerNumber, so the score changes the moment the referee presses Goal.
    function addGoal(team as String, playerNumber as Number or Null, nowMs as Long) as String or Null {
        if (!isPlaying()) {
            return null;
        }
        if (team.equals(TEAM_HOME)) {
            homeScore += 1;
        } else {
            awayScore += 1;
        }
        var event = newEvent("GOAL", nowMs);
        event["team"] = team;
        event["homeScoreAtTime"] = homeScore;
        event["awayScoreAtTime"] = awayScore;
        if (playerNumber != null) {
            event["playerNumber"] = playerNumber;
        }
        events.add(event);
        return event["id"] as String;
    }

    function setPlayerNumber(eventId as String, number as Number) as Void {
        for (var i = 0; i < events.size(); i++) {
            if ((events[i]["id"] as String).equals(eventId)) {
                events[i]["playerNumber"] = number;
            }
        }
    }

    // Cards are allowed during either half and at half time.
    function addCard(team as String, playerNumber as Number, cardType as String, nowMs as Long) as Void {
        if (!isPlaying() && !phase.equals(PHASE_HALF_TIME)) {
            return;
        }
        var event = newEvent("CARD", nowMs);
        event["team"] = team;
        event["playerNumber"] = playerNumber;
        event["cardType"] = cardType;
        events.add(event);
    }

    // Removes the most recent goal or card (phase changes are never undone) and returns it.
    function undoLast() as Dictionary or Null {
        for (var i = events.size() - 1; i >= 0; i--) {
            var event = events[i];
            var type = event["eventType"] as String;
            if (type.equals("GOAL") || type.equals("CARD")) {
                events.remove(event);
                if (type.equals("GOAL")) {
                    if ((event["team"] as String).equals(TEAM_HOME)) {
                        homeScore -= 1;
                    } else {
                        awayScore -= 1;
                    }
                }
                return event;
            }
        }
        return null;
    }

    function toDict() as Dictionary {
        return {
            "id" => gameId,
            "homeName" => homeName,
            "awayName" => awayName,
            "homeColor" => homeColor,
            "awayColor" => awayColor,
            "halfMinutes" => halfMinutes,
            "halftimeMinutes" => halftimeMinutes,
            "kickOffTeam" => kickOffTeam,
            "scheduledStartMs" => scheduledStartMs,
            "phase" => phase,
            "homeScore" => homeScore,
            "awayScore" => awayScore,
            "events" => events,
            "startedAtMs" => startedAtMs,
            "periodStartMs" => periodStartMs,
            "pausedTotalMs" => pausedTotalMs,
            "pausedAtMs" => pausedAtMs,
            "regulationAlerted" => regulationAlerted
        };
    }

    static function fromDict(d as Dictionary) as MatchState {
        var m = new MatchState(d);
        m.phase = d["phase"] as String;
        m.homeScore = d["homeScore"] as Number;
        m.awayScore = d["awayScore"] as Number;
        m.events = d["events"] as Array<Dictionary>;
        m.startedAtMs = d["startedAtMs"] as Long or Null;
        m.periodStartMs = d["periodStartMs"] as Long or Null;
        m.pausedTotalMs = d["pausedTotalMs"] as Long;
        m.pausedAtMs = d["pausedAtMs"] as Long or Null;
        m.regulationAlerted = d["regulationAlerted"] as Boolean;
        return m;
    }

    hidden function newEvent(type as String, nowMs as Long) as Dictionary {
        return {
            "eventType" => type,
            "id" => Ids.newId(),
            "timestamp" => nowMs.toDouble(),
            "gameTimeMillis" => elapsedMs(nowMs).toDouble()
        };
    }
```

`garmin/source/model/MatchStore.mc`:
```monkeyc
import Toybox.Application;
import Toybox.Lang;

// Persists the match in progress after every change, so a crash or reboot resumes it, and keeps
// the last few finished matches (they become the upload queue in the sync phase).
module MatchStore {
    const CURRENT_KEY = "match";
    const FINISHED_KEY = "finished";
    const MAX_FINISHED = 5;

    function load() as MatchState or Null {
        var d = Application.Storage.getValue(CURRENT_KEY);
        if (d == null) {
            return null;
        }
        return MatchState.fromDict(d as Dictionary);
    }

    function save(m as MatchState) as Void {
        Application.Storage.setValue(CURRENT_KEY, m.toDict());
    }

    function clear() as Void {
        Application.Storage.deleteValue(CURRENT_KEY);
    }

    function finished() as Array<Dictionary> {
        var list = Application.Storage.getValue(FINISHED_KEY);
        return list == null ? ([] as Array<Dictionary>) : (list as Array<Dictionary>);
    }

    // Moves the match to the finished list (oldest dropped past MAX_FINISHED) and clears it.
    function archive(m as MatchState) as Void {
        var list = finished();
        list.add(m.toDict());
        while (list.size() > MAX_FINISHED) {
            list = list.slice(1, null);
        }
        Application.Storage.setValue(FINISHED_KEY, list);
        clear();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\garmin\build.ps1 -Test`
Expected: `PASSED`, 19 tests. If `Application.Storage.setValue` rejects the dictionary (e.g. a type it cannot store), the error names the value — fix the type in `toDict`, not the test.

- [ ] **Step 5: Commit**

```powershell
git add garmin
git commit -m "Add goals, cards, undo and match persistence to the Garmin app`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Match screen and quick match

**Files:**
- Create: `garmin/source/util/Format.mc`, `garmin/source/util/Alerts.mc`, `garmin/source/ui/Ask.mc`, `garmin/source/ui/GameList.mc`, `garmin/source/ui/MatchView.mc`, `garmin/source/ui/MatchDelegate.mc`, `garmin/source/model/TeamColors.mc`
- Modify: `garmin/source/RefWatchApp.mc` (replace whole file), `garmin/resources/strings/strings.xml` (replace whole file)

**Interfaces:**
- Consumes: `MatchState`, `MatchStore`, `Clock`, `Ids`, phase/team constants.
- Produces:
  - `Format.clock(ms as Long) as String` ("45:00"), `Format.timeOfDay() as String`
  - `Alerts.periodEnd() as Void`
  - `Ask.push(messageId as ResourceId, onYes as Method() as Void) as Void`
  - `GameList.show() as Void` (makes the start menu the only view), `GameList.quickMatchSetup() as Dictionary`
  - `TeamColors.VALUES as Array<Number>`, `TeamColors.nameId(color as Number) as ResourceId`
  - `MatchView.initialize(match as MatchState)`; `MatchDelegate.initialize(match as MatchState)`; `Nav.showMatch(match as MatchState) as Void` (in MatchView.mc)

- [ ] **Step 1: Strings**

Replace `garmin/resources/strings/strings.xml`:
```xml
<strings>
    <string id="AppName">RefWatch</string>
    <string id="QuickMatch">Quick match</string>
    <string id="Home">Home</string>
    <string id="Away">Away</string>
    <string id="FirstHalf">1st half</string>
    <string id="HalfTime">Half time</string>
    <string id="SecondHalf">2nd half</string>
    <string id="FullTime">Full time</string>
    <string id="Paused">PAUSED</string>
    <string id="StartSecondHalfHint">START: 2nd half</string>
    <string id="SaveHint">START: save</string>
    <string id="DiscardHint">BACK: discard</string>
    <string id="LeavePrompt">Leave match?</string>
    <string id="DiscardPrompt">Discard match?</string>
    <string id="Red">Red</string>
    <string id="Blue">Blue</string>
    <string id="White">White</string>
    <string id="Black">Black</string>
    <string id="Yellow">Yellow</string>
    <string id="Green">Green</string>
    <string id="Orange">Orange</string>
    <string id="Purple">Purple</string>
    <string id="SkyBlue">Sky blue</string>
    <string id="Grey">Grey</string>
    <string id="Custom">Custom</string>
</strings>
```

- [ ] **Step 2: Utilities**

`garmin/source/util/Format.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.System;

module Format {
    // "45:00"; minutes are not capped at 59.
    function clock(ms as Long) as String {
        var totalSeconds = (ms / 1000l).toNumber();
        return (totalSeconds / 60).format("%d") + ":" + (totalSeconds % 60).format("%02d");
    }

    function timeOfDay() as String {
        var t = System.getClockTime();
        var hour = t.hour;
        if (!System.getDeviceSettings().is24Hour) {
            hour = hour % 12;
            if (hour == 0) {
                hour = 12;
            }
        }
        return hour.format("%d") + ":" + t.min.format("%02d");
    }
}
```

`garmin/source/util/Alerts.mc`:
```monkeyc
import Toybox.Attention;
import Toybox.Lang;

module Alerts {
    // Three strong pulses at the end of a half or the break.
    function periodEnd() as Void {
        if (Attention has :vibrate) {
            Attention.vibrate([
                new Attention.VibeProfile(100, 500),
                new Attention.VibeProfile(0, 250),
                new Attention.VibeProfile(100, 500),
                new Attention.VibeProfile(0, 250),
                new Attention.VibeProfile(100, 500)
            ]);
        }
    }
}
```

`garmin/source/model/TeamColors.mc`:
```monkeyc
import Toybox.Lang;

module TeamColors {
    const VALUES = [0xFF0000, 0x0055FF, 0xFFFFFF, 0x000000, 0xFFFF00,
                    0x00AA00, 0xFF8800, 0x8800CC, 0x66CCFF, 0x888888] as Array<Number>;

    function nameId(color as Number) as ResourceId {
        var names = [Rez.Strings.Red, Rez.Strings.Blue, Rez.Strings.White, Rez.Strings.Black,
                     Rez.Strings.Yellow, Rez.Strings.Green, Rez.Strings.Orange, Rez.Strings.Purple,
                     Rez.Strings.SkyBlue, Rez.Strings.Grey] as Array<ResourceId>;
        var index = VALUES.indexOf(color);
        return index < 0 ? Rez.Strings.Custom : names[index];
    }
}
```

`garmin/source/ui/Ask.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.WatchUi;

module Ask {
    // Shows a yes/no question; onYes runs only on yes. The dialog closes itself.
    function push(messageId as ResourceId, onYes as Method() as Void) as Void {
        WatchUi.pushView(new WatchUi.Confirmation(WatchUi.loadResource(messageId) as String),
            new ConfirmDelegate(onYes), WatchUi.SLIDE_IMMEDIATE);
    }
}

class ConfirmDelegate extends WatchUi.ConfirmationDelegate {
    hidden var _onYes as Method() as Void;

    function initialize(onYes as Method() as Void) {
        ConfirmationDelegate.initialize();
        _onYes = onYes;
    }

    function onResponse(response as WatchUi.Confirm) as Boolean {
        if (response == WatchUi.CONFIRM_YES) {
            _onYes.invoke();
        }
        return true;
    }
}
```

- [ ] **Step 3: Game list (quick match starts straight away for now; Task 5 inserts the set-up menu)**

`garmin/source/ui/GameList.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.WatchUi;

// The start menu. Synced games are added here in the sync phase.
module GameList {
    function build() as WatchUi.Menu2 {
        var menu = new WatchUi.Menu2({:title => Rez.Strings.AppName});
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.QuickMatch, null, :quickMatch, null));
        return menu;
    }

    // Makes the start menu the only view on the stack.
    function show() as Void {
        WatchUi.switchToView(build(), new GameListDelegate(), WatchUi.SLIDE_IMMEDIATE);
    }

    function quickMatchSetup() as Dictionary {
        return {
            "id" => Ids.newId(),
            "homeName" => WatchUi.loadResource(Rez.Strings.Home) as String,
            "awayName" => WatchUi.loadResource(Rez.Strings.Away) as String,
            "homeColor" => TeamColors.VALUES[0],
            "awayColor" => TeamColors.VALUES[1],
            "halfMinutes" => 45,
            "halftimeMinutes" => 15,
            "kickOffTeam" => TEAM_HOME,
            "scheduledStartMs" => null
        };
    }
}

class GameListDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        if (item.getId() == :quickMatch) {
            var match = new MatchState(GameList.quickMatchSetup());
            match.kickOff(Clock.nowMs());
            MatchStore.save(match);
            Nav.showMatch(match);
        }
    }
}
```

- [ ] **Step 4: Match view**

`garmin/source/ui/MatchView.mc`:
```monkeyc
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

module Nav {
    // The match screen always replaces whatever is showing, so it is the only view on the stack.
    function showMatch(match as MatchState) as Void {
        WatchUi.switchToView(new MatchView(match), new MatchDelegate(match), WatchUi.SLIDE_LEFT);
    }
}

// One screen for the whole match; what it draws depends on the phase. Layout positions are
// fractions of the screen so the same code fits 240 px and 416 px round screens.
class MatchView extends WatchUi.View {
    hidden var _match as MatchState;
    hidden var _timer as Timer.Timer or Null;

    function initialize(match as MatchState) {
        View.initialize();
        _match = match;
    }

    function onShow() as Void {
        if (_match.phase.equals(PHASE_ABANDONED)) {
            GameList.show();
            return;
        }
        var timer = new Timer.Timer();
        timer.start(method(:onTick), 1000, true);
        _timer = timer;
    }

    function onHide() as Void {
        if (_timer != null) {
            (_timer as Timer.Timer).stop();
            _timer = null;
        }
    }

    function onTick() as Void {
        if (_match.takeRegulationAlert(Clock.nowMs())) {
            Alerts.periodEnd();
            MatchStore.save(_match);
        }
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var now = Clock.nowMs();
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            drawFullTime(dc, w, h);
            return;
        }

        var halfTime = _match.phase.equals(PHASE_HALF_TIME);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.15, Graphics.FONT_TINY, phaseLabel(),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        var clockMs = halfTime ? _match.breakRemainingMs(now) : _match.elapsedMs(now);
        dc.setColor(_match.isPaused() ? Graphics.COLOR_YELLOW : Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.38, Graphics.FONT_NUMBER_HOT, Format.clock(clockMs),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        var sub = null;
        var subColor = Graphics.COLOR_LT_GRAY;
        if (_match.isPaused()) {
            sub = WatchUi.loadResource(Rez.Strings.Paused) as String;
            subColor = Graphics.COLOR_YELLOW;
        } else if (_match.addedMs(now) > 0) {
            sub = "+" + Format.clock(_match.addedMs(now));
            subColor = Graphics.COLOR_ORANGE;
        } else if (halfTime) {
            sub = WatchUi.loadResource(Rez.Strings.StartSecondHalfHint) as String;
        }
        if (sub != null) {
            dc.setColor(subColor, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, h * 0.58, Graphics.FONT_SMALL, sub,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }

        drawScore(dc, w, h * 0.73);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.88, Graphics.FONT_XTINY, Format.timeOfDay(),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    hidden function phaseLabel() as String {
        var id = Rez.Strings.FirstHalf;
        if (_match.phase.equals(PHASE_HALF_TIME)) {
            id = Rez.Strings.HalfTime;
        } else if (_match.phase.equals(PHASE_SECOND_HALF)) {
            id = Rez.Strings.SecondHalf;
        } else if (_match.phase.equals(PHASE_GAME_ENDED)) {
            id = Rez.Strings.FullTime;
        }
        return WatchUi.loadResource(id) as String;
    }

    // "[bar] 2 - 1 [bar]" centred on y; each bar is that team's color.
    hidden function drawScore(dc as Graphics.Dc, w as Number, y as Numeric) as Void {
        var cx = w / 2;
        var font = Graphics.FONT_NUMBER_MILD;
        var home = _match.homeScore.format("%d");
        var away = _match.awayScore.format("%d");
        var gap = w * 0.04;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx - gap, y, font, home, Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(cx, y, Graphics.FONT_SMALL, "-", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(cx + gap, y, font, away, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);

        var barW = w * 0.13;
        var barH = dc.getFontHeight(font) * 0.4;
        var homeEdge = cx - gap - dc.getTextWidthInPixels(home, font) - gap;
        var awayEdge = cx + gap + dc.getTextWidthInPixels(away, font) + gap;
        drawTeamBar(dc, homeEdge - barW, y - barH / 2, barW, barH, _match.homeColor);
        drawTeamBar(dc, awayEdge, y - barH / 2, barW, barH, _match.awayColor);
    }

    // A grey outline keeps a black kit visible on the black background.
    hidden function drawTeamBar(dc as Graphics.Dc, x as Numeric, y as Numeric, bw as Numeric, bh as Numeric, color as Number) as Void {
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(x, y, bw, bh, 3);
        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawRoundedRectangle(x, y, bw, bh, 3);
    }

    hidden function drawFullTime(dc as Graphics.Dc, w as Number, h as Number) as Void {
        var cx = w / 2;
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.18, Graphics.FONT_SMALL, phaseLabel(),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        drawScore(dc, w, h * 0.42);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.68, Graphics.FONT_XTINY, WatchUi.loadResource(Rez.Strings.SaveHint) as String,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(cx, h * 0.80, Graphics.FONT_XTINY, WatchUi.loadResource(Rez.Strings.DiscardHint) as String,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}
```

`garmin/source/ui/MatchDelegate.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// START pauses/resumes, starts the 2nd half at half time, and saves at full time.
// BACK asks before leaving (the match keeps running and resumes on the next launch) or, at full
// time, before discarding.
class MatchDelegate extends WatchUi.BehaviorDelegate {
    hidden var _match as MatchState;

    function initialize(match as MatchState) {
        BehaviorDelegate.initialize();
        _match = match;
    }

    function onSelect() as Boolean {
        var now = Clock.nowMs();
        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            MatchStore.archive(_match);
            GameList.show();
            return true;
        }
        if (_match.phase.equals(PHASE_HALF_TIME)) {
            _match.kickOff(now);
        } else {
            _match.togglePause(now);
        }
        MatchStore.save(_match);
        WatchUi.requestUpdate();
        return true;
    }

    function onBack() as Boolean {
        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            Ask.push(Rez.Strings.DiscardPrompt, method(:discard));
        } else {
            Ask.push(Rez.Strings.LeavePrompt, method(:leave));
        }
        return true;
    }

    function leave() as Void {
        System.exit();
    }

    // MatchView.onShow sees PHASE_ABANDONED once the confirmation closes and shows the start menu.
    function discard() as Void {
        MatchStore.clear();
        _match.phase = PHASE_ABANDONED;
    }
}
```

- [ ] **Step 5: App entry**

Replace `garmin/source/RefWatchApp.mc`:
```monkeyc
import Toybox.Application;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.WatchUi;

class RefWatchApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary or Null) as Void {
        Clock.init();
        Math.srand(System.getTimer());
    }

    // A match left running (or unsaved at full time) reopens straight onto the match screen.
    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        var match = MatchStore.load();
        if (match != null && !match.phase.equals(PHASE_PRE_GAME)) {
            return [new MatchView(match), new MatchDelegate(match)];
        }
        return [GameList.build(), new GameListDelegate()];
    }
}
```

- [ ] **Step 6: Build, test and look at it**

```powershell
.\garmin\build.ps1 -Test
.\garmin\build.ps1 -Run
```

Expected: unit tests still `PASSED`. In the simulator (fēnix 5X): the start menu shows "Quick match"; selecting it shows "1st half", a running clock, "0 - 0" with red and blue bars, and the time of day. Then check, using the simulator's on-screen buttons:
1. START → clock turns yellow, "PAUSED"; START again resumes.
2. Simulator **Settings → Time** fast-forward is not needed: for added time, temporarily change `halfMinutes` in `quickMatchSetup` to 1, rebuild, confirm the vibration (simulator logs it) and `+0:05`-style added time; **revert to 45 before committing**.
3. BACK → "Leave match?" → yes exits; relaunch (`.\garmin\build.ps1 -Run`) → the match resumes with the clock still correct.

Take a screenshot of each state with the simulator's **File → Save Screenshot** (or ask the user to) and view the PNGs: no text may be clipped by the round edge. Repeat the run on `-Device fr265` (416 px). If `switchToView` from `MatchView.onShow` (the discard path) does not take effect, move the `GameList.show()` call into a one-shot 50 ms `Timer` started from `onShow`, and note why in a comment.

- [ ] **Step 7: Commit**

```powershell
git add garmin
git commit -m "Add the Garmin match screen with pause, half time and resume`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Pre-match set-up

Superseded: the WatchUi.Picker code below was replaced by our own picker in commit 30bc27d (fēnix 5X legibility).

**Files:**
- Create: `garmin/source/ui/Pickers.mc`, `garmin/source/ui/PreMatch.mc`
- Modify: `garmin/source/ui/GameList.mc` (`GameListDelegate.onSelect`), `garmin/resources/strings/strings.xml` (add strings)

**Interfaces:**
- Consumes: `GameList.quickMatchSetup()`, `TeamColors`, `Nav.showMatch`, `MatchState`, `MatchStore`.
- Produces:
  - `Pickers.pushNumber(titleId as ResourceId, min as Number, max as Number, current as Number, onPicked as Method(n as Number) as Void) as Void`
  - `Pickers.switchToPlayerNumber(titleId as ResourceId, onPicked as Method(n as Number) as Void) as Void` (two columns, 0–99; replaces the current view)
  - `PreMatch.push(setup as Dictionary) as Void`

- [ ] **Step 1: Strings**

Add inside `<strings>` in `garmin/resources/strings/strings.xml`:
```xml
    <string id="Setup">Set-up</string>
    <string id="KickOff">Kick off</string>
    <string id="HomeColor">Home color</string>
    <string id="AwayColor">Away color</string>
    <string id="HalfLength">Half length</string>
    <string id="BreakLength">Half-time break</string>
    <string id="KickOffTeam">Kick-off team</string>
    <string id="MinutesFormat">$1$ min</string>
```

- [ ] **Step 2: Pickers**

`garmin/source/ui/Pickers.mc`:
```monkeyc
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

module Pickers {
    // Single column min..max, starting on current. Pushed over the current view.
    function pushNumber(titleId as ResourceId, min as Number, max as Number, current as Number,
                        onPicked as Method(n as Number) as Void) as Void {
        var picker = new WatchUi.Picker({
            :title => title(titleId),
            :pattern => [new NumberFactory(min, max)],
            :defaults => [current - min]
        });
        WatchUi.pushView(picker, new NumberPickerDelegate(onPicked), WatchUi.SLIDE_IMMEDIATE);
    }

    // Tens and ones columns (0–99): two short scrolls instead of up to 99 button presses.
    // Replaces the current view, so accepting returns to the view underneath it.
    function switchToPlayerNumber(titleId as ResourceId, onPicked as Method(n as Number) as Void) as Void {
        var picker = new WatchUi.Picker({
            :title => title(titleId),
            :pattern => [new NumberFactory(0, 9), new NumberFactory(0, 9)],
            :defaults => [0, 0]
        });
        WatchUi.switchToView(picker, new NumberPickerDelegate(onPicked), WatchUi.SLIDE_IMMEDIATE);
    }

    function title(titleId as ResourceId) as WatchUi.Text {
        return new WatchUi.Text({
            :text => WatchUi.loadResource(titleId) as String,
            :color => Graphics.COLOR_WHITE,
            :font => Graphics.FONT_TINY,
            :locX => WatchUi.LAYOUT_HALIGN_CENTER,
            :locY => WatchUi.LAYOUT_VALIGN_BOTTOM
        });
    }
}

class NumberFactory extends WatchUi.PickerFactory {
    hidden var _min as Number;
    hidden var _max as Number;

    function initialize(min as Number, max as Number) {
        PickerFactory.initialize();
        _min = min;
        _max = max;
    }

    function getSize() as Number {
        return _max - _min + 1;
    }

    function getValue(index as Number) as Object or Null {
        return _min + index;
    }

    function getDrawable(index as Number, selected as Boolean) as WatchUi.Drawable or Null {
        return new WatchUi.Text({
            :text => (_min + index).format("%d"),
            :color => selected ? Graphics.COLOR_WHITE : Graphics.COLOR_LT_GRAY,
            :font => Graphics.FONT_NUMBER_MEDIUM,
            :locX => WatchUi.LAYOUT_HALIGN_CENTER,
            :locY => WatchUi.LAYOUT_VALIGN_CENTER
        });
    }
}

// Two columns are read as tens and ones. The picker closes before onPicked runs, so the
// callback can push or switch views.
class NumberPickerDelegate extends WatchUi.PickerDelegate {
    hidden var _onPicked as Method(n as Number) as Void;

    function initialize(onPicked as Method(n as Number) as Void) {
        PickerDelegate.initialize();
        _onPicked = onPicked;
    }

    function onAccept(values as Array) as Boolean {
        var n = values[0] as Number;
        if (values.size() == 2) {
            n = n * 10 + (values[1] as Number);
        }
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
        _onPicked.invoke(n);
        return true;
    }

    function onCancel() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
        return true;
    }
}
```

- [ ] **Step 3: Pre-match menu**

`garmin/source/ui/PreMatch.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.WatchUi;

// Set-up before kick-off. Each line shows its current value; "Kick off" starts the match.
module PreMatch {
    function push(setup as Dictionary) as Void {
        var menu = new WatchUi.Menu2({:title => Rez.Strings.Setup});
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.KickOff, null, :kickOff, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.HomeColor, null, :homeColor, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.AwayColor, null, :awayColor, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.HalfLength, null, :halfMinutes, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.BreakLength, null, :halftimeMinutes, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.KickOffTeam, null, :kickOffTeam, null));
        var delegate = new PreMatchDelegate(menu, setup);
        delegate.refresh();
        WatchUi.pushView(menu, delegate, WatchUi.SLIDE_LEFT);
    }
}

class PreMatchDelegate extends WatchUi.Menu2InputDelegate {
    hidden var _menu as WatchUi.Menu2;
    hidden var _setup as Dictionary;
    hidden var _colorKey as String = "homeColor";

    function initialize(menu as WatchUi.Menu2, setup as Dictionary) {
        Menu2InputDelegate.initialize();
        _menu = menu;
        _setup = setup;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id == :kickOff) {
            var match = new MatchState(_setup);
            match.kickOff(Clock.nowMs());
            MatchStore.save(match);
            // Close set-up first so the match replaces the start menu and is the only view.
            WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
            Nav.showMatch(match);
        } else if (id == :homeColor || id == :awayColor) {
            _colorKey = id == :homeColor ? "homeColor" : "awayColor";
            pushColorMenu();
        } else if (id == :halfMinutes) {
            Pickers.pushNumber(Rez.Strings.HalfLength, 5, 60, _setup["halfMinutes"] as Number, method(:onHalfMinutes));
        } else if (id == :halftimeMinutes) {
            Pickers.pushNumber(Rez.Strings.BreakLength, 1, 30, _setup["halftimeMinutes"] as Number, method(:onHalftimeMinutes));
        } else if (id == :kickOffTeam) {
            _setup["kickOffTeam"] = (_setup["kickOffTeam"] as String).equals(TEAM_HOME) ? TEAM_AWAY : TEAM_HOME;
            refresh();
        }
    }

    function onHalfMinutes(n as Number) as Void {
        _setup["halfMinutes"] = n;
        refresh();
    }

    function onHalftimeMinutes(n as Number) as Void {
        _setup["halftimeMinutes"] = n;
        refresh();
    }

    function onColor(color as Number) as Void {
        _setup[_colorKey] = color;
        refresh();
    }

    function refresh() as Void {
        setSub(:homeColor, WatchUi.loadResource(TeamColors.nameId(_setup["homeColor"] as Number)) as String);
        setSub(:awayColor, WatchUi.loadResource(TeamColors.nameId(_setup["awayColor"] as Number)) as String);
        setSub(:halfMinutes, minutes(_setup["halfMinutes"] as Number));
        setSub(:halftimeMinutes, minutes(_setup["halftimeMinutes"] as Number));
        var homeKicks = (_setup["kickOffTeam"] as String).equals(TEAM_HOME);
        setSub(:kickOffTeam, (homeKicks ? _setup["homeName"] : _setup["awayName"]) as String);
        WatchUi.requestUpdate();
    }

    hidden function setSub(id as Symbol, text as String) as Void {
        var index = _menu.findItemById(id);
        if (index >= 0) {
            (_menu.getItem(index) as WatchUi.MenuItem).setSubLabel(text);
        }
    }

    hidden function minutes(n as Number) as String {
        return Lang.format(WatchUi.loadResource(Rez.Strings.MinutesFormat) as String, [n]);
    }

    hidden function pushColorMenu() as Void {
        var title = _colorKey.equals("homeColor") ? Rez.Strings.HomeColor : Rez.Strings.AwayColor;
        var menu = new WatchUi.Menu2({:title => title});
        for (var i = 0; i < TeamColors.VALUES.size(); i++) {
            menu.addItem(new WatchUi.MenuItem(TeamColors.nameId(TeamColors.VALUES[i]), null, i, null));
        }
        WatchUi.pushView(menu, new ColorMenuDelegate(method(:onColor)), WatchUi.SLIDE_LEFT);
    }
}

class ColorMenuDelegate extends WatchUi.Menu2InputDelegate {
    hidden var _onColor as Method(color as Number) as Void;

    function initialize(onColor as Method(color as Number) as Void) {
        Menu2InputDelegate.initialize();
        _onColor = onColor;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        _onColor.invoke(TeamColors.VALUES[item.getId() as Number]);
    }
}
```

- [ ] **Step 4: Route quick match through set-up**

In `garmin/source/ui/GameList.mc`, replace the body of `GameListDelegate.onSelect` with:
```monkeyc
        if (item.getId() == :quickMatch) {
            PreMatch.push(GameList.quickMatchSetup());
        }
```

- [ ] **Step 5: Build, test and look at it**

```powershell
.\garmin\build.ps1 -Test
.\garmin\build.ps1 -Run
```

Expected: tests `PASSED`. In the simulator: Quick match → Set-up lists Kick off, Home color (Red), Away color (Blue), Half length (45 min), Half-time break (15 min), Kick-off team (Home). Change each value; the sub-label updates. Kick off → match screen with the chosen colors, and half time after the chosen length. Screenshot the set-up menu, the color list and the picker on `fenix5x` and `fr265`; check nothing is clipped. Then test with a 1-minute half to confirm half-time break, the "START: 2nd half" hint, the kick-off flip, and full time.

- [ ] **Step 6: Commit**

```powershell
git add garmin
git commit -m "Add pre-match set-up to the Garmin app`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Team actions, match menu and game log

**Files:**
- Create: `garmin/source/util/Settings.mc`, `garmin/source/model/GameLog.mc`, `garmin/source/ui/TeamActions.mc`, `garmin/source/ui/MatchMenu.mc`, `garmin/source/ui/GameLogView.mc`, `garmin/resources/settings/properties.xml`, `garmin/test/GameLogTest.mc`
- Modify: `garmin/source/ui/MatchDelegate.mc` (add methods), `garmin/resources/strings/strings.xml` (add strings)

**Interfaces:**
- Consumes: `MatchState` (`addGoal`, `setPlayerNumber`, `addCard`, `undoLast`, `endPeriod`, `kickOff`, `isPastRegulation`), `MatchStore`, `Pickers.switchToPlayerNumber`, `Ask.push`.
- Produces:
  - `Settings.logGoalScorer() as Boolean`
  - `GameLog.minuteLabel(periodIndex as Number, gameTimeMs as Long, halfMinutes as Number) as String`
  - `GameLog.rows(match as MatchState, labels as Dictionary) as Array<Array<String>>` — each row `[label, subLabel]`; `labels` maps `"GOAL"`, `"YELLOW"`, `"RED"`, and each phase name to display text
  - `TeamActions.push(match as MatchState, team as String) as Void`, `MatchMenu.push(match as MatchState) as Void`, `GameLogView.push(match as MatchState) as Void`

- [ ] **Step 1: Write the failing tests**

`garmin/test/GameLogTest.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.Test;

(:test)
function minuteLabelsCountFromOne(logger as Logger) as Boolean {
    Test.assertEqual("1'", GameLog.minuteLabel(0, 0l, 30));
    Test.assertEqual("13'", GameLog.minuteLabel(0, 754000l, 30));
    Test.assertEqual("30'", GameLog.minuteLabel(0, 29 * MIN + 59000l, 30));
    return true;
}

(:test)
function minuteLabelsShowAddedTime(logger as Logger) as Boolean {
    Test.assertEqual("30+1'", GameLog.minuteLabel(0, 30 * MIN, 30));
    Test.assertEqual("30+3'", GameLog.minuteLabel(0, 32 * MIN + 12000l, 30));
    return true;
}

(:test)
function secondHalfMinutesContinueFromTheFirst(logger as Logger) as Boolean {
    Test.assertEqual("32'", GameLog.minuteLabel(1, MIN, 30));
    Test.assertEqual("60+1'", GameLog.minuteLabel(1, 30 * MIN + 30000l, 30));
    return true;
}

(:test)
function rowsDescribeEventsInOrder(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addGoal(TEAM_HOME, 9, T0 + 754000l);
    m.endPeriod(T0 + 30 * MIN);
    m.kickOff(T0 + 35 * MIN);
    m.addCard(TEAM_AWAY, 4, CARD_YELLOW, T0 + 36 * MIN);
    var labels = {
        "GOAL" => "Goal", "YELLOW" => "Yellow", "RED" => "Red",
        PHASE_FIRST_HALF => "1st half", PHASE_HALF_TIME => "Half time",
        PHASE_SECOND_HALF => "2nd half", PHASE_GAME_ENDED => "Full time"
    };
    var rows = GameLog.rows(m, labels);
    Test.assertEqual(5, rows.size());
    Test.assertEqual("1st half", rows[0][0]);
    Test.assertEqual("13' Goal", rows[1][0]);
    Test.assertEqual("Eagles #9", rows[1][1]);
    Test.assertEqual("Half time", rows[2][0]);
    Test.assertEqual("2nd half", rows[3][0]);
    Test.assertEqual("32' Yellow", rows[4][0]);
    Test.assertEqual("Hawks #4", rows[4][1]);
    return true;
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\garmin\build.ps1 -Test`
Expected: compile error — `GameLog` not found.

- [ ] **Step 3: Implement the log model**

`garmin/source/model/GameLog.mc`:
```monkeyc
import Toybox.Lang;

module GameLog {
    // Football-style minute: the first minute is 1', added time is "30+2'", and the 2nd half
    // continues from where a full-length 1st half ends.
    function minuteLabel(periodIndex as Number, gameTimeMs as Long, halfMinutes as Number) as String {
        var minute = (gameTimeMs / 60000l).toNumber();
        var offset = periodIndex * halfMinutes;
        if (minute < halfMinutes) {
            return (offset + minute + 1).format("%d") + "'";
        }
        return (offset + halfMinutes).format("%d") + "+" + (minute - halfMinutes + 1).format("%d") + "'";
    }

    // [label, subLabel] per event. Phase changes become plain separator rows.
    function rows(match as MatchState, labels as Dictionary) as Array<Array<String>> {
        var result = [] as Array<Array<String>>;
        var periodIndex = 0;
        for (var i = 0; i < match.events.size(); i++) {
            var e = match.events[i];
            var type = e["eventType"] as String;
            if (type.equals("PHASE_CHANGE")) {
                var phase = e["newPhase"] as String;
                if (phase.equals(PHASE_SECOND_HALF)) {
                    periodIndex = 1;
                }
                result.add([labels[phase] as String, ""]);
                continue;
            }
            var kind = type.equals("CARD") ? e["cardType"] as String : type;
            var minute = minuteLabel(periodIndex, (e["gameTimeMillis"] as Double).toLong(), match.halfMinutes);
            var team = (e["team"] as String).equals(TEAM_HOME) ? match.homeName : match.awayName;
            var player = e["playerNumber"];
            var sub = player == null ? team : team + " #" + (player as Number).format("%d");
            result.add([minute + " " + (labels[kind] as String), sub]);
        }
        return result;
    }
}
```

Run: `.\garmin\build.ps1 -Test`
Expected: `PASSED`, 23 tests.

- [ ] **Step 4: Settings and strings**

`garmin/resources/settings/properties.xml`:
```xml
<resources>
    <properties>
        <property id="logGoalScorer" type="boolean">false</property>
    </properties>
    <settings>
        <setting propertyKey="@Properties.logGoalScorer" title="@Strings.LogGoalScorerTitle">
            <settingConfig type="boolean"/>
        </setting>
    </settings>
</resources>
```

`garmin/source/util/Settings.mc`:
```monkeyc
import Toybox.Application;
import Toybox.Lang;

// App settings, edited in the Garmin Connect phone app (or the simulator's property editor).
module Settings {
    function logGoalScorer() as Boolean {
        return Application.Properties.getValue("logGoalScorer") == true;
    }
}
```

Add inside `<strings>`:
```xml
    <string id="LogGoalScorerTitle">Log goal scorer</string>
    <string id="Goal">Goal</string>
    <string id="YellowCard">Yellow</string>
    <string id="RedCard">Red</string>
    <string id="PlayerNumber">Player #</string>
    <string id="Scorer">Scorer #</string>
    <string id="Menu">Match</string>
    <string id="EndFirstHalf">End 1st half</string>
    <string id="EndMatch">End match</string>
    <string id="StartSecondHalf">Start 2nd half</string>
    <string id="GameLog">Game log</string>
    <string id="Undo">Undo last</string>
    <string id="Abandon">Abandon match</string>
    <string id="EndFirstHalfPrompt">End 1st half?</string>
    <string id="EndMatchPrompt">End match?</string>
    <string id="AbandonPrompt">Abandon match?</string>
    <string id="NoEvents">No events yet</string>
```

- [ ] **Step 5: Team actions**

`garmin/source/ui/TeamActions.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.WatchUi;

// Goal / Yellow / Red for one team. A goal counts immediately; with "Log goal scorer" on, the
// number picker follows and BACK skips it. A card needs its player number; its time is taken
// when the card is chosen, not when the number is confirmed.
module TeamActions {
    function push(match as MatchState, team as String) as Void {
        var title = team.equals(TEAM_HOME) ? match.homeName : match.awayName;
        var menu = new WatchUi.Menu2({:title => title});
        if (match.isPlaying()) {
            menu.addItem(new WatchUi.MenuItem(Rez.Strings.Goal, null, :goal, null));
        }
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.YellowCard, null, :yellow, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.RedCard, null, :red, null));
        WatchUi.pushView(menu, new TeamActionsDelegate(match, team), WatchUi.SLIDE_LEFT);
    }
}

class TeamActionsDelegate extends WatchUi.Menu2InputDelegate {
    hidden var _match as MatchState;
    hidden var _team as String;
    hidden var _goalId as String or Null = null;
    hidden var _cardType as String = CARD_YELLOW;
    hidden var _cardAtMs as Long = 0l;

    function initialize(match as MatchState, team as String) {
        Menu2InputDelegate.initialize();
        _match = match;
        _team = team;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var now = Clock.nowMs();
        var id = item.getId();
        if (id == :goal) {
            _goalId = _match.addGoal(_team, null, now);
            MatchStore.save(_match);
            if (_goalId != null && Settings.logGoalScorer()) {
                Pickers.switchToPlayerNumber(Rez.Strings.Scorer, method(:onScorer));
            } else {
                WatchUi.popView(WatchUi.SLIDE_RIGHT);
            }
        } else {
            _cardType = id == :yellow ? CARD_YELLOW : CARD_RED;
            _cardAtMs = now;
            Pickers.switchToPlayerNumber(Rez.Strings.PlayerNumber, method(:onCardPlayer));
        }
    }

    function onScorer(n as Number) as Void {
        _match.setPlayerNumber(_goalId as String, n);
        MatchStore.save(_match);
    }

    function onCardPlayer(n as Number) as Void {
        _match.addCard(_team, n, _cardType, _cardAtMs);
        MatchStore.save(_match);
    }
}
```

- [ ] **Step 6: Match menu and game log**

`garmin/source/ui/MatchMenu.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.WatchUi;

// Hold UP. Once regulation time has passed, ending the period is the first item.
module MatchMenu {
    function push(match as MatchState) as Void {
        var now = Clock.nowMs();
        var menu = new WatchUi.Menu2({:title => Rez.Strings.Menu});
        var endLabel = match.phase.equals(PHASE_FIRST_HALF) ? Rez.Strings.EndFirstHalf : Rez.Strings.EndMatch;
        var endFirst = match.isPlaying() && match.isPastRegulation(now);
        if (match.phase.equals(PHASE_HALF_TIME)) {
            menu.addItem(new WatchUi.MenuItem(Rez.Strings.StartSecondHalf, null, :startSecondHalf, null));
        } else if (endFirst) {
            menu.addItem(new WatchUi.MenuItem(endLabel, null, :end, null));
        }
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.GameLog, null, :log, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Undo, null, :undo, null));
        if (match.isPlaying() && !endFirst) {
            menu.addItem(new WatchUi.MenuItem(endLabel, null, :end, null));
        }
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Abandon, null, :abandon, null));
        WatchUi.pushView(menu, new MatchMenuDelegate(match), WatchUi.SLIDE_UP);
    }
}

class MatchMenuDelegate extends WatchUi.Menu2InputDelegate {
    hidden var _match as MatchState;

    function initialize(match as MatchState) {
        Menu2InputDelegate.initialize();
        _match = match;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id == :log) {
            WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
            GameLogView.push(_match);
            return;
        }
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        if (id == :end) {
            var prompt = _match.phase.equals(PHASE_FIRST_HALF) ? Rez.Strings.EndFirstHalfPrompt : Rez.Strings.EndMatchPrompt;
            Ask.push(prompt, method(:endPeriod));
        } else if (id == :startSecondHalf) {
            _match.kickOff(Clock.nowMs());
            MatchStore.save(_match);
        } else if (id == :undo) {
            _match.undoLast();
            MatchStore.save(_match);
        } else if (id == :abandon) {
            Ask.push(Rez.Strings.AbandonPrompt, method(:abandon));
        }
    }

    function endPeriod() as Void {
        _match.endPeriod(Clock.nowMs());
        MatchStore.save(_match);
        WatchUi.requestUpdate();
    }

    // MatchView.onShow sees PHASE_ABANDONED and returns to the start menu.
    function abandon() as Void {
        MatchStore.clear();
        _match.phase = PHASE_ABANDONED;
    }
}
```

`garmin/source/ui/GameLogView.mc`:
```monkeyc
import Toybox.Lang;
import Toybox.WatchUi;

// Read-only list of the match's events; BACK returns to the match.
module GameLogView {
    function push(match as MatchState) as Void {
        var labels = {
            "GOAL" => WatchUi.loadResource(Rez.Strings.Goal),
            "YELLOW" => WatchUi.loadResource(Rez.Strings.YellowCard),
            "RED" => WatchUi.loadResource(Rez.Strings.RedCard),
            PHASE_FIRST_HALF => WatchUi.loadResource(Rez.Strings.FirstHalf),
            PHASE_HALF_TIME => WatchUi.loadResource(Rez.Strings.HalfTime),
            PHASE_SECOND_HALF => WatchUi.loadResource(Rez.Strings.SecondHalf),
            PHASE_GAME_ENDED => WatchUi.loadResource(Rez.Strings.FullTime)
        };
        var menu = new WatchUi.Menu2({:title => Rez.Strings.GameLog});
        var rows = GameLog.rows(match, labels);
        if (rows.size() == 0) {
            menu.addItem(new WatchUi.MenuItem(Rez.Strings.NoEvents, null, 0, null));
        }
        for (var i = 0; i < rows.size(); i++) {
            var sub = rows[i][1].equals("") ? null : rows[i][1];
            menu.addItem(new WatchUi.MenuItem(rows[i][0], sub, i, null));
        }
        WatchUi.pushView(menu, new GameLogDelegate(), WatchUi.SLIDE_LEFT);
    }
}

class GameLogDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
    }
}
```

- [ ] **Step 7: Wire the buttons**

Add to `MatchDelegate` in `garmin/source/ui/MatchDelegate.mc`, after `onSelect`:
```monkeyc
    function onPreviousPage() as Boolean {
        return openTeam(TEAM_HOME);
    }

    function onNextPage() as Boolean {
        return openTeam(TEAM_AWAY);
    }

    // Touch watches: tap the left half for Home, the right half for Away.
    function onTap(event as WatchUi.ClickEvent) as Boolean {
        var x = event.getCoordinates()[0];
        return openTeam(x < System.getDeviceSettings().screenWidth / 2 ? TEAM_HOME : TEAM_AWAY);
    }

    function onMenu() as Boolean {
        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            GameLogView.push(_match);
        } else {
            MatchMenu.push(_match);
        }
        return true;
    }

    hidden function openTeam(team as String) as Boolean {
        if (_match.isPlaying() || _match.phase.equals(PHASE_HALF_TIME)) {
            TeamActions.push(_match, team);
        }
        return true;
    }
```

- [ ] **Step 8: Build, test and look at it**

```powershell
.\garmin\build.ps1 -Test
.\garmin\build.ps1 -Run
```

Expected: tests `PASSED`. In the simulator, a full match with 1-minute halves (temporarily, reverted before commit):
1. UP → Home menu → Goal → score 1-0. DOWN → Away → Yellow → picker → 0,4 → back on match screen.
2. Turn on "Log goal scorer" (**File → Edit Persistent Storage → Edit Application.Properties**), score a goal → scorer picker; BACK skips, goal still counted.
3. Hold UP (simulator: hold the UP button or press the Menu key) → menu; after 1:00 "End 1st half" is first. Undo removes the yellow card. End 1st half → confirm → half-time screen. Hold UP → "Start 2nd half".
4. End match → full time → hold UP shows the log with `1'`/`+` minutes → BACK → START saves → start menu.
5. Abandon from the menu returns to the start menu and relaunching does not resume.

Screenshot each menu, the pickers and the log on `fenix5x` and `fr265`; nothing may be clipped (team names in menu titles truncate cleanly).

- [ ] **Step 9: Commit**

```powershell
git add garmin
git commit -m "Add goals, cards, match menu and game log screens to the Garmin app`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Device matrix, memory and the real fēnix 5X

**Files:**
- Modify: `garmin/README.md` (memory results, device checklist)

- [ ] **Step 1: Build every product**

```powershell
foreach ($d in "fenix5x", "fenix7s", "fenix7", "epix2pro47mm", "fr265") { .\garmin\build.ps1 -Device $d }
```

Expected: five `.prg` files in `garmin\bin`, no errors.

- [ ] **Step 2: Memory on the fēnix 5X**

Run `.\garmin\build.ps1 -Run`, play a match with ~10 events, open the game log, then **File → View Memory** in the simulator. Record peak memory. Expected: under 60% of the limit in the README. If over, shrink the largest objects shown before going further.

- [ ] **Step 3: Visual pass on each product**

For each device: `.\garmin\build.ps1 -Device <id> -Run`, step through start menu → set-up → match (running, paused, added time) → team menu → picker → match menu → half time → full time → log, and save a screenshot of each. Look at every PNG: nothing clipped by the bezel, the score bars clear of the edge, the clock legible. Fix layout fractions in `MatchView.mc` if needed and re-run the unit tests.

- [ ] **Step 4: Sideload to the fēnix 5X (with the user)**

Ask the user to connect the friend's fēnix 5X by USB. Then:
```powershell
.\garmin\build.ps1 -Device fenix5x
Get-Volume | Where-Object FileSystemLabel -match "GARMIN" | Select-Object DriveLetter, FileSystemLabel
Copy-Item .\garmin\bin\RefWatch-fenix5x.prg "<DriveLetter>:\GARMIN\APPS\RefWatch.prg"
```
If no `GARMIN` volume appears, the watch is in MTP mode: the user copies the file in Explorer to `GARMIN\APPS`.

Checklist on the watch (user reports results):
1. App launches from the app list; start menu → set-up → kick off.
2. All five buttons do what the plan says; hold UP opens the menu.
3. Vibration is felt at the end of a half and of the break.
4. Leave the app open **10 minutes without touching it** — it must stay open and the clock correct.
5. Exit mid-match and relaunch — the match resumes with the right time.
6. Text is readable in sunlight and nothing is clipped.

- [ ] **Step 5: Record and commit**

Add to `garmin/README.md` a "Phase 1 results" section: memory peak vs. limit on the fēnix 5X, devices checked, the on-watch checklist results, and any issues found.

```powershell
git add garmin
git commit -m "Record Garmin phase 1 device and memory results`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Next plans (written after phase 1 is on the watch)

- Phase 2: activity recording (`recording/Recorder.mc`, `recordActivity` setting, pre-match override, `Fit`/`Positioning` permissions).
- Phase 3: pairing (Firebase functions `createGarminPairingCode`, `garminPair`, `listGarminDevices`, `unlinkGarminDevice`; phone Settings section; watch `sync/Pairing.mc`).
- Phase 4: game sync (`garminGames`, `garminUploadGame`, `sync/GameCache.mc`, `sync/UploadQueue.mc`, phone fixture cross-check).
- Phase 5: release (Connect IQ store listing, phone release, privacy policy).
