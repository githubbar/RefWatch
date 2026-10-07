import Toybox.Lang;
import Toybox.WatchUi;

// The start menu. Synced games are added here in the sync phase.
module GameList {
    function build() as WatchUi.Menu2 {
        var menu = new WatchUi.Menu2({:title => Rez.Strings.AppName});
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.QuickMatch, null, :quickMatch, null));
        return menu;
    }

    // Makes the start menu the only view on the stack.
    function show() as Void {
        WatchUi.switchToView(build(), new GameListDelegate(), WatchUi.SLIDE_IMMEDIATE);
    }

    function quickMatchSetup() as Dictionary {
        return {
            "id" => Ids.newId(),
            "homeName" => WatchUi.loadResource(Rez.Strings.Home) as String,
            "awayName" => WatchUi.loadResource(Rez.Strings.Away) as String,
            "homeColor" => TeamColors.VALUES[0],
            "awayColor" => TeamColors.VALUES[1],
            "halfMinutes" => 45,
            "halftimeMinutes" => 15,
            "kickOffTeam" => TEAM_HOME,
            "scheduledStartMs" => null
        };
    }
}

class GameListDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        if (item.getId() == :quickMatch) {
            var match = new MatchState(GameList.quickMatchSetup());
            match.kickOff(Clock.nowMs());
            MatchStore.save(match);
            Nav.showMatch(match);
        }
    }
}
