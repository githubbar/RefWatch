# RefWatch for Garmin (Connect IQ)

Design: `docs/superpowers/specs/2026-10-07-garmin-app-design.md`.

## One-time setup
1. Free Garmin developer account; install the Connect IQ SDK Manager, the latest SDK, and the
   devices fēnix 5X, fēnix 7S, fēnix 7, epix Pro 47mm, Forerunner 265.
2. Developer key at `%USERPROFILE%\keys_for_garmin\developer_key.der`. Generate it once
   (Git Bash has openssl at `/mingw64/bin/openssl`; do not overwrite an existing key):

       mkdir -p ~/keys_for_garmin && cd ~/keys_for_garmin
       openssl genrsa -out developer_key.pem 4096
       openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key.der -nocrypt

   **Back it up** — store updates must be signed with the same key. Never put it in the repo
   (`garmin/.gitignore` excludes `*.der` / `*.pem`).
3. Java comes from Android Studio's bundled JBR; `build.ps1` sets it.

## Build, test, run
    .\garmin\build.ps1                      # fenix5x .prg in garmin\bin
    .\garmin\build.ps1 -Device fenix7 -Run  # open in the simulator
    .\garmin\build.ps1 -Test                # unit tests in the simulator
    .\garmin\build.ps1 -Release             # release build: leaves out (:debug) code such as test fixtures

`-Run` starts the simulator if needed and then stays attached (monkeydo blocks until you
press Ctrl+C or close the app). `-Test` returns when the tests finish.

## Drive and screenshot the simulator without taking focus
The simulator has no command-line control, and bringing it to the front would interrupt
whatever you are doing. `tools\sim.ps1` keeps its window far off-screen and inactive, presses
buttons by posting mouse messages to it, and captures it with `PrintWindow`. It never
focuses the window, moves the mouse or sends keystrokes. `build.ps1` starts the simulator
this way too.

    .\garmin\tools\sim.ps1 -Start                 # launch if needed, parked off-screen
    .\garmin\tools\sim.ps1 -Hide                  # park a simulator you opened yourself
    .\garmin\tools\sim.ps1 -Device fenix5x -Click START,DOWN,DOWN -Out C:\temp\sim.png
    .\garmin\tools\sim.ps1 -Out C:\temp\sim.png   # capture only

Buttons are `START`, `BACK`, `UP`, `DOWN`; `-Wait` (ms, default 900) sets the pause after each
click. Button positions are stored per device in the script (all five products).
Do not minimize the window: a minimized window does not render. A touch tap is a click on the
watch screen: pass `-Click @x,y` (window coordinates, as in a screenshot).

## Memory budget
fēnix 5X watch-app limit: 1275.5 kB in the simulator. Keep use under 60% (about 765 kB)
(simulator: File → View Memory, or the used/limit kB figure in its status bar).

## Sideload to a watch
Build for that watch's device ID, connect it by USB, and copy `garmin\bin\RefWatch-<device>.prg`
to the watch's `GARMIN\APPS` folder. Eject; the app appears in the watch's app list.

The fēnix 5X mounts as a removable drive labeled `GARMIN` (USB Mass Storage, built into
Windows; no Garmin driver is needed). If it does not appear:
- No USB device at all in Device Manager: the clip is charge-only or not seated. Use a
  genuine Garmin cable, snap the clip in firmly, and clean the watch's contacts.
- `Unknown USB Device (Device Descriptor Request Failed)`: the data pins only half connect.
  Plug into a port on the PC itself rather than a hub, dock or monitor, and reseat the clip.

`GARMIN\GarminDevice.xml` on the watch's drive names the model, firmware and Connect IQ version
(`<VmVersion>`) of the connected watch. After a crash, `GARMIN\APPS\LOGS\CIQ_LOG.YML` holds
the error; a debug build includes line numbers.
Sideloaded apps cannot be configured from Garmin Connect; test settings in the simulator
(File → Edit Persistent Storage → Edit Application.Properties).

## The match menu in the simulator
BACK opens the match menu, so `-Click BACK` reaches it. Hold UP opens the same menu on a watch,
but the simulator does not turn a held mouse button posted by `sim.ps1` into a long press.

## Phase 1 results

Visual pass and memory were done in the simulator; the on-watch part is still to do.

**Builds.** `build.ps1 -Device <id>` succeeds (no errors) for fenix5x, fenix7s, fenix7,
epix2pro47mm and fr265. Only warning: the 70x70 launcher icon is scaled to each device's size.

**Memory.** Figures are the simulator's status-bar kB. fenix5x limit 1275.5 kB, so 60% is about
765 kB; fenix7s and epix2pro47mm limit 763.6 kB, so 60% is about 458 kB. On the fenix5x the
heaviest point observed (7 goals and 3 cards, about 11 events with phase changes, the match menu
and the game log scrolled to the end) was 39.6 kB, 3.1% of the limit; the idle start menu was
29.7 kB. These are current-use readings at the heaviest point, not a measured peak.

**Screens checked in the simulator on all five products** (start menu, set-up, half-length
picker, match running / paused / added time, team menu, player-number picker, match menu,
end-of-half and end-of-match confirmations, half time, second half, full time, game log).
Our own screens (match, pickers, half time, full time) are unclipped on all five products, with
score bars clear of the edge and a legible clock. System menus (Menu2) show the next row partially
at the bottom of round screens as a scroll hint; it is fully visible when focused. No layout
changes were needed. Hold UP (match menu) and short halves were simulated with temporary,
uncommitted edits. Touch input on fr265 and epix2pro47mm was not exercised (buttons only).

**On-watch results (fenix5x, firmware 25.00, Connect IQ 3.1.9), 2026-10-08:** the user sideloaded
the debug build and reported that everything works. Checklist: launch from the app list; five
buttons and hold UP; vibration at end of half and break; 10 minutes untouched; exit mid-match and
resume; readability in sunlight; focused bottom menu row fully readable (set-up, match menu,
game log); vibration timing while a menu is open; 10 minutes untouched with the screen off.
Tap input on a touch watch is still untested (no touch watch available).

## Phase 2 results (activity recording, match menu, added-time reminders)

**Builds and tests.** fenix5x, fenix7s, fenix7, epix2pro47mm and fr265 build; the only warnings are
the scaled launcher icon and the deprecated `ActivityRecording.SPORT_SOCCER` (used on purpose: the
fēnix 5X's Connect IQ 3.1 lacks `Activity.SPORT_SOCCER`). 68 unit tests pass in the simulator.

**Memory (fenix5x).** About 37 kB on the set-up menu and 41 kB with the match menu open, against
the 1275.5 kB limit.

**Simulator.** The set-up toggle, every match-menu item ("Exit (keep match)" is the widest) and
the confirmations fit the fēnix 5X without clipping. Reset game returns to set-up with the same
teams. The period-end alert was traced firing at regulation (`Attention has :vibrate` true).

**On-watch (fenix5x), 2026-10-09.** The first on-watch test found no way to end the half (the menu
was only behind hold UP) and no buzz at regulation (a single pulse, only while the match screen
showed). BACK now opens the match menu, and a half past regulation buzzes every 30 s of added
time from an app-level ticker. After that change the user reported that everything works:
recording, the menu, and the reminders.

**Behavior worth knowing.** Closing the app saves the activity recorded so far. If the referee
exits at full time before choosing Save or Discard, the activity is already saved, and a later
Discard leaves it in the watch history and Garmin Connect, to delete by hand. Exiting mid-match
splits the match into two activities.

## Phase 3 results (pairing), 2026-10-09

**Deployed** to `refwatchapp` (us-central1): `createGarminPairingCode`, `listGarminDevices`,
`unlinkGarminDevice` (callables), `garminPair` (HTTP,
`https://us-central1-refwatchapp.cloudfunctions.net/garminPair`) and
`deleteGarminDataOnAccountDelete` (1st-gen auth trigger). Firestore TTL policies are ACTIVE on
`garminPairingCodes.expiresAt` and `garminPairAttempts.expiresAt`.

**Tests.** Functions: 31 pass against the Firestore emulator (`npm --prefix functions test`, needs
Java on PATH). Phone: 14 Garmin tests pass. Watch: 75 unit tests pass; five products build.

**Live checks.**
- `garminPair` answers a malformed code with `400 {"error":"bad_request"}` and a GET with `405`.
- Rate limiting counts the real client IP: a request with a forged `X-Forwarded-For: 1.1.1.1`
  created no bucket for that address and incremented this PC's own (the trusted value is the last
  `X-Forwarded-For` entry, which Google's front end appends).
- End to end, simulator: the phone (OnePlus 13) showed a code, the simulator entered it and showed
  "Linked", the phone cleared the code and listed the watch within seconds, and the start menu read
  "Link account / Linked". Unlink on the phone removed the device record.
- End to end, fēnix 5X (through Garmin Connect on the phone): "Linked"; the server lists it as
  `006-B2604-00` (the watch's `partNumber`).

**Known limits until phase 4.**
- The watch's token has no expiry; Unlink on the phone is the only revocation. Nothing on the watch
  uses the token yet, and the watch does not notice an unlink until phase 4 handles `401` (a
  Garmin Connect code is ignored while the watch still thinks it is linked; relink from the start
  menu).
- Relinking an already-linked watch leaves its old record in the phone's list until it is unlinked.
- Running the watch unit tests in the simulator clears the simulator's stored token
  (`Pairing.forget()`).
- The global ceiling (1000 failed pairing attempts an hour, all users) trades availability for
  safety: a large attack can block pairing for everyone for up to an hour.
