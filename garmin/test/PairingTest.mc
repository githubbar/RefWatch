import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

(:test)
function aSuccessfulReplyStoresTheToken(logger as Logger) as Boolean {
    Pairing.forget();
    Test.assertEqual(PAIR_LINKED, Pairing.applyResponse(200, {"token" => "abc"}));
    Test.assert(Pairing.isLinked());
    Test.assertEqual("abc", Pairing.token());
    Pairing.forget();
    Test.assert(!Pairing.isLinked());
    return true;
}

(:test)
function aReplyWithoutATokenIsAFailure(logger as Logger) as Boolean {
    Pairing.forget();
    Test.assertEqual(PAIR_FAILED, Pairing.applyResponse(200, {"other" => 1}));
    Test.assertEqual(PAIR_FAILED, Pairing.applyResponse(200, null));
    Test.assert(!Pairing.isLinked());
    return true;
}

(:test)
function serverRefusalsMapToStatuses(logger as Logger) as Boolean {
    Pairing.forget();
    Test.assertEqual(PAIR_BAD_CODE, Pairing.applyResponse(404, {"error" => "not_found"}));
    Test.assertEqual(PAIR_BAD_CODE, Pairing.applyResponse(400, {"error" => "bad_request"}));
    Test.assertEqual(PAIR_TOO_MANY, Pairing.applyResponse(429, {"error" => "too_many_attempts"}));
    Test.assertEqual(PAIR_FAILED, Pairing.applyResponse(500, null));
    Test.assert(!Pairing.isLinked());
    return true;
}

// Negative codes are Connect IQ's own: above -400 the phone or the network was not reachable;
// -400 and below, a reply came back that could not be parsed.
(:test)
function connectionErrorsMeanOffline(logger as Logger) as Boolean {
    Test.assertEqual(PAIR_OFFLINE, Pairing.applyResponse(-104, null));
    Test.assertEqual(PAIR_OFFLINE, Pairing.applyResponse(-2, null));
    Test.assertEqual(PAIR_OFFLINE, Pairing.applyResponse(-300, null));
    Test.assertEqual(PAIR_FAILED, Pairing.applyResponse(-400, null));
    return true;
}

(:test)
function aFailedRelinkKeepsTheExistingToken(logger as Logger) as Boolean {
    Pairing.forget();
    Pairing.applyResponse(200, {"token" => "first"});
    Pairing.applyResponse(404, {"error" => "not_found"});
    Test.assertEqual("first", Pairing.token());
    Pairing.forget();
    return true;
}

(:test)
function onlySixDigitsAreACode(logger as Logger) as Boolean {
    Test.assert(Pairing.isCode("012345"));
    Test.assert(!Pairing.isCode("12345"));
    Test.assert(!Pairing.isCode("1234567"));
    Test.assert(!Pairing.isCode("12a456"));
    Test.assert(!Pairing.isCode(""));
    return true;
}

(:test)
function thePairRequestCarriesTheCodeAndTheAppVersion(logger as Logger) as Boolean {
    var body = Pairing.requestBody("012345");
    Test.assertEqual("012345", body["code"]);
    Test.assertEqual(Settings.appVersion(), body["appVersion"]);
    Test.assert(body["deviceName"] instanceof String);
    return true;
}
