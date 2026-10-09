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
