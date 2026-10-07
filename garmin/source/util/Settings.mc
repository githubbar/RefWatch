import Toybox.Application;
import Toybox.Lang;

// App settings, edited in the Garmin Connect phone app (or the simulator's property editor).
module Settings {
    function logGoalScorer() as Boolean {
        return Application.Properties.getValue("logGoalScorer") == true;
    }
}
