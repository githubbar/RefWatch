import Toybox.Lang;

// State of the number picker, without any drawing. A column steps through min, min + step,
// min + 2 * step ... up to max. One column is a plain value; two columns are read as tens
// and ones (callers pass 0..9 step 1 for each digit).
class NumberPickerModel {
    var values as Array<Number>;   // one entry per column
    var column as Number = 0;      // the active column

    hidden var _min as Number;
    hidden var _step as Number;
    hidden var _largest as Number; // the largest valid value: it is not max when step skips it

    function initialize(min as Number, max as Number, step as Number, current as Number, columns as Number) {
        _min = min;
        _step = step;
        _largest = min + ((max - min) / step) * step;
        // Clamp into range, then round to the nearest valid value (halves round up).
        var clamped = current < min ? min : (current > max ? max : current);
        var start = min + ((clamped - min + step / 2) / step) * step;
        if (start > _largest) {
            start = _largest;
        }
        values = [] as Array<Number>;
        for (var i = 0; i < columns; i++) {
            values.add(start);
        }
    }

    // Past the largest value it wraps to min.
    function increment() as Void {
        values[column] = values[column] + _step > _largest ? _min : values[column] + _step;
    }

    // Below min it wraps to the largest valid value.
    function decrement() as Void {
        values[column] = values[column] - _step < _min ? _largest : values[column] - _step;
    }

    // Moves to the next column. True when there is none, which means the value is accepted.
    function advance() as Boolean {
        if (column + 1 >= values.size()) {
            return true;
        }
        column += 1;
        return false;
    }

    // Moves to the previous column. True when already on the first, which means cancel.
    function back() as Boolean {
        if (column == 0) {
            return true;
        }
        column -= 1;
        return false;
    }

    function isLastColumn() as Boolean {
        return column + 1 >= values.size();
    }

    function result() as Number {
        return values.size() == 2 ? values[0] * 10 + values[1] : values[0];
    }
}