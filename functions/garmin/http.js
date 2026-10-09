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
