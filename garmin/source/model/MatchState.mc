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

// What takeAlert asks the watch to do.
const ALERT_NONE = 0;
const ALERT_PERIOD_END = 1;     // regulation reached: the strong period-end buzz
const ALERT_REMINDER = 2;       // still in added time: a short reminder buzz
const REMINDER_INTERVAL_MS = 30000l;

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
    var recordActivity as Boolean;       // save this match as a Garmin activity

    var phase as String;
    var homeScore as Number;
    var awayScore as Number;
    var events as Array<Dictionary>;     // GameEvent JSON shape, see the spec
    var startedAtMs as Long or Null;     // wall clock of the first kick-off
    var periodStartMs as Long or Null;   // start of the current half or break
    var pausedTotalMs as Long;
    var pausedAtMs as Long or Null;
    var regulationAlerted as Boolean;    // end-of-period vibration already given
    var remindersGiven as Number;        // added-time reminders given in this half

    // Version of the stored dictionary shape, see MatchStore.isValid.
    static const SCHEMA_VERSION = 1;

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
        recordActivity = setup["recordActivity"] == true;
        phase = PHASE_PRE_GAME;
        homeScore = 0;
        awayScore = 0;
        events = [] as Array<Dictionary>;
        startedAtMs = null;
        periodStartMs = null;
        pausedTotalMs = 0l;
        pausedAtMs = null;
        regulationAlerted = false;
        remindersGiven = 0;
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
        if (phase.equals(PHASE_FIRST_HALF)) {
            var elapsed = elapsedMs(nowMs);
            phase = PHASE_HALF_TIME;
            startPeriod(nowMs);
            logPhase(nowMs, elapsed);
        } else {
            finishGame(nowMs);
        }
    }

    // Straight to full time from either half or the break; the score stands.
    function finishGame(nowMs as Long) as Void {
        if (!isPlaying() && !phase.equals(PHASE_HALF_TIME)) {
            return;
        }
        var elapsed = elapsedMs(nowMs);
        phase = PHASE_GAME_ENDED;
        periodStartMs = null;
        pausedAtMs = null;
        pausedTotalMs = 0l;
        logPhase(nowMs, elapsed);
    }

    // Restarts the current half's clock at 0:00, paused, so START begins it again.
    function resetPeriodClock(nowMs as Long) as Void {
        if (!isPlaying()) {
            return;
        }
        startPeriod(nowMs);
        pausedAtMs = nowMs;
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

    // ALERT_PERIOD_END once per half or break when regulation time is reached; then, in a half,
    // ALERT_REMINDER for every REMINDER_INTERVAL_MS of added time until the half is ended. Added
    // time stops while paused, so reminders do too. Reminders a busy watch missed collapse into
    // one.
    function takeAlert(nowMs as Long) as Number {
        if (!isPastRegulation(nowMs)) {
            return ALERT_NONE;
        }
        if (!regulationAlerted) {
            regulationAlerted = true;
            return ALERT_PERIOD_END;
        }
        var due = (addedMs(nowMs) / REMINDER_INTERVAL_MS).toNumber();
        if (isPlaying() && due > remindersGiven) {
            remindersGiven = due;
            return ALERT_REMINDER;
        }
        return ALERT_NONE;
    }

    // The set-up this match started from, for Reset game: same teams, colors and lengths, and
    // the team that kicked off the 1st half (kickOffTeam flips at the 2nd half).
    function replaySetup() as Dictionary {
        var firstKickOff = kickOffTeam;
        for (var i = 0; i < events.size(); i++) {
            var e = events[i];
            if ((e["eventType"] as String).equals("PHASE_CHANGE") && PHASE_SECOND_HALF.equals(e["newPhase"])) {
                firstKickOff = kickOffTeam.equals(TEAM_HOME) ? TEAM_AWAY : TEAM_HOME;
            }
        }
        return {
            "id" => gameId,
            "homeName" => homeName,
            "awayName" => awayName,
            "homeColor" => homeColor,
            "awayColor" => awayColor,
            "halfMinutes" => halfMinutes,
            "halftimeMinutes" => halftimeMinutes,
            "kickOffTeam" => firstKickOff,
            "scheduledStartMs" => scheduledStartMs,
            "recordActivity" => recordActivity
        };
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

    // The most recent goal or card (what undoLast would remove), without removing it.
    function lastUndoable() as Dictionary or Null {
        for (var i = events.size() - 1; i >= 0; i--) {
            var type = events[i]["eventType"] as String;
            if (type.equals("GOAL") || type.equals("CARD")) {
                return events[i];
            }
        }
        return null;
    }

    // Removes the most recent goal or card (phase changes are never undone) and returns it.
    function undoLast() as Dictionary or Null {
        var event = lastUndoable();
        if (event == null) {
            return null;
        }
        events.remove(event);
        if ((event["eventType"] as String).equals("GOAL")) {
            if ((event["team"] as String).equals(TEAM_HOME)) {
                homeScore -= 1;
            } else {
                awayScore -= 1;
            }
        }
        return event;
    }

    function toDict() as Dictionary {
        return {
            "v" => SCHEMA_VERSION,
            "id" => gameId,
            "homeName" => homeName,
            "awayName" => awayName,
            "homeColor" => homeColor,
            "awayColor" => awayColor,
            "halfMinutes" => halfMinutes,
            "halftimeMinutes" => halftimeMinutes,
            "kickOffTeam" => kickOffTeam,
            "scheduledStartMs" => scheduledStartMs,
            "recordActivity" => recordActivity,
            "phase" => phase,
            "homeScore" => homeScore,
            "awayScore" => awayScore,
            "events" => events,
            "startedAtMs" => startedAtMs,
            "periodStartMs" => periodStartMs,
            "pausedTotalMs" => pausedTotalMs,
            "pausedAtMs" => pausedAtMs,
            "regulationAlerted" => regulationAlerted,
            "remindersGiven" => remindersGiven
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
        // Matches stored before reminders existed have no count.
        var reminders = d["remindersGiven"];
        m.remindersGiven = reminders instanceof Number ? reminders as Number : 0;
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
        remindersGiven = 0;
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
