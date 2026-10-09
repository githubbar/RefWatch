import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;

// App settings, edited in the watch's Settings menu or in the Garmin Connect phone app (both
// write the same properties).
module Settings {
    function logGoalScorer() as Boolean {
        return Application.Properties.getValue("logGoalScorer") == true;
    }

    function setLogGoalScorer(on as Boolean) as Void {
        Application.Properties.setValue("logGoalScorer", on);
    }

    // On unless the referee turned it off; a missing value (older install) counts as on.
    function recordActivity() as Boolean {
        return Application.Properties.getValue("recordActivity") != false;
    }

    function setRecordActivity(on as Boolean) as Void {
        Application.Properties.setValue("recordActivity", on);
    }

    // Connect IQ gives an app no way to read its own version, so it lives in
    // resources/strings/version.xml and is bumped at each release.
    function appVersion() as String {
        return WatchUi.loadResource(Rez.Strings.AppVersion) as String;
    }

    // The code typed in Garmin Connect; empty when none. Pairing.isCode decides whether it is usable.
    function pairingCode() as String {
        var value = Application.Properties.getValue("pairingCode");
        return value instanceof String ? (value as String) : "";
    }

    function clearPairingCode() as Void {
        Application.Properties.setValue("pairingCode", "");
    }
}
