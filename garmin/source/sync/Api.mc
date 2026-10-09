import Toybox.Communications;
import Toybox.Lang;

// RefWatch's Cloud Functions. Requests travel through the Garmin Connect phone app, so they
// fail with a negative response code when the phone is out of reach.
module Api {
    const BASE_URL = "https://us-central1-refwatchapp.cloudfunctions.net/";

    // POSTs body as JSON, with the watch's token when it has one. The callback gets the HTTP
    // status (negative for a Connect IQ connection error) and the decoded JSON reply, if any.
    function postJson(path as String, body as Dictionary, token as String or Null,
                      callback as Method(responseCode as Number, data as Dictionary or String or Null) as Void) as Void {
        var headers = {"Content-Type" => Communications.REQUEST_CONTENT_TYPE_JSON};
        if (token != null) {
            headers["Authorization"] = "Bearer " + token;
        }
        Communications.makeWebRequest(BASE_URL + path, body, {
            :method => Communications.HTTP_REQUEST_METHOD_POST,
            :headers => headers,
            :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
        }, callback);
    }

    // GETs path with the watch's token; the callback is as for postJson.
    function getJson(path as String, token as String,
                     callback as Method(responseCode as Number, data as Dictionary or String or Null) as Void) as Void {
        Communications.makeWebRequest(BASE_URL + path, null, {
            :method => Communications.HTTP_REQUEST_METHOD_GET,
            :headers => {"Authorization" => "Bearer " + token},
            :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
        }, callback);
    }
}