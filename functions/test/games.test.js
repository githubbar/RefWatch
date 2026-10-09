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
  await addGame("alice", "tooOld",
      {gameDateTimeEpochMillis: T0 - 6 * HOUR - 1});
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
  await addGame("alice", "g1",
      {homeTeamName: long, awayTeamName: "😀".repeat(30)});
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
