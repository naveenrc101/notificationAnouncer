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
| `POST_NOTIFICATIONS` | Required on Android 13+ |

No internet, contacts, or storage permissions are used.

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
