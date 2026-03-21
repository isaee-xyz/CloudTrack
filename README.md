# ☁️ CloudTrack — Intelligent Call Tracker & Recorder

[![Android](https://img.shields.io/badge/Platform-Android%2010+-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-orange.svg)](https://firebase.google.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

CloudTrack is a real-time intelligent **call tracking and recording app** for Android, designed to function as a lightweight CRM for sales teams. It silently logs all incoming and outgoing SIM calls in the background, records audio (from the caller side), and syncs all data securely to Firebase Cloud in real time.

> **Inspired by SalesTrail** — Built for teams that need call intelligence without complex enterprise tools.

---

## ✨ Features

| Feature | Status |
|---|---|
| 📞 Automatic PSTN Call Detection | ✅ Complete |
| 🎙️ Background Audio Recording | ✅ Complete |
| 🔥 Firebase Cloud Sync (Firestore + Storage) | ✅ Complete |
| 📱 In-App Call Vault History UI | ✅ Complete |
| ▶️ Native Audio Playback with SeekBar | ✅ Complete |
| 🔍 Smart Filters (Incoming / Outgoing / Has Audio) | ✅ Complete |
| 🐛 Live In-App Debug Console | ✅ Complete |
| 🛡️ Accessibility Elevation for Mic Priority | ✅ Complete |

---

## 🏗️ Architecture

```
CloudTrack App
│
├── 📡 Tracking Layer (Background Detection)
│   ├── CallStateReceiver.kt         → BroadcastReceiver for Android PHONE_STATE
│   ├── AudioRecordingService.kt     → Foreground service with MediaRecorder
│   └── CallLogObserver.kt           → ContentObserver watching the system Call Log
│
├── 🗄️ Database Layer (Local Cache — Room SQLite)
│   ├── AppDatabase.kt               → Room Database singleton
│   ├── CallDataEntity.kt            → Data model for a single call log
│   └── CallDataDao.kt               → All queries (insert, update, getAllCalls, etc.)
│
├── ☁️ Sync Layer (Background Upload)
│   ├── UploadWorker.kt              → WorkManager background task
│   └── FirebaseManager.kt           → Uploads audio to Storage, metadata to Firestore
│
├── 📺 UI Layer
│   ├── MainActivity.kt              → Permissions onboarding + Live Debug Console
│   └── history/
│       ├── CallHistoryActivity.kt   → The "Call Vault" — lists all past calls
│       └── CallHistoryAdapter.kt    → RecyclerView Adapter + MediaPlayer logic
│
└── 🔧 Utilities
    ├── Logger.kt                    → Centralized logging to SharedPreferences + LogCat
    └── CloudTrackAccessibilityService.kt → Elevates background mic priority silently
```

---

## 🔥 Firebase Stack

| Service | Purpose |
|---|---|
| **Cloud Firestore** | Stores structured call metadata (name, number, duration, timestamp) |
| **Cloud Storage** | Stores raw `.mp4` audio recording files |

### Firestore Data Model (`call_logs` collection)

```json
{
  "contactName": "John Doe",
  "phoneNumber": "+91-9876543210",
  "callType": "INCOMING",
  "startTime": 1711045200000,
  "durationSeconds": 142,
  "audioStorageUrl": "gs://cloudtrack.appspot.com/recordings/call_123.mp4",
  "syncedAt": "2026-03-22T00:00:00Z"
}
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android Device / Emulator (API 29 / Android 10+)
- Firebase Project with Firestore + Storage enabled

### Setup

**1. Clone the repository:**
```bash
git clone https://github.com/isaee-xyz/CloudTrack.git
cd CloudTrack
```

**2. Add your `google-services.json`:**
- Download it from your [Firebase Console](https://console.firebase.google.com)
- Place it in `/app/google-services.json`

**3. Firebase Security Rules (Development):**

*Firestore:*
```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

*Cloud Storage:*
```text
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read, write: if true;
    }
  }
}
```

**4. Build and Run from Android Studio.**

---

## 📱 Required Permissions

The app will request the following upon first launch:

| Permission | Purpose |
|---|---|
| `READ_PHONE_STATE` | Detect when a call starts/ends |
| `READ_CALL_LOG` | Fetch metadata (duration, contact) after the call |
| `READ_CONTACTS` | Resolve the caller's name from the address book |
| `RECORD_AUDIO` | Record the caller's side of the conversation |
| `POST_NOTIFICATIONS` | Show a silent foreground service notification |
| `MODIFY_AUDIO_SETTINGS` | Manage audio priority to ensure mic access |

Additionally, you must manually enable **Notification Access** and **Accessibility Elevation** via the buttons on the home screen.

---

## 🏛️ Technical Decisions

### Why Accessibility Elevation?
Android 10+ enforces strict Microphone Concurrency rules. When the system dialer holds the microphone, background apps receive a zeroed-out silent audio buffer. By registering as a lightweight Accessibility Service, CloudTrack is elevated to a "privileged foreground" status by the Android kernel, granting concurrent microphone access so the caller's voice can be recorded.

### Why WorkManager for File Sync?
`WorkManager` guarantees the background upload job runs even if the user kills the app immediately after a call. It also handles retries automatically if network connectivity fails mid-upload.

### Why Room Database?
All call data is first persisted locally in SQLite (Room) before being synced to Firebase. This ensures no call data is ever lost if the network is unavailable during the call.

---

## 📂 Project Structure

```
app/
└── src/main/
    ├── java/com/learn/cloudtrack/
    │   ├── db/                     # Room database (Entity, DAO, AppDatabase)
    │   ├── history/                # Call Vault UI + MediaPlayer Adapter
    │   ├── sync/                   # WorkManager + Firebase upload
    │   ├── tracking/               # BroadcastReceiver, AudioService, AccessibilityService
    │   ├── utils/                  # Logger utility
    │   └── MainActivity.kt
    └── res/
        ├── layout/
        │   ├── activity_main.xml
        │   ├── activity_call_history.xml
        │   └── item_call_log.xml
        └── xml/
            └── accessibility_service_config.xml
```

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---

*Built with ❤️ by [Isaee](https://github.com/isaee-xyz)*
