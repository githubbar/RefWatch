import Toybox.Lang;
import Toybox.WatchUi;

// Hold UP. Once regulation time has passed, ending the period is the first item.
module MatchMenu {
    function push(match as MatchState) as Void {
        var now = Clock.nowMs();
        var menu = new WatchUi.Menu2({:title => Rez.Strings.Menu});
        var endLabel = match.phase.equals(PHASE_FIRST_HALF) ? Rez.Strings.EndFirstHalf : Rez.Strings.EndMatch;
        var endFirst = match.isPlaying() && match.isPastRegulation(now);
        if (match.phase.equals(PHASE_HALF_TIME)) {
            menu.addItem(new WatchUi.MenuItem(Rez.Strings.StartSecondHalf, null, :startSecondHalf, null));
        } else if (endFirst) {
            menu.addItem(new WatchUi.MenuItem(endLabel, null, :end, null));
        }
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.GameLog, null, :log, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Undo, null, :undo, null));
        if (match.isPlaying() && !endFirst) {
            menu.addItem(new WatchUi.MenuItem(endLabel, null, :end, null));
        }
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Abandon, null, :abandon, null));
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
        } else if (id == :undo) {
            var last = _match.lastUndoable();
            if (last != null) {
                Ask.pushText(undoPrompt(last as Dictionary), method(:undo));
            }
        } else if (id == :abandon) {
            Ask.push(Rez.Strings.AbandonPrompt, method(:abandon));
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
        WatchUi.requestUpdate();
    }

    // MatchView.onShow sees PHASE_ABANDONED and returns to the start menu.
    function abandon() as Void {
        MatchStore.clear();
        _match.phase = PHASE_ABANDONED;
    }
}
