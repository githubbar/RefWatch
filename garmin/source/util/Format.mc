import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Time.Gregorian;

module Format {
    // "45:00"; minutes are not capped at 59.
    function clock(ms as Long) as String {
        var totalSeconds = (ms / 1000l).toNumber();
        return (totalSeconds / 60).format("%d") + ":" + (totalSeconds % 60).format("%02d");
    }

    function timeOfDay() as String {
        var t = System.getClockTime();
        return hourMinute(t.hour, t.min);
    }

    // "14:05", or "2:05" on a watch set to 12-hour time.
    function hourMinute(hour as Number, min as Number) as String {
        if (!System.getDeviceSettings().is24Hour) {
            hour = hour % 12;
            if (hour == 0) {
                hour = 12;
            }
        }
        return hour.format("%d") + ":" + min.format("%02d");
    }

    // "Sat 10:00": a game's start in the watch's time zone.
    function startTime(epochSec as Number) as String {
        var info = Gregorian.info(new Time.Moment(epochSec), Time.FORMAT_MEDIUM);
        return (info.day_of_week as String) + " " + hourMinute(info.hour as Number, info.min as Number);
    }
}
