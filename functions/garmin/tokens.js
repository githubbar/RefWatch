const crypto = require("crypto");

/** How long a pairing code stays valid. */
const CODE_TTL_MS = 10 * 60 * 1000;

/**
 * A random 6-digit pairing code; leading zeros are kept.
 * @return {string} the code
 */
function newPairingCode() {
  return crypto.randomInt(0, 1000000).toString().padStart(6, "0");
}

/**
 * A watch's bearer token: 32 random bytes, base64url.
 * @return {string} the token
 */
function newDeviceToken() {
  return crypto.randomBytes(32).toString("base64url");
}

/**
 * Hex SHA-256, used so tokens and IPs are never stored as given.
 * @param {string} text what to hash
 * @return {string} 64 hex characters
 */
function sha256Hex(text) {
  return crypto.createHash("sha256").update(text).digest("hex");
}

/**
 * True for a string of exactly six decimal digits.
 * @param {*} value anything received from a client
 * @return {boolean} whether it is a well-formed pairing code
 */
function isPairingCode(value) {
  return typeof value === "string" && /^\d{6}$/.test(value);
}

module.exports = {
  CODE_TTL_MS,
  newPairingCode,
  newDeviceToken,
  sha256Hex,
  isPairingCode,
};
