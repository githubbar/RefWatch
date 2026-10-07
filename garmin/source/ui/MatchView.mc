import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

module Nav {
    // The match screen always replaces whatever is showing, so it is the only view on the stack.
    function showMatch(match as MatchState) as Void {
        WatchUi.switchToView(new MatchView(match), new MatchDelegate(match), WatchUi.SLIDE_LEFT);
    }
}

// One screen for the whole match; what it draws depends on the phase. Layout positions are
// fractions of the screen so the same code fits 240 px and 416 px round screens.
class MatchView extends WatchUi.View {
    hidden var _match as MatchState;
    hidden var _timer as Timer.Timer or Null;

    function initialize(match as MatchState) {
        View.initialize();
        _match = match;
    }

    function onShow() as Void {
        if (_match.phase.equals(PHASE_ABANDONED)) {
            GameList.show();
            return;
        }
        var timer = new Timer.Timer();
        timer.start(method(:onTick), 1000, true);
        _timer = timer;
    }

    function onHide() as Void {
        if (_timer != null) {
            (_timer as Timer.Timer).stop();
            _timer = null;
        }
    }

    function onTick() as Void {
        if (_match.takeRegulationAlert(Clock.nowMs())) {
            Alerts.periodEnd();
            MatchStore.save(_match);
        }
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var now = Clock.nowMs();
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        if (_match.phase.equals(PHASE_GAME_ENDED)) {
            drawFullTime(dc, w, h);
            return;
        }

        var halfTime = _match.phase.equals(PHASE_HALF_TIME);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.15, Graphics.FONT_TINY, phaseLabel(),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        var clockMs = halfTime ? _match.breakRemainingMs(now) : _match.elapsedMs(now);
        dc.setColor(_match.isPaused() ? Graphics.COLOR_YELLOW : Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.38, Graphics.FONT_NUMBER_HOT, Format.clock(clockMs),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        var sub = null;
        var subColor = Graphics.COLOR_LT_GRAY;
        if (_match.isPaused()) {
            sub = WatchUi.loadResource(Rez.Strings.Paused) as String;
            subColor = Graphics.COLOR_YELLOW;
        } else if (_match.addedMs(now) > 0) {
            sub = "+" + Format.clock(_match.addedMs(now));
            subColor = Graphics.COLOR_ORANGE;
        } else if (halfTime) {
            sub = WatchUi.loadResource(Rez.Strings.StartSecondHalfHint) as String;
        }
        if (sub != null) {
            dc.setColor(subColor, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, h * 0.58, Graphics.FONT_SMALL, sub,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }

        drawScore(dc, w, h * 0.73);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.88, Graphics.FONT_XTINY, Format.timeOfDay(),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    hidden function phaseLabel() as String {
        var id = Rez.Strings.FirstHalf;
        if (_match.phase.equals(PHASE_HALF_TIME)) {
            id = Rez.Strings.HalfTime;
        } else if (_match.phase.equals(PHASE_SECOND_HALF)) {
            id = Rez.Strings.SecondHalf;
        } else if (_match.phase.equals(PHASE_GAME_ENDED)) {
            id = Rez.Strings.FullTime;
        }
        return WatchUi.loadResource(id) as String;
    }

    // "[bar] 2 - 1 [bar]" centred on y; each bar is that team's colour.
    hidden function drawScore(dc as Graphics.Dc, w as Number, y as Numeric) as Void {
        var cx = w / 2;
        var font = Graphics.FONT_NUMBER_MILD;
        var home = _match.homeScore.format("%d");
        var away = _match.awayScore.format("%d");
        var gap = w * 0.04;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx - gap, y, font, home, Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(cx, y, Graphics.FONT_SMALL, "-", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(cx + gap, y, font, away, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);

        var barW = w * 0.13;
        var barH = dc.getFontHeight(font) * 0.4;
        var homeEdge = cx - gap - dc.getTextWidthInPixels(home, font) - gap;
        var awayEdge = cx + gap + dc.getTextWidthInPixels(away, font) + gap;
        drawTeamBar(dc, homeEdge - barW, y - barH / 2, barW, barH, _match.homeColor);
        drawTeamBar(dc, awayEdge, y - barH / 2, barW, barH, _match.awayColor);
    }

    // A grey outline keeps a black kit visible on the black background.
    hidden function drawTeamBar(dc as Graphics.Dc, x as Numeric, y as Numeric, bw as Numeric, bh as Numeric, color as Number) as Void {
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(x, y, bw, bh, 3);
        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawRoundedRectangle(x, y, bw, bh, 3);
    }

    hidden function drawFullTime(dc as Graphics.Dc, w as Number, h as Number) as Void {
        var cx = w / 2;
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.18, Graphics.FONT_SMALL, phaseLabel(),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        drawScore(dc, w, h * 0.42);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.68, Graphics.FONT_XTINY, WatchUi.loadResource(Rez.Strings.SaveHint) as String,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(cx, h * 0.80, Graphics.FONT_XTINY, WatchUi.loadResource(Rez.Strings.DiscardHint) as String,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}
