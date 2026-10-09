import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// Start menu → Link account: enter the code, see "Linking…", then the result. Any button on
// the result returns to the start menu, which shows the new status.
module LinkFlow {
    function start() as Void {
        Pickers.pushCode(Rez.Strings.PairingCode, new LinkFlowSteps().method(:onCode));
    }
}

class LinkFlowSteps {
    function initialize() {
    }

    function onCode(code as String) as Void {
        WatchUi.pushView(new MessageView(Rez.Strings.Linking), new MessageDelegate(false), WatchUi.SLIDE_IMMEDIATE);
        Pairing.pair(code, method(:onPaired));
    }

    function onPaired(status as Number) as Void {
        WatchUi.switchToView(new MessageView(Pairing.messageId(status)), new MessageDelegate(true),
            WatchUi.SLIDE_IMMEDIATE);
    }
}

// One line of text in the middle of the screen.
class MessageView extends WatchUi.View {
    hidden var _textId as ResourceId;

    function initialize(textId as ResourceId) {
        View.initialize();
        _textId = textId;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(dc.getWidth() / 2, dc.getHeight() / 2, Graphics.FONT_SMALL,
            WatchUi.loadResource(_textId) as String, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}

// While linking, buttons are ignored (the reply replaces the screen). On the result, any
// button or tap returns to the start menu.
class MessageDelegate extends WatchUi.BehaviorDelegate {
    hidden var _closes as Boolean;

    function initialize(closes as Boolean) {
        BehaviorDelegate.initialize();
        _closes = closes;
    }

    function onKey(event as WatchUi.KeyEvent) as Boolean {
        return close();
    }

    function onTap(event as WatchUi.ClickEvent) as Boolean {
        return close();
    }

    function onBack() as Boolean {
        return close();
    }

    hidden function close() as Boolean {
        if (_closes) {
            // Remove the result first, so the start menu it covers is the one replaced.
            WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
            GameList.show();
        }
        return true;
    }
}
