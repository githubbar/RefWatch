const test = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const pairing = require("../garmin/pairing");
const {sha256Hex} = require("../garmin/tokens");

initializeApp({projectId: "demo-refwatch"});
const db = getFirestore();
const T0 = Date.UTC(2026, 9, 9, 12, 0, 0);
const MIN = 60 * 1000;

const clearFirestore = async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  const url = `http://${host}/emulator/v1/projects/demo-refwatch` +
    "/databases/(default)/documents";
  await fetch(url, {method: "DELETE"});
};

test.beforeEach(clearFirestore);

test("a new code is 6 digits and expires in 10 minutes", async () => {
  const result = await pairing.createPairingCode(db, "alice", T0);
  assert.match(result.code, /^\d{6}$/);
  assert.equal(result.expiresAt, T0 + 10 * MIN);
  const doc = await db.collection(pairing.CODES).doc(result.code).get();
  assert.equal(doc.get("uid"), "alice");
  assert.equal(doc.get("expiresAt").toMillis(), T0 + 10 * MIN);
});

test("a second code replaces the caller's first", async () => {
  const first = await pairing.createPairingCode(
      db, "alice", T0, () => "111111");
  const second = await pairing.createPairingCode(
      db, "alice", T0, () => "222222");
  assert.equal(first.code, "111111");
  assert.equal(second.code, "222222");
  const old = await db.collection(pairing.CODES).doc("111111").get();
  assert.equal(old.exists, false);
});

test("a code in use by someone else is skipped", async () => {
  await pairing.createPairingCode(db, "bob", T0, () => "333333");
  const codes = ["333333", "444444"];
  const result = await pairing.createPairingCode(
      db, "alice", T0, () => codes.shift());
  assert.equal(result.code, "444444");
  const bobs = await db.collection(pairing.CODES).doc("333333").get();
  assert.equal(bobs.get("uid"), "bob");
});

test("an expired code can be handed out again", async () => {
  await pairing.createPairingCode(db, "bob", T0, () => "555555");
  const later = T0 + 11 * MIN;
  const result = await pairing.createPairingCode(
      db, "alice", later, () => "555555");
  assert.equal(result.code, "555555");
});

test("pairing with a valid code returns a token and links the device",
    async () => {
      await pairing.createPairingCode(db, "alice", T0, () => "123456");
      const result = await pairing.pairDevice(db,
          {code: "123456", deviceName: "006-B2604-00", ip: "1.2.3.4"},
          T0 + MIN, () => "token-abc");
      assert.deepEqual(result, {status: 200, token: "token-abc"});
      const device = await db.collection(pairing.DEVICES)
          .doc(sha256Hex("token-abc")).get();
      assert.equal(device.get("uid"), "alice");
      assert.equal(device.get("deviceName"), "006-B2604-00");
      assert.equal(device.get("createdAt").toMillis(), T0 + MIN);
      assert.equal(device.get("lastSeenAt").toMillis(), T0 + MIN);
    });

test("a code works only once", async () => {
  await pairing.createPairingCode(db, "alice", T0, () => "123456");
  const input = {code: "123456", deviceName: "w", ip: "1.2.3.4"};
  await pairing.pairDevice(db, input, T0 + MIN);
  const again = await pairing.pairDevice(db, input, T0 + MIN);
  assert.deepEqual(again, {status: 404});
});

test("an expired code is refused", async () => {
  await pairing.createPairingCode(db, "alice", T0, () => "123456");
  const result = await pairing.pairDevice(db,
      {code: "123456", deviceName: "w", ip: "1.2.3.4"}, T0 + 10 * MIN);
  assert.deepEqual(result, {status: 404});
});

test("a malformed code is a bad request", async () => {
  for (const code of ["12345", "1234567", "12a456", 123456, undefined]) {
    const result = await pairing.pairDevice(db,
        {code, deviceName: "w", ip: "1.2.3.4"}, T0);
    assert.deepEqual(result, {status: 400}, `code ${code}`);
  }
});

test("ten failures in an hour block the caller, even with a good code",
    async () => {
      await pairing.createPairingCode(db, "alice", T0, () => "123456");
      for (let i = 0; i < pairing.MAX_FAILED_ATTEMPTS; i++) {
        await pairing.pairDevice(db,
            {code: "000000", deviceName: "w", ip: "9.9.9.9"}, T0 + i);
      }
      const blocked = await pairing.pairDevice(db,
          {code: "123456", deviceName: "w", ip: "9.9.9.9"}, T0 + MIN);
      assert.deepEqual(blocked, {status: 429});
      const otherIp = await pairing.pairDevice(db,
          {code: "123456", deviceName: "w", ip: "8.8.8.8"}, T0 + MIN);
      assert.equal(otherIp.status, 200);
    });

test("the failure window resets after an hour", async () => {
  for (let i = 0; i < pairing.MAX_FAILED_ATTEMPTS; i++) {
    await pairing.pairDevice(db,
        {code: "000000", deviceName: "w", ip: "9.9.9.9"}, T0);
  }
  await pairing.createPairingCode(
      db, "alice", T0 + 61 * MIN, () => "123456");
  const result = await pairing.pairDevice(db,
      {code: "123456", deviceName: "w", ip: "9.9.9.9"}, T0 + 61 * MIN);
  assert.equal(result.status, 200);
});

test("the attempt record is keyed by a hash of the IP, not the IP",
    async () => {
      await pairing.pairDevice(db,
          {code: "000000", deviceName: "w", ip: "9.9.9.9"}, T0);
      const raw = await db.collection(pairing.ATTEMPTS).doc("9.9.9.9").get();
      assert.equal(raw.exists, false);
      const hashed = await db.collection(pairing.ATTEMPTS)
          .doc(sha256Hex("9.9.9.9")).get();
      assert.equal(hashed.get("count"), 1);
      assert.equal(hashed.get("expiresAt").toMillis(), T0 + 60 * MIN);
    });

test("rateLimitKey keeps IPv4 and cuts IPv6 to its /64", () => {
  const key = pairing.rateLimitKey;
  assert.equal(key("9.9.9.9"), "9.9.9.9");
  assert.equal(key("2001:db8:1:2:3:4:5:6"), "2001:db8:1:2::/64");
  assert.equal(key("2001:0DB8:0001:0002::1"), "2001:db8:1:2::/64");
  assert.equal(key("2001:db8::1"), "2001:db8:0:0::/64");
  assert.equal(key("2001:db8:1:2::"), "2001:db8:1:2::/64");
  assert.equal(key("::1"), "0:0:0:0::/64");
  assert.equal(key("fe80::1%eth0"), "fe80:0:0:0::/64");
  assert.equal(key("::ffff:1.2.3.4"), "1.2.3.4");
  assert.equal(key("::FFFF:102:304"), "1.2.3.4");
  assert.equal(key("0:0:0:0:0:ffff:1.2.3.4"), "1.2.3.4");
});

test("IPv6 addresses in one /64 share a failure count", async () => {
  await pairing.createPairingCode(db, "alice", T0, () => "123456");
  for (let i = 0; i < pairing.MAX_FAILED_ATTEMPTS; i++) {
    await pairing.pairDevice(db,
        {code: "000000", deviceName: "w", ip: `2001:db8:1:2::${i + 1}`},
        T0 + i);
  }
  const sameSlash64 = await pairing.pairDevice(db,
      {code: "123456", deviceName: "w", ip: "2001:db8:1:2:ffff::9"},
      T0 + MIN);
  assert.deepEqual(sameSlash64, {status: 429});
  const otherSlash64 = await pairing.pairDevice(db,
      {code: "123456", deviceName: "w", ip: "2001:db8:1:3::1"}, T0 + MIN);
  assert.equal(otherSlash64.status, 200);
});

test("an IPv4-mapped IPv6 address counts against the IPv4 address",
    async () => {
      await pairing.pairDevice(db,
          {code: "000000", deviceName: "w", ip: "::ffff:9.9.9.9"}, T0);
      const ipv4 = await db.collection(pairing.ATTEMPTS)
          .doc(sha256Hex("9.9.9.9")).get();
      assert.equal(ipv4.get("count"), 1);
    });

test("every failure also counts toward the global ceiling", async () => {
  await pairing.createPairingCode(db, "alice", T0, () => "123456");
  await pairing.pairDevice(db,
      {code: "000000", deviceName: "w", ip: "9.9.9.9"}, T0);
  await pairing.pairDevice(db,
      {code: "000001", deviceName: "w", ip: "8.8.8.8"}, T0 + MIN);
  await pairing.pairDevice(db,
      {code: "123456", deviceName: "w", ip: "7.7.7.7"}, T0 + MIN);
  const global = await db.collection(pairing.ATTEMPTS)
      .doc(pairing.GLOBAL_ATTEMPTS_ID).get();
  assert.equal(global.get("count"), 2);
  assert.equal(global.get("windowStart").toMillis(), T0);
  assert.equal(global.get("expiresAt").toMillis(), T0 + 60 * MIN);
});

test("the global ceiling blocks a fresh IP, and resets after an hour",
    async () => {
      assert.equal(pairing.MAX_GLOBAL_FAILED_ATTEMPTS, 1000);
      await db.collection(pairing.ATTEMPTS)
          .doc(pairing.GLOBAL_ATTEMPTS_ID).set({
            count: pairing.MAX_GLOBAL_FAILED_ATTEMPTS,
            windowStart: Timestamp.fromMillis(T0),
            expiresAt: Timestamp.fromMillis(T0 + 60 * MIN),
          });
      await pairing.createPairingCode(db, "alice", T0, () => "123456");
      const blocked = await pairing.pairDevice(db,
          {code: "123456", deviceName: "w", ip: "5.5.5.5"}, T0 + MIN);
      assert.deepEqual(blocked, {status: 429});
      await pairing.createPairingCode(
          db, "alice", T0 + 61 * MIN, () => "123456");
      const later = await pairing.pairDevice(db,
          {code: "123456", deviceName: "w", ip: "5.5.5.5"}, T0 + 61 * MIN);
      assert.equal(later.status, 200);
    });

test("one below the global ceiling still lets a good code through",
    async () => {
      await db.collection(pairing.ATTEMPTS)
          .doc(pairing.GLOBAL_ATTEMPTS_ID).set({
            count: pairing.MAX_GLOBAL_FAILED_ATTEMPTS - 1,
            windowStart: Timestamp.fromMillis(T0),
            expiresAt: Timestamp.fromMillis(T0 + 60 * MIN),
          });
      await pairing.createPairingCode(db, "alice", T0, () => "123456");
      const result = await pairing.pairDevice(db,
          {code: "123456", deviceName: "w", ip: "5.5.5.5"}, T0 + MIN);
      assert.equal(result.status, 200);
    });

test("the device name is trimmed, capped at 64 and defaulted", async () => {
  const names = ["  fenix  ", "x".repeat(100), "", undefined];
  const expected = ["fenix", "x".repeat(64), "Garmin watch", "Garmin watch"];
  for (let i = 0; i < names.length; i++) {
    const code = `10000${i}`;
    await pairing.createPairingCode(db, "alice", T0, () => code);
    const result = await pairing.pairDevice(db,
        {code, deviceName: names[i], ip: "1.1.1.1"}, T0, () => `tok${i}`);
    const device = await db.collection(pairing.DEVICES)
        .doc(sha256Hex(result.token)).get();
    assert.equal(device.get("deviceName"), expected[i]);
  }
});

test("listDevices returns only the caller's devices, newest first",
    async () => {
      const add = (id, uid, createdMs) => db.collection(pairing.DEVICES)
          .doc(id).set({
            uid,
            deviceName: id,
            createdAt: Timestamp.fromMillis(createdMs),
            lastSeenAt: Timestamp.fromMillis(createdMs + 5),
          });
      await add("old", "alice", T0);
      await add("new", "alice", T0 + MIN);
      await add("bobs", "bob", T0);
      const devices = await pairing.listDevices(db, "alice");
      assert.deepEqual(devices, [
        {id: "new", deviceName: "new", createdAt: T0 + MIN,
          lastSeenAt: T0 + MIN + 5},
        {id: "old", deviceName: "old", createdAt: T0, lastSeenAt: T0 + 5},
      ]);
    });

test("unlinkDevice deletes only the caller's own device", async () => {
  await db.collection(pairing.DEVICES).doc("h1").set({
    uid: "alice",
    deviceName: "w",
    createdAt: Timestamp.fromMillis(T0),
    lastSeenAt: Timestamp.fromMillis(T0),
  });
  assert.equal(await pairing.unlinkDevice(db, "bob", "h1"), false);
  assert.equal(
      (await db.collection(pairing.DEVICES).doc("h1").get()).exists, true);
  assert.equal(await pairing.unlinkDevice(db, "alice", "missing"), false);
  assert.equal(await pairing.unlinkDevice(db, "alice", 42), false);
  assert.equal(await pairing.unlinkDevice(db, "alice", "h1"), true);
  assert.equal(
      (await db.collection(pairing.DEVICES).doc("h1").get()).exists, false);
});

test("deleteUserGarminData removes only that user's watches and codes",
    async () => {
      const device = (uid) => ({
        uid,
        deviceName: "w",
        createdAt: Timestamp.fromMillis(T0),
        lastSeenAt: Timestamp.fromMillis(T0),
      });
      for (let i = 0; i < 3; i++) {
        await db.collection(pairing.DEVICES).doc(`alice${i}`)
            .set(device("alice"));
      }
      await db.collection(pairing.DEVICES).doc("bob0").set(device("bob"));
      await pairing.createPairingCode(db, "alice", T0, () => "111111");
      await pairing.createPairingCode(db, "bob", T0, () => "222222");

      await pairing.deleteUserGarminData(db, "alice");

      const devices = await db.collection(pairing.DEVICES).get();
      assert.deepEqual(devices.docs.map((d) => d.id), ["bob0"]);
      const codes = await db.collection(pairing.CODES).get();
      assert.deepEqual(codes.docs.map((d) => d.id), ["222222"]);
    });

test("deleteUserGarminData copes with a user with nothing to delete",
    async () => {
      await pairing.deleteUserGarminData(db, "nobody");
    });
