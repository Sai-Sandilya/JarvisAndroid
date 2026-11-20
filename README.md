# JarvisAndroid

## On-device voice mode

Build and install the debug APK with `./gradlew assembleDebug`; the APK is
`app/build/outputs/apk/debug/app-debug.apk`.

The app requests microphone permission when voice mode starts and runs speech
recognition in a user-started foreground service with an ongoing stop
notification. English (India), Hindi, and Telugu are selectable. Recognition
uses Android's on-device speech recognizer only; Android 12 or later and the
matching offline speech model are required. On Android 13 and later, Jarvis
requests a download when the selected model is missing; connect to the internet,
wait for the download to finish, then start voice mode again. On earlier Android
versions, install the model in the device's speech-recognition settings. Spoken
replies likewise require an installed offline text-to-speech voice for the
selected language.

“Hey Jarvis” is currently matched in recognized speech, not by an always-on
keyword-spotting model. A dedicated offline wake-word engine is needed for
reliable low-power hotword detection.

## Local command actions

The voice service sends recognized text through `CommandNormalizer`,
`IntentClassifier`, `VoiceCommandManager`, and `JarvisActionRouter`. Commands
can also be entered on the dashboard. English, Hindi, and Telugu patterns cover
common app, torch, volume, settings, battery, web, Maps, alarm/timer, note, task,
and reminder requests. Unrecognized phrases are reported rather than silently
treated as successful actions.

Apps are matched against visible launcher activities by their installed labels;
Android may limit what apps are visible to other apps. Flashlight control
requires the runtime Camera permission and a device camera with a flash.
Volume changes, battery status, and saved notes/tasks/reminders are handled
locally. Notes, tasks, and reminders use private app preferences; reminders are
saved as text and do not schedule an alert. Alarm and timer requests open the
system Clock UI for review.

Call, WhatsApp, SMS, and email actions require an in-app confirmation before
opening the relevant app/composer. Jarvis does not place calls or send messages
automatically. Calls still require choosing a contact and placing the call in
the dialer; message recipients/content must be reviewed in the destination
app. Notification reading and exact contact lookup are not implemented.
Web search and Maps navigation open external apps and need connectivity for
online results. These actions have not been verified on a physical OnePlus
device; Android version, installed apps, system settings, permissions, and
offline language models affect availability.