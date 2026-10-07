import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// Picker text carries its own black background: on the fenix 5X the picker is drawn on white
// and ignores dc.clear(), so plain white digits would be invisible there. On devices with a
// black picker the black behind the text is not visible.
module Pickers {
    // Single column min..max, starting on current. Pushed over the current view.
    function pushNumber(titleId as ResourceId, min as Number, max as Number, current as Number,
                        onPicked as Method(n as Number) as Void) as Void {
        var picker = new WatchUi.Picker({
            :title => title(titleId),
            :pattern => [new NumberFactory(min, max)],
            :defaults => [current - min]
        });
        WatchUi.pushView(picker, new NumberPickerDelegate(onPicked), WatchUi.SLIDE_IMMEDIATE);
    }

    // Tens and ones columns (0–99): two short scrolls instead of up to 99 button presses.
    // Replaces the current view, so accepting returns to the view underneath it.
    function switchToPlayerNumber(titleId as ResourceId, onPicked as Method(n as Number) as Void) as Void {
        var picker = new WatchUi.Picker({
            :title => title(titleId),
            :pattern => [new NumberFactory(0, 9), new NumberFactory(0, 9)],
            :defaults => [0, 0]
        });
        WatchUi.switchToView(picker, new NumberPickerDelegate(onPicked), WatchUi.SLIDE_IMMEDIATE);
    }

    function title(titleId as ResourceId) as WatchUi.Text {
        return new WatchUi.Text({
            :text => WatchUi.loadResource(titleId) as String,
            :color => Graphics.COLOR_WHITE,
            :backgroundColor => Graphics.COLOR_BLACK,
            :font => Graphics.FONT_TINY,
            :locX => WatchUi.LAYOUT_HALIGN_CENTER,
            :locY => WatchUi.LAYOUT_VALIGN_BOTTOM
        });
    }
}

class NumberFactory extends WatchUi.PickerFactory {
    hidden var _min as Number;
    hidden var _max as Number;

    function initialize(min as Number, max as Number) {
        PickerFactory.initialize();
        _min = min;
        _max = max;
    }

    function getSize() as Number {
        return _max - _min + 1;
    }

    function getValue(index as Number) as Object or Null {
        return _min + index;
    }

    function getDrawable(index as Number, selected as Boolean) as WatchUi.Drawable or Null {
        return new WatchUi.Text({
            :text => (_min + index).format("%d"),
            :color => selected ? Graphics.COLOR_WHITE : Graphics.COLOR_LT_GRAY,
            :backgroundColor => Graphics.COLOR_BLACK,
            :font => Graphics.FONT_NUMBER_MEDIUM,
            :locX => WatchUi.LAYOUT_HALIGN_CENTER,
            :locY => WatchUi.LAYOUT_VALIGN_CENTER
        });
    }
}

// Two columns are read as tens and ones. The picker closes before onPicked runs, so the
// callback can push or switch views.
class NumberPickerDelegate extends WatchUi.PickerDelegate {
    hidden var _onPicked as Method(n as Number) as Void;

    function initialize(onPicked as Method(n as Number) as Void) {
        PickerDelegate.initialize();
        _onPicked = onPicked;
    }

    function onAccept(values as Array) as Boolean {
        var n = values[0] as Number;
        if (values.size() == 2) {
            n = n * 10 + (values[1] as Number);
        }
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
        _onPicked.invoke(n);
        return true;
    }

    function onCancel() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
        return true;
    }
}
