const test = require("node:test");
const assert = require("node:assert/strict");
const {handlePairRequest, clientIp} = require("../garmin/http");

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

const fromIp = (ip) => ({"x-forwarded-for": ip});

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
    headers: fromIp("1.2.3.4"),
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
        {method: "POST", headers: fromIp("x"), body: {}},
        res, 5, pairing.pairDevice);
    assert.equal(res.statusCode, status);
    assert.deepEqual(res.body, {error: name});
  }
});

test("a body that is not an object is treated as empty", async () => {
  const res = fakeResponse();
  const pairing = fakePairing({status: 400});
  await handlePairRequest("db",
      {method: "POST", headers: fromIp("x"), body: "junk"},
      res, 5, pairing.pairDevice);
  assert.deepEqual(pairing.calls[0].input,
      {code: undefined, deviceName: undefined, ip: "x"});
});

test("a request with no usable client IP is a bad request", async () => {
  const res = fakeResponse();
  const pairing = fakePairing({status: 200, token: "t"});
  await handlePairRequest("db",
      {method: "POST", ip: "1.2.3.4", headers: {}, body: {code: "123456"}},
      res, 5, pairing.pairDevice);
  assert.equal(res.statusCode, 400);
  assert.deepEqual(res.body, {error: "bad_request"});
  assert.equal(pairing.calls.length, 0);
});

test("the IP passed on is the last X-Forwarded-For entry, not req.ip",
    async () => {
      const res = fakeResponse();
      const pairing = fakePairing({status: 404});
      await handlePairRequest("db", {
        method: "POST",
        ip: "6.6.6.6",
        headers: fromIp("6.6.6.6, 203.0.113.7"),
        body: {code: "123456"},
      }, res, 5, pairing.pairDevice);
      assert.equal(pairing.calls[0].input.ip, "203.0.113.7");
    });

test("clientIp takes the last non-empty, trimmed X-Forwarded-For entry",
    () => {
      const req = (xff) => ({headers: fromIp(xff)});
      assert.equal(clientIp(req("1.1.1.1")), "1.1.1.1");
      assert.equal(clientIp(req("1.1.1.1, 2.2.2.2")), "2.2.2.2");
      assert.equal(clientIp(req("1.1.1.1,  2.2.2.2  ")), "2.2.2.2");
      assert.equal(clientIp(req("1.1.1.1, 2.2.2.2, ,")), "2.2.2.2");
      assert.equal(clientIp(req("spoofed,2001:db8::1")), "2001:db8::1");
    });

test("clientIp falls back to the socket address, else null", () => {
  const socket = {remoteAddress: "10.0.0.1"};
  assert.equal(clientIp({headers: {}, socket}), "10.0.0.1");
  assert.equal(clientIp({socket}), "10.0.0.1");
  assert.equal(clientIp({headers: fromIp(" , "), socket}), "10.0.0.1");
  assert.equal(clientIp({headers: {}}), null);
  assert.equal(clientIp({headers: {}, socket: {}}), null);
  assert.equal(clientIp({headers: {}, socket: {remoteAddress: " "}}), null);
  assert.equal(clientIp({ip: "1.2.3.4"}), null);
});

test("a pairing error is a logged 500 that never logs the body",
    async (t) => {
      const logged = [];
      t.mock.method(console, "error", (...args) => logged.push(args));
      const res = fakeResponse();
      const failure = new Error("firestore down");
      const pairDevice = async () => {
        throw failure;
      };
      await handlePairRequest("db", {
        method: "POST",
        headers: fromIp("1.2.3.4"),
        body: {code: "987654", deviceName: "w"},
      }, res, 5, pairDevice);
      assert.equal(res.statusCode, 500);
      assert.deepEqual(res.body, {error: "internal"});
      assert.deepEqual(logged, [["garminPair failed", failure]]);
      assert.equal(JSON.stringify(logged).includes("987654"), false);
    });
