import Toybox.Lang;
import Toybox.WatchUi;

// The start menu: matches first (synced games are added here in the sync phase), Settings last.
module GameList {
    function build() as WatchUi.Menu2 {
        var menu = new WatchUi.Menu2({:title => Rez.Strings.AppName});
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.QuickMatch, null, :quickMatch, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Settings, null, :settings, null));
        return menu;
    }

    // Makes the start menu the only view on the stack; the match, if any, is over.
    function show() as Void {
        Nav.stopTicker();
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
            "scheduledStartMs" => null,
            "recordActivity" => Settings.recordActivity()
        };
    }
}

class GameListDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        if (item.getId() == :quickMatch) {
            PreMatch.push(GameList.quickMatchSetup());
        } else if (item.getId() == :settings) {
            SettingsMenu.push();
        }
    }
}
