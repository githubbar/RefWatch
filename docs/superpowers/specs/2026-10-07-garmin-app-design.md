# RefWatch for Garmin — design

Date: 2026-10-07
Status: approved in brainstorming, awaiting spec review

## Goal

Bring RefWatch to Garmin watches as a Connect IQ device app. A referee links the watch to
their RefWatch account, sees their scheduled games on the watch, referees the match on it,
and the finished game appears in the RefWatch phone app's history.

The app must run on the **fēnix 5X** (the test device: 240×240, five buttons, no touch,
Connect IQ 3.1, small app memory) and on current watches (fēnix 7/8, epix, Forerunner 265/965,
touch-capable). One project builds for all of them. If the fēnix 5X later blocks a feature we
want, we drop the 5X rather than design around it; nothing in this spec requires that.

## Scope

**Version 1 (this spec)**

- Match tool: pick a synced game or a quick match; two halves and halftime; added time counts
  up past regulation; vibration at period ends; goals (optional scorer number); yellow and red
  cards with player number; game log; undo last event; full-time summary.
- Garmin activity recording of the match, on by default, switchable in settings.
- Pairing the watch to a RefWatch account with a code shown in the phone app.
- Pulling upcoming games from the account; uploading finished games to it.

**Later versions (not in this spec):** extra time, penalty shootout, on-watch analytics,
uploading GPS/heart-rate history to RefWatch, Instinct-series (monochrome) layouts, Garmin
build in CI, Google sign-in on the watch.

## Architecture

```
 Phone (RefWatch Android)        Firebase                         Garmin watch
 ────────────────────────        ────────────────────────         ─────────────────────
 Settings → "Garmin watch" ───►  createGarminPairingCode (callable)
   shows 6-digit code             writes garminPairingCodes/{code}
                                                                  referee types code into
                                                                  Garmin Connect → RefWatch
                                                                  app settings
                                 garminPair (HTTP)          ◄───  POST code
                                   returns device token     ───►  stores token
                                 garminGames (HTTP)         ◄───  GET upcoming games
                                   reads users/{uid}/games  ───►  trimmed list
                                 garminUploadGame (HTTP)    ◄───  POST finished game
 history updates via the  ◄────    merges users/{uid}/games/{id}
 existing Firestore listener
```

The watch cannot use the Firebase SDK; Connect IQ only offers `Communications.makeWebRequest`,
proxied through the Garmin Connect phone app. So the watch authenticates with its own opaque
bearer token, and the HTTP functions use the Admin SDK, scoped to the uid the token maps to.

Code lives in this repository:

| Piece | Location |
|---|---|
| Cloud Functions | `functions/index.js` (beside `generateCustomToken`), split into `functions/garmin/*.js` modules |
| Phone UI | `mobile/.../screens/SettingsScreen.kt` plus a new `GarminLinkSection` composable and view model |
| Watch app | new top-level `garmin/` (Connect IQ project; Gradle ignores it) |

## Data model (Firestore)

New top-level collections, denied to clients by the existing catch-all rule; only the
functions (Admin SDK) touch them. `firestore.rules` needs no change, but its header comment
is updated to mention them.

`garminPairingCodes/{code}` — `code` is 6 decimal digits.

| Field | Type | Notes |
|---|---|---|
| `uid` | string | owner |
| `expiresAt` | timestamp | created + 10 minutes |

`garminDevices/{tokenHash}` — `tokenHash` is hex SHA-256 of the bearer token.

| Field | Type | Notes |
|---|---|---|
| `uid` | string | owner |
| `deviceName` | string | Connect IQ part number / model name sent by the watch |
| `createdAt` | timestamp | |
| `lastSeenAt` | timestamp | updated on each authenticated request (at most once per hour, to limit writes) |

`garminPairAttempts/{ipHash}` — rate limiting: `count`, `windowStart`.

Game documents under `users/{uid}/games/{id}` keep their current shape.

## Pairing

1. **Phone → `createGarminPairingCode`** (callable, requires Firebase auth). In a transaction:
   delete any existing code for this uid, pick a random 6-digit code not currently in use,
   write it with a 10-minute expiry. Returns `{code, expiresAt}`.
2. Phone Settings shows the code large, with a countdown and "Open Garmin Connect → RefWatch →
   Settings and enter this code". On expiry it offers a new code.
3. **Watch → `garminPair`** (`POST`, JSON `{code, deviceName, appVersion}`). The watch sends it
   when the `pairingCode` app setting changes (`onSettingsChanged`) or at start when a code is set
   and no token is stored.
   - Rate limit: at most 10 failed attempts per caller IP per hour → `429`.
   - Unknown or expired code → `404`. Valid code: delete it, create a 32-byte random token
     (base64url), write `garminDevices/{sha256(token)}`, return `{token}`.
4. Watch stores the token in `Application.Storage`, clears the `pairingCode` property, and shows
   "Linked".
5. **Unlinking:** the phone's Garmin section lists `garminDevices` for the user (via a callable
   `listGarminDevices`) with model and last-seen time, and an Unlink button (callable
   `unlinkGarminDevice {tokenHash}`, which checks ownership before deleting). Tokens have no
   expiry otherwise.

## Pulling games — `GET garminGames`

Header `Authorization: Bearer <token>`. Unknown token → `401`.

Returns games of the token's uid with `gameDateTimeEpochMillis` from **now − 6 h** to
**now + 14 d** whose `currentPhase` is not `GAME_ENDED`, sorted by start, **at most 12**.
Response, with short keys to save watch memory:

```json
{"games":[{"id":"…","t":1791500400,"h":"Eagles U12","a":"Hawks U12",
           "hc":16711680,"ac":255,"hd":30,"ht":5,"f":"Field 3, West Park",
           "r":"Center","ag":"U12"}]}
```

| Key | Source field | Notes |
|---|---|---|
| `t` | `gameDateTimeEpochMillis` | epoch **seconds** |
| `h` / `a` | `homeTeamName` / `awayTeamName` | truncated to 24 chars |
| `hc` / `ac` | `homeTeamColorArgb` / `awayTeamColorArgb` | RGB, alpha dropped |
| `hd` / `ht` | `halfDurationMinutes` / `halftimeDurationMinutes` | |
| `f` | `fieldNumber` or `venue` | optional, truncated to 32 chars |
| `r` | `refereeAssignment` | optional |
| `ag` | `ageGroup` | optional, enum name |

Target ≤ 250 bytes per game, so a full response stays under ~3.5 KB — well inside the
~16 KB that older watches can receive.

The watch syncs at app start when the phone is connected and from a "Sync" menu item, and
caches the list in `Application.Storage` so it works offline at the field.

## Uploading games — `POST garminUploadGame`

Header `Authorization: Bearer <token>`. Body:

```json
{"id":"…", "source":"garmin",
 "homeTeamName":"…", "awayTeamName":"…",
 "homeTeamColorArgb":-65536, "awayTeamColorArgb":-16776961,
 "halfDurationMinutes":30, "halftimeDurationMinutes":5,
 "kickOffTeam":"HOME", "currentPhase":"GAME_ENDED",
 "homeScore":2, "awayScore":1,
 "gameDateTimeEpochMillis":1791500460000,
 "events":[ … ]}
```

`id` is the synced game's id, or a watch-generated UUID for a quick match.

`events` use the existing `GameEvent` JSON (discriminator `eventType`), exactly as the phone
parses them (`JSONHelpers.kt`, `AppJsonConfiguration`):

```json
{"eventType":"PHASE_CHANGE","id":"…","newPhase":"FIRST_HALF","timestamp":1791500460000.0,"gameTimeMillis":0.0}
{"eventType":"GOAL","id":"…","team":"HOME","timestamp":…,"gameTimeMillis":754000.0,"homeScoreAtTime":1,"awayScoreAtTime":0,"playerNumber":9}
{"eventType":"CARD","id":"…","team":"AWAY","playerNumber":4,"cardType":"YELLOW","timestamp":…,"gameTimeMillis":1302000.0}
```

`timestamp` is wall-clock epoch milliseconds; `gameTimeMillis` is time elapsed **within the
current period**, matching `WearGameViewModel` (`actualTimeElapsedInPeriodMillis`).
`playerNumber` is omitted on goals when no scorer was logged.

Server behaviour:
- Validates types, enum values, `events.length ≤ 200`, body ≤ 64 KB; otherwise `400`.
- **Existing game:** merges only match-result fields (`currentPhase`, `homeScore`, `awayScore`,
  `kickOffTeam`, `events`, team colours, durations, `lastUpdated`). Schedule fields (`venue`,
  `notes`, `gameNumber`, `ageGroup`, …) are untouched. Team names and kick-off time are written
  only when the document is new.
- **New game (quick match):** creates the document with the fields above, `userId` = uid, and
  `Game` defaults for the rest.
- Idempotent: uploading the same `id` again rewrites the same fields.
- Returns `200 {ok:true}`.

## Watch app

### Buttons

| Button | Match screen | Lists / pickers |
|---|---|---|
| START | pause / resume clock | select |
| UP | Home team actions | previous |
| DOWN | Away team actions | next |
| hold UP (menu) | match menu | — |
| BACK | "Leave match?" confirm | back |

Touch watches map taps onto the same behaviours (tap a team's half of the screen = that team's
actions). The Light button is reserved by the system.

### Screens

1. **Game list** — "Quick match" first, then synced games (time, teams). Status line:
   "Linked · synced 2 min ago" / "Not linked" / "Not synced".
2. **Pre-match** — teams with colour dots, half and halftime lengths, kick-off team, Record
   activity on/off. Each line is editable from a `Menu2`. START kicks off.
3. **Match** — large clock (elapsed in period), period name, score beside each team's colour
   bar, time of day. Past regulation the watch vibrates once and the clock continues with added
   time shown as `+2:14` in a distinct colour. Pause icon while stopped.
4. **Team actions** — Goal, Yellow, Red. Cards open a two-column (tens/ones) number picker;
   goals log immediately unless "Log goal scorer" is on, in which case the picker opens with a
   Skip option.
5. **Match menu** — End half (first item once regulation has passed), Game log, Undo last event,
   Abandon match.
6. **Halftime** — break countdown with vibration at its end; START starts the 2nd half;
   kick-off flips to the other team.
7. **Full time** — score and card summary; Save (queue upload, save activity) or Discard
   (confirm).
8. **Game log** — events in order with match minute.

### Timekeeping and persistence

- The clock is computed from timestamps (period start, accumulated pause), never from tick
  counting, so it cannot drift when the app is busy or the screen sleeps. The view refreshes
  once a second.
- `MatchState` is written to `Application.Storage` after every state change (kick-off, pause,
  resume, event, period end). Reopening the app after a crash or reboot resumes the match.
  Known limitation: a recording interrupted that way restarts as a new Garmin activity.
- Vibration via `Attention.vibrate` where the device supports it.

### Activity recording

`ActivityRecording.createSession` started at kick-off and saved at full time (discarded on
Discard/Abandon). Sport is soccer where the device defines it, otherwise a generic sport.
Controlled by the "Record activity" setting (default on); the pre-match screen can override it
for one match. Requires the `Fit` and `Positioning` permissions in the manifest.

### App settings (edited in the Garmin Connect phone app)

| Key | Type | Default |
|---|---|---|
| `pairingCode` | string | empty |
| `recordActivity` | boolean | true |
| `logGoalScorer` | boolean | false |

### Sync on the watch

- Upload queue in `Application.Storage`: finished games stay queued until a `200`. Retried at
  app start, after each match, and on manual sync.
- `401` → drop the token, keep the queue, show "Pair again in Garmin Connect".
- Bluetooth/network errors (negative Connect IQ response codes) → keep the queue, show a small
  "not synced" indicator. A match never depends on connectivity.

### Code layout (`garmin/`)

```
garmin/
  manifest.xml            device list, permissions (Communications, Fit, Positioning)
  monkey.jungle
  resources/              strings, settings, properties, layouts, launcher icon
  resources-round-240x240/  …  per-screen overrides only where needed
  source/
    RefWatchApp.mc
    model/MatchState.mc   all match rules; no UI, storage or clock access (clock injected)
    model/EventJson.mc    events ↔ GameEvent JSON shape
    sync/Api.mc           makeWebRequest wrapper, auth header, error mapping
    sync/Pairing.mc
    sync/GameCache.mc
    sync/UploadQueue.mc
    recording/Recorder.mc
    ui/                   one View + one BehaviorDelegate per screen
  test/                   (:test) unit tests for MatchState and EventJson
```

Memory budget: no bitmaps beyond the launcher icon; strings in resources; stay under **60% of
the fēnix 5X watch-app memory limit** (exact figure read from the SDK's device
`compiler.json` once installed).

## Phone app

- `SettingsScreen.kt` gains a "Garmin watch" section: "Link a Garmin watch" button → code with
  countdown and instructions; list of linked watches (model, last seen) with Unlink.
- Calls the callables through the existing Firebase Functions client used for
  `generateCustomToken` (`AuthRepository.kt`).
- No new Android permissions. Ships as a phone-only update.
- `PRIVACY POLICY.html` and `docs/privacy-policy.md`: add that linking a Garmin watch stores a
  record of the watch and sends game data to it through Garmin Connect.
- Play: no listing change needed. If the listing later mentions Garmin, only in the description
  ("Works with Garmin watches"), never the title or icon. Review the Data safety form before the
  release.

## Testing

- **Watch unit tests** (Connect IQ `(:test)`, run in the simulator): `MatchState` — clock across
  pauses, added time, ending periods, kick-off flip at half time, undo of goals and cards,
  restore from storage; `EventJson` — field names and types match the phone's parser.
- **Functions** against the Firebase emulator (Firestore + Functions), Node's built-in test
  runner: pairing (expiry, single use, wrong code, rate limit, replacement, unlink ownership),
  `garminGames` (window, `GAME_ENDED` excluded, 12-game cap, response size, `401`),
  `garminUploadGame` (merge preserves schedule fields, new quick match, idempotent re-upload,
  validation `400`, `401`).
- **Cross-check:** a phone unit test decodes a fixture produced by `EventJson` with
  `AppJsonConfiguration`, so the two formats cannot drift apart.
- **Phone:** unit test of the Garmin link view model (code countdown, expiry, unlink).
- **Simulator matrix:** fēnix 5X, fēnix 7S, fēnix 7, epix Pro / Forerunner 265. Memory viewer
  checked on every build; fēnix 5X under 60%.
- **Device:** sideload a fēnix 5X `.prg` over USB to `GARMIN/APPS` on the test watch, signed
  with a local Connect IQ developer key kept outside the repository.

## Build order

1. **Standalone match** — quick match, all screens, timing, cards, goals, persistence. Test on
   the fēnix 5X before any backend work.
2. **Activity recording** and its setting.
3. **Pairing** — functions, phone Settings section, watch pairing.
4. **Game sync** — `garminGames`, upload queue, `garminUploadGame`, phone history shows Garmin
   matches.
5. **Release** — Garmin developer account, Connect IQ store listing and review; phone release
   with the Garmin section and privacy policy update.

## Prerequisites for the developer

Connect IQ SDK Manager with the current SDK and the device images above; a free Garmin
developer account; the Monkey C extension for VS Code; a Connect IQ developer key (generated
locally, stored outside the repo). Java from Android Studio's bundled JBR.

## Risks

- **fēnix 5X memory.** Mitigated by the budget above; checked every build. Fallback: drop the 5X.
- **Old-watch response size.** Responses are capped at 12 trimmed games (~3.5 KB).
- **Connect IQ store review** can take days; plan the phone release to follow, not precede, the
  watch app's approval, so the Garmin section never points at an app that isn't available.
