import Toybox.Lang;
import Toybox.WatchUi;

// Read-only list of the match's events; BACK returns to the match.
module GameLogView {
    function push(match as MatchState) as Void {
        var labels = {
            "GOAL" => WatchUi.loadResource(Rez.Strings.Goal),
            "YELLOW" => WatchUi.loadResource(Rez.Strings.YellowCard),
            "RED" => WatchUi.loadResource(Rez.Strings.RedCard),
            "HT" => WatchUi.loadResource(Rez.Strings.HalfTimeShort),
            PHASE_FIRST_HALF => WatchUi.loadResource(Rez.Strings.FirstHalf),
            PHASE_HALF_TIME => WatchUi.loadResource(Rez.Strings.HalfTime),
            PHASE_SECOND_HALF => WatchUi.loadResource(Rez.Strings.SecondHalf),
            PHASE_GAME_ENDED => WatchUi.loadResource(Rez.Strings.FullTime)
        };
        var menu = new WatchUi.Menu2({:title => Rez.Strings.GameLog});
        var rows = GameLog.rows(match, labels);
        if (rows.size() == 0) {
            menu.addItem(new WatchUi.MenuItem(Rez.Strings.NoEvents, null, 0, null));
        }
        for (var i = 0; i < rows.size(); i++) {
            var sub = rows[i][1].equals("") ? null : rows[i][1];
            menu.addItem(new WatchUi.MenuItem(rows[i][0], sub, i, null));
        }
        WatchUi.pushView(menu, new GameLogDelegate(), WatchUi.SLIDE_LEFT);
    }
}

class GameLogDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
    }
}
