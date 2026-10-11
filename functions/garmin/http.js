const pairing = require("./pairing");
const devices = require("./devices");
const games = require("./games");

/** The largest upload body accepted, in bytes. */
const MAX_UPLOAD_BYTES = 64 * 1024;

const ERROR_NAMES = {
  400: "bad_request",
  401: "unauthorized",
  404: "not_found",
  429: "too_many_attempts",
};

/**
 * The caller's IP for rate limiting. Not req.ip: the Functions Framework
 * trusts the proxy, so req.ip is the left-most X-Forwarded-For entry, which
 * the client controls. Google's front end appends the address it saw, so the
 * last entry is the real one.
 * @param {object} req the HTTP request
 * @return {?string} the IP, or null when there is nothing usable
 */
function clientIp(req) {
  const headers = req.headers || {};
  let forwarded = headers["x-forwarded-for"];
  if (Array.isArray(forwarded)) {
    forwarded = forwarded.join(",");
  }
  if (typeof forwarded === "string") {
    const entries = forwarded.split(",")
        .map((entry) => entry.trim())
        .filter((entry) => entry !== "");
    if (entries.length > 0) {
      return entries[entries.length - 1];
    }
  }
  const socketIp = req.socket && req.socket.remoteAddress;
  if (typeof socketIp === "string" && socketIp.trim() !== "") {
    return socketIp.trim();
  }
  return null;
}

/**
 * Handles POST garminPair from a watch (through the Garmin Connect app).
 * @param {FirebaseFirestore.Firestore} db Firestore
 * @param {object} req the HTTP request (method, headers, parsed JSON body)
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
  const ip = clientIp(req);
  if (ip === null) {
    res.status(400).json({error: ERROR_NAMES[400]});
    return;
  }
  const body = req.body !== null && typeof req.body === "object" ?
    req.body : {};
  let result;
  try {
    result = await pairDevice(db,
        {code: body.code, deviceName: body.deviceName, ip}, nowMs);
  } catch (err) {
    // Never log the request body: it holds the pairing code.
    console.error("garminPair failed", err);
    res.status(500).json({error: "internal"});
    return;
  }
  if (result.status === 200) {
    res.status(200).json({token: result.token});
  } else {
    res.status(result.status).json({error: ERROR_NAMES[result.status]});
  }
}

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

module.exports = {
  MAX_UPLOAD_BYTES,
  clientIp,
  bearerToken,
  handlePairRequest,
  handleGamesRequest,
  handleUploadRequest,
};
