import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

(:test)
function recordActivitySettingFollowsTheProperty(logger as Logger) as Boolean {
    var saved = Application.Properties.getValue("recordActivity");
    Application.Properties.setValue("recordActivity", false);
    Test.assertEqual(false, Settings.recordActivity());
    Application.Properties.setValue("recordActivity", true);
    Test.assertEqual(true, Settings.recordActivity());
    Application.Properties.setValue("recordActivity", saved);
    return true;
}
