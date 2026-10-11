import Toybox.Lang;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// The start menu: Quick match, the synced games (soonest first), Sync, Settings. After a sync
// it is rebuilt: at once if it is showing, otherwise as soon as it shows again.
module GameList {
    var _menu as GameListMenu or Null = null;   // the start menu while it is the top view
    var _stale as Boolean = false;              // a sync finished while it was hidden

    function build() as WatchUi.Menu2 {
        var menu = new GameListMenu();
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.QuickMatch, null, :quickMatch, null));
        var games = GameCache.games(Time.now().value());
        for (var i = 0; i < games.size(); i++) {
            var g = games[i];
            menu.addItem(new WatchUi.MenuItem(teams(g), when(g), g["id"] as String, null));
        }
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Sync, syncStatus(), :sync, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Settings, null, :settings, null));
        _stale = false;
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

    function onMenuShown(menu as GameListMenu) as Void {
        _menu = menu;
    }

    // ("hidden" is a Monkey C keyword, hence the names.)
    function onMenuHidden(menu as GameListMenu) as Void {
        if (_menu == menu) {
            _menu = null;
        }
    }

    function isShowing(menu as GameListMenu) as Boolean {
        return _menu == menu;
    }

    function isStale() as Boolean {
        return _stale;
    }

    // The Sync item's status line, updated in place (sync started).
    function refreshSyncItem() as Void {
        var menu = _menu;
        if (menu != null) {
            var index = menu.findItemById(:sync);
            if (index >= 0) {
                (menu.getItem(index) as WatchUi.MenuItem).setSubLabel(syncStatus());
            }
            WatchUi.requestUpdate();
        }
    }

    // The game list may have changed: rebuild the menu now if it is showing.
    function onSyncFinished() as Void {
        if (_menu != null) {
            show();
        } else {
            _stale = true;
        }
    }

    function syncStatus() as String {
        var id;
        if (Sync.isRunning()) {
            id = Rez.Strings.Syncing;
        } else if (!Pairing.isLinked()) {
            id = Sync.lastStatus == SYNC_UNLINKED ? Rez.Strings.LinkAgain : Rez.Strings.NotLinked;
        } else if (Sync.lastStatus == SYNC_OFFLINE) {
            id = Rez.Strings.OfflineMessage;
        } else if (Sync.lastStatus == SYNC_FAILED) {
            id = Rez.Strings.SyncFailed;
        } else {
            var waiting = MatchStore.finishedIndex().size();
            if (waiting > 0) {
                return Lang.format(WatchUi.loadResource(Rez.Strings.ToUpload) as String, [waiting]);
            }
            var at = GameCache.syncedAt();
            if (at == null) {
                id = Rez.Strings.NotSynced;
            } else {
                return Lang.format(WatchUi.loadResource(Rez.Strings.SyncedAt) as String,
                    [Format.startTime(at as Number)]);
            }
        }
        return WatchUi.loadResource(id) as String;
    }

    function teams(g as Dictionary) as String {
        return Lang.format(WatchUi.loadResource(Rez.Strings.TeamsFormat) as String, [g["h"], g["a"]]);
    }

    // "Sat 10:00 · Field 3"
    function when(g as Dictionary) as String {
        var text = Format.startTime(GameCache.startSec(g));
        if (g["f"] instanceof String) {
            text += " · " + (g["f"] as String);
        }
        return text;
    }
}

// The start menu view. It reports when it shows and hides so a finished sync knows whether it
// can rebuild it at once. switchToView is not called from inside onShow; a short one-shot timer
// defers the rebuild.
class GameListMenu extends WatchUi.Menu2 {
    hidden var _timer as Timer.Timer or Null = null;

    function initialize() {
        Menu2.initialize({:title => Rez.Strings.AppName});
    }

    function onShow() as Void {
        GameList.onMenuShown(self);
        if (GameList.isStale()) {
            var timer = new Timer.Timer();
            _timer = timer;
            timer.start(method(:rebuild), 50, false);
        }
    }

    function onHide() as Void {
        GameList.onMenuHidden(self);
    }

    function rebuild() as Void {
        _timer = null;
        if (GameList.isShowing(self)) {
            GameList.show();
        }
    }
}

class GameListDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id == :quickMatch) {
            PreMatch.push(GameList.quickMatchSetup());
        } else if (id == :sync) {
            if (Pairing.isLinked()) {
                Sync.run();
            } else {
                LinkFlow.start();
            }
        } else if (id == :settings) {
            SettingsMenu.push();
        } else if (id instanceof String) {
            var game = GameCache.find(id as String);
            if (game != null) {
                PreMatch.push(GameCache.setupFor(game));
            }
        }
    }
}
