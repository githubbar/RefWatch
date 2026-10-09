import Toybox.Lang;
import Toybox.Test;

(:debug)
const SEC = 1000l;

(:test)
function halfBuzzesAtRegulationThenEveryThirtySeconds(logger as Logger) as Boolean {
    var m = testMatch();                          // 30-minute halves
    m.kickOff(T0);
    Test.assertEqual(ALERT_NONE, m.takeAlert(T0 + 30 * MIN - SEC));
    Test.assertEqual(ALERT_PERIOD_END, m.takeAlert(T0 + 30 * MIN));
    Test.assertEqual(ALERT_NONE, m.takeAlert(T0 + 30 * MIN + 29 * SEC));
    Test.assertEqual(ALERT_REMINDER, m.takeAlert(T0 + 30 * MIN + 30 * SEC));
    Test.assertEqual(ALERT_NONE, m.takeAlert(T0 + 30 * MIN + 31 * SEC));
    Test.assertEqual(ALERT_REMINDER, m.takeAlert(T0 + 31 * MIN));
    return true;
}

// A watch that was busy for a while gives one reminder, not one for every 30 s it missed.
(:test)
function missedRemindersCollapseIntoOne(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.takeAlert(T0 + 30 * MIN);
    Test.assertEqual(ALERT_REMINDER, m.takeAlert(T0 + 32 * MIN));
    Test.assertEqual(ALERT_NONE, m.takeAlert(T0 + 32 * MIN + 10 * SEC));
    Test.assertEqual(ALERT_REMINDER, m.takeAlert(T0 + 32 * MIN + 30 * SEC));
    return true;
}

(:test)
function noRemindersWhilePaused(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.takeAlert(T0 + 30 * MIN);
    m.pause(T0 + 30 * MIN + 10 * SEC);
    Test.assertEqual(ALERT_NONE, m.takeAlert(T0 + 35 * MIN));
    m.resume(T0 + 35 * MIN);                      // added time resumes at 0:10
    Test.assertEqual(ALERT_NONE, m.takeAlert(T0 + 35 * MIN + 19 * SEC));
    Test.assertEqual(ALERT_REMINDER, m.takeAlert(T0 + 35 * MIN + 20 * SEC));
    return true;
}

(:test)
function breakBuzzesOnceWithoutReminders(logger as Logger) as Boolean {
    var m = testMatch();                          // 5-minute break
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    Test.assertEqual(ALERT_PERIOD_END, m.takeAlert(T0 + 35 * MIN));
    Test.assertEqual(ALERT_NONE, m.takeAlert(T0 + 36 * MIN));
    Test.assertEqual(ALERT_NONE, m.takeAlert(T0 + 40 * MIN));
    return true;
}

(:test)
function remindersRestartInTheSecondHalf(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.takeAlert(T0 + 30 * MIN);
    m.takeAlert(T0 + 31 * MIN);
    m.endPeriod(T0 + 32 * MIN);
    m.kickOff(T0 + 37 * MIN);
    Test.assertEqual(ALERT_PERIOD_END, m.takeAlert(T0 + 67 * MIN));
    Test.assertEqual(ALERT_REMINDER, m.takeAlert(T0 + 67 * MIN + 30 * SEC));
    return true;
}

(:test)
function reminderCountSurvivesSaveAndLoad(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.takeAlert(T0 + 30 * MIN);
    m.takeAlert(T0 + 30 * MIN + 30 * SEC);
    MatchStore.save(m);
    var loaded = MatchStore.load() as MatchState;
    Test.assertEqual(ALERT_NONE, loaded.takeAlert(T0 + 30 * MIN + 40 * SEC));
    Test.assertEqual(ALERT_REMINDER, loaded.takeAlert(T0 + 31 * MIN));
    MatchStore.clear();
    return true;
}

(:test)
function resetPeriodClockRestartsTheHalfAtZeroPaused(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.takeAlert(T0 + 30 * MIN);
    m.resetPeriodClock(T0 + 31 * MIN);
    Test.assertEqual(PHASE_FIRST_HALF, m.phase);
    Test.assert(m.isPaused());
    Test.assertEqual(0l, m.elapsedMs(T0 + 40 * MIN));
    m.resume(T0 + 40 * MIN);
    Test.assertEqual(MIN, m.elapsedMs(T0 + 41 * MIN));
    Test.assertEqual(ALERT_PERIOD_END, m.takeAlert(T0 + 70 * MIN));   // the alert is re-armed
    return true;
}

(:test)
function resetPeriodClockOnlyActsDuringAHalf(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.resetPeriodClock(T0 + 31 * MIN);
    Test.assert(!m.isPaused());
    Test.assertEqual(MIN, m.elapsedMs(T0 + 31 * MIN));
    return true;
}

(:test)
function finishGameEndsTheMatchFromTheFirstHalf(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addGoal(TEAM_HOME, null, T0 + MIN);
    m.finishGame(T0 + 20 * MIN);
    Test.assertEqual(PHASE_GAME_ENDED, m.phase);
    Test.assertEqual(1, m.homeScore);
    var e = m.events[m.events.size() - 1];
    Test.assertEqual(PHASE_GAME_ENDED, e["newPhase"]);
    Test.assertEqual((20 * MIN).toDouble(), e["gameTimeMillis"]);
    Test.assertEqual(ALERT_NONE, m.takeAlert(T0 + 40 * MIN));
    return true;
}

(:test)
function finishGameEndsTheMatchFromHalfTime(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.finishGame(T0 + 32 * MIN);
    Test.assertEqual(PHASE_GAME_ENDED, m.phase);
    return true;
}

(:test)
function finishGameDoesNothingBeforeKickOff(logger as Logger) as Boolean {
    var m = testMatch();
    m.finishGame(T0);
    Test.assertEqual(PHASE_PRE_GAME, m.phase);
    Test.assertEqual(0, m.events.size());
    return true;
}

(:test)
function replaySetupKeepsTeamsAndTheFirstHalfKickOff(logger as Logger) as Boolean {
    var m = testMatch();                          // HOME kicks off the 1st half
    m.recordActivity = true;
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.kickOff(T0 + 35 * MIN);                     // AWAY kicks off the 2nd half
    m.addGoal(TEAM_AWAY, 7, T0 + 40 * MIN);
    var s = m.replaySetup();
    Test.assertEqual(m.gameId, s["id"]);
    Test.assertEqual("Eagles", s["homeName"]);
    Test.assertEqual("Hawks", s["awayName"]);
    Test.assertEqual(0xFF0000, s["homeColor"]);
    Test.assertEqual(0x0055FF, s["awayColor"]);
    Test.assertEqual(30, s["halfMinutes"]);
    Test.assertEqual(5, s["halftimeMinutes"]);
    Test.assertEqual(TEAM_HOME, s["kickOffTeam"]);
    Test.assertEqual(true, s["recordActivity"]);
    var fresh = new MatchState(s);
    Test.assertEqual(PHASE_PRE_GAME, fresh.phase);
    Test.assertEqual(0, fresh.awayScore);
    Test.assertEqual(0, fresh.events.size());
    return true;
}

(:test)
function replaySetupBeforeTheSecondHalfKeepsTheKickOffTeam(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    Test.assertEqual(TEAM_HOME, m.replaySetup()["kickOffTeam"]);
    return true;
}
