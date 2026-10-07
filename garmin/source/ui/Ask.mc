import Toybox.Lang;
import Toybox.WatchUi;

module Ask {
    // Shows a yes/no question; onYes runs only on yes. The dialog closes itself.
    function push(messageId as ResourceId, onYes as Method() as Void) as Void {
        WatchUi.pushView(new WatchUi.Confirmation(WatchUi.loadResource(messageId) as String),
            new ConfirmDelegate(onYes), WatchUi.SLIDE_IMMEDIATE);
    }
}

class ConfirmDelegate extends WatchUi.ConfirmationDelegate {
    hidden var _onYes as Method() as Void;

    function initialize(onYes as Method() as Void) {
        ConfirmationDelegate.initialize();
        _onYes = onYes;
    }

    function onResponse(response as WatchUi.Confirm) as Boolean {
        if (response == WatchUi.CONFIRM_YES) {
            _onYes.invoke();
        }
        return true;
    }
}
