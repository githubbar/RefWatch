import Toybox.Application;
import Toybox.Lang;
import Toybox.System;

const PAIR_LINKED = 0;
const PAIR_BAD_CODE = 1;
const PAIR_TOO_MANY = 2;
const PAIR_OFFLINE = 3;
const PAIR_FAILED = 4;

// Links this watch to a RefWatch account: a 6-digit code from the phone app is exchanged for
// the watch's own access token, which is kept in storage. A failed attempt never removes a
// token the watch already has.
module Pairing {
    const TOKEN_KEY = "deviceToken";
    const APP_VERSION = "1";

    function isLinked() as Boolean {
        return token() != null;
    }

    function token() as String or Null {
        var value = Application.Storage.getValue(TOKEN_KEY);
        return value instanceof String ? value as String : null;
    }

    function forget() as Void {
        Application.Storage.deleteValue(TOKEN_KEY);
    }

    function isCode(text as String) as Boolean {
        if (text.length() != 6) {
            return false;
        }
        var chars = text.toCharArray();
        for (var i = 0; i < chars.size(); i++) {
            var c = chars[i].toNumber();     // '0' is 48, '9' is 57
            if (c < 48 || c > 57) {
                return false;
            }
        }
        return true;
    }

    // Sends the code; onDone gets one of the PAIR_* statuses.
    function pair(code as String, onDone as Method(status as Number) as Void) as Void {
        var request = new PairRequest(onDone);
        Api.postJson("garminPair", {
            "code" => code,
            "deviceName" => deviceName(),
            "appVersion" => APP_VERSION
        }, request.method(:onResponse));
    }

    // The garminPair reply as a PAIR_* status; a token in a 200 reply is stored.
    function applyResponse(responseCode as Number, data as Dictionary or String or Null) as Number {
        if (responseCode == 200) {
            if (data instanceof Dictionary && (data as Dictionary)["token"] instanceof String) {
                Application.Storage.setValue(TOKEN_KEY, (data as Dictionary)["token"] as String);
                return PAIR_LINKED;
            }
            return PAIR_FAILED;
        }
        if (responseCode == 400 || responseCode == 404) {
            return PAIR_BAD_CODE;
        }
        if (responseCode == 429) {
            return PAIR_TOO_MANY;
        }
        // Connect IQ's own codes: down to -399 the phone or the network was not reachable;
        // -400 and below mean a reply arrived but could not be read (for example an HTML page).
        if (responseCode < 0 && responseCode > -400) {
            return PAIR_OFFLINE;
        }
        return PAIR_FAILED;
    }

    function messageId(status as Number) as ResourceId {
        if (status == PAIR_LINKED) {
            return Rez.Strings.LinkedMessage;
        } else if (status == PAIR_BAD_CODE) {
            return Rez.Strings.BadCodeMessage;
        } else if (status == PAIR_TOO_MANY) {
            return Rez.Strings.TooManyMessage;
        } else if (status == PAIR_OFFLINE) {
            return Rez.Strings.OfflineMessage;
        }
        return Rez.Strings.PairFailedMessage;
    }

    // Shown in the phone's list of linked watches. partNumber names the model (for example
    // 006-B2604-00 is a fēnix 5X); older firmware may not have it.
    function deviceName() as String {
        var settings = System.getDeviceSettings();
        if (settings has :partNumber && settings.partNumber != null) {
            return settings.partNumber as String;
        }
        return "Garmin watch";
    }
}

// Holds the caller's callback for the duration of one web request.
class PairRequest {
    hidden var _onDone as Method(status as Number) as Void;

    function initialize(onDone as Method(status as Number) as Void) {
        _onDone = onDone;
    }

    function onResponse(responseCode as Number, data as Dictionary or String or Null) as Void {
        _onDone.invoke(Pairing.applyResponse(responseCode, data));
    }
}
