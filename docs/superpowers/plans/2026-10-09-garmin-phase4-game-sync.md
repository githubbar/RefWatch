# Garmin Phase 4 — Game Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A linked Garmin watch lists the referee's upcoming RefWatch games, starts a match from one with its teams, colors and lengths, and uploads every finished match (synced or quick) so it appears under Past in the phone app.

**Architecture:** Two new HTTP Cloud Functions for the watch, `garminGames` (GET) and `garminUploadGame` (POST), authenticated by the bearer token from phase 3. Their logic lives in plain async functions (`functions/garmin/devices.js`, `functions/garmin/games.js`) tested against the Firestore emulator; `functions/garmin/http.js` turns requests into calls and replies. On the watch, `sync/GameCache.mc` stores the downloaded list, `sync/UploadQueue.mc` turns a finished match into the upload body, and `sync/Sync.mc` runs one download-then-upload pass at app start, after a match is saved, after linking, and from a new "Sync" start-menu item. The phone needs no code change: its existing Firestore listener shows uploaded matches; a `common` unit test pins that the watch's event JSON decodes.

**Tech Stack:** Node 22 Cloud Functions (firebase-functions v6, firebase-admin v12), Node's built-in test runner, Firestore emulator; Monkey C, Connect IQ SDK 9.2.0, `minApiLevel` 3.1.0; Kotlin + kotlinx.serialization + JUnit4 (`common` module tests).

**Spec:** `docs/superpowers/specs/2026-10-07-garmin-app-design.md` — sections "Pulling games — `GET garminGames`", "Uploading games — `POST garminUploadGame`", "Sync on the watch", "Screens" (1, 7), "Testing", build-order step 4.

## Global Constraints

- `garminGames`: header `Authorization: Bearer <token>`; unknown token → `401`. Returns the token owner's games with `gameDateTimeEpochMillis` from **now − 6 h** to **now + 14 d**, `currentPhase` not `GAME_ENDED`, sorted by start, **at most 12**, as `{"games":[{id,t,h,a,hc,ac,hd,ht,f?,r?,ag?}]}`: `t` epoch **seconds**; `h`/`a` truncated to 24 characters; `hc`/`ac` RGB with alpha dropped; `hd`/`ht` half and break minutes; `f` = `fieldNumber` or else `venue`, truncated to 32; `r` = `refereeAssignment`; `ag` = `ageGroup` enum name. Optional keys are left out when empty.
- `garminUploadGame`: `POST`, bearer token, JSON body as in the spec (`id`, team names, `homeTeamColorArgb`/`awayTeamColorArgb`, `halfDurationMinutes`, `halftimeDurationMinutes`, `kickOffTeam`, `currentPhase`, `homeScore`, `awayScore`, `gameDateTimeEpochMillis`, `events`). Validates types, enum values, `events.length ≤ 200`, body ≤ 64 KB, else `400`. **Existing game:** writes only `currentPhase`, `homeScore`, `awayScore`, `kickOffTeam`, `events`, the two colors, the two durations and `lastUpdated`; everything else (team names, `gameDateTimeEpochMillis`, `venue`, `notes`, `gameNumber`, `ageGroup` …) is untouched. **New game:** also writes `id`, `userId` (= token's uid), team names and `gameDateTimeEpochMillis`; the phone's `Game` defaults cover the rest. Idempotent. Returns `200 {"ok":true}`.
- Error replies are JSON `{error: "<name>"}`: `400 bad_request`, `401 unauthorized`, `405 method_not_allowed`, `500 internal`. Never log a token or a request body.
- `lastSeenAt` on `garminDevices/{tokenHash}` is refreshed on an authenticated request at most once per hour.
- Event JSON is the phone's `GameEvent` shape (discriminator `eventType`; `GOAL`, `CARD`, `PHASE_CHANGE`), exactly what `MatchState` already records. `playerNumber` is left out of a goal without a scorer.
- The spec body's `"source":"garmin"` is not sent or stored: nothing reads it, and the phone's `Game` has no such field. (Ruling for this plan.)
- Watch: the upload queue is `MatchStore`'s finished list; a match leaves it only on a `200`. `401` → drop the token, keep the queue, the Sync item says "Link again". Network errors (negative codes above −400) → keep the queue, the Sync item says "No phone". A match never depends on connectivity.
- Watch: `minApiLevel` 3.1.0; the fēnix 5X runs Connect IQ 3.1.9, so no newer API without a `has` guard. User-visible text goes in `garmin/resources/strings/strings.xml`. Look at rendered screens on the fēnix 5X (240 px) before calling a watch UI done. Memory stays under 60% of the 5X limit (about 765 kB).
- Functions code passes `npm run lint` (eslint-config-google: 2-space indent, double quotes, max line 80, JSDoc on every `function` declaration, trailing commas on multi-line literals, `ecmaVersion` 2018 — use `Object.assign`, not object spread).
- Deploying functions publishes them: only with the user's explicit go-ahead in chat (Task 8).
- Phone release ordering (spec): no phone release in this phase; merging to `main` is fine.
- American spelling ("color"). Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Commands are PowerShell from the repository root.
- Gradle needs `$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"`; the Firestore emulator also needs `$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"`.

## Review Focus

- **A watch unlinked on the phone while it is in use.** Its next request gets `401`; the watch forgets the token, keeps every finished match queued and shows "Link again" — no crash, no lost match. If the unlink lands between the token lookup and the `lastSeenAt` write, the server answers `401`, not `500`. Pinned by `onGamesWith401ForgetsTheTokenAndKeepsGames` (Task 6) and `a device unlinked during the request is refused` (Task 1).
- **Imported games with gaps.** Calendar imports can lack colors, durations, field and venue, or have no start time. Games without a start are simply not listed; missing fields fall back to defaults (white/red, 45/15) and never cause a `500`. Pinned by `missing fields fall back to defaults` and `games without a start time are left out` (Task 1).
- **Uploading over a scheduled game.** The phone's schedule data (venue, notes, game number, age group, team names, kick-off time) survives a result upload. Pinned by `a result keeps the game's schedule fields` (Task 2).
- **The same match uploaded twice** (the reply was lost after the server saved it). The second upload rewrites the same document; no duplicate. Pinned by `uploading the same match again is idempotent` (Task 2).
- **Long or non-English team names.** Accents and emoji are truncated by character, never split into broken text. Pinned by `team names are cut by character, not code unit` (Task 1).

---

## File Structure

```
functions/
  index.js                     + garminGames, garminUploadGame exports
  garmin/devices.js            NEW: authenticateDevice (token → uid, lastSeenAt)
  garmin/games.js              NEW: listGamesForWatch, cleanUpload, uploadGame
  garmin/http.js               + bearerToken, handleGamesRequest, handleUploadRequest
  test/games.test.js           NEW: emulator tests for devices.js and games.js
  test/http.test.js            + tests for the two new handlers
common/src/test/java/com/databelay/refwatch/common/
  GarminEventsTest.kt          NEW: the watch's events decode as GameEvents
garmin/
  resources/strings/strings.xml   + sync strings
  resources/strings/version.xml   0.4.0
  source/sync/Api.mc           + getJson, token on postJson
  source/sync/Pairing.mc       postJson call passes no token
  source/sync/GameCache.mc     NEW: downloaded games in storage
  source/sync/UploadQueue.mc   NEW: finished match → upload body
  source/sync/Sync.mc          NEW: one download-then-upload pass, status
  source/model/MatchStore.mc   + finishedMatch, removeFinished; MAX_FINISHED 10
  source/util/Format.mc        + hourMinute, startTime
  source/ui/GameList.mc        synced games, Sync item, rebuild after a sync
  source/ui/MatchDelegate.mc   full time: drop the game from the cache, sync
  source/ui/LinkFlow.mc        sync once linked
  source/RefWatchApp.mc        sync at start and after a Garmin Connect link
  test/GameCacheTest.mc        NEW
  test/UploadQueueTest.mc      NEW
  test/SyncTest.mc             NEW
  test/MatchStoreTest.mc       + finishedMatch / removeFinished tests
  README.md                    + Phase 4 section
docs/superpowers/specs/2026-10-07-garmin-app-design.md   rulings from this phase
```

---

### Task 1: Device authentication and the games list (functions)

**Files:**
- Create: `functions/garmin/devices.js`, `functions/garmin/games.js`, `functions/test/games.test.js`

**Interfaces:**
- Consumes: `pairing.DEVICES` (`"garminDevices"`), `tokens.sha256Hex(text)` from phase 3.
- Produces (`functions/garmin/devices.js`): `authenticateDevice(db, token, nowMs) → Promise<?string>` (the uid, or `null`), `LAST_SEEN_INTERVAL_MS`.
- Produces (`functions/garmin/games.js`): `listGamesForWatch(db, uid, nowMs) → Promise<Array<object>>` (watch-shaped games), constants `MAX_GAMES = 12`, `PAST_WINDOW_MS`, `AHEAD_WINDOW_MS`, helper `gamesOf(db, uid)` (the `users/{uid}/games` collection). Task 2 adds `uploadGame` to the same file.

- [ ] **Step 1: Write the failing tests**

Create `functions/test/games.test.js`:

```js
const test = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const devices = require("../garmin/devices");
const games = require("../garmin/games");
const {DEVICES} = require("../garmin/pairing");
const {sha256Hex} = require("../garmin/tokens");

initializeApp({projectId: "demo-refwatch"});
const db = getFirestore();
const T0 = Date.UTC(2026, 9, 10, 12, 0, 0);
const MIN = 60 * 1000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;

const clearFirestore = async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  const url = `http://${host}/emulator/v1/projects/demo-refwatch` +
    "/databases/(default)/documents";
  await fetch(url, {method: "DELETE"});
};

test.beforeEach(clearFirestore);

const linkDevice = async (token, uid, lastSeenAt) => {
  await db.collection(DEVICES).doc(sha256Hex(token)).set({
    uid,
    deviceName: "006-B2604-00",
    createdAt: Timestamp.fromMillis(T0 - DAY),
    lastSeenAt: Timestamp.fromMillis(lastSeenAt),
  });
};

const addGame = (uid, id, fields) =>
  games.gamesOf(db, uid).doc(id).set(Object.assign({
    id,
    userId: uid,
    homeTeamName: "Eagles U12",
    awayTeamName: "Hawks U12",
    homeTeamColorArgb: -65536,
    awayTeamColorArgb: -16776961,
    halfDurationMinutes: 30,
    halftimeDurationMinutes: 5,
    currentPhase: "NOT_STARTED",
    gameDateTimeEpochMillis: T0 + DAY,
  }, fields));

test("a linked token gives its uid", async () => {
  await linkDevice("tok", "alice", T0);
  assert.equal(await devices.authenticateDevice(db, "tok", T0), "alice");
});

test("unknown, empty and non-string tokens give null", async () => {
  await linkDevice("tok", "alice", T0);
  assert.equal(await devices.authenticateDevice(db, "other", T0), null);
  assert.equal(await devices.authenticateDevice(db, "", T0), null);
  assert.equal(await devices.authenticateDevice(db, null, T0), null);
  assert.equal(await devices.authenticateDevice(db, 42, T0), null);
});

test("lastSeenAt is refreshed at most once an hour", async () => {
  await linkDevice("tok", "alice", T0);
  const ref = db.collection(DEVICES).doc(sha256Hex("tok"));
  await devices.authenticateDevice(db, "tok", T0 + 30 * MIN);
  assert.equal((await ref.get()).get("lastSeenAt").toMillis(), T0);
  await devices.authenticateDevice(db, "tok", T0 + HOUR);
  assert.equal((await ref.get()).get("lastSeenAt").toMillis(), T0 + HOUR);
});

test("a device unlinked during the request is refused", async () => {
  await linkDevice("tok", "alice", T0);
  const ref = db.collection(DEVICES).doc(sha256Hex("tok"));
  // The phone unlinks between the lookup and the lastSeenAt write.
  const racingDb = {
    collection: (name) => {
      const real = db.collection(name);
      return {
        doc: (id) => {
          const docRef = real.doc(id);
          return {
            get: async () => {
              const snapshot = await docRef.get();
              await ref.delete();
              return snapshot;
            },
            update: (data) => docRef.update(data),
          };
        },
      };
    },
  };
  assert.equal(
      await devices.authenticateDevice(racingDb, "tok", T0 + 2 * HOUR), null);
});

test("a game is sent in the watch's short form", async () => {
  await addGame("alice", "g1", {
    fieldNumber: "Field 3",
    refereeAssignment: "Center",
    ageGroup: "U12",
  });
  const list = await games.listGamesForWatch(db, "alice", T0);
  assert.deepEqual(list, [{
    id: "g1",
    t: Math.floor((T0 + DAY) / 1000),
    h: "Eagles U12",
    a: "Hawks U12",
    hc: 0xFF0000,
    ac: 0x0000FF,
    hd: 30,
    ht: 5,
    f: "Field 3",
    r: "Center",
    ag: "U12",
  }]);
});

test("venue stands in for a missing field number", async () => {
  await addGame("alice", "g1", {venue: "West Park"});
  const [game] = await games.listGamesForWatch(db, "alice", T0);
  assert.equal(game.f, "West Park");
  assert.equal("r" in game, false);
  assert.equal("ag" in game, false);
});

test("missing fields fall back to defaults", async () => {
  await games.gamesOf(db, "alice").doc("bare").set({
    currentPhase: "NOT_STARTED",
    gameDateTimeEpochMillis: T0 + HOUR,
    halfDurationMinutes: "thirty",
    homeTeamColorArgb: null,
  });
  const [game] = await games.listGamesForWatch(db, "alice", T0);
  assert.deepEqual(game, {
    id: "bare",
    t: Math.floor((T0 + HOUR) / 1000),
    h: "Home",
    a: "Away",
    hc: 0xFFFFFF,
    ac: 0xFF0000,
    hd: 45,
    ht: 15,
  });
});

test("games without a start time are left out", async () => {
  await addGame("alice", "noStart", {gameDateTimeEpochMillis: null});
  await addGame("alice", "ok", {});
  const list = await games.listGamesForWatch(db, "alice", T0);
  assert.deepEqual(list.map((g) => g.id), ["ok"]);
});

test("only games from 6 hours ago to 14 days ahead are sent", async () => {
  await addGame("alice", "tooOld", {gameDateTimeEpochMillis: T0 - 6 * HOUR - 1});
  await addGame("alice", "oldest", {gameDateTimeEpochMillis: T0 - 6 * HOUR});
  await addGame("alice", "latest", {gameDateTimeEpochMillis: T0 + 14 * DAY});
  await addGame("alice", "tooFar",
      {gameDateTimeEpochMillis: T0 + 14 * DAY + 1});
  const list = await games.listGamesForWatch(db, "alice", T0);
  assert.deepEqual(list.map((g) => g.id), ["oldest", "latest"]);
});

test("ended games and other users' games are not sent", async () => {
  await addGame("alice", "ended", {currentPhase: "GAME_ENDED"});
  await addGame("bob", "bobs", {});
  await addGame("alice", "mine", {});
  const list = await games.listGamesForWatch(db, "alice", T0);
  assert.deepEqual(list.map((g) => g.id), ["mine"]);
});

test("at most 12 games, soonest first", async () => {
  for (let i = 14; i >= 0; i--) {
    await addGame("alice", `g${i}`,
        {gameDateTimeEpochMillis: T0 + (i + 1) * HOUR});
  }
  const list = await games.listGamesForWatch(db, "alice", T0);
  assert.equal(list.length, games.MAX_GAMES);
  assert.deepEqual(list.slice(0, 3).map((g) => g.id), ["g0", "g1", "g2"]);
});

test("team names are cut by character, not code unit", async () => {
  const long = "Équipe ⚽ des Étoiles Filantes du Nord";
  await addGame("alice", "g1", {homeTeamName: long, awayTeamName: "😀".repeat(30)});
  const [game] = await games.listGamesForWatch(db, "alice", T0);
  assert.equal(game.h, Array.from(long).slice(0, 24).join(""));
  assert.equal(Array.from(game.a).length, 24);
  assert.equal(game.a, "😀".repeat(24));
});

test("12 games with every field at its longest stay under 3.6 KB", async () => {
  for (let i = 0; i < 12; i++) {
    await addGame("alice", `0123456789abcdef0123456789abcdef-${i}`, {
      homeTeamName: "H".repeat(40),
      awayTeamName: "A".repeat(40),
      fieldNumber: "F".repeat(60),
      refereeAssignment: "R".repeat(40),
      ageGroup: "GENERIC_ADULT",
      gameDateTimeEpochMillis: T0 + (i + 1) * HOUR,
    });
  }
  const list = await games.listGamesForWatch(db, "alice", T0);
  const bytes = Buffer.byteLength(JSON.stringify({games: list}));
  assert.ok(bytes < 3600, `response is ${bytes} bytes`);
});
```

- [ ] **Step 2: Run the tests to make sure they fail**

Run (from `functions/`): `$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"; npm test`
Expected: FAIL with `Cannot find module '../garmin/devices'`.

- [ ] **Step 3: Write `functions/garmin/devices.js`**

```js
const {Timestamp} = require("firebase-admin/firestore");
const tokens = require("./tokens");
const {DEVICES} = require("./pairing");

/** How often a watch's lastSeenAt is written, to limit writes. */
const LAST_SEEN_INTERVAL_MS = 60 * 60 * 1000;
/** Tokens are 43 characters; anything far longer is not one of ours. */
const MAX_TOKEN_LENGTH = 256;

/**
 * The account a watch's bearer token belongs to. Refreshes the device's
 * lastSeenAt when the last refresh is an hour old or more.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {*} token the bearer token the watch sent
 * @param {number} nowMs current time, epoch ms
 * @return {Promise<?string>} the uid, or null for an unknown token
 */
async function authenticateDevice(db, token, nowMs) {
  if (typeof token !== "string" || token === "" ||
    token.length > MAX_TOKEN_LENGTH) {
    return null;
  }
  const ref = db.collection(DEVICES).doc(tokens.sha256Hex(token));
  const doc = await ref.get();
  if (!doc.exists) {
    return null;
  }
  if (nowMs - doc.get("lastSeenAt").toMillis() >= LAST_SEEN_INTERVAL_MS) {
    try {
      await ref.update({lastSeenAt: Timestamp.fromMillis(nowMs)});
    } catch (err) {
      // Unlinked between the read and this write: the token is no longer
      // valid. Firestore's NOT_FOUND is gRPC code 5.
      if (err.code === 5) {
        return null;
      }
      throw err;
    }
  }
  return doc.get("uid");
}

module.exports = {LAST_SEEN_INTERVAL_MS, authenticateDevice};
```

- [ ] **Step 4: Write `functions/garmin/games.js`**

```js
const MAX_GAMES = 12;
const PAST_WINDOW_MS = 6 * 60 * 60 * 1000;
const AHEAD_WINDOW_MS = 14 * 24 * 60 * 60 * 1000;
const MAX_TEAM_NAME = 24;
const MAX_FIELD = 32;
const MAX_ASSIGNMENT = 16;
// The phone's defaults (common/.../theme/Color.kt and Game.kt).
const DEFAULT_HOME_RGB = 0xFFFFFF;
const DEFAULT_AWAY_RGB = 0xFF0000;
const DEFAULT_HALF_MINUTES = 45;
const DEFAULT_HALFTIME_MINUTES = 15;
const MAX_MINUTES = 120;

/**
 * The user's games collection.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {string} uid the account
 * @return {FirebaseFirestore.CollectionReference} users/{uid}/games
 */
function gamesOf(db, uid) {
  return db.collection("users").doc(uid).collection("games");
}

/**
 * Trimmed text cut to a number of characters (not UTF-16 code units, so an
 * emoji or accent is never split).
 * @param {*} value a stored field
 * @param {number} max the longest result, in characters
 * @return {?string} the text, or null when there is none
 */
function shortText(value, max) {
  if (typeof value !== "string" || value.trim() === "") {
    return null;
  }
  return Array.from(value.trim()).slice(0, max).join("");
}

/**
 * An ARGB color as RGB, or the fallback when it is not a whole number.
 * @param {*} argb the stored color
 * @param {number} fallback RGB to use instead
 * @return {number} 0..0xFFFFFF
 */
function rgb(argb, fallback) {
  return Number.isInteger(argb) ? (argb & 0xFFFFFF) : fallback;
}

/**
 * A period length in minutes, or the fallback when it is not a sensible one.
 * @param {*} value the stored length
 * @param {number} fallback minutes to use instead
 * @return {number} minutes
 */
function minutes(value, fallback) {
  return Number.isInteger(value) && value >= 1 && value <= MAX_MINUTES ?
    value : fallback;
}

/**
 * One game as the watch receives it: short keys, optional ones left out.
 * @param {FirebaseFirestore.DocumentSnapshot} doc a game document
 * @return {object} the watch's form of the game
 */
function toWatchGame(doc) {
  const game = {
    id: doc.id,
    t: Math.floor(doc.get("gameDateTimeEpochMillis") / 1000),
    h: shortText(doc.get("homeTeamName"), MAX_TEAM_NAME) || "Home",
    a: shortText(doc.get("awayTeamName"), MAX_TEAM_NAME) || "Away",
    hc: rgb(doc.get("homeTeamColorArgb"), DEFAULT_HOME_RGB),
    ac: rgb(doc.get("awayTeamColorArgb"), DEFAULT_AWAY_RGB),
    hd: minutes(doc.get("halfDurationMinutes"), DEFAULT_HALF_MINUTES),
    ht: minutes(doc.get("halftimeDurationMinutes"), DEFAULT_HALFTIME_MINUTES),
  };
  const field = shortText(doc.get("fieldNumber"), MAX_FIELD) ||
    shortText(doc.get("venue"), MAX_FIELD);
  if (field) {
    game.f = field;
  }
  const assignment = shortText(doc.get("refereeAssignment"), MAX_ASSIGNMENT);
  if (assignment) {
    game.r = assignment;
  }
  const ageGroup = doc.get("ageGroup");
  if (typeof ageGroup === "string" && /^[A-Z0-9_]{1,20}$/.test(ageGroup)) {
    game.ag = ageGroup;
  }
  return game;
}

/**
 * The games a watch should offer: starting from 6 hours ago to 14 days
 * ahead, not ended, soonest first, at most MAX_GAMES. A game without a
 * numeric start time is never in the range, so it is left out.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {string} uid the watch's account
 * @param {number} nowMs current time, epoch ms
 * @return {Promise<Array<object>>} games in the watch's form
 */
async function listGamesForWatch(db, uid, nowMs) {
  const snapshot = await gamesOf(db, uid)
      .where("gameDateTimeEpochMillis", ">=", nowMs - PAST_WINDOW_MS)
      .where("gameDateTimeEpochMillis", "<=", nowMs + AHEAD_WINDOW_MS)
      .orderBy("gameDateTimeEpochMillis")
      .get();
  return snapshot.docs
      .filter((doc) => doc.get("currentPhase") !== "GAME_ENDED")
      .slice(0, MAX_GAMES)
      .map(toWatchGame);
}

module.exports = {
  MAX_GAMES,
  PAST_WINDOW_MS,
  AHEAD_WINDOW_MS,
  gamesOf,
  listGamesForWatch,
};
```

- [ ] **Step 5: Run the tests and lint**

Run (from `functions/`): `npm test; npm run lint`
Expected: all tests PASS (the phase 3 tests too); lint reports nothing.

- [ ] **Step 6: Commit**

```powershell
git add functions/garmin/devices.js functions/garmin/games.js functions/test/games.test.js
git commit -m "Authenticate Garmin watches by token and list their upcoming games

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Uploading a finished match (functions)

**Files:**
- Modify: `functions/garmin/games.js`
- Test: `functions/test/games.test.js`

**Interfaces:**
- Consumes: `gamesOf(db, uid)` from Task 1.
- Produces (`functions/garmin/games.js`): `cleanUpload(body) → ?object` (the validated, whitelisted game, or `null`), `uploadGame(db, uid, body, nowMs) → Promise<{status: 200} | {status: 400}>`, `MAX_EVENTS = 200`.

- [ ] **Step 1: Write the failing tests**

Append to `functions/test/games.test.js`:

```js
const upload = (fields) => Object.assign({
  id: "3f2c9a10-0b1e-4c7d-9a55-6e2f1d0c8b7a",
  homeTeamName: "Home",
  awayTeamName: "Away",
  homeTeamColorArgb: -65536,
  awayTeamColorArgb: -16755201,
  halfDurationMinutes: 30,
  halftimeDurationMinutes: 5,
  kickOffTeam: "AWAY",
  currentPhase: "GAME_ENDED",
  homeScore: 1,
  awayScore: 0,
  gameDateTimeEpochMillis: T0 + 0.25,
  events: [
    {eventType: "PHASE_CHANGE", id: "e1", newPhase: "FIRST_HALF",
      timestamp: T0, gameTimeMillis: 0},
    {eventType: "GOAL", id: "e2", team: "HOME", timestamp: T0 + 754000.5,
      gameTimeMillis: 754000.5, homeScoreAtTime: 1, awayScoreAtTime: 0,
      playerNumber: 9},
    {eventType: "CARD", id: "e3", team: "AWAY", playerNumber: 4,
      cardType: "YELLOW", timestamp: T0 + 900000, gameTimeMillis: 900000},
    {eventType: "GOAL", id: "e4", team: "HOME", timestamp: T0 + 950000,
      gameTimeMillis: 950000, homeScoreAtTime: 2, awayScoreAtTime: 0},
    {eventType: "PHASE_CHANGE", id: "e5", newPhase: "GAME_ENDED",
      timestamp: T0 + 1800000, gameTimeMillis: 1800000},
  ],
}, fields);

test("a quick match creates a game", async () => {
  const body = upload({});
  assert.deepEqual(
      await games.uploadGame(db, "alice", body, T0 + DAY), {status: 200});
  const doc = await games.gamesOf(db, "alice").doc(body.id).get();
  assert.deepEqual(doc.data(), {
    id: body.id,
    userId: "alice",
    homeTeamName: "Home",
    awayTeamName: "Away",
    gameDateTimeEpochMillis: T0,
    homeTeamColorArgb: -65536,
    awayTeamColorArgb: -16755201,
    halfDurationMinutes: 30,
    halftimeDurationMinutes: 5,
    kickOffTeam: "AWAY",
    currentPhase: "GAME_ENDED",
    homeScore: 1,
    awayScore: 0,
    events: body.events,
    lastUpdated: T0 + DAY,
  });
});

test("a result keeps the game's schedule fields", async () => {
  await addGame("alice", "sched", {
    venue: "West Park",
    notes: "Bring flags",
    gameNumber: "1234",
    ageGroup: "U12",
    fieldNumber: "3",
  });
  const body = upload({id: "sched", homeTeamName: "Renamed",
    gameDateTimeEpochMillis: T0 + 5 * HOUR});
  assert.deepEqual(
      await games.uploadGame(db, "alice", body, T0 + DAY), {status: 200});
  const doc = (await games.gamesOf(db, "alice").doc("sched").get()).data();
  assert.equal(doc.venue, "West Park");
  assert.equal(doc.notes, "Bring flags");
  assert.equal(doc.gameNumber, "1234");
  assert.equal(doc.ageGroup, "U12");
  assert.equal(doc.fieldNumber, "3");
  assert.equal(doc.homeTeamName, "Eagles U12");
  assert.equal(doc.gameDateTimeEpochMillis, T0 + DAY);
  assert.equal(doc.currentPhase, "GAME_ENDED");
  assert.equal(doc.homeScore, 1);
  assert.equal(doc.kickOffTeam, "AWAY");
  assert.equal(doc.events.length, 5);
  assert.equal(doc.lastUpdated, T0 + DAY);
});

test("uploading the same match again is idempotent", async () => {
  const body = upload({});
  await games.uploadGame(db, "alice", body, T0 + DAY);
  await games.uploadGame(db, "alice", body, T0 + DAY + MIN);
  const all = await games.gamesOf(db, "alice").get();
  assert.equal(all.size, 1);
  assert.equal(all.docs[0].get("events").length, 5);
  assert.equal(all.docs[0].get("lastUpdated"), T0 + DAY + MIN);
});

test("a goal without a scorer is stored without playerNumber", async () => {
  const body = upload({});
  await games.uploadGame(db, "alice", body, T0);
  const doc = await games.gamesOf(db, "alice").doc(body.id).get();
  assert.equal("playerNumber" in doc.get("events")[3], false);
});

test("unknown keys are dropped from the game and its events", async () => {
  const body = upload({source: "garmin", notes: "x"});
  body.events[0].extra = "y";
  await games.uploadGame(db, "alice", body, T0);
  const doc = (await games.gamesOf(db, "alice").doc(body.id).get()).data();
  assert.equal("source" in doc, false);
  assert.equal("notes" in doc, false);
  assert.equal("extra" in doc.events[0], false);
});

test("malformed uploads are refused", async () => {
  const goal = (fields) => upload({events: [Object.assign({
    eventType: "GOAL", id: "g", team: "HOME", timestamp: T0,
    gameTimeMillis: 0, homeScoreAtTime: 1, awayScoreAtTime: 0,
  }, fields)]});
  const bad = [
    null,
    "text",
    [],
    upload({id: "a/b"}),
    upload({id: ""}),
    upload({id: "x".repeat(129)}),
    upload({homeTeamName: ""}),
    upload({awayTeamName: 7}),
    upload({homeTeamColorArgb: 1.5}),
    upload({halfDurationMinutes: 0}),
    upload({halftimeDurationMinutes: 121}),
    upload({kickOffTeam: "NOBODY"}),
    upload({currentPhase: "FIRST_HALF"}),
    upload({homeScore: -1}),
    upload({gameDateTimeEpochMillis: "soon"}),
    upload({events: "none"}),
    upload({events: new Array(201).fill(upload({}).events[0])}),
    goal({eventType: "PENALTY"}),
    goal({team: "away"}),
    goal({timestamp: "now"}),
    goal({gameTimeMillis: -1}),
    goal({id: 5}),
    goal({playerNumber: "9"}),
    goal({homeScoreAtTime: null}),
    upload({events: [{eventType: "CARD", id: "c", team: "HOME",
      cardType: "GREEN", playerNumber: 4, timestamp: T0, gameTimeMillis: 0}]}),
    upload({events: [{eventType: "CARD", id: "c", team: "HOME",
      cardType: "RED", timestamp: T0, gameTimeMillis: 0}]}),
    upload({events: [{eventType: "PHASE_CHANGE", id: "p",
      newPhase: "OVERTIME", timestamp: T0, gameTimeMillis: 0}]}),
  ];
  for (const body of bad) {
    assert.deepEqual(await games.uploadGame(db, "alice", body, T0),
        {status: 400}, JSON.stringify(body).slice(0, 120));
  }
  assert.equal((await games.gamesOf(db, "alice").get()).size, 0);
});

test("the largest allowed match is accepted", async () => {
  const events = [];
  for (let i = 0; i < games.MAX_EVENTS; i++) {
    events.push({eventType: "CARD", id: `c${i}`, team: "HOME",
      playerNumber: i % 100, cardType: "YELLOW", timestamp: T0 + i,
      gameTimeMillis: i});
  }
  assert.deepEqual(await games.uploadGame(db, "alice", upload({events}), T0),
      {status: 200});
});
```

- [ ] **Step 2: Run the tests to make sure they fail**

Run (from `functions/`): `npm test`
Expected: the new tests FAIL with `games.uploadGame is not a function`.

- [ ] **Step 3: Add the upload logic to `functions/garmin/games.js`**

Insert before `module.exports`:

```js
const MAX_EVENTS = 200;
const MAX_NAME = 64;
const MAX_ID = 128;
const MAX_SCORE = 999;
// Firestore document ids may not contain "/"; the watch sends UUIDs and the
// phone's ids are UUIDs or "ics-<hex>".
const ID_PATTERN = /^[A-Za-z0-9_-]+$/;
const TEAMS = new Set(["HOME", "AWAY"]);
const CARD_TYPES = new Set(["YELLOW", "RED"]);
// GamePhase in common/.../DataModels.kt.
const PHASES = new Set([
  "NOT_STARTED", "PRE_GAME", "KICK_OFF_SELECTION_FIRST_HALF", "FIRST_HALF",
  "HALF_TIME", "SECOND_HALF", "KICK_OFF_SELECTION_EXTRA_TIME",
  "EXTRA_TIME_FIRST_HALF", "EXTRA_TIME_HALF_TIME", "EXTRA_TIME_SECOND_HALF",
  "KICK_OFF_SELECTION_PENALTIES", "PENALTIES", "GAME_ENDED",
]);

/**
 * True for a plain object (not null, not an array).
 * @param {*} value anything from the request body
 * @return {boolean} whether it is an object
 */
function isObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

/**
 * True for a whole number within a range.
 * @param {*} value anything from the request body
 * @param {number} min smallest allowed
 * @param {number} max largest allowed
 * @return {boolean} whether it is in range
 */
function isInt(value, min, max) {
  return Number.isInteger(value) && value >= min && value <= max;
}

/**
 * True for a finite, non-negative number (a time in ms; fractions allowed).
 * @param {*} value anything from the request body
 * @return {boolean} whether it is a usable time
 */
function isTime(value) {
  return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

/**
 * True for an id usable as a Firestore document id.
 * @param {*} value anything from the request body
 * @return {boolean} whether it is a usable id
 */
function isId(value) {
  return typeof value === "string" && value.length >= 1 &&
    value.length <= MAX_ID && ID_PATTERN.test(value);
}

/**
 * One event, checked against the phone's GameEvent shape and stripped to
 * the keys the phone reads.
 * @param {*} event one entry of the upload's events
 * @return {?object} the clean event, or null if it is malformed
 */
function cleanEvent(event) {
  if (!isObject(event) || typeof event.id !== "string" ||
    event.id === "" || event.id.length > MAX_ID ||
    !isTime(event.timestamp) || !isTime(event.gameTimeMillis)) {
    return null;
  }
  const clean = {
    eventType: event.eventType,
    id: event.id,
    timestamp: event.timestamp,
    gameTimeMillis: event.gameTimeMillis,
  };
  if (event.eventType === "GOAL") {
    if (!TEAMS.has(event.team) ||
      !isInt(event.homeScoreAtTime, 0, MAX_SCORE) ||
      !isInt(event.awayScoreAtTime, 0, MAX_SCORE)) {
      return null;
    }
    const scorer = event.playerNumber;
    if (scorer !== undefined && scorer !== null &&
      !isInt(scorer, 0, MAX_SCORE)) {
      return null;
    }
    clean.team = event.team;
    clean.homeScoreAtTime = event.homeScoreAtTime;
    clean.awayScoreAtTime = event.awayScoreAtTime;
    if (isInt(scorer, 0, MAX_SCORE)) {
      clean.playerNumber = scorer;
    }
    return clean;
  }
  if (event.eventType === "CARD") {
    if (!TEAMS.has(event.team) || !CARD_TYPES.has(event.cardType) ||
      !isInt(event.playerNumber, 0, MAX_SCORE)) {
      return null;
    }
    clean.team = event.team;
    clean.playerNumber = event.playerNumber;
    clean.cardType = event.cardType;
    return clean;
  }
  if (event.eventType === "PHASE_CHANGE") {
    if (!PHASES.has(event.newPhase)) {
      return null;
    }
    clean.newPhase = event.newPhase;
    return clean;
  }
  return null;
}

/**
 * A team name from the watch: trimmed, capped, never empty.
 * @param {*} value what the watch sent
 * @return {?string} the name, or null if it is not usable
 */
function cleanName(value) {
  if (typeof value !== "string" || value.trim() === "") {
    return null;
  }
  return Array.from(value.trim()).slice(0, MAX_NAME).join("");
}

/**
 * The upload body checked and reduced to the fields the server writes.
 * Only finished matches are uploaded, so currentPhase must be GAME_ENDED.
 * @param {*} body the parsed JSON body
 * @return {?object} the clean game, or null if anything is malformed
 */
function cleanUpload(body) {
  if (!isObject(body) || !isId(body.id) ||
    !isInt(body.homeTeamColorArgb, -2147483648, 2147483647) ||
    !isInt(body.awayTeamColorArgb, -2147483648, 2147483647) ||
    !isInt(body.halfDurationMinutes, 1, MAX_MINUTES) ||
    !isInt(body.halftimeDurationMinutes, 1, MAX_MINUTES) ||
    !TEAMS.has(body.kickOffTeam) ||
    body.currentPhase !== "GAME_ENDED" ||
    !isInt(body.homeScore, 0, MAX_SCORE) ||
    !isInt(body.awayScore, 0, MAX_SCORE) ||
    !isTime(body.gameDateTimeEpochMillis) ||
    !Array.isArray(body.events) || body.events.length > MAX_EVENTS) {
    return null;
  }
  const homeTeamName = cleanName(body.homeTeamName);
  const awayTeamName = cleanName(body.awayTeamName);
  const events = body.events.map(cleanEvent);
  if (homeTeamName === null || awayTeamName === null ||
    events.some((event) => event === null)) {
    return null;
  }
  return {
    id: body.id,
    homeTeamName,
    awayTeamName,
    // The watch sends it as a double; the phone reads a whole Long.
    gameDateTimeEpochMillis: Math.floor(body.gameDateTimeEpochMillis),
    homeTeamColorArgb: body.homeTeamColorArgb,
    awayTeamColorArgb: body.awayTeamColorArgb,
    halfDurationMinutes: body.halfDurationMinutes,
    halftimeDurationMinutes: body.halftimeDurationMinutes,
    kickOffTeam: body.kickOffTeam,
    currentPhase: body.currentPhase,
    homeScore: body.homeScore,
    awayScore: body.awayScore,
    events,
  };
}

/**
 * Saves a finished match from a watch into the account's games. An existing
 * game (one from the phone's schedule, or an earlier upload) gets only the
 * match result; a new one (a quick match) is created with its teams and
 * kick-off time as well. Uploading the same match again rewrites the same
 * fields.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {string} uid the watch's account
 * @param {*} body the parsed JSON body
 * @param {number} nowMs current time, epoch ms
 * @return {Promise<object>} {status: 200} or {status: 400}
 */
async function uploadGame(db, uid, body, nowMs) {
  const game = cleanUpload(body);
  if (game === null) {
    return {status: 400};
  }
  const result = {
    homeTeamColorArgb: game.homeTeamColorArgb,
    awayTeamColorArgb: game.awayTeamColorArgb,
    halfDurationMinutes: game.halfDurationMinutes,
    halftimeDurationMinutes: game.halftimeDurationMinutes,
    kickOffTeam: game.kickOffTeam,
    currentPhase: game.currentPhase,
    homeScore: game.homeScore,
    awayScore: game.awayScore,
    events: game.events,
    lastUpdated: nowMs,
  };
  const ref = gamesOf(db, uid).doc(game.id);
  await db.runTransaction(async (tx) => {
    const existing = await tx.get(ref);
    if (existing.exists) {
      tx.update(ref, result);
    } else {
      tx.set(ref, Object.assign({
        id: game.id,
        userId: uid,
        homeTeamName: game.homeTeamName,
        awayTeamName: game.awayTeamName,
        gameDateTimeEpochMillis: game.gameDateTimeEpochMillis,
      }, result));
    }
  });
  return {status: 200};
}
```

Add `MAX_EVENTS`, `cleanUpload` and `uploadGame` to `module.exports`.

- [ ] **Step 4: Run the tests and lint**

Run (from `functions/`): `npm test; npm run lint`
Expected: all PASS; lint clean.

- [ ] **Step 5: Commit**

```powershell
git add functions/garmin/games.js functions/test/games.test.js
git commit -m "Save finished Garmin matches into the account's games

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The two HTTP endpoints (functions)

**Files:**
- Modify: `functions/garmin/http.js`, `functions/index.js`
- Test: `functions/test/http.test.js`

**Interfaces:**
- Consumes: `authenticateDevice` (Task 1), `listGamesForWatch` (Task 1), `uploadGame` (Task 2).
- Produces (`functions/garmin/http.js`): `bearerToken(req) → ?string`, `handleGamesRequest(db, req, res, nowMs, deps?)`, `handleUploadRequest(db, req, res, nowMs, deps?)`, `MAX_UPLOAD_BYTES = 65536`. `deps` is `{authenticateDevice, listGamesForWatch}` or `{authenticateDevice, uploadGame}` (tests pass fakes).
- Produces (`functions/index.js`): exports `garminGames`, `garminUploadGame` (HTTP, default region `us-central1`).

- [ ] **Step 1: Write the failing tests**

Change the `require` line at the top of `functions/test/http.test.js` to:

```js
const {
  handlePairRequest, clientIp, bearerToken,
  handleGamesRequest, handleUploadRequest, MAX_UPLOAD_BYTES,
} = require("../garmin/http");
```

Append:

```js
const fakeGames = (uid, outcome) => {
  const calls = [];
  return {
    calls,
    authenticateDevice: async (db, token, nowMs) => {
      calls.push({fn: "auth", token, nowMs});
      return uid;
    },
    listGamesForWatch: async (db, gotUid, nowMs) => {
      calls.push({fn: "list", uid: gotUid, nowMs});
      if (outcome instanceof Error) {
        throw outcome;
      }
      return outcome;
    },
    uploadGame: async (db, gotUid, body, nowMs) => {
      calls.push({fn: "upload", uid: gotUid, body, nowMs});
      if (outcome instanceof Error) {
        throw outcome;
      }
      return outcome;
    },
  };
};

const withToken = (token) => ({authorization: `Bearer ${token}`});

test("bearerToken reads the Authorization header", () => {
  assert.equal(bearerToken({headers: withToken("abc")}), "abc");
  assert.equal(bearerToken({headers: {authorization: "bearer  abc "}}),
      "abc");
  assert.equal(bearerToken({headers: {authorization: "Basic abc"}}), null);
  assert.equal(bearerToken({headers: {authorization: "Bearer a b"}}), null);
  assert.equal(bearerToken({headers: {}}), null);
  assert.equal(bearerToken({}), null);
});

test("garminGames accepts only GET", async () => {
  const res = fakeResponse();
  const deps = fakeGames("alice", []);
  await handleGamesRequest(null, {method: "POST", headers: withToken("t")},
      res, 5, deps);
  assert.equal(res.statusCode, 405);
  assert.deepEqual(res.body, {error: "method_not_allowed"});
  assert.equal(deps.calls.length, 0);
});

test("garminGames refuses an unknown token", async () => {
  const res = fakeResponse();
  const deps = fakeGames(null, []);
  await handleGamesRequest(null, {method: "GET", headers: withToken("t")},
      res, 5, deps);
  assert.equal(res.statusCode, 401);
  assert.deepEqual(res.body, {error: "unauthorized"});
  assert.deepEqual(deps.calls.map((c) => c.fn), ["auth"]);
});

test("garminGames returns the token owner's games", async () => {
  const res = fakeResponse();
  const deps = fakeGames("alice", [{id: "g1"}]);
  await handleGamesRequest(null, {method: "GET", headers: withToken("tok")},
      res, 5, deps);
  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.body, {games: [{id: "g1"}]});
  assert.deepEqual(deps.calls, [
    {fn: "auth", token: "tok", nowMs: 5},
    {fn: "list", uid: "alice", nowMs: 5},
  ]);
});

test("garminGames answers 500 when Firestore fails", async () => {
  const res = fakeResponse();
  const deps = fakeGames("alice", new Error("boom"));
  await handleGamesRequest(null, {method: "GET", headers: withToken("t")},
      res, 5, deps);
  assert.equal(res.statusCode, 500);
  assert.deepEqual(res.body, {error: "internal"});
});

test("garminUploadGame accepts only POST", async () => {
  const res = fakeResponse();
  const deps = fakeGames("alice", {status: 200});
  await handleUploadRequest(null, {method: "GET", headers: withToken("t")},
      res, 5, deps);
  assert.equal(res.statusCode, 405);
  assert.equal(deps.calls.length, 0);
});

test("garminUploadGame refuses an unknown token before reading the body",
    async () => {
      const res = fakeResponse();
      const deps = fakeGames(null, {status: 200});
      await handleUploadRequest(null,
          {method: "POST", headers: withToken("t"), body: {id: "x"}},
          res, 5, deps);
      assert.equal(res.statusCode, 401);
      assert.deepEqual(deps.calls.map((c) => c.fn), ["auth"]);
    });

test("garminUploadGame refuses a body over 64 KB", async () => {
  const res = fakeResponse();
  const deps = fakeGames("alice", {status: 200});
  await handleUploadRequest(null, {
    method: "POST",
    headers: withToken("t"),
    rawBody: Buffer.alloc(MAX_UPLOAD_BYTES + 1),
    body: {},
  }, res, 5, deps);
  assert.equal(res.statusCode, 400);
  assert.deepEqual(res.body, {error: "bad_request"});
  assert.equal(deps.calls.some((c) => c.fn === "upload"), false);
});

test("garminUploadGame passes the body on and reports the result",
    async () => {
      const ok = fakeResponse();
      const okDeps = fakeGames("alice", {status: 200});
      const body = {id: "m1"};
      await handleUploadRequest(null, {method: "POST",
        headers: withToken("t"), rawBody: Buffer.from("{}"), body},
      ok, 5, okDeps);
      assert.equal(ok.statusCode, 200);
      assert.deepEqual(ok.body, {ok: true});
      assert.deepEqual(okDeps.calls[1],
          {fn: "upload", uid: "alice", body, nowMs: 5});

      const bad = fakeResponse();
      await handleUploadRequest(null, {method: "POST",
        headers: withToken("t"), body}, bad, 5,
      fakeGames("alice", {status: 400}));
      assert.equal(bad.statusCode, 400);
      assert.deepEqual(bad.body, {error: "bad_request"});
    });

test("garminUploadGame answers 500 when Firestore fails", async () => {
  const res = fakeResponse();
  await handleUploadRequest(null, {method: "POST", headers: withToken("t"),
    body: {}}, res, 5, fakeGames("alice", new Error("boom")));
  assert.equal(res.statusCode, 500);
  assert.deepEqual(res.body, {error: "internal"});
});
```

- [ ] **Step 2: Run the tests to make sure they fail**

Run (from `functions/`): `npm test`
Expected: FAIL — `bearerToken is not a function`.

- [ ] **Step 3: Add the handlers to `functions/garmin/http.js`**

At the top, below `const pairing = require("./pairing");`:

```js
const devices = require("./devices");
const games = require("./games");

/** The largest upload body accepted, in bytes. */
const MAX_UPLOAD_BYTES = 64 * 1024;
```

Add `401: "unauthorized",` to `ERROR_NAMES`. Then, before `module.exports`:

```js
/**
 * The bearer token from the Authorization header.
 * @param {object} req the HTTP request
 * @return {?string} the token, or null when there is none
 */
function bearerToken(req) {
  const header = (req.headers || {})["authorization"];
  if (typeof header !== "string") {
    return null;
  }
  const match = /^Bearer\s+(\S+)$/i.exec(header.trim());
  return match ? match[1] : null;
}

/**
 * Sends an error reply with its JSON name.
 * @param {object} res the HTTP response
 * @param {number} status 400, 401, 405 or 500
 */
function replyError(res, status) {
  const names = Object.assign({405: "method_not_allowed", 500: "internal"},
      ERROR_NAMES);
  res.status(status).json({error: names[status]});
}

/**
 * Handles GET garminGames from a watch: its account's upcoming games.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {object} req the HTTP request
 * @param {object} res the HTTP response
 * @param {number} nowMs current time, epoch ms
 * @param {object} deps authenticateDevice and listGamesForWatch (tests
 *     pass fakes)
 * @return {Promise<void>} resolves once the reply is sent
 */
async function handleGamesRequest(db, req, res, nowMs, deps = {
  authenticateDevice: devices.authenticateDevice,
  listGamesForWatch: games.listGamesForWatch,
}) {
  if (req.method !== "GET") {
    replyError(res, 405);
    return;
  }
  try {
    const uid = await deps.authenticateDevice(db, bearerToken(req), nowMs);
    if (uid === null) {
      replyError(res, 401);
      return;
    }
    res.status(200).json({
      games: await deps.listGamesForWatch(db, uid, nowMs),
    });
  } catch (err) {
    console.error("garminGames failed", err);
    replyError(res, 500);
  }
}

/**
 * Handles POST garminUploadGame from a watch: one finished match. The token
 * is checked before the body is looked at.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {object} req the HTTP request (parsed JSON body, rawBody)
 * @param {object} res the HTTP response
 * @param {number} nowMs current time, epoch ms
 * @param {object} deps authenticateDevice and uploadGame (tests pass fakes)
 * @return {Promise<void>} resolves once the reply is sent
 */
async function handleUploadRequest(db, req, res, nowMs, deps = {
  authenticateDevice: devices.authenticateDevice,
  uploadGame: games.uploadGame,
}) {
  if (req.method !== "POST") {
    replyError(res, 405);
    return;
  }
  try {
    const uid = await deps.authenticateDevice(db, bearerToken(req), nowMs);
    if (uid === null) {
      replyError(res, 401);
      return;
    }
    if (req.rawBody && req.rawBody.length > MAX_UPLOAD_BYTES) {
      replyError(res, 400);
      return;
    }
    const result = await deps.uploadGame(db, uid, req.body, nowMs);
    if (result.status === 200) {
      res.status(200).json({ok: true});
    } else {
      replyError(res, 400);
    }
  } catch (err) {
    // Never log the request: it carries the token and the match.
    console.error("garminUploadGame failed", err);
    replyError(res, 500);
  }
}
```

Change `module.exports` to:

```js
module.exports = {
  MAX_UPLOAD_BYTES,
  clientIp,
  bearerToken,
  handlePairRequest,
  handleGamesRequest,
  handleUploadRequest,
};
```

- [ ] **Step 4: Export the endpoints from `functions/index.js`**

Change the `http` require to:

```js
const {
  handlePairRequest, handleGamesRequest, handleUploadRequest,
} = require("./garmin/http");
```

After the `garminPair` export add:

```js
// Watch: the account's upcoming games (bearer token from garminPair).
exports.garminGames = onRequest((req, res) =>
  handleGamesRequest(getFirestore(), req, res, Date.now()));

// Watch: one finished match, saved into the account's games.
exports.garminUploadGame = onRequest((req, res) =>
  handleUploadRequest(getFirestore(), req, res, Date.now()));
```

- [ ] **Step 5: Run the tests and lint**

Run (from `functions/`): `npm test; npm run lint`
Expected: all PASS; lint clean.

- [ ] **Step 6: Commit**

```powershell
git add functions/garmin/http.js functions/index.js functions/test/http.test.js
git commit -m "Add the garminGames and garminUploadGame endpoints for Garmin watches

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The phone reads the watch's events (cross-check)

**Files:**
- Create: `common/src/test/java/com/databelay/refwatch/common/GarminEventsTest.kt`

**Interfaces:**
- Consumes: `mapToJsonObject(map)`, `AppJsonConfiguration` (`common/.../JSONHelpers.kt`) — the exact path `parseGameEventsFromDocument` uses on a Firestore event map. The maps below are what `garminUploadGame` stores: Firestore hands whole numbers back as `Long` and fractions as `Double`.
- Produces: nothing for later tasks. The watch-side half of the contract is `MatchStateEventsTest.mc` (existing) plus `UploadQueueTest.mc` (Task 5); if either side's shape changes, change this test's maps with it.

- [ ] **Step 1: Write the test**

```kotlin
package com.databelay.refwatch.common

import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Events as a Garmin watch uploads them (garmin/source/model/MatchState.mc) and garminUploadGame
// stores them, read back the way the phone reads a game document. Firestore returns whole numbers
// as Long, so timestamps and clock times arrive as Long or Double depending on the value.
class GarminEventsTest {
    private val t0 = 1_791_500_460_000L

    private val stored: List<Map<String, Any?>> = listOf(
        mapOf("eventType" to "PHASE_CHANGE", "id" to "e1", "newPhase" to "FIRST_HALF",
            "timestamp" to t0, "gameTimeMillis" to 0L),
        mapOf("eventType" to "GOAL", "id" to "e2", "team" to "HOME", "timestamp" to t0 + 754_000L,
            "gameTimeMillis" to 754_000.5, "homeScoreAtTime" to 1L, "awayScoreAtTime" to 0L,
            "playerNumber" to 9L),
        mapOf("eventType" to "GOAL", "id" to "e3", "team" to "AWAY", "timestamp" to t0 + 800_000.25,
            "gameTimeMillis" to 800_000L, "homeScoreAtTime" to 1L, "awayScoreAtTime" to 1L),
        mapOf("eventType" to "CARD", "id" to "e4", "team" to "AWAY", "playerNumber" to 4L,
            "cardType" to "YELLOW", "timestamp" to t0 + 900_000L, "gameTimeMillis" to 900_000L),
        mapOf("eventType" to "PHASE_CHANGE", "id" to "e5", "newPhase" to "GAME_ENDED",
            "timestamp" to t0 + 1_800_000L, "gameTimeMillis" to 1_800_000L),
    )

    private fun decode(map: Map<String, Any?>): GameEvent =
        AppJsonConfiguration.decodeFromJsonElement(mapToJsonObject(map))

    @Test
    fun everyEventAGarminWatchUploadsDecodes() {
        val events = stored.map(::decode)

        val kickOff = events[0] as PhaseChangedEvent
        assertEquals(GamePhase.FIRST_HALF, kickOff.newPhase)
        assertEquals(t0.toDouble(), kickOff.timestamp, 0.0)

        val goal = events[1] as GoalScoredEvent
        assertEquals(Team.HOME, goal.team)
        assertEquals(9, goal.playerNumber)
        assertEquals(754_000.5, goal.gameTimeMillis, 0.0)
        assertEquals(1, goal.homeScoreAtTime)

        val unscored = events[2] as GoalScoredEvent
        assertNull(unscored.playerNumber)
        assertEquals(1, unscored.awayScoreAtTime)

        val card = events[3] as CardIssuedEvent
        assertEquals(CardType.YELLOW, card.cardType)
        assertEquals(4, card.playerNumber)
        assertEquals(Team.AWAY, card.team)

        assertEquals(GamePhase.GAME_ENDED, (events[4] as PhaseChangedEvent).newPhase)
    }
}
```

- [ ] **Step 2: Run it**

Run: `$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"; ./gradlew :common:testDebugUnitTest --tests "com.databelay.refwatch.common.GarminEventsTest"`
Expected: PASS. (This pins existing behavior, so it passes at once. If it fails, the watch's event shape and the phone's parser disagree: stop and report — do not change `MatchState`'s event keys or the phone's parser in this plan without the user.)

- [ ] **Step 3: Commit**

```powershell
git add common/src/test/java/com/databelay/refwatch/common/GarminEventsTest.kt
git commit -m "Pin that the phone decodes the events a Garmin watch uploads

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Game cache and upload body on the watch

**Files:**
- Create: `garmin/source/sync/GameCache.mc`, `garmin/source/sync/UploadQueue.mc`, `garmin/test/GameCacheTest.mc`, `garmin/test/UploadQueueTest.mc`
- Modify: `garmin/source/model/MatchStore.mc`, `garmin/test/MatchStoreTest.mc`, `garmin/source/util/Format.mc`

**Interfaces:**
- Consumes: `MatchState` dictionary keys (`toDict`), `Settings.recordActivity()`, `TEAM_HOME`.
- Produces:
  - `GameCache.parse(data as Dictionary or String or Null) as Array<Dictionary> or Null`
  - `GameCache.isGame(g as Dictionary) as Boolean`
  - `GameCache.store(games as Array<Dictionary>, nowSec as Number) as Void`
  - `GameCache.games(nowSec as Number) as Array<Dictionary>` (valid, not older than 6 h after start, server order)
  - `GameCache.find(id as String) as Dictionary or Null`, `GameCache.remove(id as String) as Void`
  - `GameCache.syncedAt() as Number or Null`, `GameCache.startSec(g as Dictionary) as Number`
  - `GameCache.setupFor(g as Dictionary) as Dictionary` (a `MatchState` set-up)
  - `UploadQueue.body(match as Dictionary) as Dictionary`, `UploadQueue.argb(rgb as Number) as Number`
  - `MatchStore.finishedMatch(id as String) as Dictionary or Null`, `MatchStore.removeFinished(id as String) as Void`, `MatchStore.MAX_FINISHED = 10`
  - `Format.hourMinute(hour as Number, min as Number) as String`, `Format.startTime(epochSec as Number) as String` ("Sat 10:00")

- [ ] **Step 1: Write the failing tests**

Create `garmin/test/GameCacheTest.mc`:

```monkeyc
import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

(:debug)
function syncedGame(id as String, startSec as Number) as Dictionary {
    return {"id" => id, "t" => startSec, "h" => "Eagles U12", "a" => "Hawks U12",
            "hc" => 0xFF0000, "ac" => 0x0000FF, "hd" => 30, "ht" => 5, "f" => "Field 3"};
}

(:debug)
function wipeGameCache() as Void {
    Application.Storage.deleteValue(GameCache.GAMES_KEY);
    Application.Storage.deleteValue(GameCache.SYNCED_AT_KEY);
}

(:test)
function parseKeepsValidGamesAndSkipsMalformedOnes(logger as Logger) as Boolean {
    var noField = syncedGame("b", 2000);
    noField.remove("f");
    var games = GameCache.parse({"games" => [
        syncedGame("a", 1000),
        noField,
        {"id" => "c", "t" => "soon"},
        "not a game",
        {"id" => 5, "t" => 1000, "h" => "x", "a" => "y", "hc" => 0, "ac" => 0, "hd" => 1, "ht" => 1}
    ]});
    Test.assert(games != null);
    Test.assertEqual(2, (games as Array).size());
    Test.assertEqual("a", (games as Array<Dictionary>)[0]["id"]);
    Test.assertEqual("b", (games as Array<Dictionary>)[1]["id"]);
    return true;
}

(:test)
function parseRejectsAReplyThatIsNotAGameList(logger as Logger) as Boolean {
    Test.assert(GameCache.parse(null) == null);
    Test.assert(GameCache.parse("<html>") == null);
    Test.assert(GameCache.parse({"error" => "unauthorized"}) == null);
    Test.assert(GameCache.parse({"games" => "none"}) == null);
    Test.assertEqual(0, (GameCache.parse({"games" => []}) as Array).size());
    return true;
}

(:test)
function storedGamesSurviveAndOldOnesAreHidden(logger as Logger) as Boolean {
    wipeGameCache();
    var now = 1791500000;
    GameCache.store([syncedGame("old", now - 6 * 3600 - 1), syncedGame("recent", now - 6 * 3600),
                     syncedGame("soon", now + 3600)], now);
    var games = GameCache.games(now);
    Test.assertEqual(2, games.size());
    Test.assertEqual("recent", games[0]["id"]);
    Test.assertEqual("soon", games[1]["id"]);
    Test.assertEqual(now, GameCache.syncedAt());
    wipeGameCache();
    return true;
}

(:test)
function findAndRemoveWorkById(logger as Logger) as Boolean {
    wipeGameCache();
    var now = 1791500000;
    GameCache.store([syncedGame("a", now), syncedGame("b", now + 60)], now);
    Test.assertEqual("b", (GameCache.find("b") as Dictionary)["id"]);
    Test.assert(GameCache.find("zzz") == null);
    GameCache.remove("a");
    Test.assert(GameCache.find("a") == null);
    Test.assertEqual(1, GameCache.games(now).size());
    GameCache.remove("not there");
    Test.assertEqual(1, GameCache.games(now).size());
    wipeGameCache();
    return true;
}

(:test)
function emptyCacheHasNoGamesAndNoSyncTime(logger as Logger) as Boolean {
    wipeGameCache();
    Test.assertEqual(0, GameCache.games(1791500000).size());
    Test.assert(GameCache.syncedAt() == null);
    return true;
}

// A synced half length need not be on the set-up picker's 5-minute grid; it is used as sent.
(:test)
function setupForUsesTheGamesTeamsColorsAndLengths(logger as Logger) as Boolean {
    var game = syncedGame("g1", 1791500400);
    game["hd"] = 32;
    var setup = GameCache.setupFor(game);
    Test.assertEqual("g1", setup["id"]);
    Test.assertEqual("Eagles U12", setup["homeName"]);
    Test.assertEqual("Hawks U12", setup["awayName"]);
    Test.assertEqual(0xFF0000, setup["homeColor"]);
    Test.assertEqual(0x0000FF, setup["awayColor"]);
    Test.assertEqual(32, setup["halfMinutes"]);
    Test.assertEqual(5, setup["halftimeMinutes"]);
    Test.assertEqual(TEAM_HOME, setup["kickOffTeam"]);
    Test.assertEqual(1791500400000l, setup["scheduledStartMs"]);
    Test.assertEqual(Settings.recordActivity(), setup["recordActivity"]);
    var match = new MatchState(setup);
    Test.assertEqual(32l * 60000l, match.regulationMs());
    return true;
}
```

Create `garmin/test/UploadQueueTest.mc`:

```monkeyc
import Toybox.Lang;
import Toybox.Test;

(:test)
function argbAddsAnOpaqueAlpha(logger as Logger) as Boolean {
    Test.assertEqual(-65536, UploadQueue.argb(0xFF0000));        // 0xFFFF0000
    Test.assertEqual(-16755201, UploadQueue.argb(0x0055FF));     // 0xFF0055FF
    Test.assertEqual(-1, UploadQueue.argb(0xFFFFFF));
    Test.assertEqual(-16777216, UploadQueue.argb(0x000000));
    return true;
}

(:test)
function bodyCarriesTheFinishedMatch(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(1000000l);
    m.addGoal(TEAM_HOME, 9, 1060000l);
    m.addCard(TEAM_AWAY, 4, CARD_YELLOW, 1120000l);
    m.finishGame(1200000l);
    var body = UploadQueue.body(m.toDict());
    Test.assertEqual("test-game", body["id"]);
    Test.assertEqual("Eagles", body["homeTeamName"]);
    Test.assertEqual("Hawks", body["awayTeamName"]);
    Test.assertEqual(-65536, body["homeTeamColorArgb"]);
    Test.assertEqual(-16755201, body["awayTeamColorArgb"]);
    Test.assertEqual(30, body["halfDurationMinutes"]);
    Test.assertEqual(5, body["halftimeDurationMinutes"]);
    Test.assertEqual(TEAM_HOME, body["kickOffTeam"]);
    Test.assertEqual(PHASE_GAME_ENDED, body["currentPhase"]);
    Test.assertEqual(1, body["homeScore"]);
    Test.assertEqual(0, body["awayScore"]);
    // A quick match has no scheduled start: the kick-off time stands in. Sent as a Double.
    Test.assert(body["gameDateTimeEpochMillis"] instanceof Double);
    Test.assertEqual(1000000.0d, body["gameDateTimeEpochMillis"]);
    var events = body["events"] as Array<Dictionary>;
    Test.assertEqual(4, events.size());
    Test.assertEqual("GOAL", events[1]["eventType"]);
    Test.assertEqual(9, events[1]["playerNumber"]);
    Test.assert(!body.hasKey("source"));
    return true;
}

(:test)
function bodyPrefersTheScheduledStart(logger as Logger) as Boolean {
    var setup = GameCache.setupFor(syncedGame("g1", 1791500400));
    var m = new MatchState(setup);
    m.kickOff(1791500460000l);
    m.finishGame(1791502000000l);
    var body = UploadQueue.body(m.toDict());
    Test.assertEqual("g1", body["id"]);
    Test.assertEqual(1791500400000.0d, body["gameDateTimeEpochMillis"]);
    return true;
}
```

Append to `garmin/test/MatchStoreTest.mc`:

```monkeyc
(:test)
function finishedMatchReadsOneStoredMatch(logger as Logger) as Boolean {
    wipeFinished();
    var m = testMatch();
    m.gameId = "one";
    MatchStore.archive(m);
    Test.assertEqual("one", (MatchStore.finishedMatch("one") as Dictionary)["id"]);
    Test.assert(MatchStore.finishedMatch("other") == null);
    wipeFinished();
    return true;
}

(:test)
function removeFinishedDropsTheMatchAndItsKey(logger as Logger) as Boolean {
    wipeFinished();
    var a = testMatch();
    a.gameId = "a";
    MatchStore.archive(a);
    var b = testMatch();
    b.gameId = "b";
    MatchStore.archive(b);
    MatchStore.removeFinished("a");
    Test.assertEqual(1, MatchStore.finishedIndex().size());
    Test.assertEqual("b", MatchStore.finishedIndex()[0]);
    Test.assert(Application.Storage.getValue(MatchStore.FINISHED_PREFIX + "a") == null);
    MatchStore.removeFinished("missing");
    Test.assertEqual(1, MatchStore.finishedIndex().size());
    wipeFinished();
    return true;
}
```

(`wipeFinished` already exists in that file; check that it deletes every `fin_` key it may meet and the index — it is written against `MatchStore.MAX_FINISHED`, so the new value of 10 needs no change there.)

- [ ] **Step 2: Run the tests to make sure they fail**

Run: `& .\garmin\build.ps1 -Test`
Expected: the build FAILS with `Undefined symbol ':GameCache'` (and `UploadQueue`, `finishedMatch`).

- [ ] **Step 3: Add `hourMinute` and `startTime` to `garmin/source/util/Format.mc`**

Add `import Toybox.Time;` and `import Toybox.Time.Gregorian;` at the top, and replace `timeOfDay` with:

```monkeyc
    function timeOfDay() as String {
        var t = System.getClockTime();
        return hourMinute(t.hour, t.min);
    }

    // "14:05", or "2:05" on a watch set to 12-hour time.
    function hourMinute(hour as Number, min as Number) as String {
        if (!System.getDeviceSettings().is24Hour) {
            hour = hour % 12;
            if (hour == 0) {
                hour = 12;
            }
        }
        return hour.format("%d") + ":" + min.format("%02d");
    }

    // "Sat 10:00": a game's start in the watch's time zone.
    function startTime(epochSec as Number) as String {
        var info = Gregorian.info(new Time.Moment(epochSec), Time.FORMAT_MEDIUM);
        return (info.day_of_week as String) + " " + hourMinute(info.hour as Number, info.min as Number);
    }
```

- [ ] **Step 4: Write `garmin/source/sync/GameCache.mc`**

```monkeyc
import Toybox.Application;
import Toybox.Lang;

// Upcoming games from garminGames, kept in storage so the list works at the field without the
// phone. Keys are the server's short ones (see the spec): t start in epoch seconds, h/a team
// names, hc/ac RGB colors, hd/ht half and break minutes, f field or venue (optional).
module GameCache {
    const GAMES_KEY = "games";
    const SYNCED_AT_KEY = "gamesSyncedAt";       // epoch seconds of the last download
    // The server's window: a game stays listed until 6 hours after its start.
    const KEEP_AFTER_START_S = 6 * 60 * 60;

    // The games in a garminGames reply, or null when the reply is not a game list. Malformed
    // games are skipped rather than failing the whole list.
    function parse(data as Dictionary or String or Null) as Array<Dictionary> or Null {
        if (!(data instanceof Dictionary)) {
            return null;
        }
        var list = (data as Dictionary)["games"];
        if (!(list instanceof Array)) {
            return null;
        }
        var valid = [] as Array<Dictionary>;
        for (var i = 0; i < (list as Array).size(); i++) {
            var g = (list as Array)[i];
            if (g instanceof Dictionary && isGame(g as Dictionary)) {
                valid.add(g as Dictionary);
            }
        }
        return valid;
    }

    // Monkey C casts are compile-time only, so every key is checked before it is used.
    function isGame(g as Dictionary) as Boolean {
        return g["id"] instanceof String
            && (g["t"] instanceof Number || g["t"] instanceof Long)
            && g["h"] instanceof String
            && g["a"] instanceof String
            && g["hc"] instanceof Number
            && g["ac"] instanceof Number
            && g["hd"] instanceof Number
            && g["ht"] instanceof Number
            && (g["f"] == null || g["f"] instanceof String);
    }

    function startSec(g as Dictionary) as Number {
        return (g["t"] as Number or Long).toNumber();
    }

    // A storage failure keeps the previous list; the next sync tries again.
    function store(games as Array<Dictionary>, nowSec as Number) as Void {
        try {
            Application.Storage.setValue(GAMES_KEY, games);
            Application.Storage.setValue(SYNCED_AT_KEY, nowSec);
        } catch (e) {
        }
    }

    // Cached games that have not been over for long, in the server's order (soonest first).
    function games(nowSec as Number) as Array<Dictionary> {
        var result = [] as Array<Dictionary>;
        var all = stored();
        for (var i = 0; i < all.size(); i++) {
            if (startSec(all[i]) + KEEP_AFTER_START_S >= nowSec) {
                result.add(all[i]);
            }
        }
        return result;
    }

    function find(id as String) as Dictionary or Null {
        var all = stored();
        for (var i = 0; i < all.size(); i++) {
            if ((all[i]["id"] as String).equals(id)) {
                return all[i];
            }
        }
        return null;
    }

    // A game refereed on this watch leaves the list at once, before the next sync confirms it.
    function remove(id as String) as Void {
        var kept = [] as Array<Dictionary>;
        var all = stored();
        for (var i = 0; i < all.size(); i++) {
            if (!(all[i]["id"] as String).equals(id)) {
                kept.add(all[i]);
            }
        }
        if (kept.size() != all.size()) {
            try {
                Application.Storage.setValue(GAMES_KEY, kept);
            } catch (e) {
            }
        }
    }

    function syncedAt() as Number or Null {
        var value = Application.Storage.getValue(SYNCED_AT_KEY);
        return value instanceof Number ? value as Number : null;
    }

    // The set-up screen's starting point for a synced game. The first half's kick-off team is
    // chosen there, as for a quick match.
    function setupFor(g as Dictionary) as Dictionary {
        return {
            "id" => g["id"] as String,
            "homeName" => g["h"] as String,
            "awayName" => g["a"] as String,
            "homeColor" => g["hc"] as Number,
            "awayColor" => g["ac"] as Number,
            "halfMinutes" => g["hd"] as Number,
            "halftimeMinutes" => g["ht"] as Number,
            "kickOffTeam" => TEAM_HOME,
            "scheduledStartMs" => startSec(g).toLong() * 1000l,
            "recordActivity" => Settings.recordActivity()
        };
    }

    // Module functions cannot be `hidden` in Monkey C (a compile error), so this one is public.
    function stored() as Array<Dictionary> {
        var value = Application.Storage.getValue(GAMES_KEY);
        var result = [] as Array<Dictionary>;
        if (value instanceof Array) {
            for (var i = 0; i < (value as Array).size(); i++) {
                var g = (value as Array)[i];
                if (g instanceof Dictionary && isGame(g as Dictionary)) {
                    result.add(g as Dictionary);
                }
            }
        }
        return result;
    }
}
```

- [ ] **Step 5: Write `garmin/source/sync/UploadQueue.mc`**

```monkeyc
import Toybox.Lang;

// The garminUploadGame body for a finished match (a MatchState.toDict dictionary). The queue
// itself is MatchStore's finished list: a match leaves it only when the server accepts it.
module UploadQueue {
    // 0xFF000000 does not fit a Monkey C Number literal; this is the same 32 bits.
    const OPAQUE_ALPHA = -16777216;

    function body(match as Dictionary) as Dictionary {
        // A synced game keeps its scheduled start; a quick match is dated by its kick-off.
        var start = match["scheduledStartMs"];
        if (start == null) {
            start = match["startedAtMs"];
        }
        var payload = {
            "id" => match["id"],
            "homeTeamName" => match["homeName"],
            "awayTeamName" => match["awayName"],
            "homeTeamColorArgb" => argb(match["homeColor"] as Number),
            "awayTeamColorArgb" => argb(match["awayColor"] as Number),
            "halfDurationMinutes" => match["halfMinutes"],
            "halftimeDurationMinutes" => match["halftimeMinutes"],
            "kickOffTeam" => match["kickOffTeam"],
            "currentPhase" => match["phase"],
            "homeScore" => match["homeScore"],
            "awayScore" => match["awayScore"],
            "events" => match["events"]
        };
        // Sent as a Double, like the event timestamps: how Connect IQ 3.1 encodes a Long in
        // JSON is not documented, and a Double holds epoch milliseconds exactly.
        if (start != null) {
            payload["gameDateTimeEpochMillis"] = (start as Long).toDouble();
        }
        return payload;
    }

    // The phone stores colors as ARGB Ints: opaque, so alpha is 0xFF.
    function argb(rgb as Number) as Number {
        return (rgb & 0xFFFFFF) | OPAQUE_ALPHA;
    }
}
```

- [ ] **Step 6: Update `garmin/source/model/MatchStore.mc`**

Change `MAX_FINISHED` to `10` and replace the module's top comment and `archive`'s comment as follows (the code of `archive` does not change):

```monkeyc
// Persists the match in progress after every change, so a crash or reboot resumes it, and keeps
// finished matches until they are uploaded (see Sync): the finished list is the upload queue.
```

```monkeyc
    // Moves the match to the finished list (the upload queue) and clears the current one. Each
    // finished match has its own storage key because one value is limited to about 32 KB and a
    // long match with many events can exceed that. Past MAX_FINISHED the oldest is dropped even
    // if it was never uploaded: storage is small, and ten matches without the phone in reach is
    // far beyond normal use.
    // A storage failure must never trap the referee on the full-time screen, so the current
    // match is cleared even when the archive write fails.
```

Add after `finished()`:

```monkeyc
    // One finished match, or null when it is not stored.
    function finishedMatch(id as String) as Dictionary or Null {
        var d = Application.Storage.getValue(FINISHED_PREFIX + id);
        return d instanceof Dictionary ? d as Dictionary : null;
    }

    // Called once the server has the match.
    function removeFinished(id as String) as Void {
        try {
            var index = finishedIndex();
            index.remove(id);
            Application.Storage.deleteValue(FINISHED_PREFIX + id);
            Application.Storage.setValue(FINISHED_KEY, index);
        } catch (e) {
        }
    }
```

- [ ] **Step 7: Run the tests**

Run: `& .\garmin\build.ps1 -Test`
Expected: every test PASSES (the earlier phases' tests too).

- [ ] **Step 8: Commit**

```powershell
git add garmin/source/sync/GameCache.mc garmin/source/sync/UploadQueue.mc garmin/source/model/MatchStore.mc garmin/source/util/Format.mc garmin/test/GameCacheTest.mc garmin/test/UploadQueueTest.mc garmin/test/MatchStoreTest.mc
git commit -m "Keep synced games and build match uploads on the Garmin watch

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The sync pass on the watch

**Files:**
- Create: `garmin/source/sync/Sync.mc`, `garmin/test/SyncTest.mc`
- Modify: `garmin/source/sync/Api.mc`, `garmin/source/sync/Pairing.mc`, `garmin/source/ui/GameList.mc` (two empty hooks only; Task 7 fills them)

**Interfaces:**
- Consumes: `GameCache.parse/store` (Task 5), `UploadQueue.body` (Task 5), `MatchStore.finishedIndex/finishedMatch/removeFinished` (Task 5), `Pairing.isLinked/token/forget` (phase 3).
- Produces:
  - constants `SYNC_OK = 0`, `SYNC_OFFLINE = 1`, `SYNC_UNLINKED = 2`, `SYNC_FAILED = 3`
  - `Sync.run() as Void` (no-op while running or when not linked), `Sync.isRunning() as Boolean`, `Sync.lastStatus as Number or Null` (null: no pass finished since the app started), `Sync.statusFor(responseCode as Number) as Number`, `Sync.finished(status as Number) as Void`
  - class `SyncJob`: `initialize(token as String)`, `start()`, `onGames(responseCode, data)`, `onUploaded(responseCode, data)`, `(:debug) setPending(ids as Array<String>)`
  - `Api.getJson(path as String, token as String, callback)`; `Api.postJson(path, body, token as String or Null, callback)` (new `token` parameter)
  - `GameList.refreshSyncItem() as Void`, `GameList.onSyncFinished() as Void` (stubs here, real in Task 7)

- [ ] **Step 1: Write the failing tests**

Create `garmin/test/SyncTest.mc`:

```monkeyc
import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

(:debug)
function archivedMatch(id as String) as Void {
    var m = testMatch();
    m.gameId = id;
    m.kickOff(1000l);
    m.finishGame(2000l);
    MatchStore.archive(m);
}

(:test)
function statusForMapsReplyCodes(logger as Logger) as Boolean {
    Test.assertEqual(SYNC_OK, Sync.statusFor(200));
    Test.assertEqual(SYNC_UNLINKED, Sync.statusFor(401));
    Test.assertEqual(SYNC_OFFLINE, Sync.statusFor(-104));      // BLE not connected
    Test.assertEqual(SYNC_OFFLINE, Sync.statusFor(-2));
    Test.assertEqual(SYNC_FAILED, Sync.statusFor(-400));       // reply could not be read
    Test.assertEqual(SYNC_FAILED, Sync.statusFor(500));
    Test.assertEqual(SYNC_FAILED, Sync.statusFor(404));
    return true;
}

(:test)
function runDoesNothingWhenNotLinked(logger as Logger) as Boolean {
    Pairing.forget();
    Sync.run();
    Test.assert(!Sync.isRunning());
    return true;
}

(:test)
function onGamesStoresTheListAndFinishesWithNothingToUpload(logger as Logger) as Boolean {
    wipeGameCache();
    wipeFinished();
    Application.Storage.setValue(Pairing.TOKEN_KEY, "tok");
    new SyncJob("tok").onGames(200, {"games" => [syncedGame("g1", 1791500400)]});
    Test.assertEqual(SYNC_OK, Sync.lastStatus);
    Test.assert(!Sync.isRunning());
    Test.assert(GameCache.find("g1") != null);
    Test.assert(Pairing.isLinked());
    wipeGameCache();
    Pairing.forget();
    return true;
}

(:test)
function onGamesWith401ForgetsTheTokenAndKeepsGames(logger as Logger) as Boolean {
    wipeGameCache();
    wipeFinished();
    GameCache.store([syncedGame("kept", 1791500400)], 1791500000);
    archivedMatch("queued");
    Application.Storage.setValue(Pairing.TOKEN_KEY, "tok");
    new SyncJob("tok").onGames(401, {"error" => "unauthorized"});
    Test.assertEqual(SYNC_UNLINKED, Sync.lastStatus);
    Test.assert(!Pairing.isLinked());
    Test.assert(GameCache.find("kept") != null);
    Test.assertEqual(1, MatchStore.finishedIndex().size());
    wipeGameCache();
    wipeFinished();
    return true;
}

(:test)
function onGamesOfflineOrUnreadableKeepsTheCache(logger as Logger) as Boolean {
    wipeGameCache();
    GameCache.store([syncedGame("kept", 1791500400)], 1791500000);
    new SyncJob("tok").onGames(-104, null);
    Test.assertEqual(SYNC_OFFLINE, Sync.lastStatus);
    new SyncJob("tok").onGames(200, "<html>");
    Test.assertEqual(SYNC_FAILED, Sync.lastStatus);
    Test.assert(GameCache.find("kept") != null);
    Test.assertEqual(1791500000, GameCache.syncedAt());
    wipeGameCache();
    return true;
}

(:test)
function anAcceptedUploadLeavesTheQueue(logger as Logger) as Boolean {
    wipeFinished();
    archivedMatch("m1");
    var job = new SyncJob("tok");
    job.setPending(["m1"]);
    job.onUploaded(200, {"ok" => true});
    Test.assertEqual(0, MatchStore.finishedIndex().size());
    Test.assertEqual(SYNC_OK, Sync.lastStatus);
    return true;
}

(:test)
function aRefusedOrFailedUploadStaysQueued(logger as Logger) as Boolean {
    wipeFinished();
    archivedMatch("m1");
    var job = new SyncJob("tok");
    job.setPending(["m1"]);
    job.onUploaded(400, {"error" => "bad_request"});
    Test.assertEqual(SYNC_FAILED, Sync.lastStatus);
    Test.assertEqual(1, MatchStore.finishedIndex().size());

    job = new SyncJob("tok");
    job.setPending(["m1"]);
    job.onUploaded(-104, null);
    Test.assertEqual(SYNC_OFFLINE, Sync.lastStatus);
    Test.assertEqual(1, MatchStore.finishedIndex().size());

    Application.Storage.setValue(Pairing.TOKEN_KEY, "tok");
    job = new SyncJob("tok");
    job.setPending(["m1"]);
    job.onUploaded(401, null);
    Test.assertEqual(SYNC_UNLINKED, Sync.lastStatus);
    Test.assert(!Pairing.isLinked());
    Test.assertEqual(1, MatchStore.finishedIndex().size());
    wipeFinished();
    return true;
}
```

(The tests call `onGames`/`onUploaded` directly with a queue that leaves nothing more to send, so no web request is made. `syncedGame`, `wipeGameCache` come from `GameCacheTest.mc`; `wipeFinished`, `testMatch` from the existing test files.)

- [ ] **Step 2: Run the tests to make sure they fail**

Run: `& .\garmin\build.ps1 -Test`
Expected: build FAILS with `Undefined symbol ':Sync'` / `':SyncJob'`.

- [ ] **Step 3: Extend `garmin/source/sync/Api.mc`**

Replace the module body with:

```monkeyc
module Api {
    const BASE_URL = "https://us-central1-refwatchapp.cloudfunctions.net/";

    // POSTs body as JSON, with the watch's token when it has one. The callback gets the HTTP
    // status (negative for a Connect IQ connection error) and the decoded JSON reply, if any.
    function postJson(path as String, body as Dictionary, token as String or Null,
                      callback as Method(responseCode as Number, data as Dictionary or String or Null) as Void) as Void {
        var headers = {"Content-Type" => Communications.REQUEST_CONTENT_TYPE_JSON};
        if (token != null) {
            headers["Authorization"] = "Bearer " + token;
        }
        Communications.makeWebRequest(BASE_URL + path, body, {
            :method => Communications.HTTP_REQUEST_METHOD_POST,
            :headers => headers,
            :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
        }, callback);
    }

    // GETs path with the watch's token; the callback is as for postJson.
    function getJson(path as String, token as String,
                     callback as Method(responseCode as Number, data as Dictionary or String or Null) as Void) as Void {
        Communications.makeWebRequest(BASE_URL + path, null, {
            :method => Communications.HTTP_REQUEST_METHOD_GET,
            :headers => {"Authorization" => "Bearer " + token},
            :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
        }, callback);
    }
}
```

In `garmin/source/sync/Pairing.mc`, change the call in `pair` to `Api.postJson("garminPair", requestBody(code), null, request.method(:onResponse));`.

- [ ] **Step 4: Write `garmin/source/sync/Sync.mc`**

```monkeyc
import Toybox.Lang;
import Toybox.Time;

const SYNC_OK = 0;
const SYNC_OFFLINE = 1;     // the phone or the network was not reachable
const SYNC_UNLINKED = 2;    // the token was refused (unlinked on the phone): link again
const SYNC_FAILED = 3;      // the server refused or answered something unreadable

// One pass: download the upcoming games, then upload finished matches one at a time. Runs at
// app start, after a match is saved, after linking, and from the start menu's Sync item. A
// match never waits for it, and a failed pass loses nothing: games stay cached and matches stay
// queued for the next pass.
module Sync {
    var _job as SyncJob or Null = null;
    var lastStatus as Number or Null = null;

    function run() as Void {
        if (_job != null || !Pairing.isLinked()) {
            return;
        }
        var job = new SyncJob(Pairing.token() as String);
        _job = job;
        GameList.refreshSyncItem();
        job.start();
    }

    function isRunning() as Boolean {
        return _job != null;
    }

    // Down to -399 Connect IQ could not reach the phone or the network; -400 and below a reply
    // arrived but could not be read.
    function statusFor(responseCode as Number) as Number {
        if (responseCode == 200) {
            return SYNC_OK;
        }
        if (responseCode == 401) {
            return SYNC_UNLINKED;
        }
        if (responseCode < 0 && responseCode > -400) {
            return SYNC_OFFLINE;
        }
        return SYNC_FAILED;
    }

    // A refused token is dropped, so Settings shows "Not linked"; queued matches stay and are
    // uploaded once the watch is linked again (to whichever account that is).
    function finished(status as Number) as Void {
        _job = null;
        lastStatus = status;
        if (status == SYNC_UNLINKED) {
            Pairing.forget();
        }
        GameList.onSyncFinished();
    }
}

// The state of one pass, kept alive by Sync._job while requests are in flight.
class SyncJob {
    hidden var _token as String;
    hidden var _pending as Array<String> = [] as Array<String>;   // finished match ids to send
    hidden var _rejected as Boolean = false;                       // a match was refused (400)

    function initialize(token as String) {
        _token = token;
    }

    function start() as Void {
        Api.getJson("garminGames", _token, method(:onGames));
    }

    function onGames(responseCode as Number, data as Dictionary or String or Null) as Void {
        var status = Sync.statusFor(responseCode);
        var games = status == SYNC_OK ? GameCache.parse(data) : null;
        if (status == SYNC_OK && games == null) {
            status = SYNC_FAILED;
        }
        if (status != SYNC_OK) {
            Sync.finished(status);
            return;
        }
        GameCache.store(games as Array<Dictionary>, Time.now().value());
        _pending = MatchStore.finishedIndex();
        uploadNext();
    }

    // 200: the server has it. 400: it never will (a malformed match); it stays stored but the
    // rest are still sent. Anything else ends the pass; everything unsent stays queued.
    function onUploaded(responseCode as Number, data as Dictionary or String or Null) as Void {
        var id = _pending[0];
        _pending = _pending.slice(1, null);
        if (responseCode == 200) {
            MatchStore.removeFinished(id);
        } else if (responseCode == 400) {
            _rejected = true;
        } else {
            Sync.finished(Sync.statusFor(responseCode));
            return;
        }
        uploadNext();
    }

    // Matches are read one at a time to keep memory low on older watches.
    hidden function uploadNext() as Void {
        while (_pending.size() > 0) {
            var match = MatchStore.finishedMatch(_pending[0]);
            if (match != null) {
                Api.postJson("garminUploadGame", UploadQueue.body(match), _token, method(:onUploaded));
                return;
            }
            _pending = _pending.slice(1, null);
        }
        Sync.finished(_rejected ? SYNC_FAILED : SYNC_OK);
    }

    (:debug)
    function setPending(ids as Array<String>) as Void {
        _pending = ids;
    }
}
```

- [ ] **Step 5: Add the two hooks to `garmin/source/ui/GameList.mc`**

Inside `module GameList`, add (Task 7 replaces them):

```monkeyc
    // Filled in with the start menu's sync status (Task 7).
    function refreshSyncItem() as Void {
    }

    function onSyncFinished() as Void {
    }
```

- [ ] **Step 6: Run the tests**

Run: `& .\garmin\build.ps1 -Test`
Expected: every test PASSES, `PairingTest` included.

- [ ] **Step 7: Commit**

```powershell
git add garmin/source/sync/Sync.mc garmin/source/sync/Api.mc garmin/source/sync/Pairing.mc garmin/source/ui/GameList.mc garmin/test/SyncTest.mc
git commit -m "Download games and upload finished matches in one sync pass on the Garmin watch

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Synced games in the start menu, and when sync runs

**Files:**
- Modify: `garmin/source/ui/GameList.mc`, `garmin/source/ui/MatchDelegate.mc`, `garmin/source/ui/LinkFlow.mc`, `garmin/source/RefWatchApp.mc`, `garmin/resources/strings/strings.xml`, `garmin/resources/strings/version.xml`

**Interfaces:**
- Consumes: `Sync.*` (Task 6), `GameCache.games/find/setupFor/syncedAt/startSec/remove` (Task 5), `Format.startTime` (Task 5), `MatchStore.finishedIndex`, `PreMatch.push(setup)`, `LinkFlow.start()`, `SettingsMenu.push()`.
- Produces: `GameList.build() as WatchUi.Menu2` (Quick match, synced games, Sync, Settings), `GameList.show()`, `GameList.refreshSyncItem()`, `GameList.onSyncFinished()`, `GameList.syncStatus() as String`; class `GameListMenu extends WatchUi.Menu2`.

- [ ] **Step 1: Add the strings**

In `garmin/resources/strings/strings.xml`, before `</strings>`:

```xml
    <string id="Sync">Sync</string>
    <string id="Syncing">Syncing…</string>
    <string id="SyncedAt">Synced $1$</string>
    <string id="NotSynced">Not synced</string>
    <string id="ToUpload">$1$ to upload</string>
    <string id="SyncFailed">Sync failed</string>
    <string id="LinkAgain">Link again</string>
    <string id="TeamsFormat">$1$ v $2$</string>
```

("No phone" reuses `OfflineMessage`; "Not linked" reuses `NotLinked`.) In `garmin/resources/strings/version.xml` set `AppVersion` to `0.4.0`.

- [ ] **Step 2: Replace `garmin/source/ui/GameList.mc`**

```monkeyc
import Toybox.Lang;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// The start menu: Quick match, the synced games (soonest first), Sync, Settings. After a sync
// it is rebuilt: at once if it is showing, otherwise as soon as it shows again.
module GameList {
    var _menu as GameListMenu or Null = null;   // the start menu while it is the top view
    var _stale as Boolean = false;              // a sync finished while it was hidden

    function build() as WatchUi.Menu2 {
        var menu = new GameListMenu();
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.QuickMatch, null, :quickMatch, null));
        var games = GameCache.games(Time.now().value());
        for (var i = 0; i < games.size(); i++) {
            var g = games[i];
            menu.addItem(new WatchUi.MenuItem(teams(g), when(g), g["id"] as String, null));
        }
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Sync, syncStatus(), :sync, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Settings, null, :settings, null));
        _stale = false;
        return menu;
    }

    // Makes the start menu the only view on the stack; the match, if any, is over.
    function show() as Void {
        Nav.stopTicker();
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
            "scheduledStartMs" => null,
            "recordActivity" => Settings.recordActivity()
        };
    }

    function onMenuShown(menu as GameListMenu) as Void {
        _menu = menu;
    }

    // ("hidden" is a Monkey C keyword, hence the names.)
    function onMenuHidden(menu as GameListMenu) as Void {
        if (_menu == menu) {
            _menu = null;
        }
    }

    function isShowing(menu as GameListMenu) as Boolean {
        return _menu == menu;
    }

    function isStale() as Boolean {
        return _stale;
    }

    // The Sync item's status line, updated in place (sync started).
    function refreshSyncItem() as Void {
        var menu = _menu;
        if (menu != null) {
            var index = menu.findItemById(:sync);
            if (index >= 0) {
                (menu.getItem(index) as WatchUi.MenuItem).setSubLabel(syncStatus());
            }
            WatchUi.requestUpdate();
        }
    }

    // The game list may have changed: rebuild the menu now if it is showing.
    function onSyncFinished() as Void {
        if (_menu != null) {
            show();
        } else {
            _stale = true;
        }
    }

    function syncStatus() as String {
        var id;
        if (Sync.isRunning()) {
            id = Rez.Strings.Syncing;
        } else if (!Pairing.isLinked()) {
            id = Sync.lastStatus == SYNC_UNLINKED ? Rez.Strings.LinkAgain : Rez.Strings.NotLinked;
        } else if (Sync.lastStatus == SYNC_OFFLINE) {
            id = Rez.Strings.OfflineMessage;
        } else if (Sync.lastStatus == SYNC_FAILED) {
            id = Rez.Strings.SyncFailed;
        } else {
            var waiting = MatchStore.finishedIndex().size();
            if (waiting > 0) {
                return Lang.format(WatchUi.loadResource(Rez.Strings.ToUpload) as String, [waiting]);
            }
            var at = GameCache.syncedAt();
            if (at == null) {
                id = Rez.Strings.NotSynced;
            } else {
                return Lang.format(WatchUi.loadResource(Rez.Strings.SyncedAt) as String,
                    [Format.startTime(at as Number)]);
            }
        }
        return WatchUi.loadResource(id) as String;
    }

    function teams(g as Dictionary) as String {
        return Lang.format(WatchUi.loadResource(Rez.Strings.TeamsFormat) as String, [g["h"], g["a"]]);
    }

    // "Sat 10:00 · Field 3"
    function when(g as Dictionary) as String {
        var text = Format.startTime(GameCache.startSec(g));
        if (g["f"] instanceof String) {
            text += " · " + (g["f"] as String);
        }
        return text;
    }
}

// The start menu view. It reports when it shows and hides so a finished sync knows whether it
// can rebuild it at once. switchToView is not called from inside onShow; a short one-shot timer
// defers the rebuild.
class GameListMenu extends WatchUi.Menu2 {
    hidden var _timer as Timer.Timer or Null = null;

    function initialize() {
        Menu2.initialize({:title => Rez.Strings.AppName});
    }

    function onShow() as Void {
        GameList.onMenuShown(self);
        if (GameList.isStale()) {
            var timer = new Timer.Timer();
            _timer = timer;
            timer.start(method(:rebuild), 50, false);
        }
    }

    function onHide() as Void {
        GameList.onMenuHidden(self);
    }

    function rebuild() as Void {
        _timer = null;
        if (GameList.isShowing(self)) {
            GameList.show();
        }
    }
}

class GameListDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id == :quickMatch) {
            PreMatch.push(GameList.quickMatchSetup());
        } else if (id == :sync) {
            if (Pairing.isLinked()) {
                Sync.run();
            } else {
                LinkFlow.start();
            }
        } else if (id == :settings) {
            SettingsMenu.push();
        } else if (id instanceof String) {
            var game = GameCache.find(id as String);
            if (game != null) {
                PreMatch.push(GameCache.setupFor(game));
            }
        }
    }
}
```

- [ ] **Step 3: Run sync at the right moments**

In `garmin/source/ui/MatchDelegate.mc`, `start()`, replace the full-time branch with:

```monkeyc
        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            Recorder.save();
            MatchStore.archive(_match);
            GameCache.remove(_match.gameId);
            // Before the menu is built, so its Sync line already says "Syncing…".
            Sync.run();
            GameList.show();
            return true;
        }
```

In `garmin/source/ui/LinkFlow.mc`, `LinkFlowSteps.onPaired`, add as its first statement:

```monkeyc
        if (status == PAIR_LINKED) {
            Sync.run();
        }
```

In `garmin/source/RefWatchApp.mc`: at the end of `onStart` add `Sync.run();` (a no-op when not linked), and in `onSettingsPaired` add after the `clearPairingCode` block:

```monkeyc
        if (status == PAIR_LINKED) {
            Sync.run();
        }
```

- [ ] **Step 4: Build and run the unit tests**

Run: `& .\garmin\build.ps1 -Test`
Expected: every test PASSES; no compile warnings about `GameListMenu`.

- [ ] **Step 5: Look at it in the simulator (fēnix 5X), not linked**

Run: `& .\garmin\build.ps1 -Device fenix5x -Run`, then drive it with `garmin/tools/sim.ps1` (see `garmin/README.md`, "Drive and screenshot the simulator") and screenshot:
- Start menu: Quick match, **Sync** with "Not linked" (or "Link again" / the link status the simulator's storage holds), Settings. Selecting Sync while not linked opens the code picker; BACK returns.
- Add a temporary `System.println("GameListMenu.onShow")` in `GameListMenu.onShow`, open Settings and go BACK, and confirm in the simulator console that it prints once per return to the menu. Remove the line afterwards. If it never prints, `Menu2` subclasses do not get `onShow` on this SDK: stop and report — the rebuild-when-stale path depends on it.

- [ ] **Step 6: Commit**

```powershell
git add garmin/source/ui/GameList.mc garmin/source/ui/MatchDelegate.mc garmin/source/ui/LinkFlow.mc garmin/source/RefWatchApp.mc garmin/resources/strings/strings.xml garmin/resources/strings/version.xml
git commit -m "List synced games on the Garmin watch and sync at start, after linking and after each match

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Deploy, end-to-end check, and records

**Files:**
- Modify: `garmin/README.md`, `docs/superpowers/specs/2026-10-07-garmin-app-design.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the deployed `garminGames` and `garminUploadGame`; a Phase 4 section in the README.

- [ ] **Step 1: Full test run**

Run (from `functions/`): `npm test; npm run lint`. Run (root): `& .\garmin\build.ps1 -Test`; `./gradlew :common:testDebugUnitTest`.
Expected: all PASS.

- [ ] **Step 2: Ask the user before deploying**

Deploying publishes two new public HTTPS endpoints on the live `refwatchapp` project. Stop and ask in chat; continue only after an explicit yes. If the Firebase CLI reports an expired login, the user runs `firebase login --reauth` themselves.

- [ ] **Step 3: Deploy**

Run (root): `firebase deploy --only "functions:garminGames,functions:garminUploadGame"`
Expected: both functions created in `us-central1`; lint passes in predeploy.

- [ ] **Step 4: Probe the live endpoints**

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" https://us-central1-refwatchapp.cloudfunctions.net/garminGames
curl.exe -s -w "`n%{http_code}`n" -H "Authorization: Bearer not-a-token" https://us-central1-refwatchapp.cloudfunctions.net/garminGames
curl.exe -s -w "`n%{http_code}`n" -X POST -H "Content-Type: application/json" -H "Authorization: Bearer not-a-token" -d "{}" https://us-central1-refwatchapp.cloudfunctions.net/garminUploadGame
curl.exe -s -w "`n%{http_code}`n" https://us-central1-refwatchapp.cloudfunctions.net/garminUploadGame
```

Expected: `401`; `{"error":"unauthorized"}` `401`; `{"error":"unauthorized"}` `401`; `{"error":"method_not_allowed"}` `405`.

- [ ] **Step 5: End to end in the simulator**

With the simulator watch linked to the user's account (Settings → Link account; a fresh code from the phone app if it shows "Not linked"):
1. Relaunch the app. The Sync line shows "Syncing…" and then "Synced <day time>"; the user's upcoming games (next 14 days) are listed with teams and "day time · field". Compare against the phone's Upcoming tab: same games, same order (at most 12).
2. Open one synced game: set-up shows its teams, colors and lengths.
3. Play a **quick match** briefly: kick off, a goal with scorer, a yellow card, then menu → Finish game → START (Save). The start menu shows "Syncing…", then "Synced …".
4. On the OnePlus 13 (`adb -s 4ec8f1c7`, after checking `ro.product.model` is `CPH2655`), the match appears under **Past** with the score, and its game log shows the goal (with scorer), the card and the phase changes at the right minutes. Its date is today at the kick-off time.
5. Play the **synced game** from step 2 the same way and check the phone: the game moves from Upcoming to Past with the result, and keeps its venue, field, notes and age group; it is gone from the watch's list after the save.
6. Unlink the simulator watch in the phone's Settings, finish another quick match on the watch: the Sync line ends as "Link again", Settings shows "Not linked", and the match is still queued ("1 to upload" once linked again). Link again: it uploads.

Record any mismatch as a finding before going on; do not patch around it.

- [ ] **Step 6: On the fēnix 5X**

Build and sideload as in `garmin/README.md` ("Sideload to a watch"): the user connects the watch by USB and confirms it shows as a drive. On the watch: the start menu lists the synced games (look at the 240 px rendering: team names cut cleanly, the sub-line readable), sync works through Garmin Connect on the phone, and a finished match reaches the phone's Past tab. Read the memory figure for the 5X build in the simulator (File → View Memory) with the full game list loaded; it must be under 765 kB.

- [ ] **Step 7: Records**

Append a "Phase 4 results" section to `garmin/README.md` in the style of the earlier phases: what was checked (Steps 4–6), the 5X memory figure, and anything found. In the spec, update:
- "Sync on the watch": `401` → the Sync item says "Link again" and linking is done in Settings → Link account (on the watch or in Garmin Connect); the not-synced indicator is the Sync item's status line ("No phone", "Sync failed", "N to upload"); the queue holds up to 10 finished matches and drops the oldest past that, uploaded or not.
- "Screens" 1: the start menu order is Quick match, synced games, Sync, Settings.
- "Uploading games": the `source` field is not sent or stored (ruling 2026-10-09).
- Build order step 4: done, with the date.

- [ ] **Step 8: Commit**

```powershell
git add garmin/README.md docs/superpowers/specs/2026-10-07-garmin-app-design.md
git commit -m "Record Garmin phase 4 game sync results

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
