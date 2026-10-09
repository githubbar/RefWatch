const {Timestamp} = require("firebase-admin/firestore");
const tokens = require("./tokens");

const CODES = "garminPairingCodes";
const DEVICES = "garminDevices";
const ATTEMPTS = "garminPairAttempts";
const MAX_FAILED_ATTEMPTS = 10;
// One counter for all callers, so spreading guesses over many IPs (or many
// IPv6 /64s) still runs into a ceiling.
const GLOBAL_ATTEMPTS_ID = "_global";
const MAX_GLOBAL_FAILED_ATTEMPTS = 1000;
const ATTEMPT_WINDOW_MS = 60 * 60 * 1000;
const MAX_CODE_TRIES = 10;
const MAX_DEVICE_NAME = 64;
const DEFAULT_DEVICE_NAME = "Garmin watch";
const MAX_BATCH_WRITES = 500;

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
 * Parses one side of an IPv6 address (split at "::") into 16-bit numbers.
 * @param {string} part colon-separated groups, possibly empty
 * @param {boolean} dottedTail whether the last group may be dotted IPv4
 * @return {?Array<number>} the hextets, or null if malformed
 */
function parseHextets(part, dottedTail) {
  if (part === "") {
    return [];
  }
  const groups = part.split(":");
  const hextets = [];
  for (let i = 0; i < groups.length; i++) {
    const group = groups[i];
    if (dottedTail && i === groups.length - 1 && group.includes(".")) {
      const octets = group.split(".");
      if (octets.length !== 4 ||
        !octets.every((o) => /^\d{1,3}$/.test(o) && Number(o) <= 255)) {
        return null;
      }
      const n = octets.map(Number);
      hextets.push(n[0] * 256 + n[1], n[2] * 256 + n[3]);
    } else if (/^[0-9a-f]{1,4}$/.test(group)) {
      hextets.push(parseInt(group, 16));
    } else {
      return null;
    }
  }
  return hextets;
}

/**
 * Expands an IPv6 address (with "::" compression, a zone id or a dotted
 * IPv4 tail) into its eight 16-bit numbers.
 * @param {string} ip an IPv6 address
 * @return {?Array<number>} eight hextets, or null if malformed
 */
function expandIpv6(ip) {
  const text = ip.toLowerCase().split("%")[0];
  const halves = text.split("::");
  if (halves.length > 2) {
    return null;
  }
  if (halves.length === 1) {
    const all = parseHextets(text, true);
    return all !== null && all.length === 8 ? all : null;
  }
  const head = parseHextets(halves[0], false);
  const tail = parseHextets(halves[1], true);
  if (head === null || tail === null || head.length + tail.length > 7) {
    return null;
  }
  const zeros = new Array(8 - head.length - tail.length).fill(0);
  return head.concat(zeros, tail);
}

/**
 * What failed attempts are counted against: an IPv4 address as is, an IPv6
 * address by its /64 (one subscriber usually holds a whole /64), and an
 * IPv4-mapped IPv6 address as its IPv4 address.
 * @param {string} ip the caller's IP (non-empty)
 * @return {string} the rate-limit key, before hashing
 */
function rateLimitKey(ip) {
  if (!ip.includes(":")) {
    return ip;
  }
  const h = expandIpv6(ip);
  if (h === null) {
    return ip.toLowerCase();
  }
  if (h.slice(0, 5).every((x) => x === 0) && h[5] === 0xffff) {
    return [h[6] >> 8, h[6] & 255, h[7] >> 8, h[7] & 255].join(".");
  }
  return h.slice(0, 4).map((x) => x.toString(16)).join(":") + "::/64";
}

/**
 * A failure counter's state at nowMs: its count in the current window, and
 * when that window started (a fresh window when the old one has ended).
 * @param {FirebaseFirestore.DocumentSnapshot} doc the counter document
 * @param {number} nowMs current time, epoch ms
 * @return {{count: number, windowStart: Timestamp}} the live window
 */
function failureWindow(doc, nowMs) {
  const inWindow = doc.exists &&
    nowMs - doc.get("windowStart").toMillis() < ATTEMPT_WINDOW_MS;
  return inWindow ?
    {count: doc.get("count"), windowStart: doc.get("windowStart")} :
    {count: 0, windowStart: Timestamp.fromMillis(nowMs)};
}

/**
 * Records one more failure in a counter's window.
 * @param {FirebaseFirestore.Transaction} tx the transaction
 * @param {FirebaseFirestore.DocumentReference} ref the counter document
 * @param {{count: number, windowStart: Timestamp}} window from failureWindow
 */
function countFailure(tx, ref, window) {
  tx.set(ref, {
    count: window.count + 1,
    windowStart: window.windowStart,
    expiresAt: Timestamp.fromMillis(
        window.windowStart.toMillis() + ATTEMPT_WINDOW_MS),
  });
}

/**
 * Exchanges a pairing code for a device token. Failed attempts are counted
 * per caller (hashed rateLimitKey of the IP) and across all callers; after
 * MAX_FAILED_ATTEMPTS from one caller, or MAX_GLOBAL_FAILED_ATTEMPTS in all,
 * within an hour, callers are refused even with a good code.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {{code: *, deviceName: *, ip: string}} input the watch's request;
 *     ip is a non-empty string
 * @param {number} nowMs current time, epoch ms
 * @param {function(): string} makeToken token generator (tests pass their own)
 * @return {Promise<object>} {status: 200, token} or {status: 400|404|429}
 */
async function pairDevice(db, input, nowMs,
    makeToken = tokens.newDeviceToken) {
  const ipKey = tokens.sha256Hex(rateLimitKey(input.ip));
  const attemptRef = db.collection(ATTEMPTS).doc(ipKey);
  const globalRef = db.collection(ATTEMPTS).doc(GLOBAL_ATTEMPTS_ID);
  return db.runTransaction(async (tx) => {
    const [attempt, globalDoc] = await tx.getAll(attemptRef, globalRef);
    const callerWindow = failureWindow(attempt, nowMs);
    const globalWindow = failureWindow(globalDoc, nowMs);
    if (callerWindow.count >= MAX_FAILED_ATTEMPTS ||
      globalWindow.count >= MAX_GLOBAL_FAILED_ATTEMPTS) {
      return {status: 429};
    }
    const wellFormed = tokens.isPairingCode(input.code);
    const codeDoc = wellFormed ?
      await tx.get(db.collection(CODES).doc(input.code)) : null;
    const valid = codeDoc !== null && codeDoc.exists &&
      codeDoc.get("expiresAt").toMillis() > nowMs;
    if (!valid) {
      countFailure(tx, attemptRef, callerWindow);
      countFailure(tx, globalRef, globalWindow);
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

/**
 * Removes everything Garmin-related a user owns: their linked watches (so
 * the tokens stop working) and any pending pairing code. Used when the
 * account is deleted.
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {string} uid the deleted user
 * @return {Promise<number>} how many documents were deleted
 */
async function deleteUserGarminData(db, uid) {
  const snapshots = await Promise.all([DEVICES, CODES].map(
      (name) => db.collection(name).where("uid", "==", uid).get()));
  const refs = snapshots.flatMap((snapshot) => snapshot.docs)
      .map((doc) => doc.ref);
  for (let i = 0; i < refs.length; i += MAX_BATCH_WRITES) {
    const batch = db.batch();
    refs.slice(i, i + MAX_BATCH_WRITES).forEach((ref) => batch.delete(ref));
    await batch.commit();
  }
  return refs.length;
}

module.exports = {
  CODES,
  DEVICES,
  ATTEMPTS,
  MAX_FAILED_ATTEMPTS,
  GLOBAL_ATTEMPTS_ID,
  MAX_GLOBAL_FAILED_ATTEMPTS,
  rateLimitKey,
  createPairingCode,
  pairDevice,
  listDevices,
  unlinkDevice,
  deleteUserGarminData,
};
