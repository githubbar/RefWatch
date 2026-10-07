import Toybox.Lang;
import Toybox.Test;

(:test)
function minuteLabelsCountFromOne(logger as Logger) as Boolean {
    Test.assertEqual("1'", GameLog.minuteLabel(0, 0l, 30));
    Test.assertEqual("13'", GameLog.minuteLabel(0, 754000l, 30));
    Test.assertEqual("30'", GameLog.minuteLabel(0, 29 * MIN + 59000l, 30));
    return true;
}

(:test)
function minuteLabelsShowAddedTime(logger as Logger) as Boolean {
    Test.assertEqual("30+1'", GameLog.minuteLabel(0, 30 * MIN, 30));
    Test.assertEqual("30+3'", GameLog.minuteLabel(0, 32 * MIN + 12000l, 30));
    return true;
}

(:test)
function secondHalfMinutesContinueFromTheFirst(logger as Logger) as Boolean {
    Test.assertEqual("32'", GameLog.minuteLabel(1, MIN, 30));
    Test.assertEqual("60+1'", GameLog.minuteLabel(1, 30 * MIN + 30000l, 30));
    return true;
}

(:test)
function rowsDescribeEventsInOrder(logger as Logger) as Boolean {
    var m = testMatch();
    m.kickOff(T0);
    m.addGoal(TEAM_HOME, 9, T0 + 754000l);
    m.endPeriod(T0 + 30 * MIN);
    m.kickOff(T0 + 35 * MIN);
    m.addCard(TEAM_AWAY, 4, CARD_YELLOW, T0 + 36 * MIN);
    var labels = {
        "GOAL" => "Goal", "YELLOW" => "Yellow", "RED" => "Red",
        PHASE_FIRST_HALF => "1st half", PHASE_HALF_TIME => "Half time",
        PHASE_SECOND_HALF => "2nd half", PHASE_GAME_ENDED => "Full time"
    };
    var rows = GameLog.rows(m, labels);
    Test.assertEqual(5, rows.size());
    Test.assertEqual("1st half", rows[0][0]);
    Test.assertEqual("13' Goal", rows[1][0]);
    Test.assertEqual("Eagles #9", rows[1][1]);
    Test.assertEqual("Half time", rows[2][0]);
    Test.assertEqual("2nd half", rows[3][0]);
    Test.assertEqual("32' Yellow", rows[4][0]);
    Test.assertEqual("Hawks #4", rows[4][1]);
    return true;
}
