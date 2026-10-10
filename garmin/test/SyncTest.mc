import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

(:debug)
function archivedMatch(id as String) as Void {
    var m = testMatch();
    m.gameId = id;
    m.kickOff(1000l);
    m.finishGame(2000l);
    MatchStore.archive(m);
}

(:test)
function statusForMapsReplyCodes(logger as Logger) as Boolean {
    Test.assertEqual(SYNC_OK, Sync.statusFor(200));
    Test.assertEqual(SYNC_UNLINKED, Sync.statusFor(401));
    Test.assertEqual(SYNC_OFFLINE, Sync.statusFor(-104));      // BLE not connected
    Test.assertEqual(SYNC_OFFLINE, Sync.statusFor(-2));
    Test.assertEqual(SYNC_FAILED, Sync.statusFor(-400));       // reply could not be read
    Test.assertEqual(SYNC_FAILED, Sync.statusFor(500));
    Test.assertEqual(SYNC_FAILED, Sync.statusFor(404));
    return true;
}

(:test)
function runDoesNothingWhenNotLinked(logger as Logger) as Boolean {
    Pairing.forget();
    Sync.run();
    Test.assert(!Sync.isRunning());
    return true;
}

(:test)
function onGamesStoresTheListAndFinishesWithNothingToUpload(logger as Logger) as Boolean {
    Sync.lastStatus = null;
    wipeGameCache();
    wipeFinished();
    Application.Storage.setValue(Pairing.TOKEN_KEY, "tok");
    new SyncJob("tok").onGames(200, {"games" => [syncedGame("g1", 1791500400)]});
    Test.assertEqual(SYNC_OK, Sync.lastStatus);
    Test.assert(!Sync.isRunning());
    Test.assert(GameCache.find("g1") != null);
    Test.assert(Pairing.isLinked());
    wipeGameCache();
    Pairing.forget();
    return true;
}

(:test)
function onGamesWith401ForgetsTheTokenAndKeepsGames(logger as Logger) as Boolean {
    wipeGameCache();
    wipeFinished();
    GameCache.store([syncedGame("kept", 1791500400)], 1791500000);
    archivedMatch("queued");
    Application.Storage.setValue(Pairing.TOKEN_KEY, "tok");
    new SyncJob("tok").onGames(401, {"error" => "unauthorized"});
    Test.assertEqual(SYNC_UNLINKED, Sync.lastStatus);
    Test.assert(!Pairing.isLinked());
    Test.assert(GameCache.find("kept") != null);
    Test.assertEqual(1, MatchStore.finishedIndex().size());
    wipeGameCache();
    wipeFinished();
    return true;
}

(:test)
function onGamesOfflineOrUnreadableKeepsTheCache(logger as Logger) as Boolean {
    Sync.lastStatus = null;
    wipeGameCache();
    GameCache.store([syncedGame("kept", 1791500400)], 1791500000);
    new SyncJob("tok").onGames(-104, null);
    Test.assertEqual(SYNC_OFFLINE, Sync.lastStatus);
    new SyncJob("tok").onGames(200, "<html>");
    Test.assertEqual(SYNC_FAILED, Sync.lastStatus);
    Test.assert(GameCache.find("kept") != null);
    Test.assertEqual(1791500000, GameCache.syncedAt());
    wipeGameCache();
    return true;
}

(:test)
function aDownloadDoesNotBringBackAGamePlayedHere(logger as Logger) as Boolean {
    Sync.lastStatus = null;
    wipeGameCache();
    wipeFinished();
    archivedMatch("g1");
    new SyncJob("tok").onGames(200, {"games" => [syncedGame("g1", 1791500400), syncedGame("g2", 1791500400)]});
    Test.assert(GameCache.find("g1") == null);
    Test.assert(GameCache.find("g2") != null);
    wipeGameCache();
    wipeFinished();
    return true;
}

(:test)
function anAcceptedUploadLeavesTheQueue(logger as Logger) as Boolean {
    Sync.lastStatus = null;
    wipeFinished();
    archivedMatch("m1");
    var job = new SyncJob("tok");
    job.setPending(["m1"]);
    job.onUploaded(200, {"ok" => true});
    Test.assertEqual(0, MatchStore.finishedIndex().size());
    Test.assertEqual(SYNC_OK, Sync.lastStatus);
    return true;
}

(:test)
function aRefusedOrFailedUploadStaysQueued(logger as Logger) as Boolean {
    wipeFinished();
    archivedMatch("m1");
    var job = new SyncJob("tok");
    job.setPending(["m1"]);
    job.onUploaded(400, {"error" => "bad_request"});
    Test.assertEqual(SYNC_FAILED, Sync.lastStatus);
    Test.assertEqual(1, MatchStore.finishedIndex().size());

    job = new SyncJob("tok");
    job.setPending(["m1"]);
    job.onUploaded(-104, null);
    Test.assertEqual(SYNC_OFFLINE, Sync.lastStatus);
    Test.assertEqual(1, MatchStore.finishedIndex().size());

    Application.Storage.setValue(Pairing.TOKEN_KEY, "tok");
    job = new SyncJob("tok");
    job.setPending(["m1"]);
    job.onUploaded(401, null);
    Test.assertEqual(SYNC_UNLINKED, Sync.lastStatus);
    Test.assert(!Pairing.isLinked());
    Test.assertEqual(1, MatchStore.finishedIndex().size());
    wipeFinished();
    return true;
}

(:test)
function finishedClearsTheRunningStateAndAStrayUploadReplyFinishesWithAStatus(logger as Logger) as Boolean {
    Sync.finished(SYNC_OFFLINE);
    Test.assert(!Sync.isRunning());
    Test.assertEqual(SYNC_OFFLINE, Sync.lastStatus);
    Sync.lastStatus = null;
    new SyncJob("tok").onUploaded(200, null);       // nothing pending: must not throw
    Test.assertEqual(SYNC_FAILED, Sync.lastStatus);
    Test.assert(!Sync.isRunning());
    return true;
}
