import Toybox.Lang;
import Toybox.WatchUi;

// Set-up before kick-off. Each line shows its current value; "Kick off" starts the match.
module PreMatch {
    function push(setup as Dictionary) as Void {
        var menu = new WatchUi.Menu2({:title => Rez.Strings.Setup});
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.KickOff, null, :kickOff, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.HomeColor, null, :homeColor, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.AwayColor, null, :awayColor, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.HalfLength, null, :halfMinutes, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.BreakLength, null, :halftimeMinutes, null));
        menu.addItem(new WatchUi.MenuItem(Rez.Strings.KickOffTeam, null, :kickOffTeam, null));
        menu.addItem(new WatchUi.ToggleMenuItem(Rez.Strings.RecordActivity, null, :recordActivity, setup["recordActivity"] == true, null));
        if (setup["recordActivity"] == true) {
            Recorder.warmUp();
        }
        var delegate = new PreMatchDelegate(menu, setup);
        delegate.refresh();
        WatchUi.pushView(menu, delegate, WatchUi.SLIDE_LEFT);
    }
}

class PreMatchDelegate extends WatchUi.Menu2InputDelegate {
    hidden var _menu as WatchUi.Menu2;
    hidden var _setup as Dictionary;
    hidden var _colorKey as String = "homeColor";

    function initialize(menu as WatchUi.Menu2, setup as Dictionary) {
        Menu2InputDelegate.initialize();
        _menu = menu;
        _setup = setup;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id == :kickOff) {
            var match = new MatchState(_setup);
            match.kickOff(Clock.nowMs());
            MatchStore.save(match);
            Recorder.forKickOff(match);
            // Close set-up first so the match replaces the start menu and is the only view.
            WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
            Nav.showMatch(match);
        } else if (id == :homeColor || id == :awayColor) {
            _colorKey = id == :homeColor ? "homeColor" : "awayColor";
            pushColorMenu();
        } else if (id == :halfMinutes) {
            Pickers.pushNumber(Rez.Strings.HalfLength, 5, 60, 5, _setup["halfMinutes"] as Number, method(:onHalfMinutes));
        } else if (id == :halftimeMinutes) {
            Pickers.pushNumber(Rez.Strings.BreakLength, 5, 30, 5, _setup["halftimeMinutes"] as Number, method(:onHalftimeMinutes));
        } else if (id == :kickOffTeam) {
            _setup["kickOffTeam"] = (_setup["kickOffTeam"] as String).equals(TEAM_HOME) ? TEAM_AWAY : TEAM_HOME;
            refresh();
        } else if (id == :recordActivity) {
            var on = (item as WatchUi.ToggleMenuItem).isEnabled();
            _setup["recordActivity"] = on;
            if (on) {
                Recorder.warmUp();
            } else {
                Recorder.releaseGps();
            }
        }
    }

    function onBack() as Void {
        Recorder.releaseGps();
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
    }

    function onHalfMinutes(n as Number) as Void {
        _setup["halfMinutes"] = n;
        refresh();
    }

    function onHalftimeMinutes(n as Number) as Void {
        _setup["halftimeMinutes"] = n;
        refresh();
    }

    function onColor(color as Number) as Void {
        _setup[_colorKey] = color;
        refresh();
    }

    function refresh() as Void {
        setSub(:homeColor, WatchUi.loadResource(TeamColors.nameId(_setup["homeColor"] as Number)) as String);
        setSub(:awayColor, WatchUi.loadResource(TeamColors.nameId(_setup["awayColor"] as Number)) as String);
        setSub(:halfMinutes, minutes(_setup["halfMinutes"] as Number));
        setSub(:halftimeMinutes, minutes(_setup["halftimeMinutes"] as Number));
        var homeKicks = (_setup["kickOffTeam"] as String).equals(TEAM_HOME);
        setSub(:kickOffTeam, (homeKicks ? _setup["homeName"] : _setup["awayName"]) as String);
        WatchUi.requestUpdate();
    }

    hidden function setSub(id as Symbol, text as String) as Void {
        var index = _menu.findItemById(id);
        if (index >= 0) {
            (_menu.getItem(index) as WatchUi.MenuItem).setSubLabel(text);
        }
    }

    hidden function minutes(n as Number) as String {
        return Lang.format(WatchUi.loadResource(Rez.Strings.MinutesFormat) as String, [n]);
    }

    hidden function pushColorMenu() as Void {
        var title = _colorKey.equals("homeColor") ? Rez.Strings.HomeColor : Rez.Strings.AwayColor;
        var menu = new WatchUi.Menu2({:title => title});
        for (var i = 0; i < TeamColors.VALUES.size(); i++) {
            menu.addItem(new WatchUi.MenuItem(TeamColors.nameId(TeamColors.VALUES[i]), null, i, null));
        }
        WatchUi.pushView(menu, new ColorMenuDelegate(method(:onColor)), WatchUi.SLIDE_LEFT);
    }
}

class ColorMenuDelegate extends WatchUi.Menu2InputDelegate {
    hidden var _onColor as Method(color as Number) as Void;

    function initialize(onColor as Method(color as Number) as Void) {
        Menu2InputDelegate.initialize();
        _onColor = onColor;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        _onColor.invoke(TeamColors.VALUES[item.getId() as Number]);
    }
}
