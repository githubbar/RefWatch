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
        pairFromSettings();
    }

    // A code entered in Garmin Connect is used once: when the settings change, or at start if
    // the watch is not linked yet.
    function onSettingsChanged() as Void {
        pairFromSettings();
        WatchUi.requestUpdate();
    }

    hidden function pairFromSettings() as Void {
        var code = Settings.pairingCode();
        if (Pairing.isCode(code) && !Pairing.isLinked()) {
            Pairing.pair(code, method(:onSettingsPaired));
        }
    }

    // A refused code is cleared too, so it is not retried at every start; an offline attempt
    // keeps it for the next start.
    function onSettingsPaired(status as Number) as Void {
        if (status != PAIR_OFFLINE) {
            Settings.clearPairingCode();
        }
        WatchUi.requestUpdate();
    }

    // Whatever closes the app (BACK → Leave, the system, a crash-free exit), the activity
    // recorded so far is kept. At full time this saves it before the referee chooses Save or
    // Discard, so a Discard after reopening the app cannot remove it.
    function onStop(state as Dictionary or Null) as Void {
        Recorder.save();
    }

    // A match left running (or unsaved at full time) reopens straight onto the match screen.
    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        var match = MatchStore.load();
        if (match != null && !match.phase.equals(PHASE_PRE_GAME)) {
            Recorder.forResume(match);
            Nav.startTicker(match);
            return [new MatchView(match), new MatchDelegate(match)];
        }
        return [GameList.build(), new GameListDelegate()];
    }
}
