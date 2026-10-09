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

module.exports = {
  MAX_EVENTS,
  cleanUpload,
  uploadGame,
  MAX_GAMES,
  PAST_WINDOW_MS,
  AHEAD_WINDOW_MS,
  gamesOf,
  listGamesForWatch,
};
