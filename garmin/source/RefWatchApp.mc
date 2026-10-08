import Toybox.Application;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.WatchUi;

class RefWatchApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary or Null) as Void {
        Clock.init();
        Math.srand(System.getTimer());
    }

    // A match left running (or unsaved at full time) reopens straight onto the match screen.
    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        var match = MatchStore.load();
        if (match != null && !match.phase.equals(PHASE_PRE_GAME)) {
            return [new MatchView(match), new MatchDelegate(match)];
        }
        return [GameList.build(), new GameListDelegate()];
    }
}
