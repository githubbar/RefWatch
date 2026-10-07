# RefWatch for Garmin (Connect IQ)

Design: `docs/superpowers/specs/2026-10-07-garmin-app-design.md`.

## One-time setup
1. Free Garmin developer account; install the Connect IQ SDK Manager, the latest SDK, and the
   devices fēnix 5X, fēnix 7S, fēnix 7, epix Pro 47mm, Forerunner 965.
2. Developer key at `%USERPROFILE%\keys_for_garmin\developer_key.der` (generated with openssl,
   see the plan). **Back it up** — store updates must be signed with the same key. Never put it
   in the repo (`garmin/.gitignore` excludes `*.der` / `*.pem`).
3. Java comes from Android Studio's bundled JBR; `build.ps1` sets it.

## Build, test, run
    .\garmin\build.ps1                      # fenix5x .prg in garmin\bin
    .\garmin\build.ps1 -Device fenix7 -Run  # open in the simulator
    .\garmin\build.ps1 -Test                # unit tests in the simulator

`-Run` starts the simulator if needed and then stays attached (monkeydo blocks until you
press Ctrl+C or close the app). `-Test` returns when the tests finish.

## Screenshot the simulator
The simulator has no command-line screenshot. With the app running:

    .\garmin\tools\sim-screenshot.ps1 -Out C:\temp\sim.png

It captures the simulator window (bringing it to the front first).

## Memory budget
fēnix 5X watch-app limit: 1310720 bytes. Keep peak use under 60% (786432 bytes)
(simulator: File → View Memory).

## Sideload to a watch
Build for that watch's device ID, connect it by USB, and copy `garmin\bin\RefWatch-<device>.prg`
to the watch's `GARMIN\APPS` folder. Eject; the app appears in the watch's app list.
Sideloaded apps cannot be configured from Garmin Connect; test settings in the simulator
(File → Edit Persistent Storage → Edit Application.Properties).
