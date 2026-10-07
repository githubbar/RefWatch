import Toybox.Lang;
import Toybox.System;

module Format {
    // "45:00"; minutes are not capped at 59.
    function clock(ms as Long) as String {
        var totalSeconds = (ms / 1000l).toNumber();
        return (totalSeconds / 60).format("%d") + ":" + (totalSeconds % 60).format("%02d");
    }

    function timeOfDay() as String {
        var t = System.getClockTime();
        var hour = t.hour;
        if (!System.getDeviceSettings().is24Hour) {
            hour = hour % 12;
            if (hour == 0) {
                hour = 12;
            }
        }
        return hour.format("%d") + ":" + t.min.format("%02d");
    }
}
