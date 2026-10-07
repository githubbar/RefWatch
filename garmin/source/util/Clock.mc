import Toybox.Lang;
import Toybox.System;
import Toybox.Time;

// Wall-clock epoch milliseconds. Time.now() has one-second resolution, so the sub-second part
// comes from System.getTimer(), measured from when the app started. Wall-clock based, so a
// match saved before a restart resumes with the right elapsed time.
module Clock {
    var baseEpochMs as Long = 0l;
    var baseTimer as Number = 0;

    function init() as Void {
        baseEpochMs = Time.now().value().toLong() * 1000l;
        baseTimer = System.getTimer();
    }

    function nowMs() as Long {
        return baseEpochMs + (System.getTimer() - baseTimer).toLong();
    }
}
