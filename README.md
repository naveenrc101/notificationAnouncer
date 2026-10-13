# Notification Announcer

An Android app that announces individual WhatsApp and WhatsApp Business messages using the device's built-in Text-to-Speech engine. Group chats and SMS apps are excluded.

---

## Features

- Announces **WhatsApp** and **WhatsApp Business** messages
- Announces only individual chats; group chats and SMS apps are excluded
- Announces the sender; say **read it** in the two-second voice window to hear the message
- Skips group summary and ongoing notifications to avoid duplicates
- Simple one-tap permission setup screen
- Works in the background — no need to keep the app open

---

## Supported Apps

| App | Package |
|-----|---------|
| WhatsApp | `com.whatsapp` |
| WhatsApp Business | `com.whatsapp.w4b` |

---

## Requirements

- Android **8.0 (API 26)** or higher
- Device TTS engine installed (pre-installed on most Android devices)
- Notification access permission granted

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/naveenrc101/notificationAnouncer.git
```

### 2. Build the APK

**Option A — Without Android Studio (Windows):**

```powershell
cd notificationAnouncer
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\build.ps1
```

This script automatically installs JDK 17, Gradle, Android SDK, and builds the APK.

**Option B — Via GitHub Actions:**

Every push to `master` triggers a build. Download the APK from:
`Actions → latest run → Artifacts`

**Option C — Android Studio:**

File → Open → select the folder → Run ▶

---

## Installing the APK

### Disable Play Protect (required for sideloading)

1. Open **Google Play Store** → profile picture → **Play Protect**
2. Tap ⚙️ → toggle **"Scan apps with Play Protect"** OFF
3. Install the APK, then re-enable Play Protect

### Enable Unknown Sources

Settings → Apps → Special app access → Install unknown apps → select your file manager → Allow

---

## Granting Notification Access

The app requires **Notification Access** (a special system permission) to read notifications.

### Standard path (Android 8–13)

Settings → Apps → ⋮ → **Special app access** → **Notification access** → Notification Announcer → ON → Allow

### Google Pixel (Android 14+)

Settings → search **`notification access`** → Notification access → Notification Announcer → ON

### Via ADB (Android 16 / if option not visible in UI)

Android 16 may not show the Notification Access toggle in the UI. Use ADB to grant it directly:

**1. Install ADB (standalone)**

Download Platform Tools:
`https://dl.google.com/android/repository/platform-tools-latest-windows.zip`

Extract to `C:\platform-tools`

**2. Enable USB Debugging on phone**

Settings → About Phone → tap **Build Number** 7 times → Settings → System → Developer Options → **USB Debugging** ON

**3. Connect and grant permission**

```powershell
# Verify phone is detected
C:\platform-tools\adb.exe devices

# Grant notification listener access
C:\platform-tools\adb.exe shell cmd notification allow_listener com.example.notificationannouncer/.NotificationAnnouncerService
```

Reopen the app — it should show **"Listening"** in green.

---

## How It Works

```
Notification arrives
        ↓
NotificationListenerService.onNotificationPosted()
        ↓
Filter WhatsApp / WhatsApp Business, then require an explicit individual-chat flag
        ↓
Skip ongoing & group summary notifications
        ↓
Extract: title (sender) + message text
        ↓
Announce sender -> listen for "read it" -> optionally speak message
```

---

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
└── java/com/example/notificationannouncer/
│   ├── MainActivity.kt                  # Permission UI and status display
│   └── NotificationAnnouncerService.kt  # Core listener and TTS logic
└── res/
    ├── layout/activity_main.xml
    └── values/
        ├── strings.xml
        ├── colors.xml
        └── themes.xml
```

---

## Permissions

| Permission | Why |
|------------|-----|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Required to read posted notifications |
| `POST_NOTIFICATIONS` | Shows the voice-mode notification on Android 13+ |
| `RECORD_AUDIO` | Listens for the read-it command |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Keeps user-enabled voice mode available in the background |

No contacts or storage access is required. ML Kit may add network permissions for SDK services; message-language identification uses the bundled on-device model. The system speech and TTS providers may use online processing.

---

## Compatibility Notes

| Issue | Fix |
|-------|-----|
| Play Protect blocks install | Disable Play Protect in Play Store settings |
| "Not Active" after install | Grant Notification Access via Settings or ADB |
| Option not visible on Android 16 | Use ADB command above |
| "App does not send notifications" | You're in the wrong screen — use Special app access path |

---

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **TTS:** Android `TextToSpeech` API
- **Notifications:** `NotificationListenerService`
- **UI:** Material Components 1.11
- **CI/CD:** GitHub Actions

---

## License

MIT License — feel free to use and modify.

## Voice command: read it

The sender announcement is: "Mister Naveen, you have a message from <name>. Would you like me to read it?"
To hear the message body, open the app, tap **Enable voice commands**, and grant
microphone permission. Allow notifications to see the persistent voice-mode
notification and its **Stop voice commands** action.

After the announcement and spoken question finish, the recognizer opens a microphone window for
up to **two seconds**, measured from when the recognizer reports it is ready.
Say **read it** during that window. Only that complete final command triggers
message playback. Silence, unrelated speech, denied microphone access or a
recognition error skips playback and advances to the next notification.
The recognizer may take a little longer to return its result after recording stops.
New notifications wait until the current announce/listen/read interaction finishes.
Message text is held only in a bounded in-memory queue; MessagingStyle notifications
use the latest message when available.

Voice mode must be enabled while the app is visible, because Android restricts
starting microphone foreground services from the background. Once enabled, it can
handle notification command windows with the app in the background. It does not
record continuously. Disable it in the app or use the notification stop action.
After a reboot, force-stop or service termination, reopen the app: previously enabled voice mode resumes. An explicit Disable or Stop stays disabled.

The system speech recognizer chooses its normal engine; offline-only mode and a
fixed English (India) model are no longer forced. The device English locale is
used, or English (US) when the device language is not English. The provider may
process audio online. Wait for the short beep indicating microphone readiness.
Use **Test microphone** to try the same two-second window without a notification.
The result or an actionable error remains visible below the voice-mode button.
The microphone still closes after two seconds; the recognizer gets up to eight
more seconds to return its final result without extending recording.

Validation on a phone:
1. Enable notification access and voice commands; grant microphone access.
2. Receive a message, wait for the sender announcement to finish, then say "read it".
3. Repeat with silence and "do not read it": no message body should be spoken.
4. Receive messages from two senders in quick succession: each command should
   read only the message belonging to its preceding announcement.
5. Repeat with the app backgrounded and the screen locked; try disabling voice
   mode and denying microphone access. The app should remain usable without mic access.

### Individual WhatsApp chats only

Announcements and the read-it microphone window apply only to individual
WhatsApp and WhatsApp Business messages. Group chat messages, notification
summaries, ongoing notifications, SMS and other apps are excluded before queuing.
The filter uses the notification conversation-group flag, not the notification
bundle/group key, because individual chats can also be bundled by Android.
If WhatsApp omits the conversation-type metadata, the notification is skipped
rather than risking a group announcement.

## Automatic message language

After you say **read it**, the app detects the message language on the device
using bundled ML Kit language identification, then asks the installed TTS engine
to speak the original message in that language. No translation is performed.
The sender greeting, spoken question and **read it** command remain English.

Install the required language voices in your device text-to-speech settings.
If detection is uncertain, fails or takes more than three seconds, playback
uses the device language, then English if needed. If the detected voice is
unavailable, the same fallback applies; the app shows the selected language
and missing-voice guidance below the information text. Detection does not
send the message text to a language-detection server. Speech synthesis may
use a network voice depending on your TTS engine settings.

A single language is selected for each message. Very short messages, mixed
languages and languages typed using English letters can be misidentified.
Native-script text gives better results; this does not add a custom voice.

Phone validation: receive an individual WhatsApp message written in Kannada,
Hindi and English in turn, say **read it**, and check both pronunciation and
the displayed language. Check a short ambiguous message and a language without
an installed voice. Queue two different-language messages and confirm each
greeting returns to English. Disconnect notification access during detection
and confirm no stale message plays.
