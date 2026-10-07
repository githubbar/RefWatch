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

(:test)
function archiveKeepsOnlyTheLatestFinishedMatches(logger as Logger) as Boolean {
    Application.Storage.deleteValue(MatchStore.FINISHED_KEY);
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
    Application.Storage.deleteValue(MatchStore.FINISHED_KEY);   // leave no test matches in storage
    return true;
}

(:test)
function loadDiscardsAMalformedStoredMatch(logger as Logger) as Boolean {
    Application.Storage.setValue(MatchStore.CURRENT_KEY, {"id" => "x"});
    Test.assert(MatchStore.load() == null);
    Test.assert(Application.Storage.getValue(MatchStore.CURRENT_KEY) == null);
    return true;
}
