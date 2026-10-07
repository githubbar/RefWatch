import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

(:test)
function storeSavesAndLoadsCurrentMatch(logger as Logger) as Boolean {
    MatchStore.clear();
    Test.assert(MatchStore.load() == null);
    var m = testMatch();
    m.kickOff(T0);
    m.addCard(TEAM_HOME, 3, CARD_YELLOW, T0 + MIN);
    MatchStore.save(m);
    var loaded = MatchStore.load() as MatchState;
    Test.assertEqual(m.gameId, loaded.gameId);
    Test.assertEqual(2, loaded.events.size());
    MatchStore.clear();
    Test.assert(MatchStore.load() == null);
    return true;
}

// Removes every finished match, whatever state an earlier (possibly failed) run left behind.
(:debug)
function wipeFinished() as Void {
    var index = Application.Storage.getValue(MatchStore.FINISHED_KEY);
    if (index instanceof Array) {
        for (var i = 0; i < index.size(); i++) {
            Application.Storage.deleteValue(MatchStore.FINISHED_PREFIX + index[i]);
        }
    }
    Application.Storage.deleteValue(MatchStore.FINISHED_KEY);
}

(:test)
function archiveKeepsOnlyTheLatestFinishedMatches(logger as Logger) as Boolean {
    wipeFinished();
    for (var i = 0; i < MatchStore.MAX_FINISHED + 2; i++) {
        var m = testMatch();
        m.gameId = "game-" + i;
        MatchStore.save(m);
        MatchStore.archive(m);
    }
    var finished = MatchStore.finished();
    Test.assertEqual(MatchStore.MAX_FINISHED, finished.size());
    Test.assertEqual("game-2", finished[0]["id"]);
    Test.assertEqual("game-" + (MatchStore.MAX_FINISHED + 1), finished[finished.size() - 1]["id"]);
    Test.assert(MatchStore.load() == null);      // archiving clears the current match
    wipeFinished();                              // leave no test matches in storage
    return true;
}

(:test)
function eachFinishedMatchIsStoredUnderItsOwnKey(logger as Logger) as Boolean {
    wipeFinished();
    var m = testMatch();
    m.gameId = "alpha";
    MatchStore.archive(m);
    var stored = Application.Storage.getValue(MatchStore.FINISHED_PREFIX + "alpha");
    Test.assert(stored instanceof Dictionary);
    Test.assertEqual("alpha", (stored as Dictionary)["id"]);
    var index = Application.Storage.getValue(MatchStore.FINISHED_KEY);
    Test.assertEqual(1, (index as Array).size());
    Test.assertEqual("alpha", (index as Array)[0]);   // the index holds ids only, not matches
    wipeFinished();
    return true;
}

(:test)
function finishedFollowsIndexOrderAndSkipsMissingValues(logger as Logger) as Boolean {
    wipeFinished();
    var ids = ["a", "b", "c"];
    for (var i = 0; i < ids.size(); i++) {
        var m = testMatch();
        m.gameId = ids[i];
        MatchStore.archive(m);
    }
    Application.Storage.deleteValue(MatchStore.FINISHED_PREFIX + "b");
    var finished = MatchStore.finished();
    Test.assertEqual(2, finished.size());
    Test.assertEqual("a", finished[0]["id"]);
    Test.assertEqual("c", finished[1]["id"]);
    wipeFinished();
    return true;
}

(:test)
function evictionDeletesTheOldestMatchesKey(logger as Logger) as Boolean {
    wipeFinished();
    for (var i = 0; i < MatchStore.MAX_FINISHED + 1; i++) {
        var m = testMatch();
        m.gameId = "g" + i;
        MatchStore.archive(m);
    }
    Test.assert(Application.Storage.getValue(MatchStore.FINISHED_PREFIX + "g0") == null);
    Test.assert(Application.Storage.getValue(MatchStore.FINISHED_PREFIX + "g1") != null);
    Test.assertEqual(MatchStore.MAX_FINISHED, (Application.Storage.getValue(MatchStore.FINISHED_KEY) as Array).size());
    wipeFinished();
    return true;
}

(:test)
function archivingTheSameMatchTwiceKeepsOneCopy(logger as Logger) as Boolean {
    wipeFinished();
    var m = testMatch();
    MatchStore.archive(m);
    MatchStore.archive(m);
    Test.assertEqual(1, MatchStore.finished().size());
    wipeFinished();
    return true;
}

(:test)
function storedMatchWithAnUnknownSchemaVersionIsDiscarded(logger as Logger) as Boolean {
    var d = testMatch().toDict();
    d["v"] = 99;
    Application.Storage.setValue(MatchStore.CURRENT_KEY, d);
    Test.assert(MatchStore.load() == null);
    Test.assert(Application.Storage.getValue(MatchStore.CURRENT_KEY) == null);
    return true;
}

(:test)
function storedMatchWithoutAVersionIsTreatedAsVersionOne(logger as Logger) as Boolean {
    var d = testMatch().toDict();
    d.remove("v");
    Application.Storage.setValue(MatchStore.CURRENT_KEY, d);
    Test.assert(MatchStore.load() != null);
    MatchStore.clear();
    return true;
}

(:test)
function toDictCarriesTheSchemaVersion(logger as Logger) as Boolean {
    Test.assertEqual(1, testMatch().toDict()["v"]);
    return true;
}

(:test)
function loadDiscardsAMalformedStoredMatch(logger as Logger) as Boolean {
    Application.Storage.setValue(MatchStore.CURRENT_KEY, {"id" => "x"});
    Test.assert(MatchStore.load() == null);
    Test.assert(Application.Storage.getValue(MatchStore.CURRENT_KEY) == null);
    return true;
}

(:test)
function loadDiscardsAStoredMatchMissingRequiredKeys(logger as Logger) as Boolean {
    var d = testMatch().toDict();
    d.remove("homeScore");
    d.remove("pausedTotalMs");
    d.remove("regulationAlerted");
    Application.Storage.setValue(MatchStore.CURRENT_KEY, d);
    Test.assert(MatchStore.load() == null);
    Test.assert(Application.Storage.getValue(MatchStore.CURRENT_KEY) == null);
    return true;
}
