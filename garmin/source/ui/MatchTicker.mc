import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

// The match's one-second tick. It lives as long as the match, not the match screen, so the
// period-end buzz and the added-time reminders also fire while a menu, a picker or the game log
// is open.
class MatchTicker {
    hidden var _match as MatchState;
    hidden var _timer as Timer.Timer;

    function initialize(match as MatchState) {
        _match = match;
        _timer = new Timer.Timer();
        _timer.start(method(:onTick), 1000, true);
    }

    function stop() as Void {
        _timer.stop();
    }

    function onTick() as Void {
        var alert = _match.takeAlert(Clock.nowMs());
        if (alert == ALERT_PERIOD_END) {
            Alerts.periodEnd();
        } else if (alert == ALERT_REMINDER) {
            Alerts.reminder();
        }
        if (alert != ALERT_NONE) {
            MatchStore.save(_match);
        }
        WatchUi.requestUpdate();
    }
}
