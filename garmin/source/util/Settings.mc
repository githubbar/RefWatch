import Toybox.Application;
import Toybox.Lang;

// App settings, edited in the Garmin Connect phone app (or the simulator's property editor).
module Settings {
    function logGoalScorer() as Boolean {
        return Application.Properties.getValue("logGoalScorer") == true;
    }

    // On unless the referee turned it off; a missing value (older install) counts as on.
    function recordActivity() as Boolean {
        return Application.Properties.getValue("recordActivity") != false;
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
