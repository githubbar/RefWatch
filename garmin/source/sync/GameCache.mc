import Toybox.Application;
import Toybox.Lang;

// Upcoming games from garminGames, kept in storage so the list works at the field without the
// phone. Keys are the server's short ones (see the spec): t start in epoch seconds, h/a team
// names, hc/ac RGB colors, hd/ht half and break minutes, f field or venue (optional).
module GameCache {
    const GAMES_KEY = "games";
    const SYNCED_AT_KEY = "gamesSyncedAt";       // epoch seconds of the last download
    // The server's window: a game stays listed until 6 hours after its start.
    const KEEP_AFTER_START_S = 6 * 60 * 60;

    // The games in a garminGames reply, or null when the reply is not a game list. Malformed
    // games are skipped rather than failing the whole list.
    function parse(data as Dictionary or String or Null) as Array<Dictionary> or Null {
        if (!(data instanceof Dictionary)) {
            return null;
        }
        var list = (data as Dictionary)["games"];
        if (!(list instanceof Array)) {
            return null;
        }
        var valid = [] as Array<Dictionary>;
        for (var i = 0; i < (list as Array).size(); i++) {
            var g = (list as Array)[i];
            if (g instanceof Dictionary && isGame(g as Dictionary)) {
                valid.add(g as Dictionary);
            }
        }
        return valid;
    }

    // Monkey C casts are compile-time only, so every key is checked before it is used.
    function isGame(g as Dictionary) as Boolean {
        return g["id"] instanceof String
            && (g["t"] instanceof Number || g["t"] instanceof Long)
            && g["h"] instanceof String
            && g["a"] instanceof String
            && g["hc"] instanceof Number
            && g["ac"] instanceof Number
            && g["hd"] instanceof Number
            && g["ht"] instanceof Number
            && (g["f"] == null || g["f"] instanceof String);
    }

    function startSec(g as Dictionary) as Number {
        return (g["t"] as Number or Long).toNumber();
    }

    // A storage failure keeps the previous list; the next sync tries again.
    function store(games as Array<Dictionary>, nowSec as Number) as Void {
        try {
            Application.Storage.setValue(GAMES_KEY, games);
            Application.Storage.setValue(SYNCED_AT_KEY, nowSec);
        } catch (e) {
        }
    }

    // Cached games that have not been over for long, in the server's order (soonest first).
    function games(nowSec as Number) as Array<Dictionary> {
        var result = [] as Array<Dictionary>;
        var all = stored();
        for (var i = 0; i < all.size(); i++) {
            if (startSec(all[i]) + KEEP_AFTER_START_S >= nowSec) {
                result.add(all[i]);
            }
        }
        return result;
    }

    function find(id as String) as Dictionary or Null {
        var all = stored();
        for (var i = 0; i < all.size(); i++) {
            if ((all[i]["id"] as String).equals(id)) {
                return all[i];
            }
        }
        return null;
    }

    // A game refereed on this watch leaves the list at once, before the next sync confirms it.
    function remove(id as String) as Void {
        var kept = [] as Array<Dictionary>;
        var all = stored();
        for (var i = 0; i < all.size(); i++) {
            if (!(all[i]["id"] as String).equals(id)) {
                kept.add(all[i]);
            }
        }
        if (kept.size() != all.size()) {
            try {
                Application.Storage.setValue(GAMES_KEY, kept);
            } catch (e) {
            }
        }
    }

    function syncedAt() as Number or Null {
        var value = Application.Storage.getValue(SYNCED_AT_KEY);
        return value instanceof Number ? value as Number : null;
    }

    // The set-up screen's starting point for a synced game. The first half's kick-off team is
    // chosen there, as for a quick match.
    function setupFor(g as Dictionary) as Dictionary {
        return {
            "id" => g["id"] as String,
            "homeName" => g["h"] as String,
            "awayName" => g["a"] as String,
            "homeColor" => g["hc"] as Number,
            "awayColor" => g["ac"] as Number,
            "halfMinutes" => g["hd"] as Number,
            "halftimeMinutes" => g["ht"] as Number,
            "kickOffTeam" => TEAM_HOME,
            "scheduledStartMs" => startSec(g).toLong() * 1000l,
            "recordActivity" => Settings.recordActivity()
        };
    }

    // Module functions cannot be `hidden` in Monkey C (a compile error), so this one is public.
    function stored() as Array<Dictionary> {
        var value = Application.Storage.getValue(GAMES_KEY);
        var result = [] as Array<Dictionary>;
        if (value instanceof Array) {
            for (var i = 0; i < (value as Array).size(); i++) {
                var g = (value as Array)[i];
                if (g instanceof Dictionary && isGame(g as Dictionary)) {
                    result.add(g as Dictionary);
                }
            }
        }
        return result;
    }
}
