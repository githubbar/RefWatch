import Toybox.Application;
import Toybox.Lang;

// Persists the match in progress after every change, so a crash or reboot resumes it, and keeps
// the last few finished matches (they become the upload queue in the sync phase).
module MatchStore {
    const CURRENT_KEY = "match";
    const FINISHED_KEY = "finished";
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

    function isValid(d as Dictionary) as Boolean {
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
    function save(m as MatchState) as Void {
        Application.Storage.setValue(CURRENT_KEY, m.toDict());
    }

    function clear() as Void {
        Application.Storage.deleteValue(CURRENT_KEY);
    }

    function finished() as Array<Dictionary> {
        var list = Application.Storage.getValue(FINISHED_KEY);
        return list == null ? ([] as Array<Dictionary>) : (list as Array<Dictionary>);
    }

    // Moves the match to the finished list (oldest dropped past MAX_FINISHED) and clears it.
    function archive(m as MatchState) as Void {
        var list = finished();
        list.add(m.toDict());
        while (list.size() > MAX_FINISHED) {
            list = list.slice(1, null);
        }
        Application.Storage.setValue(FINISHED_KEY, list);
        clear();
    }
}
