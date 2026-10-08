import Toybox.Application;
import Toybox.Lang;

// Persists the match in progress after every change, so a crash or reboot resumes it, and keeps
// the last few finished matches (they become the upload queue in the sync phase).
module MatchStore {
    const CURRENT_KEY = "match";
    const FINISHED_KEY = "finished";        // array of finished match ids, oldest first
    const FINISHED_PREFIX = "fin_";         // each finished match lives under FINISHED_PREFIX + id
    const MAX_FINISHED = 5;

    // A malformed or older-format stored match must not crash the app on every launch: it is
    // discarded and the app starts fresh. Monkey C "as" casts are compile-time only, so
    // fromDict would happily build a null-filled match from missing keys; every key is checked
    // here first.
    function load() as MatchState or Null {
        var d = Application.Storage.getValue(CURRENT_KEY);
        if (d == null) {
            return null;
        }
        if (d instanceof Dictionary && isValid(d as Dictionary)) {
            return MatchState.fromDict(d as Dictionary);
        }
        clear();
        return null;
    }

    // Future fields must be optional here (accept a missing key) or bump SCHEMA_VERSION, so an
    // older stored match still loads or is deliberately discarded. A missing "v" means version 1.
    function isValid(d as Dictionary) as Boolean {
        var v = d["v"];
        if (v != null && (!(v instanceof Number) || (v as Number) > MatchState.SCHEMA_VERSION)) {
            return false;
        }
        return d["id"] instanceof String
            && d["homeName"] instanceof String
            && d["awayName"] instanceof String
            && d["homeColor"] instanceof Number
            && d["awayColor"] instanceof Number
            && d["halfMinutes"] instanceof Number
            && d["halftimeMinutes"] instanceof Number
            && d["kickOffTeam"] instanceof String
            && (d["scheduledStartMs"] == null || d["scheduledStartMs"] instanceof Long)
            && d["phase"] instanceof String
            && d["homeScore"] instanceof Number
            && d["awayScore"] instanceof Number
            && d["events"] instanceof Array
            && (d["startedAtMs"] == null || d["startedAtMs"] instanceof Long)
            && (d["periodStartMs"] == null || d["periodStartMs"] instanceof Long)
            && d["pausedTotalMs"] instanceof Long
            && (d["pausedAtMs"] == null || d["pausedAtMs"] instanceof Long)
            && d["regulationAlerted"] instanceof Boolean;
    }

    // Writes can fail (storage full, value too large); the caller keeps playing with the
    // in-memory match rather than crashing, and the next change tries the save again.
    function save(m as MatchState) as Void {
        try {
            Application.Storage.setValue(CURRENT_KEY, m.toDict());
        } catch (e) {
        }
    }

    function clear() as Void {
        Application.Storage.deleteValue(CURRENT_KEY);
    }

    // Finished matches, oldest first. Ids whose value is missing are skipped.
    function finished() as Array<Dictionary> {
        var result = [] as Array<Dictionary>;
        var index = finishedIndex();
        for (var i = 0; i < index.size(); i++) {
            var d = Application.Storage.getValue(FINISHED_PREFIX + index[i]);
            if (d instanceof Dictionary) {
                result.add(d as Dictionary);
            }
        }
        return result;
    }

    // Moves the match to the finished list and clears the current one. Each finished match
    // has its own storage key because one value is limited to about 32 KB and a long match
    // with many events can exceed that. The oldest are dropped past MAX_FINISHED; the sync
    // phase will change these eviction rules (matches must stay until they are uploaded).
    // A storage failure must never trap the referee on the full-time screen, so the current
    // match is cleared even when the archive write fails.
    function archive(m as MatchState) as Void {
        try {
            var index = finishedIndex();
            var at = index.indexOf(m.gameId);
            if (at >= 0) {
                index.remove(m.gameId);
            }
            Application.Storage.setValue(FINISHED_PREFIX + m.gameId, m.toDict());
            index.add(m.gameId);
            while (index.size() > MAX_FINISHED) {
                Application.Storage.deleteValue(FINISHED_PREFIX + index[0]);
                index = index.slice(1, null);
            }
            Application.Storage.setValue(FINISHED_KEY, index);
        } catch (e) {
        }
        clear();
    }

    function finishedIndex() as Array<String> {
        var index = Application.Storage.getValue(FINISHED_KEY);
        if (index instanceof Array) {
            return (index as Array<String>).slice(0, null);
        }
        return [] as Array<String>;
    }
}