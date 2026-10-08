import Toybox.Activity;
import Toybox.ActivityRecording;
import Toybox.Lang;
import Toybox.Position;
import Toybox.WatchUi;

// The Garmin activity recorded alongside a match: one session from kick-off to full time, with a
// lap at each phase change, so the activity shows the 1st half, the break and the 2nd half
// separately. Pausing the match clock does not pause the recording, because the referee keeps
// moving. A match never depends on recording: everything here is a no-op on a watch that cannot
// record, and recording errors are swallowed.
module Recorder {
    var _session as ActivityRecording.Session or Null = null;
    var _gpsOn as Boolean = false;

    // After MatchState.kickOff: the 1st half starts the activity; the 2nd half adds a lap. A
    // match resumed after a restart has no session yet, so the 2nd half starts one too.
    function forKickOff(match as MatchState) as Void {
        if (!match.recordActivity) {
            return;
        }
        if (_session == null) {
            start();
        } else {
            lap();
        }
    }

    // After MatchState.endPeriod: half time adds a lap; full time stops the timer. The session
    // is kept until the referee saves or discards the match.
    function forPeriodEnd(match as MatchState) as Void {
        if (!match.recordActivity) {
            return;
        }
        if (match.phase.equals(PHASE_GAME_ENDED)) {
            stop();
        } else {
            lap();
        }
    }

    // At launch, for a match left underway. The earlier part was saved when the app closed, so
    // this starts a second activity (a known limitation, see the spec).
    function forResume(match as MatchState) as Void {
        if (match.recordActivity && (match.isPlaying() || match.phase.equals(PHASE_HALF_TIME))) {
            start();
        }
    }

    function start() as Void {
        if (_session != null || !(Toybox has :ActivityRecording)) {
            return;
        }
        try {
            warmUp();
            var session = ActivityRecording.createSession({
                :name => WatchUi.loadResource(Rez.Strings.ActivityName) as String,
                :sport => sport()
            });
            session.start();
            _session = session;
        } catch (e) {
            _session = null;
        }
    }

    function lap() as Void {
        var session = _session;
        if (session != null && session.isRecording()) {
            session.addLap();
        }
    }

    function stop() as Void {
        var session = _session;
        if (session != null && session.isRecording()) {
            session.stop();
        }
    }

    function save() as Void {
        var session = _session;
        if (session != null) {
            try {
                stop();
                session.save();
            } catch (e) {
            }
        }
        _session = null;
        releaseGps();
    }

    function discard() as Void {
        var session = _session;
        if (session != null) {
            try {
                stop();
                session.discard();
            } catch (e) {
            }
        }
        _session = null;
        releaseGps();
    }

    function isActive() as Boolean {
        return _session != null;
    }

    // Turning GPS on during set-up gives it time to find a fix before kick-off.
    function warmUp() as Void {
        if (!_gpsOn && (Toybox has :Position)) {
            Position.enableLocationEvents(Position.LOCATION_CONTINUOUS, new RecorderGps().method(:onPosition));
            _gpsOn = true;
        }
    }

    function releaseGps() as Void {
        if (_gpsOn && _session == null) {
            Position.enableLocationEvents(Position.LOCATION_DISABLE, null);
            _gpsOn = false;
        }
    }

    // Activity.SPORT_SOCCER needs API 3.2; the fēnix 5X has 3.1, where only the deprecated
    // ActivityRecording constant exists. Both are FIT sport 7.
    function sport() as Activity.Sport or ActivityRecording.Sport {
        if (Activity has :SPORT_SOCCER) {
            return Activity.SPORT_SOCCER;
        }
        return ActivityRecording.SPORT_SOCCER;
    }
}

// Position.enableLocationEvents needs a listener method. The session reads GPS on its own, so
// the listener ignores the fixes.
class RecorderGps {
    function initialize() {
    }

    function onPosition(info as Position.Info) as Void {
    }
}
