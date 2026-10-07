import Toybox.Lang;
import Toybox.WatchUi;

// Goal / Yellow / Red for one team. A goal counts immediately; with "Log goal scorer" on, the
// number picker follows and BACK skips it. A card needs its player number; its time is taken
// when the card is chosen, not when the number is confirmed.
module TeamActions {
    function push(match as MatchState, team as String) as Void {
        var title = team.equals(TEAM_HOME) ? match.homeName : match.awayName;
        var menu = new WatchUi.Menu2({:title => title});
        if (match.isPlaying()) {
            menu.addItem(new WatchUi.MenuItem(Rez.Strings.Goal, null, :goal, null));
        }
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.YellowCard, null, :yellow, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.RedCard, null, :red, null));
        WatchUi.pushView(menu, new TeamActionsDelegate(match, team), WatchUi.SLIDE_LEFT);
    }
}

class TeamActionsDelegate extends WatchUi.Menu2InputDelegate {
    hidden var _match as MatchState;
    hidden var _team as String;
    hidden var _goalId as String or Null = null;
    hidden var _cardType as String = CARD_YELLOW;
    hidden var _cardAtMs as Long = 0l;

    function initialize(match as MatchState, team as String) {
        Menu2InputDelegate.initialize();
        _match = match;
        _team = team;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var now = Clock.nowMs();
        var id = item.getId();
        if (id == :goal) {
            _goalId = _match.addGoal(_team, null, now);
            MatchStore.save(_match);
            if (_goalId != null && Settings.logGoalScorer()) {
                Pickers.switchToPlayerNumber(Rez.Strings.Scorer, method(:onScorer));
            } else {
                WatchUi.popView(WatchUi.SLIDE_RIGHT);
            }
        } else {
            _cardType = id == :yellow ? CARD_YELLOW : CARD_RED;
            _cardAtMs = now;
            Pickers.switchToPlayerNumber(Rez.Strings.PlayerNumber, method(:onCardPlayer));
        }
    }

    function onScorer(n as Number) as Void {
        _match.setPlayerNumber(_goalId as String, n);
        MatchStore.save(_match);
    }

    function onCardPlayer(n as Number) as Void {
        _match.addCard(_team, n, _cardType, _cardAtMs);
        MatchStore.save(_match);
    }
}
