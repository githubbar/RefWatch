import Toybox.Communications;
import Toybox.Lang;

// RefWatch's Cloud Functions. Requests travel through the Garmin Connect phone app, so they
// fail with a negative response code when the phone is out of reach.
module Api {
    const BASE_URL = "https://us-central1-refwatchapp.cloudfunctions.net/";

    // POSTs body as JSON. The callback gets the HTTP status (negative for a Connect IQ
    // connection error) and the decoded JSON reply, if any.
    function postJson(path as String, body as Dictionary,
                      callback as Method(responseCode as Number, data as Dictionary or String or Null) as Void) as Void {
        Communications.makeWebRequest(BASE_URL + path, body, {
            :method => Communications.HTTP_REQUEST_METHOD_POST,
            :headers => {"Content-Type" => Communications.REQUEST_CONTENT_TYPE_JSON},
            :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
        }, callback);
    }
}
