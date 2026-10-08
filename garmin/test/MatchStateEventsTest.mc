import Toybox.Lang;
import Toybox.Test;

(:test)
function goalUpdatesScoreAndLogsEvent(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    var id = m.addGoal(TEAM_HOME, null, T0 + 754000l);
    Test.assert(id != null);
    Test.assertEqual(1, m.homeScore);
    Test.assertEqual(0, m.awayScore);
    var e = m.events[m.events.size() - 1];
    Test.assertEqual("GOAL", e["eventType"]);
    Test.assertEqual(TEAM_HOME, e["team"]);
    Test.assertEqual(754000.0d, e["gameTimeMillis"]);
    Test.assertEqual((T0 + 754000l).toDouble(), e["timestamp"]);
    Test.assertEqual(1, e["homeScoreAtTime"]);
    Test.assertEqual(0, e["awayScoreAtTime"]);
    Test.assert(!e.hasKey("playerNumber"));
    return true;
}

(:test)
function scorerCanBeAddedAfterTheGoal(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    var id = m.addGoal(TEAM_AWAY, null, T0 + MIN);
    m.setPlayerNumber(id as String, 9);
    Test.assertEqual(9, m.events[m.events.size() - 1]["playerNumber"]);
    return true;
}

(:test)
function goalsOnlyDuringAHalf(logger as Logger) as Boolean {
    var m = testMatch();
    Test.assert(m.addGoal(TEAM_HOME, null, T0) == null);
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    Test.assert(m.addGoal(TEAM_HOME, null, T0 + 31 * MIN) == null);
    Test.assertEqual(0, m.homeScore);
    return true;
}

(:test)
function cardIsLoggedWithPlayer(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addCard(TEAM_AWAY, 4, CARD_YELLOW, T0 + 2 * MIN);
    var e = m.events[m.events.size() - 1];
    Test.assertEqual("CARD", e["eventType"]);
    Test.assertEqual(TEAM_AWAY, e["team"]);
    Test.assertEqual(4, e["playerNumber"]);
    Test.assertEqual(CARD_YELLOW, e["cardType"]);
    Test.assertEqual((2 * MIN).toDouble(), e["gameTimeMillis"]);
    return true;
}

(:test)
function cardsAllowedAtHalfTimeButNotBeforeKickOff(logger as Logger) as Boolean {
    var m = testMatch();
    m.addCard(TEAM_HOME, 5, CARD_RED, T0);
    Test.assertEqual(0, m.events.size());
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.addCard(TEAM_HOME, 5, CARD_RED, T0 + 32 * MIN);
    Test.assertEqual("CARD", m.events[m.events.size() - 1]["eventType"]);
    return true;
}

(:test)
function undoRemovesLatestGoalOrCardAndFixesScore(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addGoal(TEAM_HOME, null, T0 + MIN);
    m.addCard(TEAM_AWAY, 4, CARD_YELLOW, T0 + 2 * MIN);
    var removed = m.undoLast();
    Test.assertEqual("CARD", (removed as Dictionary)["eventType"]);
    Test.assertEqual(1, m.homeScore);
    removed = m.undoLast();
    Test.assertEqual("GOAL", (removed as Dictionary)["eventType"]);
    Test.assertEqual(0, m.homeScore);
    Test.assert(m.undoLast() == null);           // only the kick-off phase change is left
    Test.assertEqual(1, m.events.size());
    return true;
}

(:test)
function undoSkipsPhaseChanges(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addGoal(TEAM_AWAY, 7, T0 + MIN);
    m.endPeriod(T0 + 30 * MIN);
    m.undoLast();
    Test.assertEqual(0, m.awayScore);
    Test.assertEqual(PHASE_HALF_TIME, m.phase);
    Test.assertEqual(2, m.events.size());         // FIRST_HALF and HALF_TIME phase changes
    return true;
}

(:test)
function dictRoundTripPreservesState(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addGoal(TEAM_HOME, 10, T0 + MIN);
    m.pause(T0 + 2 * MIN);
    var copy = MatchState.fromDict(m.toDict());
    Test.assertEqual(m.gameId, copy.gameId);
    Test.assertEqual(PHASE_FIRST_HALF, copy.phase);
    Test.assertEqual(1, copy.homeScore);
    Test.assertEqual(2, copy.events.size());
    Test.assert(copy.isPaused());
    Test.assertEqual(2 * MIN, copy.elapsedMs(T0 + 10 * MIN));
    Test.assertEqual(TEAM_HOME, copy.kickOffTeam);
    Test.assertEqual(30, copy.halfMinutes);
    return true;
}

(:test)
function lastUndoableReturnsWhatUndoWouldRemoveWithoutRemovingIt(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    Test.assert(m.lastUndoable() == null);       // only the kick-off phase change so far
    m.addGoal(TEAM_HOME, null, T0 + MIN);
    m.addCard(TEAM_AWAY, 4, CARD_YELLOW, T0 + 2 * MIN);
    var last = m.lastUndoable() as Dictionary;
    Test.assertEqual("CARD", last["eventType"]);
    Test.assertEqual(3, m.events.size());        // nothing removed
    Test.assertEqual(last["id"], (m.undoLast() as Dictionary)["id"]);
    Test.assertEqual("GOAL", (m.lastUndoable() as Dictionary)["eventType"]);
    Test.assertEqual(1, m.homeScore);
    return true;
}
