import Toybox.Lang;
import Toybox.Math;

// Random UUID-shaped ids for games and events. Math.srand is seeded in RefWatchApp.onStart.
module Ids {
    function newId() as String {
        var hex = "0123456789abcdef";
        var id = "";
        for (var i = 0; i < 32; i++) {
            if (i == 8 || i == 12 || i == 16 || i == 20) {
                id += "-";
            }
            var n = Math.rand() % 16;
            id += hex.substring(n, n + 1);
        }
        return id;
    }
}
