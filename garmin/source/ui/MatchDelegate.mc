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

    function onSelect() as Boolean {
        var now = Clock.nowMs();
        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            MatchStore.archive(_match);
            GameList.show();
            return true;
        }
        if (_match.phase.equals(PHASE_HALF_TIME)) {
            _match.kickOff(now);
        } else {
            _match.togglePause(now);
        }
        MatchStore.save(_match);
        WatchUi.requestUpdate();
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

    function leave() as Void {
        System.exit();
    }

    // MatchView.onShow sees PHASE_ABANDONED once the confirmation closes and shows the start menu.
    function discard() as Void {
        MatchStore.clear();
        _match.phase = PHASE_ABANDONED;
    }
}
