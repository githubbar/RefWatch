import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

(:debug)
function syncedGame(id as String, startSec as Number) as Dictionary {
    return {"id" => id, "t" => startSec, "h" => "Eagles U12", "a" => "Hawks U12",
            "hc" => 0xFF0000, "ac" => 0x0000FF, "hd" => 30, "ht" => 5, "f" => "Field 3"};
}

(:debug)
function wipeGameCache() as Void {
    Application.Storage.deleteValue(GameCache.GAMES_KEY);
    Application.Storage.deleteValue(GameCache.SYNCED_AT_KEY);
}

(:test)
function parseKeepsValidGamesAndSkipsMalformedOnes(logger as Logger) as Boolean {
    var noField = syncedGame("b", 2000);
    noField.remove("f");
    var games = GameCache.parse({"games" => [
        syncedGame("a", 1000),
        noField,
        {"id" => "c", "t" => "soon"},
        "not a game",
        {"id" => 5, "t" => 1000, "h" => "x", "a" => "y", "hc" => 0, "ac" => 0, "hd" => 1, "ht" => 1}
    ]});
    Test.assert(games != null);
    Test.assertEqual(2, (games as Array).size());
    Test.assertEqual("a", (games as Array<Dictionary>)[0]["id"]);
    Test.assertEqual("b", (games as Array<Dictionary>)[1]["id"]);
    return true;
}

(:test)
function parseRejectsAReplyThatIsNotAGameList(logger as Logger) as Boolean {
    Test.assert(GameCache.parse(null) == null);
    Test.assert(GameCache.parse("<html>") == null);
    Test.assert(GameCache.parse({"error" => "unauthorized"}) == null);
    Test.assert(GameCache.parse({"games" => "none"}) == null);
    Test.assertEqual(0, (GameCache.parse({"games" => []}) as Array).size());
    return true;
}

(:test)
function storedGamesSurviveAndOldOnesAreHidden(logger as Logger) as Boolean {
    wipeGameCache();
    var now = 1791500000;
    GameCache.store([syncedGame("old", now - 6 * 3600 - 1), syncedGame("recent", now - 6 * 3600),
                     syncedGame("soon", now + 3600)], now);
    var games = GameCache.games(now);
    Test.assertEqual(2, games.size());
    Test.assertEqual("recent", games[0]["id"]);
    Test.assertEqual("soon", games[1]["id"]);
    Test.assertEqual(now, GameCache.syncedAt());
    wipeGameCache();
    return true;
}

(:test)
function findAndRemoveWorkById(logger as Logger) as Boolean {
    wipeGameCache();
    var now = 1791500000;
    GameCache.store([syncedGame("a", now), syncedGame("b", now + 60)], now);
    Test.assertEqual("b", (GameCache.find("b") as Dictionary)["id"]);
    Test.assert(GameCache.find("zzz") == null);
    GameCache.remove("a");
    Test.assert(GameCache.find("a") == null);
    Test.assertEqual(1, GameCache.games(now).size());
    GameCache.remove("not there");
    Test.assertEqual(1, GameCache.games(now).size());
    wipeGameCache();
    return true;
}

(:test)
function emptyCacheHasNoGamesAndNoSyncTime(logger as Logger) as Boolean {
    wipeGameCache();
    Test.assertEqual(0, GameCache.games(1791500000).size());
    Test.assert(GameCache.syncedAt() == null);
    return true;
}

// A synced half length need not be on the set-up picker's 5-minute grid; it is used as sent.
(:test)
function setupForUsesTheGamesTeamsColorsAndLengths(logger as Logger) as Boolean {
    var game = syncedGame("g1", 1791500400);
    game["hd"] = 32;
    var setup = GameCache.setupFor(game);
    Test.assertEqual("g1", setup["id"]);
    Test.assertEqual("Eagles U12", setup["homeName"]);
    Test.assertEqual("Hawks U12", setup["awayName"]);
    Test.assertEqual(0xFF0000, setup["homeColor"]);
    Test.assertEqual(0x0000FF, setup["awayColor"]);
    Test.assertEqual(32, setup["halfMinutes"]);
    Test.assertEqual(5, setup["halftimeMinutes"]);
    Test.assertEqual(TEAM_HOME, setup["kickOffTeam"]);
    Test.assertEqual(1791500400000l, setup["scheduledStartMs"]);
    Test.assertEqual(Settings.recordActivity(), setup["recordActivity"]);
    var match = new MatchState(setup);
    Test.assertEqual(32l * 60000l, match.regulationMs());
    return true;
}
