# Garmin Phase 2 — Activity Recording Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Each match refereed in the Garmin app is saved as a Garmin activity (heart rate, GPS distance, one lap per half and one for the break), controlled by a "Record activity" setting (default on) that the set-up screen can override for one match.

**Architecture:** A new `Recorder` module owns the one `ActivityRecording.Session` and GPS. It exposes low-level calls (`start`, `lap`, `stop`, `save`, `discard`, `warmUp`, `releaseGps`) and three match-aware hooks (`forKickOff`, `forPeriodEnd`, `forResume`) that the existing delegates call right after they change the match. `MatchState` gains a stored `recordActivity` flag so a match resumed after an app restart knows whether to record. Pausing the match clock does not pause the recording, because the referee keeps moving.

**Tech Stack:** Monkey C, Connect IQ SDK 9.2.0, `minApiLevel` 3.1.0, `Toybox.ActivityRecording`, `Toybox.Position`, `WatchUi.ToggleMenuItem` (API 3.0.0).

**Spec:** `docs/superpowers/specs/2026-10-07-garmin-app-design.md` (build-order step 2, "Activity recording", and the "Activity recording" and "App settings" sections).

## Global Constraints

- Everything in phase 1's Global Constraints still holds (`docs/superpowers/plans/2026-10-07-garmin-phase1-standalone-match.md`).
- `minApiLevel` stays `3.1.0`. The test fēnix 5X runs **Connect IQ 3.1.9**, so any API newer than 3.1 must be guarded with `has`.
- **Sport constant:** `Activity.SPORT_SOCCER` is API 3.2.0 (not on the 5X); `ActivityRecording.SPORT_SOCCER` is API 1.0.0 but deprecated ("may be removed after System 8"). Choose at runtime: `Activity has :SPORT_SOCCER` → `Activity.SPORT_SOCCER`, else `ActivityRecording.SPORT_SOCCER`. Both are FIT sport 7.
- Manifest permissions: `Fit` and `Positioning` only. Wrist heart rate is recorded by the session without the `Sensor` permission (as in the SDK's `RecordSample`).
- A match never depends on recording: every `Recorder` call is a no-op when `Toybox has :ActivityRecording` is false, and recording errors are caught and ignored.
- Setting key `recordActivity`, boolean, default `true`. A missing value counts as `true`.
- Known limitation (from the spec): when the app is closed mid-match, the partial activity is saved, and reopening the app starts a **new** activity for the rest of the match.
- American spelling in all strings and comments ("color").
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Commands are PowerShell, run from the repository root.

## File Structure

```
garmin/
  manifest.xml                         + Fit, Positioning permissions
  resources/strings/strings.xml        + RecordActivity, ActivityName
  resources/settings/properties.xml    + recordActivity property and setting
  source/
    RefWatchApp.mc                     resume recording on launch, save it in onStop
    recording/Recorder.mc              NEW: session, laps, GPS, match hooks
    model/MatchState.mc                + recordActivity field (stored)
    model/MatchStore.mc                isValid accepts a missing or Boolean recordActivity
    util/Settings.mc                   + recordActivity()
    ui/GameList.mc                     quick-match setup takes recordActivity from settings
    ui/PreMatch.mc                     + "Record activity" toggle, GPS warm-up, kick-off hook
    ui/MatchDelegate.mc                hooks: 2nd half, save, discard, leave
    ui/MatchMenu.mc                    hooks: 2nd half, end period, abandon
  test/
    MatchStoreTest.mc                  + recordActivity storage tests
    SettingsTest.mc                    NEW
    RecorderTest.mc                    NEW
  README.md                            + Phase 2 section
```

`monkey.jungle` already uses `base.sourcePath = source;test`, so `source/recording/` is picked up without changes.

---

### Task 1: `recordActivity` on the match and in settings

**Files:**
- Modify: `garmin/source/model/MatchState.mc` (fields, `initialize`, `toDict`)
- Modify: `garmin/source/model/MatchStore.mc` (`isValid`)
- Modify: `garmin/source/util/Settings.mc`
- Modify: `garmin/source/ui/GameList.mc` (`quickMatchSetup`)
- Modify: `garmin/resources/settings/properties.xml`
- Modify: `garmin/resources/strings/strings.xml`
- Test: `garmin/test/MatchStoreTest.mc`, create `garmin/test/SettingsTest.mc`

**Interfaces:**
- Produces: `MatchState.recordActivity as Boolean` (read from setup key `"recordActivity"`, `true` only when the value is `true`); stored under the same key by `toDict`. `Settings.recordActivity() as Boolean`. Setup dictionaries carry `"recordActivity" => Boolean`. String ids `Rez.Strings.RecordActivity`, `Rez.Strings.ActivityName`.

- [ ] **Step 1: Write the failing tests**

Append to `garmin/test/MatchStoreTest.mc`:

```monkeyc
(:test)
function recordActivityIsOffWhenTheSetupLeavesItOut(logger as Logger) as Boolean {
    Test.assertEqual(false, testMatch().recordActivity);
    return true;
}

(:test)
function recordActivitySurvivesSaveAndLoad(logger as Logger) as Boolean {
    var m = testMatch();
    m.recordActivity = true;
    MatchStore.save(m);
    Test.assertEqual(true, (MatchStore.load() as MatchState).recordActivity);
    MatchStore.clear();
    return true;
}

// A match stored by phase 1 has no recordActivity key; it must still resume, without recording.
(:test)
function storedMatchWithoutRecordActivityLoadsWithRecordingOff(logger as Logger) as Boolean {
    var d = testMatch().toDict();
    d.remove("recordActivity");
    Application.Storage.setValue(MatchStore.CURRENT_KEY, d);
    var loaded = MatchStore.load();
    Test.assert(loaded != null);
    Test.assertEqual(false, (loaded as MatchState).recordActivity);
    MatchStore.clear();
    return true;
}

(:test)
function storedMatchWithANonBooleanRecordActivityIsDiscarded(logger as Logger) as Boolean {
    var d = testMatch().toDict();
    d["recordActivity"] = "yes";
    Application.Storage.setValue(MatchStore.CURRENT_KEY, d);
    Test.assert(MatchStore.load() == null);
    return true;
}
```

Create `garmin/test/SettingsTest.mc`:

```monkeyc
import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

(:test)
function recordActivitySettingFollowsTheProperty(logger as Logger) as Boolean {
    var saved = Application.Properties.getValue("recordActivity");
    Application.Properties.setValue("recordActivity", false);
    Test.assertEqual(false, Settings.recordActivity());
    Application.Properties.setValue("recordActivity", true);
    Test.assertEqual(true, Settings.recordActivity());
    Application.Properties.setValue("recordActivity", saved);
    return true;
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

```powershell
.\garmin\build.ps1 -Test
```

Expected: `monkeyc` fails with errors that `recordActivity` is not a member of `MatchState` and `Settings.recordActivity` is undefined.

- [ ] **Step 3: Implement**

`garmin/source/model/MatchState.mc` — add the field after `scheduledStartMs`:

```monkeyc
    var scheduledStartMs as Long or Null;
    var recordActivity as Boolean;       // save this match as a Garmin activity
```

In `initialize`, after `scheduledStartMs = ...`:

```monkeyc
        recordActivity = setup["recordActivity"] == true;
```

In `toDict`, after the `"scheduledStartMs"` entry:

```monkeyc
            "scheduledStartMs" => scheduledStartMs,
            "recordActivity" => recordActivity,
```

`fromDict` needs no change: it builds the match through `initialize`, which reads the key.

`garmin/source/model/MatchStore.mc` — in `isValid`, after the `scheduledStartMs` line (the key is optional, so `SCHEMA_VERSION` stays 1):

```monkeyc
            && (d["scheduledStartMs"] == null || d["scheduledStartMs"] instanceof Long)
            && (d["recordActivity"] == null || d["recordActivity"] instanceof Boolean)
```

`garmin/source/util/Settings.mc` — add:

```monkeyc
    // On unless the referee turned it off; a missing value (older install) counts as on.
    function recordActivity() as Boolean {
        return Application.Properties.getValue("recordActivity") != false;
    }
```

`garmin/source/ui/GameList.mc` — in `quickMatchSetup`, after `"scheduledStartMs" => null`:

```monkeyc
            "scheduledStartMs" => null,
            "recordActivity" => Settings.recordActivity()
```

`garmin/resources/settings/properties.xml` — full file:

```xml
<resources>
    <properties>
        <property id="logGoalScorer" type="boolean">false</property>
        <property id="recordActivity" type="boolean">true</property>
    </properties>
    <settings>
        <setting propertyKey="@Properties.logGoalScorer" title="@Strings.LogGoalScorerTitle">
            <settingConfig type="boolean"/>
        </setting>
        <setting propertyKey="@Properties.recordActivity" title="@Strings.RecordActivity">
            <settingConfig type="boolean"/>
        </setting>
    </settings>
</resources>
```

`garmin/resources/strings/strings.xml` — add before `</strings>`:

```xml
    <string id="RecordActivity">Record activity</string>
    <string id="ActivityName">Referee</string>
```

- [ ] **Step 4: Run the tests and confirm they pass**

```powershell
.\garmin\build.ps1 -Test
```

Expected: `PASSED`, with the five new tests listed and `failed=0, errors=0`.

- [ ] **Step 5: Commit**

```powershell
git add garmin
git commit -m "Store a record-activity choice on each Garmin match, defaulting from a new setting`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `Recorder` and its hooks in the match flow

**Files:**
- Create: `garmin/source/recording/Recorder.mc`
- Modify: `garmin/manifest.xml` (permissions)
- Modify: `garmin/source/RefWatchApp.mc`
- Modify: `garmin/source/ui/PreMatch.mc` (kick-off only; the toggle is Task 3)
- Modify: `garmin/source/ui/MatchDelegate.mc`
- Modify: `garmin/source/ui/MatchMenu.mc`
- Test: create `garmin/test/RecorderTest.mc`

**Interfaces:**
- Consumes: `MatchState.recordActivity`, `MatchState.phase`, `MatchState.isPlaying()`, phase constants, `Rez.Strings.ActivityName` (Task 1).
- Produces (module `Recorder`):
  - `start() as Void` — creates and starts a session if none exists, and turns GPS on.
  - `lap() as Void`, `stop() as Void` — act on a recording session only.
  - `save() as Void`, `discard() as Void` — end the session (stopping it first if needed) and turn GPS off.
  - `isActive() as Boolean` — a session exists (recording or stopped, not yet saved or discarded).
  - `warmUp() as Void` — turns GPS on so it has a fix by kick-off.
  - `releaseGps() as Void` — turns GPS off unless a session exists.
  - `forKickOff(match as MatchState) as Void` — after `kickOff`: start if no session, else lap.
  - `forPeriodEnd(match as MatchState) as Void` — after `endPeriod`: lap at half time, stop at full time.
  - `forResume(match as MatchState) as Void` — at launch: start a new session for a match in a half or at half time.
  - All three hooks do nothing when `match.recordActivity` is false.

- [ ] **Step 1: Write the failing tests**

Create `garmin/test/RecorderTest.mc`. Each test discards at the start and end, so no session leaks between tests and no activity is saved.

```monkeyc
import Toybox.Lang;
import Toybox.Test;

(:debug)
function recordingMatch() as MatchState {
    var m = testMatch();
    m.recordActivity = true;
    return m;
}

(:test)
function recorderIsInactiveAfterDiscard(logger as Logger) as Boolean {
    Recorder.discard();
    Test.assert(!Recorder.isActive());
    return true;
}

(:test)
function recorderStartsOnceAndDiscards(logger as Logger) as Boolean {
    if (!(Toybox has :ActivityRecording)) {
        return true;
    }
    Recorder.discard();
    Recorder.start();
    Recorder.start();                 // a second start must not create another session
    Test.assert(Recorder.isActive());
    Recorder.discard();
    Test.assert(!Recorder.isActive());
    return true;
}

(:test)
function kickOffDoesNotRecordWhenTheMatchOptsOut(logger as Logger) as Boolean {
    Recorder.discard();
    var m = testMatch();              // recordActivity is false
    m.kickOff(T0);
    Recorder.forKickOff(m);
    Test.assert(!Recorder.isActive());
    return true;
}

(:test)
function sessionLastsFromKickOffUntilDiscard(logger as Logger) as Boolean {
    if (!(Toybox has :ActivityRecording)) {
        return true;
    }
    Recorder.discard();
    var m = recordingMatch();
    m.kickOff(T0);
    Recorder.forKickOff(m);
    Test.assert(Recorder.isActive());
    m.endPeriod(T0 + 30 * MIN);       // half time: lap, keep recording
    Recorder.forPeriodEnd(m);
    Test.assert(Recorder.isActive());
    m.kickOff(T0 + 35 * MIN);         // 2nd half: lap on the same session
    Recorder.forKickOff(m);
    Test.assert(Recorder.isActive());
    m.endPeriod(T0 + 65 * MIN);       // full time: stopped, kept until Save or Discard
    Recorder.forPeriodEnd(m);
    Test.assert(Recorder.isActive());
    Recorder.discard();
    Test.assert(!Recorder.isActive());
    return true;
}

(:test)
function resumeRecordsOnlyAMatchThatIsUnderway(logger as Logger) as Boolean {
    if (!(Toybox has :ActivityRecording)) {
        return true;
    }
    Recorder.discard();
    var m = recordingMatch();
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.kickOff(T0 + 35 * MIN);
    m.endPeriod(T0 + 65 * MIN);       // full time: nothing left to record
    Recorder.forResume(m);
    Test.assert(!Recorder.isActive());
    var playing = recordingMatch();
    playing.kickOff(T0);
    Recorder.forResume(playing);
    Test.assert(Recorder.isActive());
    Recorder.discard();
    return true;
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

```powershell
.\garmin\build.ps1 -Test
```

Expected: `monkeyc` fails because `Recorder` is undefined.

- [ ] **Step 3: Add the permissions**

`garmin/manifest.xml` — replace `<iq:permissions/>` with:

```xml
        <iq:permissions>
            <iq:uses-permission id="Fit"/>
            <iq:uses-permission id="Positioning"/>
        </iq:permissions>
```

- [ ] **Step 4: Write `Recorder`**

Create `garmin/source/recording/Recorder.mc`:

```monkeyc
import Toybox.Activity;
import Toybox.ActivityRecording;
import Toybox.Lang;
import Toybox.Position;
import Toybox.WatchUi;

// The Garmin activity recorded alongside a match: one session from kick-off to full time, with a
// lap at each phase change, so the activity shows the 1st half, the break and the 2nd half
// separately. Pausing the match clock does not pause the recording, because the referee keeps
// moving. A match never depends on recording: everything here is a no-op on a watch that cannot
// record, and recording errors are swallowed.
module Recorder {
    var _session as ActivityRecording.Session or Null = null;
    var _gpsOn as Boolean = false;

    // After MatchState.kickOff: the 1st half starts the activity; the 2nd half adds a lap. A
    // match resumed after a restart has no session yet, so the 2nd half starts one too.
    function forKickOff(match as MatchState) as Void {
        if (!match.recordActivity) {
            return;
        }
        if (_session == null) {
            start();
        } else {
            lap();
        }
    }

    // After MatchState.endPeriod: half time adds a lap; full time stops the timer. The session
    // is kept until the referee saves or discards the match.
    function forPeriodEnd(match as MatchState) as Void {
        if (!match.recordActivity) {
            return;
        }
        if (match.phase.equals(PHASE_GAME_ENDED)) {
            stop();
        } else {
            lap();
        }
    }

    // At launch, for a match left underway. The earlier part was saved when the app closed, so
    // this starts a second activity (a known limitation, see the spec).
    function forResume(match as MatchState) as Void {
        if (match.recordActivity && (match.isPlaying() || match.phase.equals(PHASE_HALF_TIME))) {
            start();
        }
    }

    function start() as Void {
        if (_session != null || !(Toybox has :ActivityRecording)) {
            return;
        }
        try {
            warmUp();
            var session = ActivityRecording.createSession({
                :name => WatchUi.loadResource(Rez.Strings.ActivityName) as String,
                :sport => sport()
            });
            session.start();
            _session = session;
        } catch (e) {
            _session = null;
        }
    }

    function lap() as Void {
        var session = _session;
        if (session != null && session.isRecording()) {
            session.addLap();
        }
    }

    function stop() as Void {
        var session = _session;
        if (session != null && session.isRecording()) {
            session.stop();
        }
    }

    function save() as Void {
        var session = _session;
        if (session != null) {
            try {
                stop();
                session.save();
            } catch (e) {
            }
        }
        _session = null;
        releaseGps();
    }

    function discard() as Void {
        var session = _session;
        if (session != null) {
            try {
                stop();
                session.discard();
            } catch (e) {
            }
        }
        _session = null;
        releaseGps();
    }

    function isActive() as Boolean {
        return _session != null;
    }

    // Turning GPS on during set-up gives it time to find a fix before kick-off.
    function warmUp() as Void {
        if (!_gpsOn && (Toybox has :Position)) {
            Position.enableLocationEvents(Position.LOCATION_CONTINUOUS, new RecorderGps().method(:onPosition));
            _gpsOn = true;
        }
    }

    function releaseGps() as Void {
        if (_gpsOn && _session == null) {
            Position.enableLocationEvents(Position.LOCATION_DISABLE, null);
            _gpsOn = false;
        }
    }

    // Activity.SPORT_SOCCER needs API 3.2; the fēnix 5X has 3.1, where only the deprecated
    // ActivityRecording constant exists. Both are FIT sport 7.
    function sport() as Activity.Sport or ActivityRecording.Sport {
        if (Activity has :SPORT_SOCCER) {
            return Activity.SPORT_SOCCER;
        }
        return ActivityRecording.SPORT_SOCCER;
    }
}

// Position.enableLocationEvents needs a listener method. The session reads GPS on its own, so
// the listener ignores the fixes.
class RecorderGps {
    function initialize() {
    }

    function onPosition(info as Position.Info) as Void {
    }
}
```

- [ ] **Step 5: Run the tests and confirm they pass**

```powershell
.\garmin\build.ps1 -Test
```

Expected: `PASSED`, `failed=0, errors=0`. Compiler warnings that `ActivityRecording.SPORT_SOCCER` is deprecated are expected; errors are not. If `recorderStartsOnceAndDiscards` fails because `isActive()` is false, the simulator could not create a session: check the compiler output for a permission error (Step 3) before changing `Recorder`.

- [ ] **Step 6: Call the hooks from the match flow**

`garmin/source/ui/PreMatch.mc` — in `PreMatchDelegate.onSelect`, the `:kickOff` branch becomes:

```monkeyc
        if (id == :kickOff) {
            var match = new MatchState(_setup);
            match.kickOff(Clock.nowMs());
            MatchStore.save(match);
            Recorder.forKickOff(match);
            // Close set-up first so the match replaces the start menu and is the only view.
            WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
            Nav.showMatch(match);
```

`garmin/source/ui/MatchDelegate.mc`:

In `start()`, the full-time branch becomes:

```monkeyc
        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            Recorder.save();
            MatchStore.archive(_match);
            GameList.show();
            return true;
        }
```

`startSecondHalf()` becomes:

```monkeyc
    function startSecondHalf() as Void {
        _match.kickOff(Clock.nowMs());
        MatchStore.save(_match);
        Recorder.forKickOff(_match);
        WatchUi.requestUpdate();
    }
```

`leave()` becomes:

```monkeyc
    // The match resumes on the next launch; the activity so far is saved now, and a new one
    // starts when the app reopens.
    function leave() as Void {
        Recorder.save();
        System.exit();
    }
```

`discard()` becomes:

```monkeyc
    function discard() as Void {
        Recorder.discard();
        MatchStore.clear();
        _match.phase = PHASE_ABANDONED;
    }
```

`garmin/source/ui/MatchMenu.mc` — in `MatchMenuDelegate.onSelect`, the `:startSecondHalf` branch becomes:

```monkeyc
        } else if (id == :startSecondHalf) {
            _match.kickOff(Clock.nowMs());
            MatchStore.save(_match);
            Recorder.forKickOff(_match);
```

`endPeriod()` becomes:

```monkeyc
    function endPeriod() as Void {
        _match.endPeriod(Clock.nowMs());
        MatchStore.save(_match);
        Recorder.forPeriodEnd(_match);
        WatchUi.requestUpdate();
    }
```

`abandon()` becomes:

```monkeyc
    function abandon() as Void {
        Recorder.discard();
        MatchStore.clear();
        _match.phase = PHASE_ABANDONED;
    }
```

`garmin/source/RefWatchApp.mc` — in `getInitialView`, resume recording for a match underway:

```monkeyc
        if (match != null && !match.phase.equals(PHASE_PRE_GAME)) {
            Recorder.forResume(match);
            return [new MatchView(match), new MatchDelegate(match)];
        }
```

and add `onStop` after `onStart`:

```monkeyc
    // Whatever closes the app (BACK → Leave, the system, a crash-free exit), the activity
    // recorded so far is kept. At full time this saves it before the referee chooses Save or
    // Discard, so a later Discard cannot remove it.
    function onStop(state as Dictionary or Null) as Void {
        Recorder.save();
    }
```

- [ ] **Step 7: Build every product and run the tests**

```powershell
foreach ($d in "fenix5x", "fenix7s", "fenix7", "epix2pro47mm", "fr265") { .\garmin\build.ps1 -Device $d }
.\garmin\build.ps1 -Test
```

Expected: five `BUILD SUCCESSFUL`, then `PASSED` with `failed=0, errors=0`.

- [ ] **Step 8: Commit**

```powershell
git add garmin
git commit -m "Record each Garmin match as a soccer activity with a lap per period`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: "Record activity" on the set-up screen

**Files:**
- Modify: `garmin/source/ui/PreMatch.mc`

**Interfaces:**
- Consumes: setup key `"recordActivity"` (Task 1), `Recorder.warmUp()` / `Recorder.releaseGps()` (Task 2), `Rez.Strings.RecordActivity`.

- [ ] **Step 1: Add the toggle and GPS warm-up**

In `PreMatch.push`, add the toggle as the last item (after `:kickOffTeam`) and warm up GPS when recording is on:

```monkeyc
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.KickOffTeam, null, :kickOffTeam, null));
        menu.addItem(new WatchUi.ToggleMenuItem(Rez.Strings.RecordActivity, null, :recordActivity, setup["recordActivity"] == true, null));
        if (setup["recordActivity"] == true) {
            Recorder.warmUp();
        }
```

In `PreMatchDelegate.onSelect`, add a branch at the end of the `if` chain. The menu flips a `ToggleMenuItem` before calling `onSelect`, so `isEnabled()` is the new state:

```monkeyc
        } else if (id == :recordActivity) {
            var on = (item as WatchUi.ToggleMenuItem).isEnabled();
            _setup["recordActivity"] = on;
            if (on) {
                Recorder.warmUp();
            } else {
                Recorder.releaseGps();
            }
        }
```

Add `onBack` to `PreMatchDelegate`, so backing out of set-up does not leave GPS running:

```monkeyc
    function onBack() as Void {
        Recorder.releaseGps();
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
    }
```

- [ ] **Step 2: Build every product and run the tests**

```powershell
foreach ($d in "fenix5x", "fenix7s", "fenix7", "epix2pro47mm", "fr265") { .\garmin\build.ps1 -Device $d }
.\garmin\build.ps1 -Test
```

Expected: five `BUILD SUCCESSFUL`, then `PASSED`.

- [ ] **Step 3: Look at the set-up menu on the smallest and the touch screens**

For `fenix5x` and `fr265`: start the app in the simulator (`.\garmin\build.ps1 -Device <id> -Run` in a separate terminal), then capture the set-up menu scrolled to the new item:

```powershell
.\garmin\tools\sim.ps1 -Device fenix5x -Click START,UP -Out "$env:TEMP\setup-record-fenix5x.png"
```

(`START` opens Quick match → set-up; `UP` wraps to the last item, "Record activity". If the menu does not wrap on that device, use `DOWN` six times instead.) Open each PNG and check: the label is not clipped by the bezel, the toggle is visible, and START flips it. Then press START on the item and capture again to see the "off" state. If the label clips on the 5X, shorten the string `RecordActivity` to `Record` rather than changing the layout.

- [ ] **Step 4: Check memory on the fēnix 5X**

In the fēnix 5X simulator, kick off with recording on and read the used/limit kB in the status bar (or File → View Memory). Expected: well under the 765 kB budget (phase 1 peak was 39.6 kB).

- [ ] **Step 5: Commit**

```powershell
git add garmin
git commit -m "Add a Record activity toggle to Garmin match set-up`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: On the fēnix 5X, and README

**Files:**
- Modify: `garmin/README.md`

- [ ] **Step 1: Sideload (with the user)**

Ask the user to connect the fēnix 5X by USB, then:

```powershell
.\garmin\build.ps1 -Device fenix5x
$drive = (Get-Volume | Where-Object FileSystemLabel -eq "GARMIN").DriveLetter
Copy-Item .\garmin\bin\RefWatch-fenix5x.prg "${drive}:\GARMIN\APPS\RefWatch.prg" -Force
```

The watch asks for the new permissions (Fit, Positioning) on first launch or install; that is expected.

- [ ] **Step 2: On-watch checklist (user reports results)**

Use 5-minute halves and a 5-minute break.
1. Set-up shows "Record activity" on; GPS starts acquiring before kick-off.
2. Full match, then Save → the watch's activity history shows a "Referee" activity with heart rate, distance, and 3 laps (1st half, break, 2nd half).
3. A second match with "Record activity" turned off in set-up → no new activity.
4. A third match, Abandon from the match menu → no new activity.
5. A fourth match, BACK → Leave mid-half, reopen → the match resumes, and the history has a partial activity; the rest records as a second activity.
6. After a sync, the activities appear in Garmin Connect on the phone.

- [ ] **Step 3: Record results**

Add a "Phase 2 results" section to `garmin/README.md` after "Phase 1 results": devices built, the unit-test count, the memory reading from Task 3, and the on-watch checklist outcomes, including how the sport appears in the 5X history (Soccer or a generic name).

- [ ] **Step 4: Commit**

```powershell
git add garmin/README.md
git commit -m "Record Garmin phase 2 activity-recording results`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Next plans

- Phase 3: pairing (Firebase functions `createGarminPairingCode`, `garminPair`, `listGarminDevices`, `unlinkGarminDevice`; phone Settings section; watch `sync/Pairing.mc`).
- Phase 4: game sync (`garminGames`, `garminUploadGame`, `sync/GameCache.mc`, `sync/UploadQueue.mc`, phone fixture cross-check).
- Phase 5: release (Connect IQ store listing, phone release, privacy policy).
