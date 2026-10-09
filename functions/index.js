const {onCall, onRequest, HttpsError} =
  require("firebase-functions/v2/https");
const {setGlobalOptions} = require("firebase-functions/v2");
const admin = require("firebase-admin");
const {getFirestore} = require("firebase-admin/firestore");
const pairing = require("./garmin/pairing");
const {handlePairRequest} = require("./garmin/http");

// Set global options for all v2 functions in this file
setGlobalOptions({maxInstances: 10});

admin.initializeApp();

exports.generateCustomToken = onCall(async (request) => {
  if (!request.auth) {
    // Use the imported HttpsError directly
    throw new HttpsError(
        "unauthenticated",
        "The function must be called while authenticated.",
    );
  }
  const uid = request.auth.uid;
  try {
    const customToken = await admin.auth().createCustomToken(uid);
    console.log(`Successfully created custom token for UID: ${uid}`);
    return {customToken: customToken};
  } catch (error) {
    console.error(`Error creating custom token for UID: ${uid}`, error);
    // Use the imported HttpsError directly
    throw new HttpsError(
        "internal",
        "Unable to create custom token.",
        error.message,
    );
  }
});

/**
 * The caller's uid, or an unauthenticated error.
 * @param {object} request the callable request
 * @return {string} uid
 */
function requireUid(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  return request.auth.uid;
}

// Phone: a 6-digit code the referee enters on the Garmin watch.
exports.createGarminPairingCode = onCall(async (request) => {
  const uid = requireUid(request);
  return pairing.createPairingCode(getFirestore(), uid, Date.now());
});

// Watch (through Garmin Connect): exchanges the code for a device token.
exports.garminPair = onRequest((req, res) =>
  handlePairRequest(getFirestore(), req, res, Date.now()));

// Phone: the user's linked Garmin watches.
exports.listGarminDevices = onCall(async (request) => {
  const uid = requireUid(request);
  return {devices: await pairing.listDevices(getFirestore(), uid)};
});

// Phone: unlinks one of the user's watches; its token stops working.
exports.unlinkGarminDevice = onCall(async (request) => {
  const uid = requireUid(request);
  const tokenHash = request.data ? request.data.tokenHash : undefined;
  if (!await pairing.unlinkDevice(getFirestore(), uid, tokenHash)) {
    throw new HttpsError("not-found", "No such watch.");
  }
  return {ok: true};
});
