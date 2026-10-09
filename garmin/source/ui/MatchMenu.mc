import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// BACK or hold UP. The items follow the Wear OS app's game menu. Once regulation time has
// passed, ending the period is the first item.
module MatchMenu {
    function push(match as MatchState) as Void {
        var now = Clock.nowMs();
        var menu = new WatchUi.Menu2({:title => Rez.Strings.Menu});
        var firstHalf = match.phase.equals(PHASE_FIRST_HALF);
        var endLabel = firstHalf ? Rez.Strings.EndFirstHalf : Rez.Strings.EndMatch;
        var playing = match.isPlaying();
        var endFirst = playing && match.isPastRegulation(now);
        if (match.phase.equals(PHASE_HALF_TIME)) {
            menu.addItem(new WatchUi.MenuItem(Rez.Strings.StartSecondHalf, null, :startSecondHalf, null));
        } else if (endFirst) {
            menu.addItem(new WatchUi.MenuItem(endLabel, null, :end, null));
        }
        if (playing) {
            var pauseLabel = match.isPaused() ? Rez.Strings.ResumeClock : Rez.Strings.PauseClock;
            menu.addItem(new WatchUi.MenuItem(pauseLabel, null, :pause, null));
        }
        if (playing && !endFirst) {
            menu.addItem(new WatchUi.MenuItem(endLabel, null, :end, null));
        }
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.GameLog, null, :log, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Undo, null, :undo, null));
        // In the 2nd half "End match" already does this.
        if (firstHalf || match.phase.equals(PHASE_HALF_TIME)) {
            menu.addItem(new WatchUi.MenuItem(Rez.Strings.FinishGame, null, :finish, null));
        }
        if (playing) {
            menu.addItem(new WatchUi.MenuItem(Rez.Strings.ResetTimer, null, :resetTimer, null));
        }
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.ResetGame, null, :resetGame, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Abandon, null, :abandon, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.LeaveMatch, null, :leave, null));
        WatchUi.pushView(menu, new MatchMenuDelegate(match), WatchUi.SLIDE_UP);
    }
}

class MatchMenuDelegate extends WatchUi.Menu2InputDelegate {
    hidden var _match as MatchState;

    function initialize(match as MatchState) {
        Menu2InputDelegate.initialize();
        _match = match;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id == :log) {
            WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
            GameLogView.push(_match);
            return;
        }
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        if (id == :end) {
            var prompt = _match.phase.equals(PHASE_FIRST_HALF) ? Rez.Strings.EndFirstHalfPrompt : Rez.Strings.EndMatchPrompt;
            Ask.push(prompt, method(:endPeriod));
        } else if (id == :startSecondHalf) {
            _match.kickOff(Clock.nowMs());
            MatchStore.save(_match);
            Recorder.forKickOff(_match);
        } else if (id == :pause) {
            _match.togglePause(Clock.nowMs());
            MatchStore.save(_match);
            WatchUi.requestUpdate();
        } else if (id == :undo) {
            var last = _match.lastUndoable();
            if (last != null) {
                Ask.pushText(undoPrompt(last as Dictionary), method(:undo));
            }
        } else if (id == :finish) {
            Ask.push(Rez.Strings.FinishGamePrompt, method(:finishGame));
        } else if (id == :resetTimer) {
            Ask.push(Rez.Strings.ResetTimerPrompt, method(:resetTimer));
        } else if (id == :resetGame) {
            Ask.push(Rez.Strings.ResetGamePrompt, method(:resetGame));
        } else if (id == :abandon) {
            Ask.push(Rez.Strings.AbandonPrompt, method(:abandon));
        } else if (id == :leave) {
            Ask.push(Rez.Strings.LeavePrompt, method(:leave));
        }
    }

    // "Undo Goal Hawks?" or "Undo Yellow #4?", so the referee sees what will be removed.
    hidden function undoPrompt(event as Dictionary) as String {
        var team = event["team"] as String;
        if ((event["eventType"] as String).equals("GOAL")) {
            var name = team.equals(TEAM_HOME) ? _match.homeName : _match.awayName;
            return Lang.format(WatchUi.loadResource(Rez.Strings.UndoGoalPrompt) as String, [name]);
        }
        var card = WatchUi.loadResource((event["cardType"] as String).equals(CARD_RED) ? Rez.Strings.RedCard : Rez.Strings.YellowCard);
        return Lang.format(WatchUi.loadResource(Rez.Strings.UndoCardPrompt) as String, [card, event["playerNumber"]]);
    }

    function undo() as Void {
        _match.undoLast();
        MatchStore.save(_match);
        WatchUi.requestUpdate();
    }

    function endPeriod() as Void {
        _match.endPeriod(Clock.nowMs());
        MatchStore.save(_match);
        Recorder.forPeriodEnd(_match);
        WatchUi.requestUpdate();
    }

    // Full time now; the referee then saves or discards as usual.
    function finishGame() as Void {
        _match.finishGame(Clock.nowMs());
        MatchStore.save(_match);
        Recorder.forPeriodEnd(_match);
        WatchUi.requestUpdate();
    }

    function resetTimer() as Void {
        _match.resetPeriodClock(Clock.nowMs());
        MatchStore.save(_match);
        WatchUi.requestUpdate();
    }

    // Back to set-up with the same teams; the score, events and recording are thrown away.
    // MatchView.onShow sees PHASE_ABANDONED and closes the match.
    function resetGame() as Void {
        Nav.replayAfterClose(_match.replaySetup());
        abandon();
    }

    // MatchView.onShow sees PHASE_ABANDONED and returns to the start menu.
    function abandon() as Void {
        Recorder.discard();
        MatchStore.clear();
        _match.phase = PHASE_ABANDONED;
    }

    // The match resumes on the next launch; the activity so far is saved now, and a new one
    // starts when the app reopens.
    function leave() as Void {
        Recorder.save();
        System.exit();
    }
}
