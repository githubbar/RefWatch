import Toybox.Lang;

// The garminUploadGame body for a finished match (a MatchState.toDict dictionary). The queue
// itself is MatchStore's finished list: a match leaves it only when the server accepts it.
module UploadQueue {
    // 0xFF000000 does not fit a Monkey C Number literal; this is the same 32 bits.
    const OPAQUE_ALPHA = -16777216;

    function body(match as Dictionary) as Dictionary {
        // A synced game keeps its scheduled start; a quick match is dated by its kick-off.
        var start = match["scheduledStartMs"];
        if (start == null) {
            start = match["startedAtMs"];
        }
        var payload = {
            "id" => match["id"],
            "homeTeamName" => match["homeName"],
            "awayTeamName" => match["awayName"],
            "homeTeamColorArgb" => argb(match["homeColor"] as Number),
            "awayTeamColorArgb" => argb(match["awayColor"] as Number),
            "halfDurationMinutes" => match["halfMinutes"],
            "halftimeDurationMinutes" => match["halftimeMinutes"],
            "kickOffTeam" => match["kickOffTeam"],
            "currentPhase" => match["phase"],
            "homeScore" => match["homeScore"],
            "awayScore" => match["awayScore"],
            "events" => match["events"]
        };
        // Sent as a Double, like the event timestamps: how Connect IQ 3.1 encodes a Long in
        // JSON is not documented, and a Double holds epoch milliseconds exactly.
        if (start != null) {
            payload["gameDateTimeEpochMillis"] = (start as Long).toDouble();
        }
        return payload;
    }

    // The phone stores colors as ARGB Ints: opaque, so alpha is 0xFF.
    function argb(rgb as Number) as Number {
        return (rgb & 0xFFFFFF) | OPAQUE_ALPHA;
    }
}
