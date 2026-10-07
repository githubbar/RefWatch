import Toybox.Lang;
import Toybox.Test;

(:test)
function pickerClampsStartValueIntoRange(logger as Logger) as Boolean {
    Test.assertEqual(5, new NumberPickerModel(5, 60, 5, 2, 1).result());
    Test.assertEqual(60, new NumberPickerModel(5, 60, 5, 99, 1).result());
    Test.assertEqual(45, new NumberPickerModel(5, 60, 5, 45, 1).result());
    Test.assertEqual(1, new NumberPickerModel(1, 30, 1, -4, 1).result());
    Test.assertEqual(30, new NumberPickerModel(1, 30, 1, 31, 1).result());
    return true;
}

(:test)
function pickerRoundsStartValueToTheNearestStep(logger as Logger) as Boolean {
    Test.assertEqual(10, new NumberPickerModel(5, 60, 5, 12, 1).result());
    Test.assertEqual(15, new NumberPickerModel(5, 60, 5, 13, 1).result());
    Test.assertEqual(45, new NumberPickerModel(5, 60, 5, 47, 1).result());
    Test.assertEqual(50, new NumberPickerModel(5, 60, 5, 48, 1).result());
    // Rounding may not leave the range: 58 is nearer 60 than 55, and 62 is clamped first.
    Test.assertEqual(60, new NumberPickerModel(5, 60, 5, 58, 1).result());
    Test.assertEqual(60, new NumberPickerModel(5, 60, 5, 62, 1).result());
    return true;
}

(:test)
function pickerStepsByTheStepAndWrapsAtBothEnds(logger as Logger) as Boolean {
    var m = new NumberPickerModel(5, 60, 5, 45, 1);
    m.increment();
    Test.assertEqual(50, m.result());
    m.decrement();
    m.decrement();
    Test.assertEqual(40, m.result());
    var up = new NumberPickerModel(5, 60, 5, 60, 1);
    up.increment();
    Test.assertEqual(5, up.result());
    var down = new NumberPickerModel(5, 60, 5, 5, 1);
    down.decrement();
    Test.assertEqual(60, down.result());
    return true;
}

(:test)
function pickerWrapsToTheLargestValidValueWhenTheStepDoesNotReachMax(logger as Logger) as Boolean {
    // 1, 4, 7, 10 are valid for min 1, max 11, step 3.
    var down = new NumberPickerModel(1, 11, 3, 1, 1);
    down.decrement();
    Test.assertEqual(10, down.result());
    var up = new NumberPickerModel(1, 11, 3, 10, 1);
    up.increment();
    Test.assertEqual(1, up.result());
    return true;
}

(:test)
function pickerOneMinuteStepsWork(logger as Logger) as Boolean {
    var m = new NumberPickerModel(1, 30, 1, 15, 1);
    m.increment();
    Test.assertEqual(16, m.result());
    return true;
}

(:test)
function pickerSingleColumnAcceptsOnFirstAdvanceAndCancelsOnBack(logger as Logger) as Boolean {
    var m = new NumberPickerModel(5, 30, 5, 15, 1);
    Test.assert(m.advance());          // no next column: accept now
    Test.assert(m.back());             // already on the first column: cancel
    Test.assertEqual(15, m.result());
    return true;
}

(:test)
function pickerTwoColumnsReadAsTensAndOnes(logger as Logger) as Boolean {
    var m = new NumberPickerModel(0, 9, 1, 0, 2);
    Test.assertEqual(0, m.result());
    m.increment();                     // tens: 1
    Test.assertEqual(false, m.advance());
    for (var i = 0; i < 4; i++) {
        m.increment();                 // ones: 4
    }
    Test.assertEqual(14, m.result());
    Test.assertEqual(2, m.values.size());
    return true;
}

(:test)
function pickerTwoColumnAdvanceThenAcceptAndBackWalksLeft(logger as Logger) as Boolean {
    var m = new NumberPickerModel(0, 9, 1, 0, 2);
    Test.assertEqual(0, m.column);
    Test.assertEqual(false, m.advance());   // tens -> ones, not done
    Test.assertEqual(1, m.column);
    Test.assertEqual(true, m.advance());    // ones is last: accept
    Test.assertEqual(false, m.back());      // ones -> tens, not cancelled
    Test.assertEqual(0, m.column);
    Test.assertEqual(true, m.back());       // already on tens: cancel
    return true;
}

(:test)
function pickerDigitsWrapNineToZeroAndBack(logger as Logger) as Boolean {
    var m = new NumberPickerModel(0, 9, 1, 0, 2);
    m.decrement();                     // tens: 0 -> 9
    Test.assertEqual(90, m.result());
    m.increment();                     // tens: 9 -> 0
    Test.assertEqual(0, m.result());
    m.advance();
    m.decrement();                     // ones: 0 -> 9
    Test.assertEqual(9, m.result());
    m.increment();                     // ones: 9 -> 0
    Test.assertEqual(0, m.result());
    return true;
}

(:test)
function pickerChangesOnlyTheActiveColumn(logger as Logger) as Boolean {
    var m = new NumberPickerModel(0, 9, 1, 0, 2);
    m.increment();
    m.advance();
    m.increment();
    m.increment();
    Test.assertEqual(1, m.values[0]);
    Test.assertEqual(2, m.values[1]);
    return true;
}