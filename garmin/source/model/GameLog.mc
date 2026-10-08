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

    // A missing label falls back to the key itself rather than null.
    function labelFor(labels as Dictionary, key as String) as String {
        var text = labels[key];
        return text == null ? key : text as String;
    }

    // [label, subLabel] per event. Phase changes become plain separator rows.
    function rows(match as MatchState, labels as Dictionary) as Array<Array<String>> {
        var result = [] as Array<Array<String>>;
        var periodIndex = 0;
        var phaseNow = PHASE_PRE_GAME;
        for (var i = 0; i < match.events.size(); i++) {
            var e = match.events[i];
            var type = e["eventType"] as String;
            if (type.equals("PHASE_CHANGE")) {
                var phase = e["newPhase"] as String;
                if (phase.equals(PHASE_SECOND_HALF)) {
                    periodIndex = 1;
                }
                phaseNow = phase;
                result.add([labelFor(labels, phase), ""]);
                continue;
            }
            var kind = type.equals("CARD") ? e["cardType"] as String : type;
            // Time within the break is not a match minute, so events then are marked "HT".
            var minute = phaseNow.equals(PHASE_HALF_TIME)
                ? labelFor(labels, "HT")
                : minuteLabel(periodIndex, (e["gameTimeMillis"] as Double).toLong(), match.halfMinutes);
            var team = (e["team"] as String).equals(TEAM_HOME) ? match.homeName : match.awayName;
            var player = e["playerNumber"];
            var sub = player == null ? team : team + " #" + (player as Number).format("%d");
            result.add([minute + " " + labelFor(labels, kind), sub]);
        }
        return result;
    }
}
