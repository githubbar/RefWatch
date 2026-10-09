# RefWatch Privacy Policy

**Effective date:** October 9, 2026

RefWatch is a soccer referee app for Wear OS watches with a companion phone app. It is
developed by Alex Leykin ("we", "us"). This policy explains what data RefWatch collects,
why, where it is stored, who it is shared with, and the choices and rights you have.

Contact: **alexleykin+refwatch@gmail.com**

## Data we collect

### Account data
To use RefWatch you create an account with an **email address and password**. The
account is managed by Google Firebase Authentication. We never see your password.

### Game data you enter
For each game you create or import: game and field number, team names and colours,
age group, competition, venue, your referee assignment, date and time, and your notes.
During a game the watch records the score, cards, goals, phase changes and their times.
Cards record the **player's shirt number**, and goals record it when "Log goal scorer"
is on. We do not record player names.

### Fitness and health data from your watch
While a game is running, the watch records:

- **Heart rate**, sampled throughout the game
- **Steps and distance**

**Heart rate is health data.** It is collected only after you grant the watch's
*Body sensors* permission, and only while a game is in progress.

### Location
If you turn on **"Collect position info"** in the phone app's Settings, the watch
records your **precise GPS position** during games, so the app can show where on the
field you moved. It is **off by default**, and is never collected when you are the
assistant referee. Location is never recorded outside a running game.

### Usage and device data
The app uses **Google Analytics for Firebase**, which automatically collects data such
as app opens, screens viewed, session length, device model, operating-system version,
approximate location derived from IP address, and an app-instance identifier. It does
not receive your game, health or location data.

### Schedule imports
When you import a calendar file (.ics) of your assignments, the event titles,
descriptions, times and locations in it are processed to create games (see "AI
processing" below).

### Garmin watches
If you link a Garmin watch, we store a record of it under your account: the watch's model
or part number, when it was linked and when it last contacted us, and a hashed form of the
access key we gave it. While you are linking, the 6-digit code is stored with your account
and is valid for 10 minutes; it is deleted when it is used or replaced, and otherwise
automatically within about a day after it expires. To stop code guessing, we also store a
hashed form of the IP address that tries a code, with a count of failed tries; it is not
linked to your account and is deleted automatically within about a day after the hour it
covers. Your games travel between our servers and the watch through Garmin's Connect app,
which Garmin operates under its own privacy policy. You can unlink a watch at any time in
the phone app's Settings, which deletes its record and stops its access.

## How we use your data

| Purpose | Data | Legal basis (EU / UK GDPR) |
|---|---|---|
| Run the app: time and record games, sync them between phone and watch | Account, game data | Performance of a contract (providing the service you signed up for) |
| Show your heart-rate, distance and movement statistics | Heart rate, steps, distance, location | Your **explicit consent**, given by granting the permission or turning on the setting. You can withdraw it at any time. |
| Read team, age-group and venue details from imported schedules | Imported calendar events | Performance of a contract |
| Understand how the app is used and fix problems | Usage and device data | Legitimate interests (improving the app) |

We do **not** sell your data, use it for advertising, or build advertising profiles.

## AI processing

When you import a schedule, the imported calendar events are sent to **Google's Gemini
models through Firebase AI Logic (Vertex AI)** to extract team names, age group and
other details. Only the calendar events are sent, never your health or location data.
Under Google Cloud's terms, this data is not used to train Google's models.

## Where your data is stored and who processes it

Your account and games are stored in **Google Cloud Firestore**, run by Google LLC on
our behalf, in the United States. Google acts as our data
processor for:

- **Firebase Authentication**: your account
- **Cloud Firestore and Cloud Functions**: storage and sync of your games and settings
- **Firebase AI Logic / Vertex AI**: schedule extraction
- **Google Analytics for Firebase**: usage statistics
- **Google Play services**: Wear OS phone–watch communication and maps

Data sent between your phone and watch travels over the Wear OS connection. Data is
also cached on the devices themselves.

If you link a Garmin watch, **Garmin Ltd.** carries data between our servers and the watch
through its Garmin Connect app and service, under Garmin's own privacy policy.

**International transfers:** if you are in the EU, EEA, UK or Switzerland, your data is
transferred to the United States. Google LLC is certified under the EU–US Data Privacy
Framework and its UK and Swiss extensions, and Google's data processing terms include
the European Commission's Standard Contractual Clauses.

We share data with no one else, except Garmin as described above when you link a Garmin
watch, and where required by law, for example to comply with a court order.

## How long we keep your data

- **Account and game data:** for as long as your account exists. When you ask us to
  delete it, it is erased within 30 days.
- **Analytics data:** kept by Google Analytics for up to 14 months.
- **Data on your devices:** until you delete the game or uninstall the app.

## Your choices and rights

- **Location:** turn off "Collect position info" in the phone app's Settings.
- **Heart rate:** revoke the *Body sensors* permission in the watch's settings. The
  game timer still works without it.
- **Delete a game:** delete it in the phone app.
- **Delete your account:** use **Settings → Delete account** in the phone app. This
  permanently erases your account and every game stored with it. You can also ask us
  by email to delete it.

If you are in the EU, EEA or UK, you have the right to access, correct, delete, restrict
or object to processing of your data, to receive it in a portable format, and to
withdraw consent at any time. Withdrawing consent does not affect processing that
happened before you withdrew it. Email us to exercise any of these rights; we will
respond within one month. You also have the right to complain to your local data
protection authority (in the UK, the Information Commissioner's Office).

If you are in Canada, you have the right to access and correct your personal
information and to withdraw consent, and you may complain to the Office of the Privacy
Commissioner of Canada.

If you are in California, you have the right to know what personal information we
collect, to delete it, and to correct it. We do not sell or share personal information
for cross-context behavioural advertising.

## Children

RefWatch is meant for referees and is not directed at children under 13, or under 16
in the EU. We do not knowingly collect data from children. If you believe a child has
given us data, email us and we will delete it.

Game records can contain youth players' **shirt numbers**, but never their names or any
other information that identifies them.

## Security

Data is encrypted in transit (TLS). Google encrypts data at rest.
Firestore security rules let only your signed-in
account read or write your games.

## Changes to this policy

If we change this policy we will update the effective date above. If we make a
significant change, such as collecting a new kind of data, we will also tell you in the
app.

## Contact

alexleykin+refwatch@gmail.com
