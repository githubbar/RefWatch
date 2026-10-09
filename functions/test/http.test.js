const test = require("node:test");
const assert = require("node:assert/strict");
const {handlePairRequest} = require("../garmin/http");

const fakeResponse = () => {
  const res = {statusCode: null, body: null};
  res.status = (code) => {
    res.statusCode = code;
    return res;
  };
  res.json = (body) => {
    res.body = body;
    return res;
  };
  return res;
};

const fakePairing = (result) => {
  const calls = [];
  const pairDevice = async (db, input, nowMs) => {
    calls.push({input, nowMs});
    return result;
  };
  return {calls, pairDevice};
};

test("only POST is accepted", async () => {
  const res = fakeResponse();
  const pairing = fakePairing({status: 200, token: "t"});
  await handlePairRequest(null, {method: "GET"}, res, 5, pairing.pairDevice);
  assert.equal(res.statusCode, 405);
  assert.deepEqual(res.body, {error: "method_not_allowed"});
  assert.equal(pairing.calls.length, 0);
});

test("a good request returns the token", async () => {
  const res = fakeResponse();
  const pairing = fakePairing({status: 200, token: "t"});
  const req = {
    method: "POST",
    ip: "1.2.3.4",
    body: {code: "123456", deviceName: "fenix", appVersion: "1"},
  };
  await handlePairRequest("db", req, res, 5, pairing.pairDevice);
  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.body, {token: "t"});
  assert.deepEqual(pairing.calls[0], {
    input: {code: "123456", deviceName: "fenix", ip: "1.2.3.4"},
    nowMs: 5,
  });
});

test("failures map to named errors", async () => {
  const cases = [
    [400, "bad_request"],
    [404, "not_found"],
    [429, "too_many_attempts"],
  ];
  for (const [status, name] of cases) {
    const res = fakeResponse();
    const pairing = fakePairing({status});
    await handlePairRequest("db",
        {method: "POST", ip: "x", body: {}}, res, 5, pairing.pairDevice);
    assert.equal(res.statusCode, status);
    assert.deepEqual(res.body, {error: name});
  }
});

test("a body that is not an object is treated as empty", async () => {
  const res = fakeResponse();
  const pairing = fakePairing({status: 400});
  await handlePairRequest("db",
      {method: "POST", ip: "x", body: "junk"}, res, 5, pairing.pairDevice);
  assert.deepEqual(pairing.calls[0].input,
      {code: undefined, deviceName: undefined, ip: "x"});
});
