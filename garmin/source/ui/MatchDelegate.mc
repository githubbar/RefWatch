import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// START pauses/resumes, starts the 2nd half at half time, and saves at full time.
// BACK asks before leaving (the match keeps running and resumes on the next launch) or, at full
// time, before discarding.
class MatchDelegate extends WatchUi.BehaviorDelegate {
    hidden var _match as MatchState;

    function initialize(match as MatchState) {
        BehaviorDelegate.initialize();
        _match = match;
    }

    // A BehaviorDelegate that handles the select behavior never sees onTap, because the system
    // turns a screen tap into that same behavior. onSelect therefore declines, and the START
    // key (onKey) and a tap (onTap) are handled separately.
    function onSelect() as Boolean {
        return false;
    }

    function onKey(event as WatchUi.KeyEvent) as Boolean {
        if (event.getKey() == WatchUi.KEY_ENTER) {
            return start();
        }
        return false;
    }

    hidden function start() as Boolean {
        var now = Clock.nowMs();
        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            Recorder.save();
            MatchStore.archive(_match);
            GameList.show();
            return true;
        }
        if (_match.phase.equals(PHASE_HALF_TIME)) {
            // Starting the 2nd half cannot be undone, and START is also the pause button.
            Ask.push(Rez.Strings.StartSecondHalfPrompt, method(:startSecondHalf));
            return true;
        }
        _match.togglePause(now);
        MatchStore.save(_match);
        WatchUi.requestUpdate();
        return true;
    }

    function startSecondHalf() as Void {
        _match.kickOff(Clock.nowMs());
        MatchStore.save(_match);
        Recorder.forKickOff(_match);
        WatchUi.requestUpdate();
    }

    function onPreviousPage() as Boolean {
        return openTeam(TEAM_HOME);
    }

    function onNextPage() as Boolean {
        return openTeam(TEAM_AWAY);
    }

    // Touch watches: tap the left half for Home, the right half for Away. Every tap is consumed,
    // so a stray tap never pauses the match or, at full time, saves it.
    function onTap(event as WatchUi.ClickEvent) as Boolean {
        var x = event.getCoordinates()[0];
        openTeam(x < System.getDeviceSettings().screenWidth / 2 ? TEAM_HOME : TEAM_AWAY);
        return true;
    }

    function onMenu() as Boolean {
        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            GameLogView.push(_match);
        } else {
            MatchMenu.push(_match);
        }
        return true;
    }

    hidden function openTeam(team as String) as Boolean {
        if (_match.isPlaying() || _match.phase.equals(PHASE_HALF_TIME)) {
            TeamActions.push(_match, team);
        }
        return true;
    }

    function onBack() as Boolean {
        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            Ask.push(Rez.Strings.DiscardPrompt, method(:discard));
        } else {
            Ask.push(Rez.Strings.LeavePrompt, method(:leave));
        }
        return true;
    }

    // The match resumes on the next launch; the activity so far is saved now, and a new one
    // starts when the app reopens.
    function leave() as Void {
        Recorder.save();
        System.exit();
    }

    // MatchView.onShow sees PHASE_ABANDONED once the confirmation closes and shows the start menu.
    function discard() as Void {
        Recorder.discard();
        MatchStore.clear();
        _match.phase = PHASE_ABANDONED;
    }
}
