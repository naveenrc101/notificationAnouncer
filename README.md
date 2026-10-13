# Notification Announcer

An Android app that listens for incoming notifications from WhatsApp and SMS apps and announces them aloud using the device's built-in Text-to-Speech engine.

---

## Features

- Announces **WhatsApp** and **WhatsApp Business** messages
- Announces **SMS / Direct Messages** from major messaging apps
- Speaks the sender's name followed by the message content
- Skips group summary and ongoing notifications to avoid duplicates
- Simple one-tap permission setup screen
- Works in the background — no need to keep the app open

---

## Supported Apps

| App | Package |
|-----|---------|
| WhatsApp | `com.whatsapp` |
| WhatsApp Business | `com.whatsapp.w4b` |
| Google Messages | `com.google.android.apps.messaging` |
| Samsung Messages | `com.samsung.android.messaging` |
| AOSP Messaging | `com.android.messaging` |
| Stock MMS | `com.android.mms` |

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

### 2. Open in Android Studio

File → Open → select the `notificationAnouncer` folder.

### 3. Build & Run

Connect a physical Android device (emulators do not receive real notifications) and click **Run**.

### 4. Grant Permission

On first launch, tap **Grant Notification Access** and enable the app in the system notification listener settings.

---

## How It Works

```
Notification arrives
        ↓
NotificationListenerService.onNotificationPosted()
        ↓
Filter by package name (WhatsApp / SMS only)
        ↓
Skip ongoing & group summary notifications
        ↓
Extract: title (sender) + message text
        ↓
TextToSpeech.speak("WhatsApp. John. Hey, are you there?")
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

No internet, contacts, or storage permissions are used.

---

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **TTS:** Android `TextToSpeech` API
- **Notifications:** `NotificationListenerService`
- **UI:** Material Components 1.11

---

## License

MIT License — feel free to use and modify.
