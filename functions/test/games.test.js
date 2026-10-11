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

test("a device document without a uid gives null", async () => {
  await db.collection(DEVICES).doc(sha256Hex("tok")).set({
    deviceName: "006-B2604-00",
    createdAt: Timestamp.fromMillis(T0 - DAY),
    lastSeenAt: Timestamp.fromMillis(T0),
  });
  assert.equal(await devices.authenticateDevice(db, "tok", T0), null);
});

test("games whose id the upload would refuse are not listed", async () => {
  await addGame("alice", "bad.id");
  await addGame("alice", "bad id");
  await addGame("alice", "good-id_1");
  const list = await games.listGamesForWatch(db, "alice", T0);
  assert.deepEqual(list.map((g) => g.id), ["good-id_1"]);
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
