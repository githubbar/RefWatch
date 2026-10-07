import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// Number entry drawn by us rather than WatchUi.Picker: the system picker paints its own
// (on some watches white) background, so its label and arrows can vanish. This one looks
// the same on every watch.
module Pickers {
    // Single column min..max, starting on current (clamped into range). Pushed over the
    // current view.
    function pushNumber(titleId as ResourceId, min as Number, max as Number, step as Number,
                        current as Number, onPicked as Method(n as Number) as Void) as Void {
        var model = new NumberPickerModel(min, max, step, current, 1);
        WatchUi.pushView(new NumberPickerView(titleId, model), new NumberPickerDelegate(model, onPicked),
            WatchUi.SLIDE_IMMEDIATE);
    }

    // Tens and ones columns (0–99): two short scrolls instead of up to 99 button presses.
    // Replaces the current view, so accepting returns to the view underneath it.
    function switchToPlayerNumber(titleId as ResourceId, onPicked as Method(n as Number) as Void) as Void {
        var model = new NumberPickerModel(0, 9, 1, 0, 2);
        WatchUi.switchToView(new NumberPickerView(titleId, model), new NumberPickerDelegate(model, onPicked),
            WatchUi.SLIDE_IMMEDIATE);
    }
}

// Title on top, the value(s) large in the middle with a scroll arrow above and below the
// active one, and a hint at the bottom. Positions are fractions of the screen so the same
// code fits 240 px and 416 px round screens.
class NumberPickerView extends WatchUi.View {
    hidden var _titleId as ResourceId;
    hidden var _model as NumberPickerModel;

    function initialize(titleId as ResourceId, model as NumberPickerModel) {
        View.initialize();
        _titleId = titleId;
        _model = model;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var vcenter = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.20, Graphics.FONT_TINY, WatchUi.loadResource(_titleId) as String, vcenter);

        var font = Graphics.FONT_NUMBER_HOT;
        var y = h * 0.52;
        var columns = _model.values.size();
        var digitW = dc.getTextWidthInPixels("0", font);
        var spacing = digitW * 1.1;   // distance between the centers of two digits
        var activeX = cx;
        for (var i = 0; i < columns; i++) {
            var x = cx + (i - (columns - 1) / 2.0) * spacing;
            var active = i == _model.column;
            dc.setColor(active ? Graphics.COLOR_WHITE : Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(x, y, font, _model.values[i].format("%d"), vcenter);
            if (active) {
                activeX = x;
            }
        }

        // Scroll cues: UP (arrow above) increases, DOWN (arrow below) decreases.
        var reach = dc.getFontHeight(font) * 0.5 + h * 0.025;
        var arrowW = w * 0.06;
        var arrowH = w * 0.045;
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        var top = y - reach;
        dc.fillPolygon([[activeX, top - arrowH], [activeX - arrowW, top], [activeX + arrowW, top]]);
        var bottom = y + reach;
        dc.fillPolygon([[activeX, bottom + arrowH], [activeX - arrowW, bottom], [activeX + arrowW, bottom]]);

        var hint = _model.isLastColumn() ? Rez.Strings.PickerHintOk : Rez.Strings.PickerHintNext;
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.84, Graphics.FONT_XTINY, WatchUi.loadResource(hint) as String, vcenter);
    }
}

// UP/swipe down raises the active digit, DOWN/swipe up lowers it, START/tap moves on and on
// the last column accepts, BACK steps left and on the first column cancels. The picker
// closes before onPicked runs, so the callback can push or switch views.
class NumberPickerDelegate extends WatchUi.BehaviorDelegate {
    hidden var _model as NumberPickerModel;
    hidden var _onPicked as Method(n as Number) as Void;

    function initialize(model as NumberPickerModel, onPicked as Method(n as Number) as Void) {
        BehaviorDelegate.initialize();
        _model = model;
        _onPicked = onPicked;
    }

    function onPreviousPage() as Boolean {
        _model.increment();
        WatchUi.requestUpdate();
        return true;
    }

    function onNextPage() as Boolean {
        _model.decrement();
        WatchUi.requestUpdate();
        return true;
    }

    function onSelect() as Boolean {
        if (_model.advance()) {
            var n = _model.result();
            WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
            _onPicked.invoke(n);
        } else {
            WatchUi.requestUpdate();
        }
        return true;
    }

    function onBack() as Boolean {
        if (_model.back()) {
            WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
        } else {
            WatchUi.requestUpdate();
        }
        return true;
    }
}
