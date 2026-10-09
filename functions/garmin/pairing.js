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
