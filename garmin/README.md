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
click. Button positions are stored per device in the script (fenix5x and fr265 so far).
Do not minimize the window: a minimized window does not render.
## Memory budget
fēnix 5X watch-app limit: 1310720 bytes. Keep peak use under 60% (786432 bytes)
(simulator: File → View Memory).

## Sideload to a watch
Build for that watch's device ID, connect it by USB, and copy `garmin\bin\RefWatch-<device>.prg`
to the watch's `GARMIN\APPS` folder. Eject; the app appears in the watch's app list.
Sideloaded apps cannot be configured from Garmin Connect; test settings in the simulator
(File → Edit Persistent Storage → Edit Application.Properties).
