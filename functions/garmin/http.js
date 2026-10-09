const pairing = require("./pairing");

const ERROR_NAMES = {
  400: "bad_request",
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
  const result = await pairDevice(db,
      {code: body.code, deviceName: body.deviceName, ip}, nowMs);
  if (result.status === 200) {
    res.status(200).json({token: result.token});
  } else {
    res.status(result.status).json({error: ERROR_NAMES[result.status]});
  }
}

module.exports = {clientIp, handlePairRequest};
