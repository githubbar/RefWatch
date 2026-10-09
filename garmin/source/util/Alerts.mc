import Toybox.Attention;
import Toybox.Lang;

module Alerts {
    // Three strong pulses at the end of a half or the break.
    function periodEnd() as Void {
        if (Attention has :vibrate) {
            Attention.vibrate([
                new Attention.VibeProfile(100, 500),
                new Attention.VibeProfile(0, 250),
                new Attention.VibeProfile(100, 500),
                new Attention.VibeProfile(0, 250),
                new Attention.VibeProfile(100, 500)
            ]);
        }
    }

    // Two short pulses, repeated while a half runs into added time (as the Wear OS app does).
    function reminder() as Void {
        if (Attention has :vibrate) {
            Attention.vibrate([
                new Attention.VibeProfile(100, 150),
                new Attention.VibeProfile(0, 50),
                new Attention.VibeProfile(100, 150)
            ]);
        }
    }
}
