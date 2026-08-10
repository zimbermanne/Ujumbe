# Ujumbe — Native Android SMS App

A clean, self-contained Android app that sends and receives real SMS
messages using the device's own SIM/carrier. No backend, no third-party
SMS gateway, no telemetry — everything runs through Android's built-in
`SmsManager` and SMS content provider, the same APIs the stock Messages
app uses.

## What it does
- **Conversation list** — reads existing SMS threads from the device via
  `Telephony.Sms.CONTENT_URI`, most recent first.
- **Thread view** — shows a chat-style back-and-forth for one contact/number,
  with a send box at the bottom.
- **Compose** — start a new conversation by typing a phone number directly.
- **Receiving** — `SmsReceiver` listens for `SMS_RECEIVED_ACTION` and tells
  any open screen to refresh immediately when a new text arrives.
- **Sending** — `SmsManager.sendTextMessage` / `sendMultipartTextMessage`
  for messages over 160 characters.

## What it deliberately does NOT do
- No cloud sync, no external SMS gateway, no analytics.
- No calling, contacts management, MMS, or group messaging — just plain
  1:1 SMS, per your request.

## Permissions
`SEND_SMS`, `READ_SMS`, `RECEIVE_SMS` — requested at runtime on first launch.
Android will show its usual permission dialogs; all three must be granted
for the app to function.

## Important: Android's default-SMS-app restriction
Starting with Android 4.4, only the device's **default SMS app** can write
new incoming messages into the shared SMS provider, and Google Play
restricts the `RECEIVE_SMS`/`READ_SMS` permission group to apps that either
are, or are requesting to become, the default handler.

This app is **not yet registered as a default-SMS-app candidate** (it's
missing the extra required intent filters — `SENDTO`, `RESPOND_VIA_MESSAGE`,
and a `SMS_DELIVER_ACTION` receiver instead of `SMS_RECEIVED_ACTION`). As
built, it will:
- Read and send SMS fine on any Android device when sideloaded.
- Still *receive* the broadcast for incoming messages and refresh the UI.
- Work best for personal/sideloaded use (not a Play Store listing) since
  Play requires apps requesting these permissions to be default-SMS-app
  eligible.

If you want, I can extend it to qualify as a full default-SMS-app
candidate next — that's a separate, larger change (adds a few more
manifest entries and an `SMS_DELIVER` receiver instead of `SMS_RECEIVED`).

## Building
Open the `UjumbeSMS/` folder in Android Studio (Hedgehog or newer), let it
sync Gradle, and run on a device or emulator with an active SIM/telephony
service. Emulators can send SMS to each other via the emulator console but
won't reach real numbers.

```
minSdk 23 (Android 6.0)
targetSdk 34
```
