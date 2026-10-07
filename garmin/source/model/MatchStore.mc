import Toybox.Application;
import Toybox.Lang;

// Persists the match in progress after every change, so a crash or reboot resumes it, and keeps
// the last few finished matches (they become the upload queue in the sync phase).
module MatchStore {
    const CURRENT_KEY = "match";
    const FINISHED_KEY = "finished";
    const MAX_FINISHED = 5;

    function load() as MatchState or Null {
        var d = Application.Storage.getValue(CURRENT_KEY);
        if (d == null) {
            return null;
        }
        return MatchState.fromDict(d as Dictionary);
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
