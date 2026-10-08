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

    // Whatever closes the app (BACK → Leave, the system, a crash-free exit), the activity
    // recorded so far is kept. At full time this saves it before the referee chooses Save or
    // Discard, so a later Discard cannot remove it.
    function onStop(state as Dictionary or Null) as Void {
        Recorder.save();
    }

    // A match left running (or unsaved at full time) reopens straight onto the match screen.
    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        var match = MatchStore.load();
        if (match != null && !match.phase.equals(PHASE_PRE_GAME)) {
            Recorder.forResume(match);
            return [new MatchView(match), new MatchDelegate(match)];
        }
        return [GameList.build(), new GameListDelegate()];
    }
}
