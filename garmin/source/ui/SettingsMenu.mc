import Toybox.Lang;
import Toybox.WatchUi;

// Start menu → Settings: account linking, the two match settings and the app version. The
// switches write the same properties Garmin Connect edits.
module SettingsMenu {
    // The Settings menu while it is open, kept so its link status can be refreshed.
    var _menu as WatchUi.Menu2 or Null = null;

    function push() as Void {
        var menu = new WatchUi.Menu2({:title => Rez.Strings.Settings});
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.LinkAccount, linkStatus(), :link, null));
        menu.addItem(new WatchUi.ToggleMenuItem(Rez.Strings.RecordActivity, null, :recordActivity,
            Settings.recordActivity(), null));
        menu.addItem(new WatchUi.ToggleMenuItem(Rez.Strings.LogGoalScorerTitle, null, :logGoalScorer,
            Settings.logGoalScorer(), null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.Version, Settings.appVersion(), :version, null));
        _menu = menu;
        WatchUi.pushView(menu, new SettingsMenuDelegate(), WatchUi.SLIDE_LEFT);
    }

    // Re-reads the link status into the open Settings menu, if there is one.
    function refreshLinkStatus() as Void {
        var menu = _menu;
        if (menu != null) {
            var index = menu.findItemById(:link);
            if (index >= 0) {
                (menu.getItem(index) as WatchUi.MenuItem).setSubLabel(linkStatus());
            }
        }
        WatchUi.requestUpdate();
    }

    function closed() as Void {
        _menu = null;
    }

    function linkStatus() as String {
        return WatchUi.loadResource(Pairing.isLinked() ? Rez.Strings.Linked : Rez.Strings.NotLinked) as String;
    }
}

class SettingsMenuDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    // A toggle item is already flipped when onSelect runs, so isEnabled() is the new value.
    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id == :link) {
            LinkFlow.start();
        } else if (id == :recordActivity) {
            Settings.setRecordActivity((item as WatchUi.ToggleMenuItem).isEnabled());
        } else if (id == :logGoalScorer) {
            Settings.setLogGoalScorer((item as WatchUi.ToggleMenuItem).isEnabled());
        }
    }

    function onBack() as Void {
        SettingsMenu.closed();
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
    }
}
