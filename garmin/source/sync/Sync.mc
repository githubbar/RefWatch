import Toybox.Lang;
import Toybox.Time;

const SYNC_OK = 0;
const SYNC_OFFLINE = 1;     // the phone or the network was not reachable
const SYNC_UNLINKED = 2;    // the token was refused (unlinked on the phone): link again
const SYNC_FAILED = 3;      // the server refused or answered something unreadable

// One pass: download the upcoming games, then upload finished matches one at a time. Runs at
// app start, after a match is saved, after linking, and from the start menu's Sync item. A
// match never waits for it, and a failed pass loses nothing: games stay cached and matches stay
// queued for the next pass.
module Sync {
    var _job as SyncJob or Null = null;
    var lastStatus as Number or Null = null;
    var _runAgain as Boolean = false;   // run() was asked for while a pass was underway

    function run() as Void {
        if (!Pairing.isLinked()) {
            return;
        }
        if (_job != null) {
            _runAgain = true;
            return;
        }
        var job = new SyncJob(Pairing.token() as String);
        _job = job;
        GameList.refreshSyncItem();
        job.start();
    }

    function isRunning() as Boolean {
        return _job != null;
    }

    // Down to -399 Connect IQ could not reach the phone or the network; -400 and below a reply
    // arrived but could not be read.
    function statusFor(responseCode as Number) as Number {
        if (responseCode == 200) {
            return SYNC_OK;
        }
        if (responseCode == 401) {
            return SYNC_UNLINKED;
        }
        if (responseCode < 0 && responseCode > -400) {
            return SYNC_OFFLINE;
        }
        return SYNC_FAILED;
    }

    // A refused token is dropped, so Settings shows "Not linked"; queued matches stay and are
    // uploaded once the watch is linked again (to whichever account that is).
    function finished(status as Number) as Void {
        _job = null;
        lastStatus = status;
        if (status == SYNC_UNLINKED) {
            Pairing.forget();
        }
        GameList.onSyncFinished();
        if (_runAgain) {
            _runAgain = false;
            run();
        }
    }
}

// The state of one pass, kept alive by Sync._job while requests are in flight.
class SyncJob {
    hidden var _token as String;
    hidden var _pending as Array<String> = [] as Array<String>;   // finished match ids to send
    hidden var _rejected as Boolean = false;                       // a match was refused (400)
    hidden var _done as Boolean = false;                           // Sync.finished was called

    function initialize(token as String) {
        _token = token;
    }

    // Ends the pass once, whichever path reaches it first, so a failure inside a callback
    // can never leave Sync running for the rest of the session.
    hidden function finish(status as Number) as Void {
        if (_done) {
            return;
        }
        _done = true;
        Sync.finished(status);
    }

    function start() as Void {
        try {
            Api.getJson("garminGames", _token, method(:onGames));
        } catch (ex) {
            finish(SYNC_FAILED);
        }
    }

    function onGames(responseCode as Number, data as Dictionary or String or Null) as Void {
        try {
            handleGames(responseCode, data);
        } catch (ex) {
            finish(SYNC_FAILED);
        }
    }

    hidden function handleGames(responseCode as Number, data as Dictionary or String or Null) as Void {
        var status = Sync.statusFor(responseCode);
        var games = status == SYNC_OK ? GameCache.parse(data) : null;
        if (status == SYNC_OK && games == null) {
            status = SYNC_FAILED;
        }
        if (status != SYNC_OK) {
            finish(status);
            return;
        }
        GameCache.store(GameCache.withoutPlayed(games as Array<Dictionary>), Time.now().value());
        _pending = MatchStore.finishedIndex();
        uploadNext();
    }

    // 200: the server has it. 400: it never will (a malformed match); it stays stored but the
    // rest are still sent. Anything else ends the pass; everything unsent stays queued.
    function onUploaded(responseCode as Number, data as Dictionary or String or Null) as Void {
        try {
            if (_pending.size() == 0) {
                finish(SYNC_FAILED);
                return;
            }
            var id = _pending[0];
            _pending = _pending.slice(1, null);
            if (responseCode == 200) {
                MatchStore.removeFinished(id);
            } else if (responseCode == 400) {
                _rejected = true;
            } else {
                finish(Sync.statusFor(responseCode));
                return;
            }
            uploadNext();
        } catch (ex) {
            finish(SYNC_FAILED);
        }
    }

    // Matches are read one at a time to keep memory low on older watches.
    hidden function uploadNext() as Void {
        while (_pending.size() > 0) {
            var match = MatchStore.finishedMatch(_pending[0]);
            if (match != null) {
                Api.postJson("garminUploadGame", UploadQueue.body(match), _token, method(:onUploaded));
                return;
            }
            _pending = _pending.slice(1, null);
        }
        finish(_rejected ? SYNC_FAILED : SYNC_OK);
    }

    (:debug)
    function setPending(ids as Array<String>) as Void {
        _pending = ids;
    }
}
