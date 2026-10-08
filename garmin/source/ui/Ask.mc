import Toybox.Lang;
import Toybox.WatchUi;

module Ask {
    // Shows a yes/no question; onYes runs only on yes. The dialog closes itself.
    function push(messageId as ResourceId, onYes as Method() as Void) as Void {
        pushText(WatchUi.loadResource(messageId) as String, onYes);
    }

    // Same, for a message built at run time (for example one that names the card to undo).
    function pushText(message as String, onYes as Method() as Void) as Void {
        WatchUi.pushView(new WatchUi.Confirmation(message), new ConfirmDelegate(onYes), WatchUi.SLIDE_IMMEDIATE);
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
