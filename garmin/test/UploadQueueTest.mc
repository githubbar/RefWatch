import Toybox.Lang;
import Toybox.Test;

(:test)
function argbAddsAnOpaqueAlpha(logger as Logger) as Boolean {
    Test.assertEqual(-65536, UploadQueue.argb(0xFF0000));        // 0xFFFF0000
    Test.assertEqual(-16755201, UploadQueue.argb(0x0055FF));     // 0xFF0055FF
    Test.assertEqual(-1, UploadQueue.argb(0xFFFFFF));
    Test.assertEqual(-16777216, UploadQueue.argb(0x000000));
    return true;
}

(:test)
function bodyCarriesTheFinishedMatch(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(1000000l);
    m.addGoal(TEAM_HOME, 9, 1060000l);
    m.addCard(TEAM_AWAY, 4, CARD_YELLOW, 1120000l);
    m.finishGame(1200000l);
    var body = UploadQueue.body(m.toDict());
    Test.assertEqual("test-game", body["id"]);
    Test.assertEqual("Eagles", body["homeTeamName"]);
    Test.assertEqual("Hawks", body["awayTeamName"]);
    Test.assertEqual(-65536, body["homeTeamColorArgb"]);
    Test.assertEqual(-16755201, body["awayTeamColorArgb"]);
    Test.assertEqual(30, body["halfDurationMinutes"]);
    Test.assertEqual(5, body["halftimeDurationMinutes"]);
    Test.assertEqual(TEAM_HOME, body["kickOffTeam"]);
    Test.assertEqual(PHASE_GAME_ENDED, body["currentPhase"]);
    Test.assertEqual(1, body["homeScore"]);
    Test.assertEqual(0, body["awayScore"]);
    // A quick match has no scheduled start: the kick-off time stands in. Sent as a Double.
    Test.assert(body["gameDateTimeEpochMillis"] instanceof Double);
    Test.assertEqual(1000000.0d, body["gameDateTimeEpochMillis"]);
    var events = body["events"] as Array<Dictionary>;
    Test.assertEqual(4, events.size());
    Test.assertEqual("GOAL", events[1]["eventType"]);
    Test.assertEqual(9, events[1]["playerNumber"]);
    Test.assert(!body.hasKey("source"));
    return true;
}

(:test)
function bodyPrefersTheScheduledStart(logger as Logger) as Boolean {
    var setup = GameCache.setupFor(syncedGame("g1", 1791500400));
    var m = new MatchState(setup);
    m.kickOff(1791500460000l);
    m.finishGame(1791502000000l);
    var body = UploadQueue.body(m.toDict());
    Test.assertEqual("g1", body["id"]);
    Test.assertEqual(1791500400000.0d, body["gameDateTimeEpochMillis"]);
    return true;
}
