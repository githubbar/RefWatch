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
  const uid = doc.get("uid");
  return typeof uid === "string" && uid.length > 0 ? uid : null;
}

module.exports = {LAST_SEEN_INTERVAL_MS, authenticateDevice};
