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
}
