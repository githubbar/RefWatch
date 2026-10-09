# Garmin Phase 3 — Pairing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A referee links their Garmin watch to their RefWatch account: the phone app shows a 6-digit code, the referee enters it on the watch (or in the Garmin Connect app), the watch receives its own access token, and the phone lists linked watches with an Unlink button.

**Architecture:** Four Cloud Functions in a new `functions/garmin/` module: three callables for the phone (`createGarminPairingCode`, `listGarminDevices`, `unlinkGarminDevice`) and one HTTP endpoint for the watch (`garminPair`). Their logic is plain async functions that take a Firestore handle, so they are tested against the Firestore emulator without the Functions emulator. The phone gets a `GarminLinkRepository` + `GarminLinkViewModel` + `GarminLinkSection` in Settings. The watch gets `sync/Api.mc` (web requests through Garmin Connect), `sync/Pairing.mc` (token storage, reply handling), a "Link account" start-menu item with a 6-digit code picker, and a `pairingCode` app setting for Garmin Connect.

**Tech Stack:** Node 22 Cloud Functions (firebase-functions v6, firebase-admin v12), Node's built-in test runner, Firebase Emulator Suite (Firestore); Kotlin, Jetpack Compose, Hilt, JUnit4 + Truth + kotlinx-coroutines-test; Monkey C, Connect IQ SDK 9.2.0, `minApiLevel` 3.1.0.

**Spec:** `docs/superpowers/specs/2026-10-07-garmin-app-design.md` — sections "Architecture", "Data model (Firestore)", "Pairing", "App settings", "Phone app", "Testing", build-order step 3.

## Global Constraints

- Firestore collections, exactly as the spec names them: `garminPairingCodes/{code}` (`uid`, `expiresAt` timestamp = created + 10 minutes), `garminDevices/{tokenHash}` (`uid`, `deviceName`, `createdAt`, `lastSeenAt`), `garminPairAttempts/{ipHash}` (`count`, `windowStart`). Only the functions (Admin SDK) touch them; `firestore.rules` already denies clients, only its header comment changes.
- `code` is 6 decimal digits, as a string (leading zeros kept). `tokenHash` is the hex SHA-256 of the bearer token. The token is 32 random bytes, base64url.
- `createGarminPairingCode` (callable, requires auth): in a transaction, delete the caller's existing codes, pick a random code not in use (an expired one counts as free), write it with a 10-minute expiry. Returns `{code, expiresAt}` (`expiresAt` in epoch milliseconds).
- `garminPair` (HTTP `POST`, JSON `{code, deviceName, appVersion}`): at most 10 failed attempts per caller IP per hour → `429`; malformed code → `400`; unknown or expired code → `404`; valid code → delete it, create the token, write `garminDevices/{sha256(token)}`, return `200 {token}`. Error replies are JSON `{error: "<name>"}`.
- `listGarminDevices` (callable, auth) → `{devices: [{id, deviceName, createdAt, lastSeenAt}]}` for the caller only, newest first; `id` is the token hash; times in epoch ms.
- `unlinkGarminDevice` (callable, auth, `{tokenHash}`): deletes only a device the caller owns; anything else → `not-found` (never reveals other users' devices).
- Tokens have no expiry other than Unlink.
- Watch: the referee can enter the code on the watch (start menu → "Link account") **and** in Garmin Connect (app setting `pairingCode`, string, default empty). (User decision 2026-10-09: on-watch entry added so pairing can be tested on a sideloaded watch, which Garmin Connect cannot configure.)
- Watch stores the token in `Application.Storage`; after a successful pair from Garmin Connect it clears the `pairingCode` property.
- Watch manifest gains the `Communications` permission. Base URL `https://us-central1-refwatchapp.cloudfunctions.net/` (project `refwatchapp`, default region).
- The fēnix 5X runs Connect IQ 3.1.9: no API newer than 3.1 without a `has` guard. Watch user-visible text goes in `garmin/resources/strings/strings.xml`. Look at rendered screens on the fēnix 5X (240 px) before calling a watch UI done.
- Functions code must pass `npm run lint` (eslint-config-google: 2-space indent, double quotes, max line length 80, JSDoc on every `function` declaration, trailing commas on multi-line literals). `firebase deploy` runs lint first.
- Deploying functions publishes them: only with the user's explicit go-ahead in chat (Task 7).
- Phone release ordering (spec): the phone app's Garmin section must not be released to Play before the Garmin app is in the Connect IQ store (phase 5). Merging to `main` is fine; tagging a phone release is not part of this plan.
- American spelling ("color"). Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Commands are PowerShell from the repository root.
- Gradle needs `$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"`. The Firestore emulator needs Java on `PATH` too: `$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"`.

## File Structure

```
functions/
  package.json                     + "test" script (Firestore emulator + node --test)
  index.js                         + four exports wiring the garmin module
  garmin/tokens.js                 NEW: code/token generation, hashing, code validation
  garmin/pairing.js                NEW: createPairingCode, pairDevice, listDevices, unlinkDevice
  garmin/http.js                   NEW: handlePairRequest (HTTP request → pairDevice → reply)
  test/pairing.test.js             NEW: emulator tests for pairing.js
  test/http.test.js                NEW: tests for http.js with a fake response
firestore.rules                    header comment names the Garmin collections
mobile/
  build.gradle.kts                 + testImplementation(libs.kotlinx.coroutines.test)
  src/main/java/com/databelay/refwatch/
    data/garmin/GarminLinkRepository.kt   NEW: interface, Firebase implementation, reply parsing
    data/garmin/GarminLinkViewModel.kt    NEW: code + countdown, linked devices, unlink
    di/RepositoryModule.kt                + binds GarminLinkRepository
    screens/GarminLinkSection.kt          NEW: the Settings section (stateful + stateless)
    screens/SettingsScreen.kt             + GarminLinkSection() under Preferences
  src/test/java/com/databelay/refwatch/data/garmin/
    GarminLinkParsingTest.kt              NEW
    GarminLinkViewModelTest.kt            NEW
docs/privacy-policy.md             + Garmin watch linking
garmin/
  manifest.xml                     + Communications permission
  resources/strings/strings.xml    + link strings
  resources/settings/properties.xml + pairingCode property and setting
  source/sync/Api.mc               NEW: JSON POST to the functions
  source/sync/Pairing.mc           NEW: token storage, reply → status, code validation
  source/model/NumberPickerModel.mc + digits()
  source/ui/Pickers.mc             + pushCode, smaller digits for more than 2 columns
  source/ui/LinkFlow.mc            NEW: Linking… / result screens
  source/ui/GameList.mc            + "Link account" item
  source/RefWatchApp.mc            + pair from the Garmin Connect setting
  test/PairingTest.mc              NEW
  test/NumberPickerModelTest.mc    + digits test
  README.md                        + Phase 3 section
```

---

### Task 1: Pairing logic in Cloud Functions

**Files:**
- Create: `functions/garmin/tokens.js`, `functions/garmin/pairing.js`, `functions/test/pairing.test.js`
- Modify: `functions/package.json` (scripts)

**Interfaces:**
- Produces (`functions/garmin/pairing.js`):
  - `createPairingCode(db, uid, nowMs, makeCode?) → Promise<{code: string, expiresAt: number}>`
  - `pairDevice(db, {code, deviceName, ip}, nowMs, makeToken?) → Promise<{status: 200, token: string} | {status: 400|404|429}>`
  - `listDevices(db, uid) → Promise<Array<{id, deviceName, createdAt, lastSeenAt}>>` (times in ms, newest first)
  - `unlinkDevice(db, uid, tokenHash) → Promise<boolean>`
  - constants `CODES`, `DEVICES`, `ATTEMPTS` (collection names), `MAX_FAILED_ATTEMPTS = 10`
- Produces (`functions/garmin/tokens.js`): `CODE_TTL_MS`, `newPairingCode()`, `newDeviceToken()`, `sha256Hex(text)`, `isPairingCode(value)`

- [ ] **Step 1: Add the test script**

In `functions/package.json`, add to `"scripts"`:

```json
    "test": "firebase emulators:exec --only firestore --project demo-refwatch \"node --test\"",
```

`demo-refwatch` is a demo project id: the emulator never touches the real `refwatchapp` project. `node --test` with no arguments runs `test/*.test.js`.

- [ ] **Step 2: Write the failing tests**

Create `functions/test/pairing.test.js`:

```js
const test = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const pairing = require("../garmin/pairing");
const {sha256Hex} = require("../garmin/tokens");

initializeApp({projectId: "demo-refwatch"});
const db = getFirestore();
const T0 = Date.UTC(2026, 9, 9, 12, 0, 0);
const MIN = 60 * 1000;

const clearFirestore = async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  const url = `http://${host}/emulator/v1/projects/demo-refwatch` +
    "/databases/(default)/documents";
  await fetch(url, {method: "DELETE"});
};

test.beforeEach(clearFirestore);

test("a new code is 6 digits and expires in 10 minutes", async () => {
  const result = await pairing.createPairingCode(db, "alice", T0);
  assert.match(result.code, /^\d{6}$/);
  assert.equal(result.expiresAt, T0 + 10 * MIN);
  const doc = await db.collection(pairing.CODES).doc(result.code).get();
  assert.equal(doc.get("uid"), "alice");
  assert.equal(doc.get("expiresAt").toMillis(), T0 + 10 * MIN);
});

test("a second code replaces the caller's first", async () => {
  const first = await pairing.createPairingCode(db, "alice", T0, () => "111111");
  const second = await pairing.createPairingCode(db, "alice", T0, () => "222222");
  assert.equal(first.code, "111111");
  assert.equal(second.code, "222222");
  const old = await db.collection(pairing.CODES).doc("111111").get();
  assert.equal(old.exists, false);
});

test("a code in use by someone else is skipped", async () => {
  await pairing.createPairingCode(db, "bob", T0, () => "333333");
  const codes = ["333333", "444444"];
  const result = await pairing.createPairingCode(
      db, "alice", T0, () => codes.shift());
  assert.equal(result.code, "444444");
  const bobs = await db.collection(pairing.CODES).doc("333333").get();
  assert.equal(bobs.get("uid"), "bob");
});

test("an expired code can be handed out again", async () => {
  await pairing.createPairingCode(db, "bob", T0, () => "555555");
  const later = T0 + 11 * MIN;
  const result = await pairing.createPairingCode(
      db, "alice", later, () => "555555");
  assert.equal(result.code, "555555");
});

test("pairing with a valid code returns a token and links the device",
    async () => {
      await pairing.createPairingCode(db, "alice", T0, () => "123456");
      const result = await pairing.pairDevice(db,
          {code: "123456", deviceName: "006-B2604-00", ip: "1.2.3.4"},
          T0 + MIN, () => "token-abc");
      assert.deepEqual(result, {status: 200, token: "token-abc"});
      const device = await db.collection(pairing.DEVICES)
          .doc(sha256Hex("token-abc")).get();
      assert.equal(device.get("uid"), "alice");
      assert.equal(device.get("deviceName"), "006-B2604-00");
      assert.equal(device.get("createdAt").toMillis(), T0 + MIN);
      assert.equal(device.get("lastSeenAt").toMillis(), T0 + MIN);
    });

test("a code works only once", async () => {
  await pairing.createPairingCode(db, "alice", T0, () => "123456");
  const input = {code: "123456", deviceName: "w", ip: "1.2.3.4"};
  await pairing.pairDevice(db, input, T0 + MIN);
  const again = await pairing.pairDevice(db, input, T0 + MIN);
  assert.deepEqual(again, {status: 404});
});

test("an expired code is refused", async () => {
  await pairing.createPairingCode(db, "alice", T0, () => "123456");
  const result = await pairing.pairDevice(db,
      {code: "123456", deviceName: "w", ip: "1.2.3.4"}, T0 + 10 * MIN);
  assert.deepEqual(result, {status: 404});
});

test("a malformed code is a bad request", async () => {
  for (const code of ["12345", "1234567", "12a456", 123456, undefined]) {
    const result = await pairing.pairDevice(db,
        {code, deviceName: "w", ip: "1.2.3.4"}, T0);
    assert.deepEqual(result, {status: 400}, `code ${code}`);
  }
});

test("ten failures in an hour block the caller, even with a good code",
    async () => {
      await pairing.createPairingCode(db, "alice", T0, () => "123456");
      for (let i = 0; i < pairing.MAX_FAILED_ATTEMPTS; i++) {
        await pairing.pairDevice(db,
            {code: "000000", deviceName: "w", ip: "9.9.9.9"}, T0 + i);
      }
      const blocked = await pairing.pairDevice(db,
          {code: "123456", deviceName: "w", ip: "9.9.9.9"}, T0 + MIN);
      assert.deepEqual(blocked, {status: 429});
      const otherIp = await pairing.pairDevice(db,
          {code: "123456", deviceName: "w", ip: "8.8.8.8"}, T0 + MIN);
      assert.equal(otherIp.status, 200);
    });

test("the failure window resets after an hour", async () => {
  for (let i = 0; i < pairing.MAX_FAILED_ATTEMPTS; i++) {
    await pairing.pairDevice(db,
        {code: "000000", deviceName: "w", ip: "9.9.9.9"}, T0);
  }
  await pairing.createPairingCode(db, "alice", T0 + 61 * MIN, () => "123456");
  const result = await pairing.pairDevice(db,
      {code: "123456", deviceName: "w", ip: "9.9.9.9"}, T0 + 61 * MIN);
  assert.equal(result.status, 200);
});

test("the attempt record is keyed by a hash of the IP, not the IP", async () => {
  await pairing.pairDevice(db,
      {code: "000000", deviceName: "w", ip: "9.9.9.9"}, T0);
  const raw = await db.collection(pairing.ATTEMPTS).doc("9.9.9.9").get();
  assert.equal(raw.exists, false);
  const hashed = await db.collection(pairing.ATTEMPTS)
      .doc(sha256Hex("9.9.9.9")).get();
  assert.equal(hashed.get("count"), 1);
});

test("the device name is trimmed, capped at 64 and defaulted", async () => {
  const names = ["  fenix  ", "x".repeat(100), "", undefined];
  const expected = ["fenix", "x".repeat(64), "Garmin watch", "Garmin watch"];
  for (let i = 0; i < names.length; i++) {
    const code = `10000${i}`;
    await pairing.createPairingCode(db, "alice", T0, () => code);
    const result = await pairing.pairDevice(db,
        {code, deviceName: names[i], ip: "1.1.1.1"}, T0, () => `tok${i}`);
    const device = await db.collection(pairing.DEVICES)
        .doc(sha256Hex(result.token)).get();
    assert.equal(device.get("deviceName"), expected[i]);
  }
});

test("listDevices returns only the caller's devices, newest first",
    async () => {
      const add = (id, uid, createdMs) => db.collection(pairing.DEVICES)
          .doc(id).set({
            uid,
            deviceName: id,
            createdAt: Timestamp.fromMillis(createdMs),
            lastSeenAt: Timestamp.fromMillis(createdMs + 5),
          });
      await add("old", "alice", T0);
      await add("new", "alice", T0 + MIN);
      await add("bobs", "bob", T0);
      const devices = await pairing.listDevices(db, "alice");
      assert.deepEqual(devices, [
        {id: "new", deviceName: "new", createdAt: T0 + MIN,
          lastSeenAt: T0 + MIN + 5},
        {id: "old", deviceName: "old", createdAt: T0, lastSeenAt: T0 + 5},
      ]);
    });

test("unlinkDevice deletes only the caller's own device", async () => {
  await db.collection(pairing.DEVICES).doc("h1").set({
    uid: "alice",
    deviceName: "w",
    createdAt: Timestamp.fromMillis(T0),
    lastSeenAt: Timestamp.fromMillis(T0),
  });
  assert.equal(await pairing.unlinkDevice(db, "bob", "h1"), false);
  assert.equal(
      (await db.collection(pairing.DEVICES).doc("h1").get()).exists, true);
  assert.equal(await pairing.unlinkDevice(db, "alice", "missing"), false);
  assert.equal(await pairing.unlinkDevice(db, "alice", 42), false);
  assert.equal(await pairing.unlinkDevice(db, "alice", "h1"), true);
  assert.equal(
      (await db.collection(pairing.DEVICES).doc("h1").get()).exists, false);
});
```

- [ ] **Step 3: Run the tests and confirm they fail**

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
npm --prefix functions test
```

Expected: the emulator starts, then the run fails with `Cannot find module '../garmin/pairing'`.

- [ ] **Step 4: Write `tokens.js`**

Create `functions/garmin/tokens.js`:

```js
const crypto = require("crypto");

/** How long a pairing code stays valid. */
const CODE_TTL_MS = 10 * 60 * 1000;

/**
 * A random 6-digit pairing code; leading zeros are kept.
 * @return {string} the code
 */
function newPairingCode() {
  return crypto.randomInt(0, 1000000).toString().padStart(6, "0");
}

/**
 * A watch's bearer token: 32 random bytes, base64url.
 * @return {string} the token
 */
function newDeviceToken() {
  return crypto.randomBytes(32).toString("base64url");
}

/**
 * Hex SHA-256, used so tokens and IPs are never stored as given.
 * @param {string} text what to hash
 * @return {string} 64 hex characters
 */
function sha256Hex(text) {
  return crypto.createHash("sha256").update(text).digest("hex");
}

/**
 * True for a string of exactly six decimal digits.
 * @param {*} value anything received from a client
 * @return {boolean} whether it is a well-formed pairing code
 */
function isPairingCode(value) {
  return typeof value === "string" && /^\d{6}$/.test(value);
}

module.exports = {
  CODE_TTL_MS,
  newPairingCode,
  newDeviceToken,
  sha256Hex,
  isPairingCode,
};
```

- [ ] **Step 5: Write `pairing.js`**

Create `functions/garmin/pairing.js`:

```js
const {Timestamp} = require("firebase-admin/firestore");
const tokens = require("./tokens");

const CODES = "garminPairingCodes";
const DEVICES = "garminDevices";
const ATTEMPTS = "garminPairAttempts";
const MAX_FAILED_ATTEMPTS = 10;
const ATTEMPT_WINDOW_MS = 60 * 60 * 1000;
const MAX_CODE_TRIES = 10;
const MAX_DEVICE_NAME = 64;
const DEFAULT_DEVICE_NAME = "Garmin watch";

/**
 * Gives the user a fresh pairing code, replacing any code they already had.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {string} uid the signed-in user
 * @param {number} nowMs current time, epoch ms
 * @param {function(): string} makeCode code generator (tests pass their own)
 * @return {Promise<{code: string, expiresAt: number}>} the code and expiry
 */
async function createPairingCode(db, uid, nowMs,
    makeCode = tokens.newPairingCode) {
  return db.runTransaction(async (tx) => {
    const mine = await tx.get(db.collection(CODES).where("uid", "==", uid));
    let code = null;
    for (let i = 0; i < MAX_CODE_TRIES && code === null; i++) {
      const candidate = makeCode();
      const taken = await tx.get(db.collection(CODES).doc(candidate));
      const free = !taken.exists ||
        taken.get("uid") === uid ||
        taken.get("expiresAt").toMillis() <= nowMs;
      if (free) {
        code = candidate;
      }
    }
    if (code === null) {
      throw new Error("No free pairing code found");
    }
    mine.docs
        .filter((doc) => doc.id !== code)
        .forEach((doc) => tx.delete(doc.ref));
    const expiresAt = nowMs + tokens.CODE_TTL_MS;
    tx.set(db.collection(CODES).doc(code), {
      uid,
      expiresAt: Timestamp.fromMillis(expiresAt),
    });
    return {code, expiresAt};
  });
}

/**
 * Exchanges a pairing code for a device token. Failed attempts are counted
 * per caller IP (hashed); after MAX_FAILED_ATTEMPTS in an hour the caller is
 * refused even with a good code.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {{code: *, deviceName: *, ip: *}} input the watch's request
 * @param {number} nowMs current time, epoch ms
 * @param {function(): string} makeToken token generator (tests pass their own)
 * @return {Promise<object>} {status: 200, token} or {status: 400|404|429}
 */
async function pairDevice(db, input, nowMs,
    makeToken = tokens.newDeviceToken) {
  const ipKey = tokens.sha256Hex(String(input.ip || "unknown"));
  const attemptRef = db.collection(ATTEMPTS).doc(ipKey);
  return db.runTransaction(async (tx) => {
    const attempt = await tx.get(attemptRef);
    const inWindow = attempt.exists &&
      nowMs - attempt.get("windowStart").toMillis() < ATTEMPT_WINDOW_MS;
    const failures = inWindow ? attempt.get("count") : 0;
    if (failures >= MAX_FAILED_ATTEMPTS) {
      return {status: 429};
    }
    const wellFormed = tokens.isPairingCode(input.code);
    const codeDoc = wellFormed ?
      await tx.get(db.collection(CODES).doc(input.code)) : null;
    const valid = codeDoc !== null && codeDoc.exists &&
      codeDoc.get("expiresAt").toMillis() > nowMs;
    if (!valid) {
      tx.set(attemptRef, {
        count: failures + 1,
        windowStart: inWindow ?
          attempt.get("windowStart") : Timestamp.fromMillis(nowMs),
      });
      return {status: wellFormed ? 404 : 400};
    }
    const token = makeToken();
    const now = Timestamp.fromMillis(nowMs);
    tx.delete(codeDoc.ref);
    tx.set(db.collection(DEVICES).doc(tokens.sha256Hex(token)), {
      uid: codeDoc.get("uid"),
      deviceName: cleanDeviceName(input.deviceName),
      createdAt: now,
      lastSeenAt: now,
    });
    return {status: 200, token};
  });
}

/**
 * The name to store for a watch: trimmed, capped, never empty.
 * @param {*} name what the watch sent
 * @return {string} a safe display name
 */
function cleanDeviceName(name) {
  if (typeof name !== "string" || name.trim() === "") {
    return DEFAULT_DEVICE_NAME;
  }
  return name.trim().slice(0, MAX_DEVICE_NAME);
}

/**
 * The user's linked watches, newest first.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {string} uid the signed-in user
 * @return {Promise<Array<object>>} {id, deviceName, createdAt, lastSeenAt}
 */
async function listDevices(db, uid) {
  const snapshot = await db.collection(DEVICES).where("uid", "==", uid).get();
  return snapshot.docs
      .map((doc) => ({
        id: doc.id,
        deviceName: doc.get("deviceName"),
        createdAt: doc.get("createdAt").toMillis(),
        lastSeenAt: doc.get("lastSeenAt").toMillis(),
      }))
      .sort((a, b) => b.createdAt - a.createdAt);
}

/**
 * Unlinks one of the user's watches. Someone else's watch, or an unknown id,
 * is reported the same way as a missing one.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {string} uid the signed-in user
 * @param {*} tokenHash the device id from listDevices
 * @return {Promise<boolean>} true when a device was removed
 */
async function unlinkDevice(db, uid, tokenHash) {
  if (typeof tokenHash !== "string" || tokenHash === "") {
    return false;
  }
  const ref = db.collection(DEVICES).doc(tokenHash);
  const doc = await ref.get();
  if (!doc.exists || doc.get("uid") !== uid) {
    return false;
  }
  await ref.delete();
  return true;
}

module.exports = {
  CODES,
  DEVICES,
  ATTEMPTS,
  MAX_FAILED_ATTEMPTS,
  createPairingCode,
  pairDevice,
  listDevices,
  unlinkDevice,
};
```

Note: a code that belongs to the same user counts as free in `createPairingCode`, so the "second code replaces the first" case can reuse a value without colliding with itself; the filter keeps that doc from being deleted and re-set in the same transaction.

- [ ] **Step 6: Run the tests and lint**

```powershell
npm --prefix functions test
npm --prefix functions run lint
```

Expected: every test in `pairing.test.js` passes (`# fail 0`), and lint reports no errors. If lint fixes formatting on its own (`--fix`), review the diff before committing. Some lines in this plan's test code may exceed the 80-character limit; wrap them where lint reports `max-len`, without changing what a test does.

- [ ] **Step 7: Commit**

```powershell
git add functions/package.json functions/garmin functions/test
git commit -m "Add Garmin pairing logic to Cloud Functions with emulator tests`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The four function endpoints

**Files:**
- Create: `functions/garmin/http.js`, `functions/test/http.test.js`
- Modify: `functions/index.js`, `firestore.rules` (header comment only)

**Interfaces:**
- Consumes: `pairDevice`, `createPairingCode`, `listDevices`, `unlinkDevice` (Task 1).
- Produces: deployed function names `createGarminPairingCode`, `listGarminDevices`, `unlinkGarminDevice` (callables) and `garminPair` (HTTP). Callable replies: `{code, expiresAt}`, `{devices: [...]}`, `{ok: true}`. HTTP reply: `200 {token}` or `<status> {error}` with `error` one of `bad_request`, `not_found`, `too_many_attempts`, `method_not_allowed`.

- [ ] **Step 1: Write the failing test**

Create `functions/test/http.test.js`:

```js
const test = require("node:test");
const assert = require("node:assert/strict");
const {handlePairRequest} = require("../garmin/http");

const fakeResponse = () => {
  const res = {statusCode: null, body: null};
  res.status = (code) => {
    res.statusCode = code;
    return res;
  };
  res.json = (body) => {
    res.body = body;
    return res;
  };
  return res;
};

const fakePairing = (result) => {
  const calls = [];
  const pairDevice = async (db, input, nowMs) => {
    calls.push({input, nowMs});
    return result;
  };
  return {calls, pairDevice};
};

test("only POST is accepted", async () => {
  const res = fakeResponse();
  const pairing = fakePairing({status: 200, token: "t"});
  await handlePairRequest(null, {method: "GET"}, res, 5, pairing.pairDevice);
  assert.equal(res.statusCode, 405);
  assert.deepEqual(res.body, {error: "method_not_allowed"});
  assert.equal(pairing.calls.length, 0);
});

test("a good request returns the token", async () => {
  const res = fakeResponse();
  const pairing = fakePairing({status: 200, token: "t"});
  const req = {
    method: "POST",
    ip: "1.2.3.4",
    body: {code: "123456", deviceName: "fenix", appVersion: "1"},
  };
  await handlePairRequest("db", req, res, 5, pairing.pairDevice);
  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.body, {token: "t"});
  assert.deepEqual(pairing.calls[0], {
    input: {code: "123456", deviceName: "fenix", ip: "1.2.3.4"},
    nowMs: 5,
  });
});

test("failures map to named errors", async () => {
  const cases = [
    [400, "bad_request"],
    [404, "not_found"],
    [429, "too_many_attempts"],
  ];
  for (const [status, name] of cases) {
    const res = fakeResponse();
    const pairing = fakePairing({status});
    await handlePairRequest("db",
        {method: "POST", ip: "x", body: {}}, res, 5, pairing.pairDevice);
    assert.equal(res.statusCode, status);
    assert.deepEqual(res.body, {error: name});
  }
});

test("a body that is not an object is treated as empty", async () => {
  const res = fakeResponse();
  const pairing = fakePairing({status: 400});
  await handlePairRequest("db",
      {method: "POST", ip: "x", body: "junk"}, res, 5, pairing.pairDevice);
  assert.deepEqual(pairing.calls[0].input,
      {code: undefined, deviceName: undefined, ip: "x"});
});
```

- [ ] **Step 2: Run it and confirm it fails**

```powershell
npm --prefix functions test
```

Expected: `http.test.js` fails with `Cannot find module '../garmin/http'`; `pairing.test.js` still passes.

- [ ] **Step 3: Write `http.js`**

Create `functions/garmin/http.js`:

```js
const pairing = require("./pairing");

const ERROR_NAMES = {
  400: "bad_request",
  404: "not_found",
  429: "too_many_attempts",
};

/**
 * Handles POST garminPair from a watch (through the Garmin Connect app).
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {object} req the HTTP request (method, ip, parsed JSON body)
 * @param {object} res the HTTP response
 * @param {number} nowMs current time, epoch ms
 * @param {Function} pairDevice pairing.pairDevice (tests pass a fake)
 * @return {Promise<void>} resolves once the reply is sent
 */
async function handlePairRequest(db, req, res, nowMs,
    pairDevice = pairing.pairDevice) {
  if (req.method !== "POST") {
    res.status(405).json({error: "method_not_allowed"});
    return;
  }
  const body = req.body !== null && typeof req.body === "object" ?
    req.body : {};
  const result = await pairDevice(db,
      {code: body.code, deviceName: body.deviceName, ip: req.ip}, nowMs);
  if (result.status === 200) {
    res.status(200).json({token: result.token});
  } else {
    res.status(result.status).json({error: ERROR_NAMES[result.status]});
  }
}

module.exports = {handlePairRequest};
```

- [ ] **Step 4: Wire the exports**

Replace `functions/index.js` with (the existing `generateCustomToken` is unchanged):

```js
const {onCall, onRequest, HttpsError} =
  require("firebase-functions/v2/https");
const {setGlobalOptions} = require("firebase-functions/v2");
const admin = require("firebase-admin");
const {getFirestore} = require("firebase-admin/firestore");
const pairing = require("./garmin/pairing");
const {handlePairRequest} = require("./garmin/http");

// Set global options for all v2 functions in this file
setGlobalOptions({maxInstances: 10});

admin.initializeApp();

exports.generateCustomToken = onCall(async (request) => {
  if (!request.auth) {
    // Use the imported HttpsError directly
    throw new HttpsError(
        "unauthenticated",
        "The function must be called while authenticated.",
    );
  }
  const uid = request.auth.uid;
  try {
    const customToken = await admin.auth().createCustomToken(uid);
    console.log(`Successfully created custom token for UID: ${uid}`);
    return {customToken: customToken};
  } catch (error) {
    console.error(`Error creating custom token for UID: ${uid}`, error);
    // Use the imported HttpsError directly
    throw new HttpsError(
        "internal",
        "Unable to create custom token.",
        error.message,
    );
  }
});

/**
 * The caller's uid, or an unauthenticated error.
 * @param {object} request the callable request
 * @return {string} uid
 */
function requireUid(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  return request.auth.uid;
}

// Phone: a 6-digit code the referee enters on the Garmin watch.
exports.createGarminPairingCode = onCall(async (request) => {
  const uid = requireUid(request);
  return pairing.createPairingCode(getFirestore(), uid, Date.now());
});

// Watch (through Garmin Connect): exchanges the code for a device token.
exports.garminPair = onRequest((req, res) =>
  handlePairRequest(getFirestore(), req, res, Date.now()));

// Phone: the user's linked Garmin watches.
exports.listGarminDevices = onCall(async (request) => {
  const uid = requireUid(request);
  return {devices: await pairing.listDevices(getFirestore(), uid)};
});

// Phone: unlinks one of the user's watches; its token stops working.
exports.unlinkGarminDevice = onCall(async (request) => {
  const uid = requireUid(request);
  const tokenHash = request.data ? request.data.tokenHash : undefined;
  if (!await pairing.unlinkDevice(getFirestore(), uid, tokenHash)) {
    throw new HttpsError("not-found", "No such watch.");
  }
  return {ok: true};
});
```

(The commented-out promise chain that used to sit in `generateCustomToken` is dead code and is dropped.)

- [ ] **Step 5: Update the rules comment**

In `firestore.rules`, replace the header comment's last two sentences ("Cloud Functions use the Admin SDK, which bypasses these rules. Every other path is denied.") with:

```
// same uid (functions/index.js), so it passes the same check. Cloud Functions use the
// Admin SDK, which bypasses these rules; only they read and write the Garmin pairing
// collections (garminPairingCodes, garminDevices, garminPairAttempts). Every other path,
// those included, is denied to clients.
```

(Keep the line that begins "Matches the ruleset deployed on 2025-05-16 …" and the rules themselves unchanged.)

- [ ] **Step 6: Run the tests and lint**

```powershell
npm --prefix functions test
npm --prefix functions run lint
```

Expected: all tests in both files pass; lint clean.

- [ ] **Step 7: Commit**

```powershell
git add functions firestore.rules
git commit -m "Expose Garmin pairing as Cloud Functions for the phone and the watch`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Phone repository and view model

**Files:**
- Create: `mobile/src/main/java/com/databelay/refwatch/data/garmin/GarminLinkRepository.kt`, `mobile/src/main/java/com/databelay/refwatch/data/garmin/GarminLinkViewModel.kt`
- Create: `mobile/src/test/java/com/databelay/refwatch/data/garmin/GarminLinkParsingTest.kt`, `mobile/src/test/java/com/databelay/refwatch/data/garmin/GarminLinkViewModelTest.kt`
- Modify: `mobile/src/main/java/com/databelay/refwatch/di/RepositoryModule.kt`, `mobile/build.gradle.kts`

**Interfaces:**
- Consumes: callable names and reply shapes from Task 2.
- Produces: `PairingCode(code: String, expiresAtMillis: Long)`, `LinkedGarminDevice(id: String, deviceName: String, lastSeenAtMillis: Long)`, `interface GarminLinkRepository { suspend fun createPairingCode(): Result<PairingCode>; suspend fun listDevices(): Result<List<LinkedGarminDevice>>; suspend fun unlink(deviceId: String): Result<Unit> }`, `GarminLinkViewModel` with `state: StateFlow<GarminLinkUiState>`, `requestCode()`, `unlink(deviceId: String)`, `refreshDevices()`; `GarminLinkUiState(code: PairingCode?, secondsLeft: Long, devices: List<LinkedGarminDevice>, busy: Boolean, error: String?)`.

- [ ] **Step 1: Add the coroutines test dependency**

In `mobile/build.gradle.kts`, next to `testImplementation(libs.google.truth)`:

```kotlin
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 2: Write the failing tests**

Create `mobile/src/test/java/com/databelay/refwatch/data/garmin/GarminLinkParsingTest.kt`:

```kotlin
package com.databelay.refwatch.data.garmin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GarminLinkParsingTest {
    @Test
    fun parsesAPairingCode() {
        val code = parsePairingCode(mapOf("code" to "012345", "expiresAt" to 1_700_000_600_000L))
        assertThat(code).isEqualTo(PairingCode("012345", 1_700_000_600_000L))
    }

    @Test
    fun expiryArrivingAsAnIntOrDoubleStillParses() {
        assertThat(parsePairingCode(mapOf("code" to "1", "expiresAt" to 5)).expiresAtMillis).isEqualTo(5L)
        assertThat(parsePairingCode(mapOf("code" to "1", "expiresAt" to 5.0)).expiresAtMillis).isEqualTo(5L)
    }

    @Test(expected = IllegalStateException::class)
    fun aReplyWithoutACodeIsAnError() {
        parsePairingCode(mapOf("expiresAt" to 5L))
    }

    @Test
    fun parsesTheDeviceList() {
        val devices = parseDevices(
            mapOf(
                "devices" to listOf(
                    mapOf("id" to "h1", "deviceName" to "006-B2604-00", "createdAt" to 1L, "lastSeenAt" to 9L)
                )
            )
        )
        assertThat(devices).containsExactly(LinkedGarminDevice("h1", "006-B2604-00", 9L))
    }

    @Test
    fun anEmptyOrMissingListIsNoDevices() {
        assertThat(parseDevices(mapOf("devices" to emptyList<Any>()))).isEmpty()
        assertThat(parseDevices(mapOf<String, Any>())).isEmpty()
    }

    @Test
    fun secondsLeftRoundsUpAndNeverGoesNegative() {
        assertThat(secondsLeft(expiresAtMillis = 10_000, nowMillis = 0)).isEqualTo(10)
        assertThat(secondsLeft(expiresAtMillis = 10_000, nowMillis = 9_001)).isEqualTo(1)
        assertThat(secondsLeft(expiresAtMillis = 10_000, nowMillis = 10_000)).isEqualTo(0)
        assertThat(secondsLeft(expiresAtMillis = 10_000, nowMillis = 99_000)).isEqualTo(0)
    }
}
```

Create `mobile/src/test/java/com/databelay/refwatch/data/garmin/GarminLinkViewModelTest.kt`:

```kotlin
package com.databelay.refwatch.data.garmin

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GarminLinkViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository : GarminLinkRepository {
        var codeResult: Result<PairingCode> = Result.success(PairingCode("123456", 600_000))
        var devices = mutableListOf<LinkedGarminDevice>()
        var unlinked = mutableListOf<String>()

        override suspend fun createPairingCode() = codeResult
        override suspend fun listDevices() = Result.success(devices.toList())
        override suspend fun unlink(deviceId: String): Result<Unit> {
            unlinked += deviceId
            devices.removeAll { it.id == deviceId }
            return Result.success(Unit)
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(repo: GarminLinkRepository) =
        GarminLinkViewModel(repo).also { it.nowMillis = { testScheduler.currentTime } }

    @Test
    fun startsByLoadingLinkedWatches() = runTest(dispatcher) {
        val repo = FakeRepository().apply { devices += LinkedGarminDevice("h1", "fenix", 1) }
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertThat(vm.state.value.devices.map { it.id }).containsExactly("h1")
    }

    @Test
    fun aRequestedCodeCountsDownAndDisappearsAtExpiry() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.requestCode()
        runCurrent()
        assertThat(vm.state.value.code?.code).isEqualTo("123456")
        assertThat(vm.state.value.secondsLeft).isEqualTo(600)
        advanceTimeBy(60_000)
        runCurrent()
        assertThat(vm.state.value.secondsLeft).isEqualTo(540)
        advanceTimeBy(540_000)
        runCurrent()
        assertThat(vm.state.value.code).isNull()
    }

    @Test
    fun aNewlyLinkedWatchClearsTheCode() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.requestCode()
        runCurrent()
        repo.devices += LinkedGarminDevice("h9", "fenix", 1)
        advanceTimeBy(GarminLinkViewModel.POLL_INTERVAL_MS + 1)
        runCurrent()
        assertThat(vm.state.value.code).isNull()
        assertThat(vm.state.value.devices.map { it.id }).containsExactly("h9")
    }

    @Test
    fun aFailedRequestShowsAnError() = runTest(dispatcher) {
        val repo = FakeRepository().apply { codeResult = Result.failure(RuntimeException("offline")) }
        val vm = viewModel(repo)
        vm.requestCode()
        advanceUntilIdle()
        assertThat(vm.state.value.code).isNull()
        assertThat(vm.state.value.error).isNotNull()
        assertThat(vm.state.value.busy).isFalse()
    }

    @Test
    fun unlinkRemovesTheWatch() = runTest(dispatcher) {
        val repo = FakeRepository().apply { devices += LinkedGarminDevice("h1", "fenix", 1) }
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.unlink("h1")
        advanceUntilIdle()
        assertThat(repo.unlinked).containsExactly("h1")
        assertThat(vm.state.value.devices).isEmpty()
    }
}
```

- [ ] **Step 3: Run the tests and confirm they fail**

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
.\gradlew :mobile:testDebugUnitTest --tests "com.databelay.refwatch.data.garmin.*"
```

Expected: compilation fails — `parsePairingCode`, `GarminLinkViewModel` etc. are unresolved.

- [ ] **Step 4: Write the repository**

Create `mobile/src/main/java/com/databelay/refwatch/data/garmin/GarminLinkRepository.kt`:

```kotlin
package com.databelay.refwatch.data.garmin

import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** A code the referee enters on the watch, valid until [expiresAtMillis]. */
data class PairingCode(val code: String, val expiresAtMillis: Long)

/** A Garmin watch linked to this account. [id] is the server's token hash. */
data class LinkedGarminDevice(val id: String, val deviceName: String, val lastSeenAtMillis: Long)

/** Linking Garmin watches, through the Cloud Functions in functions/garmin. */
interface GarminLinkRepository {
    suspend fun createPairingCode(): Result<PairingCode>
    suspend fun listDevices(): Result<List<LinkedGarminDevice>>
    suspend fun unlink(deviceId: String): Result<Unit>
}

class FirebaseGarminLinkRepository @Inject constructor() : GarminLinkRepository {
    override suspend fun createPairingCode(): Result<PairingCode> = runCatching {
        parsePairingCode(call("createGarminPairingCode"))
    }

    override suspend fun listDevices(): Result<List<LinkedGarminDevice>> = runCatching {
        parseDevices(call("listGarminDevices"))
    }

    override suspend fun unlink(deviceId: String): Result<Unit> = runCatching {
        call("unlinkGarminDevice", mapOf("tokenHash" to deviceId))
        Unit
    }

    private suspend fun call(name: String, data: Any? = null): Any? =
        Firebase.functions.getHttpsCallable(name).call(data).await().data
}

/** The reply of createGarminPairingCode: {code, expiresAt}. */
fun parsePairingCode(data: Any?): PairingCode {
    val map = data as? Map<*, *> ?: error("Unexpected reply: $data")
    val code = map["code"] as? String ?: error("Reply has no code: $data")
    val expiresAt = (map["expiresAt"] as? Number)?.toLong() ?: error("Reply has no expiry: $data")
    return PairingCode(code, expiresAt)
}

/** The reply of listGarminDevices: {devices: [{id, deviceName, lastSeenAt, ...}]}. */
fun parseDevices(data: Any?): List<LinkedGarminDevice> {
    val list = (data as? Map<*, *>)?.get("devices") as? List<*> ?: return emptyList()
    return list.mapNotNull { item ->
        val device = item as? Map<*, *> ?: return@mapNotNull null
        LinkedGarminDevice(
            id = device["id"] as? String ?: return@mapNotNull null,
            deviceName = device["deviceName"] as? String ?: "Garmin watch",
            lastSeenAtMillis = (device["lastSeenAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

/** Whole seconds until [expiresAtMillis], rounded up, never below zero. */
fun secondsLeft(expiresAtMillis: Long, nowMillis: Long): Long =
    ((expiresAtMillis - nowMillis + 999) / 1000).coerceAtLeast(0)
```

- [ ] **Step 5: Write the view model**

Create `mobile/src/main/java/com/databelay/refwatch/data/garmin/GarminLinkViewModel.kt`:

```kotlin
package com.databelay.refwatch.data.garmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GarminLinkUiState(
    val code: PairingCode? = null,
    val secondsLeft: Long = 0,
    val devices: List<LinkedGarminDevice> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null
)

/**
 * The Settings "Garmin watch" section: asks for a pairing code, counts it down, and while it
 * shows, checks every few seconds whether a watch has used it, so the phone confirms the link
 * without the referee refreshing anything.
 */
@HiltViewModel
class GarminLinkViewModel @Inject constructor(
    private val repository: GarminLinkRepository
) : ViewModel() {
    private val _state = MutableStateFlow(GarminLinkUiState())
    val state: StateFlow<GarminLinkUiState> = _state.asStateFlow()

    /** Replaced in tests with the test scheduler's virtual clock. */
    internal var nowMillis: () -> Long = System::currentTimeMillis

    private var countdown: Job? = null

    init {
        refreshDevices()
    }

    fun refreshDevices() {
        viewModelScope.launch { loadDevices() }
    }

    fun requestCode() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            repository.createPairingCode()
                .onSuccess { code ->
                    _state.update { it.copy(code = code, busy = false) }
                    startCountdown(code)
                }
                .onFailure {
                    _state.update {
                        it.copy(busy = false, error = "Couldn't get a code. Check your connection and try again.")
                    }
                }
        }
    }

    fun unlink(deviceId: String) {
        viewModelScope.launch {
            repository.unlink(deviceId)
                .onFailure { _state.update { it.copy(error = "Couldn't unlink the watch. Try again.") } }
            loadDevices()
        }
    }

    private fun startCountdown(code: PairingCode) {
        countdown?.cancel()
        val knownIds = _state.value.devices.map { it.id }.toSet()
        countdown = viewModelScope.launch {
            var sincePoll = 0L
            while (true) {
                val left = secondsLeft(code.expiresAtMillis, nowMillis())
                _state.update { it.copy(secondsLeft = left) }
                if (left == 0L) {
                    _state.update { it.copy(code = null) }
                    loadDevices()
                    return@launch
                }
                if (sincePoll >= POLL_INTERVAL_MS) {
                    sincePoll = 0
                    loadDevices()
                    if (_state.value.devices.any { it.id !in knownIds }) {
                        _state.update { it.copy(code = null) }
                        return@launch
                    }
                }
                delay(TICK_MS)
                sincePoll += TICK_MS
            }
        }
    }

    private suspend fun loadDevices() {
        repository.listDevices().onSuccess { devices -> _state.update { it.copy(devices = devices) } }
    }

    companion object {
        const val TICK_MS = 1_000L
        const val POLL_INTERVAL_MS = 5_000L
    }
}
```

- [ ] **Step 6: Bind the repository**

In `mobile/src/main/java/com/databelay/refwatch/di/RepositoryModule.kt`, add inside `object RepositoryModule` (with the imports `com.databelay.refwatch.data.garmin.FirebaseGarminLinkRepository` and `com.databelay.refwatch.data.garmin.GarminLinkRepository`):

```kotlin
    @Provides
    @Singleton
    fun provideGarminLinkRepository(): GarminLinkRepository = FirebaseGarminLinkRepository()
```

- [ ] **Step 7: Run the tests and confirm they pass**

```powershell
.\gradlew :mobile:testDebugUnitTest --tests "com.databelay.refwatch.data.garmin.*"
```

Expected: `BUILD SUCCESSFUL`; the 11 tests in the two new classes pass. If the coroutines-test version conflicts with the project's coroutines core, align `kotlinxCoroutinesTest` in `gradle/libs.versions.toml` with the core version Gradle resolves (`.\gradlew :mobile:dependencies --configuration debugRuntimeClasspath | Select-String coroutines-core`) and say so in the report.

- [ ] **Step 8: Commit**

```powershell
git add mobile gradle/libs.versions.toml
git commit -m "Add the phone's Garmin link repository and view model`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The phone's Settings section and the privacy policy

**Files:**
- Create: `mobile/src/main/java/com/databelay/refwatch/screens/GarminLinkSection.kt`
- Modify: `mobile/src/main/java/com/databelay/refwatch/screens/SettingsScreen.kt`, `docs/privacy-policy.md`

**Interfaces:**
- Consumes: `GarminLinkViewModel`, `GarminLinkUiState`, `LinkedGarminDevice` (Task 3).

- [ ] **Step 1: Write the section**

Create `mobile/src/main/java/com/databelay/refwatch/screens/GarminLinkSection.kt`:

```kotlin
package com.databelay.refwatch.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.databelay.refwatch.common.theme.RefWatchMobileTheme
import com.databelay.refwatch.data.garmin.GarminLinkUiState
import com.databelay.refwatch.data.garmin.GarminLinkViewModel
import com.databelay.refwatch.data.garmin.LinkedGarminDevice
import com.databelay.refwatch.data.garmin.PairingCode

@Composable
fun GarminLinkSection(viewModel: GarminLinkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GarminLinkContent(state = state, onLink = viewModel::requestCode, onUnlink = viewModel::unlink)
}

@Composable
fun GarminLinkContent(state: GarminLinkUiState, onLink: () -> Unit, onUnlink: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Garmin watch", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        val code = state.code
        if (code != null) {
            Text(
                code.code.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Expires in ${state.secondsLeft / 60}:${"%02d".format(state.secondsLeft % 60)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "On the watch, open RefWatch → Link account and enter this code. " +
                    "Or, in the Garmin Connect app, open RefWatch's settings and enter it as the pairing code.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Text(
                "Link a Garmin watch to referee your games on it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onLink, enabled = !state.busy, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Link a Garmin watch")
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.devices.forEach { device ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.deviceName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Last seen " + DateUtils.getRelativeTimeSpanString(device.lastSeenAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onUnlink(device.id) }) { Text("Unlink") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GarminLinkContentCodePreview() {
    RefWatchMobileTheme {
        GarminLinkContent(
            state = GarminLinkUiState(
                code = PairingCode("012345", 0),
                secondsLeft = 545,
                devices = listOf(LinkedGarminDevice("h1", "006-B2604-00", System.currentTimeMillis()))
            ),
            onLink = {},
            onUnlink = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GarminLinkContentIdlePreview() {
    RefWatchMobileTheme {
        GarminLinkContent(state = GarminLinkUiState(), onLink = {}, onUnlink = {})
    }
}
```

- [ ] **Step 2: Add it to Settings**

In `SettingsScreen.kt`, directly after the closing `}` of the "Ask For Player Number" `Row` (before `Spacer(modifier = Modifier.height(16.dp))` and `ExtractionPromptSection(`), add:

```kotlin
            Spacer(modifier = Modifier.height(16.dp))

            GarminLinkSection()
```

- [ ] **Step 3: Update the privacy policy**

In `docs/privacy-policy.md`:

1. Change the effective date line to `**Effective date:** October 9, 2026`.
2. Add a new subsection at the end of "## Data we collect" (after "### Schedule imports" and its paragraph):

```markdown
### Garmin watches
If you link a Garmin watch, we store a record of it: the watch's model or part number,
when it was linked and when it last contacted us, and a hashed form of the access key we
gave it. While you are linking, we also keep the 6-digit code for up to 10 minutes and,
to stop code guessing, a hashed form of the IP address that tries a code. Your games
travel between our servers and the watch through Garmin's Connect app, which Garmin
operates under its own privacy policy. You can unlink a watch at any time in the phone
app's Settings, which deletes its record and stops its access.
```

3. In "## Where your data is stored and who processes it", after the bullet list's "Google Play services" line, add:

```markdown
- **Garmin Connect** (Garmin Ltd.): carries data between our servers and a linked Garmin
  watch
```

- [ ] **Step 4: Build and run the phone unit tests**

```powershell
.\gradlew :mobile:assembleDebug :mobile:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. (Unit-test failures in classes outside `data.garmin` that also fail on `main` are pre-existing; note them in the report rather than fixing them.)

- [ ] **Step 5: Look at the section**

Render the two previews (Android Studio's preview pane, or `.\gradlew :mobile:assembleDebug` and run the app on an emulator → Settings). Check: the code reads as two groups of three digits, the countdown shows `m:ss`, the instructions wrap without clipping, and each linked watch row keeps its Unlink button on screen. Note in the report how you checked.

- [ ] **Step 6: Commit**

```powershell
git add mobile docs/privacy-policy.md
git commit -m "Add the Garmin watch section to phone Settings and the privacy policy`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Watch pairing core

**Files:**
- Create: `garmin/source/sync/Api.mc`, `garmin/source/sync/Pairing.mc`, `garmin/test/PairingTest.mc`
- Modify: `garmin/manifest.xml`, `garmin/source/model/NumberPickerModel.mc`, `garmin/test/NumberPickerModelTest.mc`, `garmin/resources/strings/strings.xml`

**Interfaces:**
- Consumes: `garminPair` reply shapes (Task 2).
- Produces: constants `PAIR_LINKED`, `PAIR_BAD_CODE`, `PAIR_TOO_MANY`, `PAIR_OFFLINE`, `PAIR_FAILED` (Numbers 0–4); module `Pairing` with `isLinked() as Boolean`, `token() as String or Null`, `forget() as Void`, `isCode(text as String) as Boolean`, `applyResponse(responseCode as Number, data as Dictionary or String or Null) as Number`, `messageId(status as Number) as ResourceId`, `pair(code as String, onDone as Method(status as Number) as Void) as Void`; module `Api` with `postJson(path as String, body as Dictionary, callback as Method(responseCode as Number, data as Dictionary or String or Null) as Void) as Void`; `NumberPickerModel.digits() as String`; string ids `Rez.Strings.LinkAccount`, `Linked`, `NotLinked`, `PairingCode`, `Linking`, `LinkedMessage`, `BadCodeMessage`, `TooManyMessage`, `OfflineMessage`, `PairFailedMessage`.

- [ ] **Step 1: Write the failing tests**

Create `garmin/test/PairingTest.mc`:

```monkeyc
import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

(:test)
function aSuccessfulReplyStoresTheToken(logger as Logger) as Boolean {
    Pairing.forget();
    Test.assertEqual(PAIR_LINKED, Pairing.applyResponse(200, {"token" => "abc"}));
    Test.assert(Pairing.isLinked());
    Test.assertEqual("abc", Pairing.token());
    Pairing.forget();
    Test.assert(!Pairing.isLinked());
    return true;
}

(:test)
function aReplyWithoutATokenIsAFailure(logger as Logger) as Boolean {
    Pairing.forget();
    Test.assertEqual(PAIR_FAILED, Pairing.applyResponse(200, {"other" => 1}));
    Test.assertEqual(PAIR_FAILED, Pairing.applyResponse(200, null));
    Test.assert(!Pairing.isLinked());
    return true;
}

(:test)
function serverRefusalsMapToStatuses(logger as Logger) as Boolean {
    Pairing.forget();
    Test.assertEqual(PAIR_BAD_CODE, Pairing.applyResponse(404, {"error" => "not_found"}));
    Test.assertEqual(PAIR_BAD_CODE, Pairing.applyResponse(400, {"error" => "bad_request"}));
    Test.assertEqual(PAIR_TOO_MANY, Pairing.applyResponse(429, {"error" => "too_many_attempts"}));
    Test.assertEqual(PAIR_FAILED, Pairing.applyResponse(500, null));
    Test.assert(!Pairing.isLinked());
    return true;
}

// Negative codes are Connect IQ's own: above -400 the phone or the network was not reachable;
// -400 and below, a reply came back that could not be parsed.
(:test)
function connectionErrorsMeanOffline(logger as Logger) as Boolean {
    Test.assertEqual(PAIR_OFFLINE, Pairing.applyResponse(-104, null));
    Test.assertEqual(PAIR_OFFLINE, Pairing.applyResponse(-2, null));
    Test.assertEqual(PAIR_OFFLINE, Pairing.applyResponse(-300, null));
    Test.assertEqual(PAIR_FAILED, Pairing.applyResponse(-400, null));
    return true;
}

(:test)
function aFailedRelinkKeepsTheExistingToken(logger as Logger) as Boolean {
    Pairing.forget();
    Pairing.applyResponse(200, {"token" => "first"});
    Pairing.applyResponse(404, {"error" => "not_found"});
    Test.assertEqual("first", Pairing.token());
    Pairing.forget();
    return true;
}

(:test)
function onlySixDigitsAreACode(logger as Logger) as Boolean {
    Test.assert(Pairing.isCode("012345"));
    Test.assert(!Pairing.isCode("12345"));
    Test.assert(!Pairing.isCode("1234567"));
    Test.assert(!Pairing.isCode("12a456"));
    Test.assert(!Pairing.isCode(""));
    return true;
}
```

Append to `garmin/test/NumberPickerModelTest.mc`:

```monkeyc
(:test)
function sixColumnsReadAsADigitStringWithLeadingZeros(logger as Logger) as Boolean {
    var m = new NumberPickerModel(0, 9, 1, 0, 6);
    m.advance();
    m.increment();          // 0 1 0 0 0 0
    for (var i = 0; i < 5; i++) {
        m.advance();
    }
    m.decrement();          // last digit wraps to 9
    Test.assertEqual("010009", m.digits());
    return true;
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

```powershell
.\garmin\build.ps1 -Test
```

Expected: compile errors — `Pairing`, `PAIR_LINKED` and `digits` are undefined.

- [ ] **Step 3: Add the permission and strings**

`garmin/manifest.xml` — add inside `<iq:permissions>`:

```xml
            <iq:uses-permission id="Communications"/>
```

`garmin/resources/strings/strings.xml` — add before `</strings>`:

```xml
    <string id="LinkAccount">Link account</string>
    <string id="Linked">Linked</string>
    <string id="NotLinked">Not linked</string>
    <string id="PairingCode">Pairing code</string>
    <string id="PairingCodeTitle">Pairing code (from the RefWatch phone app)</string>
    <string id="Linking">Linking…</string>
    <string id="LinkedMessage">Linked</string>
    <string id="BadCodeMessage">Wrong code</string>
    <string id="TooManyMessage">Too many tries</string>
    <string id="OfflineMessage">No phone</string>
    <string id="PairFailedMessage">Link failed</string>
```

(The result messages are short on purpose: they are drawn on one line on a 240 px round screen.)

- [ ] **Step 4: Add `digits()`**

In `garmin/source/model/NumberPickerModel.mc`, after `result()`:

```monkeyc
    // Every column's digit in order, as text, so a code keeps its leading zeros.
    function digits() as String {
        var text = "";
        for (var i = 0; i < values.size(); i++) {
            text += values[i].format("%d");
        }
        return text;
    }
```

- [ ] **Step 5: Write `Api`**

Create `garmin/source/sync/Api.mc`:

```monkeyc
import Toybox.Communications;
import Toybox.Lang;

// RefWatch's Cloud Functions. Requests travel through the Garmin Connect phone app, so they
// fail with a negative response code when the phone is out of reach.
module Api {
    const BASE_URL = "https://us-central1-refwatchapp.cloudfunctions.net/";

    // POSTs body as JSON. The callback gets the HTTP status (negative for a Connect IQ
    // connection error) and the decoded JSON reply, if any.
    function postJson(path as String, body as Dictionary,
                      callback as Method(responseCode as Number, data as Dictionary or String or Null) as Void) as Void {
        Communications.makeWebRequest(BASE_URL + path, body, {
            :method => Communications.HTTP_REQUEST_METHOD_POST,
            :headers => {"Content-Type" => Communications.REQUEST_CONTENT_TYPE_JSON},
            :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
        }, callback);
    }
}
```

If the compiler rejects the callback's type against `makeWebRequest`'s declared signature (the SDK's data type also allows `PersistedContent.Iterator`), widen the parameter type in `postJson` and in `PairRequest.onResponse` (Step 6) to match the SDK's exactly, and say so in the report.

- [ ] **Step 6: Write `Pairing`**

Create `garmin/source/sync/Pairing.mc`:

```monkeyc
import Toybox.Application;
import Toybox.Lang;
import Toybox.System;

const PAIR_LINKED = 0;
const PAIR_BAD_CODE = 1;
const PAIR_TOO_MANY = 2;
const PAIR_OFFLINE = 3;
const PAIR_FAILED = 4;

// Links this watch to a RefWatch account: a 6-digit code from the phone app is exchanged for
// the watch's own access token, which is kept in storage. A failed attempt never removes a
// token the watch already has.
module Pairing {
    const TOKEN_KEY = "deviceToken";
    const APP_VERSION = "1";

    function isLinked() as Boolean {
        return token() != null;
    }

    function token() as String or Null {
        var value = Application.Storage.getValue(TOKEN_KEY);
        return value instanceof String ? value as String : null;
    }

    function forget() as Void {
        Application.Storage.deleteValue(TOKEN_KEY);
    }

    function isCode(text as String) as Boolean {
        if (text.length() != 6) {
            return false;
        }
        var chars = text.toCharArray();
        for (var i = 0; i < chars.size(); i++) {
            var c = chars[i].toNumber();     // '0' is 48, '9' is 57
            if (c < 48 || c > 57) {
                return false;
            }
        }
        return true;
    }

    // Sends the code; onDone gets one of the PAIR_* statuses.
    function pair(code as String, onDone as Method(status as Number) as Void) as Void {
        var request = new PairRequest(onDone);
        Api.postJson("garminPair", {
            "code" => code,
            "deviceName" => deviceName(),
            "appVersion" => APP_VERSION
        }, request.method(:onResponse));
    }

    // The garminPair reply as a PAIR_* status; a token in a 200 reply is stored.
    function applyResponse(responseCode as Number, data as Dictionary or String or Null) as Number {
        if (responseCode == 200) {
            if (data instanceof Dictionary && (data as Dictionary)["token"] instanceof String) {
                Application.Storage.setValue(TOKEN_KEY, (data as Dictionary)["token"] as String);
                return PAIR_LINKED;
            }
            return PAIR_FAILED;
        }
        if (responseCode == 400 || responseCode == 404) {
            return PAIR_BAD_CODE;
        }
        if (responseCode == 429) {
            return PAIR_TOO_MANY;
        }
        // Connect IQ's own codes: down to -399 the phone or the network was not reachable;
        // -400 and below mean a reply arrived but could not be read (for example an HTML page).
        if (responseCode < 0 && responseCode > -400) {
            return PAIR_OFFLINE;
        }
        return PAIR_FAILED;
    }

    function messageId(status as Number) as ResourceId {
        if (status == PAIR_LINKED) {
            return Rez.Strings.LinkedMessage;
        } else if (status == PAIR_BAD_CODE) {
            return Rez.Strings.BadCodeMessage;
        } else if (status == PAIR_TOO_MANY) {
            return Rez.Strings.TooManyMessage;
        } else if (status == PAIR_OFFLINE) {
            return Rez.Strings.OfflineMessage;
        }
        return Rez.Strings.PairFailedMessage;
    }

    // Shown in the phone's list of linked watches. partNumber names the model (for example
    // 006-B2604-00 is a fēnix 5X); older firmware may not have it.
    function deviceName() as String {
        var settings = System.getDeviceSettings();
        if (settings has :partNumber && settings.partNumber != null) {
            return settings.partNumber as String;
        }
        return "Garmin watch";
    }
}

// Holds the caller's callback for the duration of one web request.
class PairRequest {
    hidden var _onDone as Method(status as Number) as Void;

    function initialize(onDone as Method(status as Number) as Void) {
        _onDone = onDone;
    }

    function onResponse(responseCode as Number, data as Dictionary or String or Null) as Void {
        _onDone.invoke(Pairing.applyResponse(responseCode, data));
    }
}
```

- [ ] **Step 7: Run the tests, build every product**

```powershell
.\garmin\build.ps1 -Test
foreach ($d in "fenix5x", "fenix7s", "fenix7", "epix2pro47mm", "fr265") { .\garmin\build.ps1 -Device $d }
```

Expected: `PASSED` with 75 tests (68 before + 7 new), `failed=0, errors=0`; five `BUILD SUCCESSFUL`, with only the known launcher-icon and `SPORT_SOCCER` warnings.

- [ ] **Step 8: Commit**

```powershell
git add garmin
git commit -m "Add Garmin watch pairing: token storage, reply handling and the web request`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Linking from the watch and from Garmin Connect

**Files:**
- Create: `garmin/source/ui/LinkFlow.mc`
- Modify: `garmin/source/ui/Pickers.mc`, `garmin/source/ui/GameList.mc`, `garmin/source/RefWatchApp.mc`, `garmin/source/util/Settings.mc`, `garmin/resources/settings/properties.xml`

**Interfaces:**
- Consumes: `Pairing.*`, `PAIR_*`, `NumberPickerModel.digits()`, strings (Task 5).
- Produces: `Pickers.pushCode(titleId as ResourceId, onPicked as Method(code as String) as Void) as Void`; module `LinkFlow` with `start() as Void`; `Settings.pairingCode() as String`, `Settings.clearPairingCode() as Void`.

- [ ] **Step 1: A code picker**

In `garmin/source/ui/Pickers.mc`:

Add to `module Pickers`:

```monkeyc
    // Six digit columns for the pairing code, each 0–9; onPicked gets the digits as text.
    function pushCode(titleId as ResourceId, onPicked as Method(code as String) as Void) as Void {
        var model = new NumberPickerModel(0, 9, 1, 0, 6);
        WatchUi.pushView(new NumberPickerView(titleId, model, false), new CodePickerDelegate(model, onPicked),
            WatchUi.SLIDE_IMMEDIATE);
    }
```

In `NumberPickerView.onUpdate`, replace `var font = Graphics.FONT_NUMBER_HOT;` with:

```monkeyc
        // Six code digits do not fit a small round screen at the size used for one or two.
        var font = _model.values.size() > 2 ? Graphics.FONT_NUMBER_MILD : Graphics.FONT_NUMBER_HOT;
```

In `NumberPickerDelegate`, change `advance()` so the accepted value is delivered through an overridable function:

```monkeyc
    hidden function advance() as Boolean {
        if (_model.advance()) {
            WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
            deliver();
        } else {
            WatchUi.requestUpdate();
        }
        return true;
    }

    hidden function deliver() as Void {
        _onPicked.invoke(_model.result());
    }
```

and add, after the `NumberPickerDelegate` class:

```monkeyc
// The same picker, delivering the digits as text so a code keeps its leading zeros.
class CodePickerDelegate extends NumberPickerDelegate {
    hidden var _onCode as Method(code as String) as Void;

    function initialize(model as NumberPickerModel, onCode as Method(code as String) as Void) {
        NumberPickerDelegate.initialize(model, method(:ignore));
        _onCode = onCode;
    }

    function ignore(n as Number) as Void {
    }

    hidden function deliver() as Void {
        _onCode.invoke(_model.digits());
    }
}
```

(If Monkey C does not dispatch the overridden `hidden function deliver()` from the base class, make `deliver` non-hidden in both classes and say so in the report.)

- [ ] **Step 2: The link flow**

Create `garmin/source/ui/LinkFlow.mc`:

```monkeyc
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// Start menu → Link account: enter the code, see "Linking…", then the result. Any button on
// the result returns to the start menu, which shows the new status.
module LinkFlow {
    function start() as Void {
        Pickers.pushCode(Rez.Strings.PairingCode, new LinkFlowSteps().method(:onCode));
    }
}

class LinkFlowSteps {
    function initialize() {
    }

    function onCode(code as String) as Void {
        WatchUi.pushView(new MessageView(Rez.Strings.Linking), new MessageDelegate(false), WatchUi.SLIDE_IMMEDIATE);
        Pairing.pair(code, method(:onPaired));
    }

    function onPaired(status as Number) as Void {
        WatchUi.switchToView(new MessageView(Pairing.messageId(status)), new MessageDelegate(true),
            WatchUi.SLIDE_IMMEDIATE);
    }
}

// One line of text in the middle of the screen.
class MessageView extends WatchUi.View {
    hidden var _textId as ResourceId;

    function initialize(textId as ResourceId) {
        View.initialize();
        _textId = textId;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(dc.getWidth() / 2, dc.getHeight() / 2, Graphics.FONT_SMALL,
            WatchUi.loadResource(_textId) as String, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}

// While linking, buttons are ignored (the reply replaces the screen). On the result, any
// button or tap returns to the start menu.
class MessageDelegate extends WatchUi.BehaviorDelegate {
    hidden var _closes as Boolean;

    function initialize(closes as Boolean) {
        BehaviorDelegate.initialize();
        _closes = closes;
    }

    function onKey(event as WatchUi.KeyEvent) as Boolean {
        return close();
    }

    function onTap(event as WatchUi.ClickEvent) as Boolean {
        return close();
    }

    function onBack() as Boolean {
        return close();
    }

    hidden function close() as Boolean {
        if (_closes) {
            GameList.show();
        }
        return true;
    }
}
```

- [ ] **Step 3: The start-menu item**

In `garmin/source/ui/GameList.mc`, `build()` becomes:

```monkeyc
    function build() as WatchUi.Menu2 {
        var menu = new WatchUi.Menu2({:title => Rez.Strings.AppName});
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.QuickMatch, null, :quickMatch, null));
        var status = Pairing.isLinked() ? Rez.Strings.Linked : Rez.Strings.NotLinked;
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.LinkAccount, status, :link, null));
        return menu;
    }
```

and `GameListDelegate.onSelect` gains:

```monkeyc
        } else if (item.getId() == :link) {
            LinkFlow.start();
        }
```

(so the `if` reads: quickMatch → PreMatch.push; link → LinkFlow.start).

- [ ] **Step 4: The Garmin Connect setting**

`garmin/resources/settings/properties.xml` — add the property (inside `<properties>`) and its setting (inside `<settings>`):

```xml
        <property id="pairingCode" type="string"></property>
```

```xml
        <setting propertyKey="@Properties.pairingCode" title="@Strings.PairingCodeTitle">
            <settingConfig type="alphaNumeric"/>
        </setting>
```

`garmin/source/util/Settings.mc` — add:

```monkeyc
    // The code typed in Garmin Connect, trimmed; empty when none.
    function pairingCode() as String {
        var value = Application.Properties.getValue("pairingCode");
        return value instanceof String ? (value as String) : "";
    }

    function clearPairingCode() as Void {
        Application.Properties.setValue("pairingCode", "");
    }
```

`garmin/source/RefWatchApp.mc` — add after `onStart`'s existing lines:

```monkeyc
        pairFromSettings();
```

and add these functions to the class:

```monkeyc
    // A code entered in Garmin Connect is used once: when the settings change, or at start if
    // the watch is not linked yet.
    function onSettingsChanged() as Void {
        pairFromSettings();
        WatchUi.requestUpdate();
    }

    hidden function pairFromSettings() as Void {
        var code = Settings.pairingCode();
        if (Pairing.isCode(code) && !Pairing.isLinked()) {
            Pairing.pair(code, method(:onSettingsPaired));
        }
    }

    // A refused code is cleared too, so it is not retried at every start; an offline attempt
    // keeps it for the next start.
    function onSettingsPaired(status as Number) as Void {
        if (status != PAIR_OFFLINE) {
            Settings.clearPairingCode();
        }
        WatchUi.requestUpdate();
    }
```

Note: re-linking an already linked watch is done from the watch's "Link account" item; the Garmin Connect setting only links an unlinked watch, so a stale code left in the setting never replaces a working token.

- [ ] **Step 5: Tests and builds**

```powershell
.\garmin\build.ps1 -Test
foreach ($d in "fenix5x", "fenix7s", "fenix7", "epix2pro47mm", "fr265") { .\garmin\build.ps1 -Device $d }
```

Expected: `PASSED` (75 tests), five `BUILD SUCCESSFUL`.

- [ ] **Step 6: Look at the screens**

Run the fēnix 5X build in the simulator (`.\garmin\build.ps1 -Device fenix5x -Run` with `run_in_background`, then `.\garmin\tools\sim.ps1`; see `garmin/README.md`). Capture and open:

1. The start menu with "Link account / Not linked" focused (`-Click DOWN`).
2. The code picker (`-Click START` on Link account): six digits must fit inside the round screen with the active digit's arrows visible.
3. After entering a code (`START` six times): "Linking…", then the result. Before the functions are deployed (Task 7) the expected result is "Link failed" (Google answers the missing function with an HTML 404 page, which Connect IQ cannot parse as JSON) or "Wrong code"; either proves the round trip. "No phone" means the simulator had no network.
4. Any button on the result returns to the start menu.

Repeat 1–2 on `fr265`. If any text clips or the digits overflow, fix it (smaller font for the picker, shorter string) before committing, and say what you changed.

- [ ] **Step 7: Commit**

```powershell
git add garmin
git commit -m "Link a Garmin watch from its start menu or from Garmin Connect`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Deploy and check end to end (with the user)

**Files:**
- Modify: `garmin/README.md`

- [ ] **Step 1: Ask before deploying**

Deploying publishes the four new functions to the live `refwatchapp` project (the existing `generateCustomToken` is redeployed unchanged). Ask the user in chat and wait for an explicit yes. Then:

```powershell
firebase deploy --only functions --project refwatchapp
```

Expected: lint passes (predeploy) and the four new functions are created. If the CLI asks to log in, stop and ask the user to run `firebase login` themselves.

- [ ] **Step 2: Check the watch endpoint**

```powershell
Invoke-WebRequest -Method POST -Uri "https://us-central1-refwatchapp.cloudfunctions.net/garminPair" -ContentType "application/json" -Body '{"code":"000000","deviceName":"test"}' -SkipHttpErrorCheck | Select-Object StatusCode, Content
```

Expected: `404` with `{"error":"not_found"}`. (This counts one failed attempt for this PC's IP; nine more in an hour would be refused with `429`.) If the URL itself is not found, the v2 function lives only at its `run.app` URL: read it from `firebase functions:list` and change `Api.BASE_URL` (Task 5) to that service's base, then rebuild.

- [ ] **Step 3: End to end (the user drives the phone)**

1. The user installs the debug phone app (`.\gradlew :mobile:installDebug` with the phone connected, or from Android Studio), opens Settings → "Link a Garmin watch", and reads out the code.
2. Simulator first: Link account → enter the code → expect "Linked"; the start menu shows "Linked"; within about 5 seconds the phone's code disappears and the watch's part number appears in the list.
3. The user taps Unlink on the phone → the row disappears.
4. Then on the fēnix 5X (sideload `RefWatch-fenix5x.prg` as before; the watch asks for the new Communications permission) with a fresh code: expect "Linked". This needs the watch paired with the Garmin Connect app on the user's phone.
5. A wrong code on the watch shows "Wrong code"; with the phone's Bluetooth off, "No phone".

- [ ] **Step 4: Record and commit**

Add a "Phase 3 results" section to `garmin/README.md`: the deployed function URLs, the end-to-end checklist outcomes on the simulator and the fēnix 5X, and these notes:

- The watch's pairing token has no expiry; unlinking on the phone is the only way to revoke it. (Phase 4's authenticated endpoints will reject an unlinked watch's token; until then nothing on the watch uses it.)
- `garminPairAttempts` documents are never deleted; each holds two fields per caller IP hash. A cleanup can come with phase 4 if they accumulate.

```powershell
git add garmin/README.md
git commit -m "Record Garmin phase 3 pairing results`n`nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Next plans

- Phase 4: game sync (`garminGames`, `garminUploadGame`, bearer-token authentication with `lastSeenAt` updates at most hourly, `sync/GameCache.mc`, `sync/UploadQueue.mc`, phone fixture cross-check of `EventJson`).
- Phase 5: release (Connect IQ store listing, phone release with the Garmin section after the store approves the watch app).
