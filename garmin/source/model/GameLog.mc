import Toybox.Lang;

module GameLog {
    // Football-style minute: the first minute is 1', added time is "30+2'", and the 2nd half
    // continues from where a full-length 1st half ends.
    function minuteLabel(periodIndex as Number, gameTimeMs as Long, halfMinutes as Number) as String {
        var minute = (gameTimeMs / 60000l).toNumber();
        var offset = periodIndex * halfMinutes;
        if (minute < halfMinutes) {
            return (offset + minute + 1).format("%d") + "'";
        }
        return (offset + halfMinutes).format("%d") + "+" + (minute - halfMinutes + 1).format("%d") + "'";
    }

    // [label, subLabel] per event. Phase changes become plain separator rows.
    function rows(match as MatchState, labels as Dictionary) as Array<Array<String>> {
        var result = [] as Array<Array<String>>;
        var periodIndex = 0;
        for (var i = 0; i < match.events.size(); i++) {
            var e = match.events[i];
            var type = e["eventType"] as String;
            if (type.equals("PHASE_CHANGE")) {
                var phase = e["newPhase"] as String;
                if (phase.equals(PHASE_SECOND_HALF)) {
                    periodIndex = 1;
                }
                result.add([labels[phase] as String, ""]);
                continue;
            }
            var kind = type.equals("CARD") ? e["cardType"] as String : type;
            var minute = minuteLabel(periodIndex, (e["gameTimeMillis"] as Double).toLong(), match.halfMinutes);
            var team = (e["team"] as String).equals(TEAM_HOME) ? match.homeName : match.awayName;
            var player = e["playerNumber"];
            var sub = player == null ? team : team + " #" + (player as Number).format("%d");
            result.add([minute + " " + (labels[kind] as String), sub]);
        }
        return result;
    }
}
