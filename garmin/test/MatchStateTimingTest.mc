import Toybox.Lang;
import Toybox.Test;

const T0 = 1000000000000l; // an arbitrary wall-clock start, epoch ms
const MIN = 60000l;

(:test)
function kickOffStartsFirstHalf(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    Test.assertEqual(PHASE_FIRST_HALF, m.phase);
    Test.assertEqual(T0, m.startedAtMs);
    Test.assertEqual(0l, m.elapsedMs(T0));
    Test.assertEqual(1, m.events.size());
    var e = m.events[0];
    Test.assertEqual("PHASE_CHANGE", e["eventType"]);
    Test.assertEqual(PHASE_FIRST_HALF, e["newPhase"]);
    Test.assertEqual(0.0d, e["gameTimeMillis"]);
    return true;
}

(:test)
function elapsedFollowsWallClock(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    Test.assertEqual(90000l, m.elapsedMs(T0 + 90000l));
    return true;
}

(:test)
function pauseFreezesClockAndResumeContinues(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.pause(T0 + MIN);
    Test.assert(m.isPaused());
    Test.assertEqual(MIN, m.elapsedMs(T0 + 3 * MIN));
    m.resume(T0 + 3 * MIN);
    Test.assert(!m.isPaused());
    Test.assertEqual(2 * MIN, m.elapsedMs(T0 + 4 * MIN));
    return true;
}

(:test)
function addedTimeCountsPastRegulation(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    Test.assert(!m.isPastRegulation(T0 + 30 * MIN - 1l));
    Test.assertEqual(0l, m.addedMs(T0 + 29 * MIN));
    Test.assert(m.isPastRegulation(T0 + 30 * MIN));
    Test.assertEqual(MIN, m.addedMs(T0 + 31 * MIN));
    return true;
}

(:test)
function regulationAlertFiresOncePerPeriod(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    Test.assert(!m.takeRegulationAlert(T0 + 29 * MIN));
    Test.assert(m.takeRegulationAlert(T0 + 30 * MIN));
    Test.assert(!m.takeRegulationAlert(T0 + 31 * MIN));
    m.endPeriod(T0 + 32 * MIN);
    Test.assert(m.takeRegulationAlert(T0 + 37 * MIN)); // end of the 5-minute break
    return true;
}

(:test)
function endFirstHalfStartsBreak(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.endPeriod(T0 + 31 * MIN);
    Test.assertEqual(PHASE_HALF_TIME, m.phase);
    Test.assertEqual(5 * MIN, m.breakRemainingMs(T0 + 31 * MIN));
    Test.assertEqual(3 * MIN, m.breakRemainingMs(T0 + 33 * MIN));
    Test.assertEqual(0l, m.breakRemainingMs(T0 + 40 * MIN));
    var e = m.events[m.events.size() - 1];
    Test.assertEqual(PHASE_HALF_TIME, e["newPhase"]);
    Test.assertEqual((31 * MIN).toDouble(), e["gameTimeMillis"]);
    return true;
}

(:test)
function secondHalfFlipsKickOffAndRestartsClock(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.kickOff(T0 + 36 * MIN);
    Test.assertEqual(PHASE_SECOND_HALF, m.phase);
    Test.assertEqual(TEAM_AWAY, m.kickOffTeam);
    Test.assertEqual(0l, m.elapsedMs(T0 + 36 * MIN));
    Test.assertEqual(MIN, m.elapsedMs(T0 + 37 * MIN));
    Test.assertEqual(T0, m.startedAtMs);
    return true;
}

(:test)
function endSecondHalfEndsGame(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.kickOff(T0 + 35 * MIN);
    m.endPeriod(T0 + 66 * MIN);
    Test.assertEqual(PHASE_GAME_ENDED, m.phase);
    Test.assert(!m.isPlaying());
    Test.assert(!m.takeRegulationAlert(T0 + 70 * MIN));
    return true;
}

(:test)
function invalidTransitionsAreIgnored(logger as Logger) as Boolean {
    var m = testMatch();
    m.pause(T0);                       // not started
    Test.assertEqual(PHASE_PRE_GAME, m.phase);
    Test.assert(!m.isPaused());
    m.kickOff(T0);
    m.kickOff(T0 + MIN);               // already playing
    Test.assertEqual(T0, m.periodStartMs);
    m.endPeriod(T0 + 30 * MIN);
    m.pause(T0 + 31 * MIN);            // no pausing the break
    Test.assert(!m.isPaused());
    return true;
}
