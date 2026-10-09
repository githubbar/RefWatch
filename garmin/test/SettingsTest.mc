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

(:test)
function settingsSettersWriteTheProperties(logger as Logger) as Boolean {
    var savedRecord = Application.Properties.getValue("recordActivity");
    var savedScorer = Application.Properties.getValue("logGoalScorer");
    Settings.setRecordActivity(false);
    Test.assertEqual(false, Settings.recordActivity());
    Settings.setRecordActivity(true);
    Test.assertEqual(true, Settings.recordActivity());
    Settings.setLogGoalScorer(true);
    Test.assertEqual(true, Settings.logGoalScorer());
    Settings.setLogGoalScorer(false);
    Test.assertEqual(false, Settings.logGoalScorer());
    Application.Properties.setValue("recordActivity", savedRecord);
    Application.Properties.setValue("logGoalScorer", savedScorer);
    return true;
}

(:test)
function appVersionIsAThreePartNumber(logger as Logger) as Boolean {
    var parts = 0;
    var chars = Settings.appVersion().toCharArray();
    for (var i = 0; i < chars.size(); i++) {
        if (chars[i].toNumber() == 46) {       // '.'
            parts += 1;
        }
    }
    Test.assertEqual(2, parts);
    return true;
}
