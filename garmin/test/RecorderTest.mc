import Toybox.Lang;
import Toybox.Test;

(:debug)
function recordingMatch() as MatchState {
    var m = testMatch();
    m.recordActivity = true;
    return m;
}

(:test)
function recorderIsInactiveAfterDiscard(logger as Logger) as Boolean {
    Recorder.discard();
    Test.assert(!Recorder.isActive());
    return true;
}

(:test)
function recorderStartsOnceAndDiscards(logger as Logger) as Boolean {
    if (!(Toybox has :ActivityRecording)) {
        return true;
    }
    Recorder.discard();
    Recorder.start();
    Recorder.start();                 // a second start must not create another session
    Test.assert(Recorder.isActive());
    Recorder.discard();
    Test.assert(!Recorder.isActive());
    return true;
}

(:test)
function kickOffDoesNotRecordWhenTheMatchOptsOut(logger as Logger) as Boolean {
    Recorder.discard();
    var m = testMatch();              // recordActivity is false
    m.kickOff(T0);
    Recorder.forKickOff(m);
    Test.assert(!Recorder.isActive());
    return true;
}

(:test)
function sessionLastsFromKickOffUntilDiscard(logger as Logger) as Boolean {
    if (!(Toybox has :ActivityRecording)) {
        return true;
    }
    Recorder.discard();
    var m = recordingMatch();
    m.kickOff(T0);
    Recorder.forKickOff(m);
    Test.assert(Recorder.isActive());
    m.endPeriod(T0 + 30 * MIN);       // half time: lap, keep recording
    Recorder.forPeriodEnd(m);
    Test.assert(Recorder.isActive());
    m.kickOff(T0 + 35 * MIN);         // 2nd half: lap on the same session
    Recorder.forKickOff(m);
    Test.assert(Recorder.isActive());
    m.endPeriod(T0 + 65 * MIN);       // full time: stopped, kept until Save or Discard
    Recorder.forPeriodEnd(m);
    Test.assert(Recorder.isActive());
    Recorder.discard();
    Test.assert(!Recorder.isActive());
    return true;
}

(:test)
function resumeRecordsOnlyAMatchThatIsUnderway(logger as Logger) as Boolean {
    if (!(Toybox has :ActivityRecording)) {
        return true;
    }
    Recorder.discard();
    var m = recordingMatch();
    m.kickOff(T0);
    m.endPeriod(T0 + 30 * MIN);
    m.kickOff(T0 + 35 * MIN);
    m.endPeriod(T0 + 65 * MIN);       // full time: nothing left to record
    Recorder.forResume(m);
    Test.assert(!Recorder.isActive());
    var playing = recordingMatch();
    playing.kickOff(T0);
    Recorder.forResume(playing);
    Test.assert(Recorder.isActive());
    Recorder.discard();
    return true;
}
