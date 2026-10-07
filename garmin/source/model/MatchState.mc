import Toybox.Lang;

// Phase, team and card names are the Kotlin enum names in common/.../DataModels.kt, because
// they are uploaded as-is and parsed by the phone app.
const PHASE_PRE_GAME = "PRE_GAME";
const PHASE_FIRST_HALF = "FIRST_HALF";
const PHASE_HALF_TIME = "HALF_TIME";
const PHASE_SECOND_HALF = "SECOND_HALF";
const PHASE_GAME_ENDED = "GAME_ENDED";
// Watch-only: set when a match is discarded so its screen closes. Never stored or uploaded.
const PHASE_ABANDONED = "ABANDONED";
const TEAM_HOME = "HOME";
const TEAM_AWAY = "AWAY";
const CARD_YELLOW = "YELLOW";
const CARD_RED = "RED";

// The rules of a match: periods, the clock, the score and the event log. It never reads the
// clock and never touches storage or the screen; callers pass the current time in, so every
// rule is unit-testable.
//
// The clock is derived from timestamps: elapsed = now - periodStartMs - pausedTotalMs (frozen at
// pausedAtMs while paused). Nothing counts ticks, so a busy or sleeping watch cannot drift.
class MatchState {
    var gameId as String;
    var homeName as String;
    var awayName as String;
    var homeColor as Number;
    var awayColor as Number;
    var halfMinutes as Number;
    var halftimeMinutes as Number;
    var kickOffTeam as String;           // team kicking off the current (or next) half
    var scheduledStartMs as Long or Null;

    var phase as String;
    var homeScore as Number;
    var awayScore as Number;
    var events as Array<Dictionary>;     // GameEvent JSON shape, see the spec
    var startedAtMs as Long or Null;     // wall clock of the first kick-off
    var periodStartMs as Long or Null;   // start of the current half or break
    var pausedTotalMs as Long;
    var pausedAtMs as Long or Null;
    var regulationAlerted as Boolean;    // end-of-period vibration already given

    function initialize(setup as Dictionary) {
        gameId = setup["id"] as String;
        homeName = setup["homeName"] as String;
        awayName = setup["awayName"] as String;
        homeColor = setup["homeColor"] as Number;
        awayColor = setup["awayColor"] as Number;
        halfMinutes = setup["halfMinutes"] as Number;
        halftimeMinutes = setup["halftimeMinutes"] as Number;
        kickOffTeam = setup["kickOffTeam"] as String;
        scheduledStartMs = setup["scheduledStartMs"] as Long or Null;
        phase = PHASE_PRE_GAME;
        homeScore = 0;
        awayScore = 0;
        events = [] as Array<Dictionary>;
        startedAtMs = null;
        periodStartMs = null;
        pausedTotalMs = 0l;
        pausedAtMs = null;
        regulationAlerted = false;
    }

    function isPlaying() as Boolean {
        return phase.equals(PHASE_FIRST_HALF) || phase.equals(PHASE_SECOND_HALF);
    }

    function isPaused() as Boolean {
        return pausedAtMs != null;
    }

    // Pre-game → 1st half, or half time → 2nd half (the other team kicks off).
    function kickOff(nowMs as Long) as Void {
        if (phase.equals(PHASE_PRE_GAME)) {
            startedAtMs = nowMs;
            phase = PHASE_FIRST_HALF;
        } else if (phase.equals(PHASE_HALF_TIME)) {
            kickOffTeam = kickOffTeam.equals(TEAM_HOME) ? TEAM_AWAY : TEAM_HOME;
            phase = PHASE_SECOND_HALF;
        } else {
            return;
        }
        startPeriod(nowMs);
        logPhase(nowMs, 0l);
    }

    // 1st half → half time, or 2nd half → game ended.
    function endPeriod(nowMs as Long) as Void {
        if (!isPlaying()) {
            return;
        }
        var elapsed = elapsedMs(nowMs);
        if (phase.equals(PHASE_FIRST_HALF)) {
            phase = PHASE_HALF_TIME;
            startPeriod(nowMs);
        } else {
            phase = PHASE_GAME_ENDED;
            periodStartMs = null;
            pausedAtMs = null;
            pausedTotalMs = 0l;
        }
        logPhase(nowMs, elapsed);
    }

    function pause(nowMs as Long) as Void {
        if (isPlaying() && pausedAtMs == null) {
            pausedAtMs = nowMs;
        }
    }

    function resume(nowMs as Long) as Void {
        if (pausedAtMs != null) {
            pausedTotalMs += nowMs - (pausedAtMs as Long);
            pausedAtMs = null;
        }
    }

    function togglePause(nowMs as Long) as Void {
        if (isPaused()) {
            resume(nowMs);
        } else {
            pause(nowMs);
        }
    }

    // Time elapsed in the current half or break.
    function elapsedMs(nowMs as Long) as Long {
        if (periodStartMs == null) {
            return 0l;
        }
        var end = nowMs;
        if (pausedAtMs != null) {
            end = pausedAtMs as Long;
        }
        var elapsed = end - (periodStartMs as Long) - pausedTotalMs;
        return elapsed > 0 ? elapsed : 0l;
    }

    // Regulation length of the current half, or of the break at half time.
    function regulationMs() as Long {
        var minutes = phase.equals(PHASE_HALF_TIME) ? halftimeMinutes : halfMinutes;
        return minutes.toLong() * 60000l;
    }

    function isPastRegulation(nowMs as Long) as Boolean {
        return periodStartMs != null && elapsedMs(nowMs) >= regulationMs();
    }

    function addedMs(nowMs as Long) as Long {
        if (!isPlaying()) {
            return 0l;
        }
        var over = elapsedMs(nowMs) - regulationMs();
        return over > 0 ? over : 0l;
    }

    function breakRemainingMs(nowMs as Long) as Long {
        if (!phase.equals(PHASE_HALF_TIME)) {
            return 0l;
        }
        var remaining = regulationMs() - elapsedMs(nowMs);
        return remaining > 0 ? remaining : 0l;
    }

    // True exactly once per half or break, when regulation time is reached.
    function takeRegulationAlert(nowMs as Long) as Boolean {
        if (regulationAlerted || !isPastRegulation(nowMs)) {
            return false;
        }
        regulationAlerted = true;
        return true;
    }

    // Returns the goal's event id, or null outside a half. The scorer can be added afterwards
    // with setPlayerNumber, so the score changes the moment the referee presses Goal.
    function addGoal(team as String, playerNumber as Number or Null, nowMs as Long) as String or Null {
        if (!isPlaying()) {
            return null;
        }
        if (team.equals(TEAM_HOME)) {
            homeScore += 1;
        } else {
            awayScore += 1;
        }
        var event = newEvent("GOAL", nowMs);
        event["team"] = team;
        event["homeScoreAtTime"] = homeScore;
        event["awayScoreAtTime"] = awayScore;
        if (playerNumber != null) {
            event["playerNumber"] = playerNumber;
        }
        events.add(event);
        return event["id"] as String;
    }

    function setPlayerNumber(eventId as String, number as Number) as Void {
        for (var i = 0; i < events.size(); i++) {
            if ((events[i]["id"] as String).equals(eventId)) {
                events[i]["playerNumber"] = number;
            }
        }
    }

    // Cards are allowed during either half and at half time.
    function addCard(team as String, playerNumber as Number, cardType as String, nowMs as Long) as Void {
        if (!isPlaying() && !phase.equals(PHASE_HALF_TIME)) {
            return;
        }
        var event = newEvent("CARD", nowMs);
        event["team"] = team;
        event["playerNumber"] = playerNumber;
        event["cardType"] = cardType;
        events.add(event);
    }

    // Removes the most recent goal or card (phase changes are never undone) and returns it.
    function undoLast() as Dictionary or Null {
        for (var i = events.size() - 1; i >= 0; i--) {
            var event = events[i];
            var type = event["eventType"] as String;
            if (type.equals("GOAL") || type.equals("CARD")) {
                events.remove(event);
                if (type.equals("GOAL")) {
                    if ((event["team"] as String).equals(TEAM_HOME)) {
                        homeScore -= 1;
                    } else {
                        awayScore -= 1;
                    }
                }
                return event;
            }
        }
        return null;
    }

    function toDict() as Dictionary {
        return {
            "id" => gameId,
            "homeName" => homeName,
            "awayName" => awayName,
            "homeColor" => homeColor,
            "awayColor" => awayColor,
            "halfMinutes" => halfMinutes,
            "halftimeMinutes" => halftimeMinutes,
            "kickOffTeam" => kickOffTeam,
            "scheduledStartMs" => scheduledStartMs,
            "phase" => phase,
            "homeScore" => homeScore,
            "awayScore" => awayScore,
            "events" => events,
            "startedAtMs" => startedAtMs,
            "periodStartMs" => periodStartMs,
            "pausedTotalMs" => pausedTotalMs,
            "pausedAtMs" => pausedAtMs,
            "regulationAlerted" => regulationAlerted
        };
    }

    static function fromDict(d as Dictionary) as MatchState {
        var m = new MatchState(d);
        m.phase = d["phase"] as String;
        m.homeScore = d["homeScore"] as Number;
        m.awayScore = d["awayScore"] as Number;
        m.events = d["events"] as Array<Dictionary>;
        m.startedAtMs = d["startedAtMs"] as Long or Null;
        m.periodStartMs = d["periodStartMs"] as Long or Null;
        m.pausedTotalMs = d["pausedTotalMs"] as Long;
        m.pausedAtMs = d["pausedAtMs"] as Long or Null;
        m.regulationAlerted = d["regulationAlerted"] as Boolean;
        return m;
    }

    hidden function newEvent(type as String, nowMs as Long) as Dictionary {
        return {
            "eventType" => type,
            "id" => Ids.newId(),
            "timestamp" => nowMs.toDouble(),
            "gameTimeMillis" => elapsedMs(nowMs).toDouble()
        };
    }

    hidden function startPeriod(nowMs as Long) as Void {
        periodStartMs = nowMs;
        pausedTotalMs = 0l;
        pausedAtMs = null;
        regulationAlerted = false;
    }

    hidden function logPhase(nowMs as Long, gameTimeMs as Long) as Void {
        events.add({
            "eventType" => "PHASE_CHANGE",
            "id" => Ids.newId(),
            "newPhase" => phase,
            "timestamp" => nowMs.toDouble(),
            "gameTimeMillis" => gameTimeMs.toDouble()
        });
    }
}
