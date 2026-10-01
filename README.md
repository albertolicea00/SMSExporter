# SMS Exporter 2 JSON, CSV, TXT, or XML

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-29-blue)
![Target SDK](https://img.shields.io/badge/targetSdk-34-blue)
![Language](https://img.shields.io/badge/language-Java-ED8B00?logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-green)
![Offline](https://img.shields.io/badge/network-offline--only-success)

Android app to export your SMS conversations; entirely on-device, with full control over which fields to include and how they're labeled.

## ✨ Features

- Browse and search all SMS conversations on the device
- Select one, several, or all chats to export
- Export formats: JSON, CSV, TXT, XML
- Export as one file per chat, or a single combined file
- Pick exactly which message fields to include (id, thread id, number, contact name, body, dates, direction, read/seen/locked status, delivery status, SIM id, etc.)
- Rename every field label before exporting
- Customizable date format (`SimpleDateFormat` pattern)
- Contact name resolution (optional, requires Contacts permission)
- Fully offline — the app requests no internet permission
- English and Spanish localization

## 🔒 Permissions

| Permission | Why |
|---|---|
| `READ_SMS` | Required to read your messages |
| `READ_CONTACTS` | Optional — only used to show contact names instead of raw numbers |

The app never requests internet access. No data leaves the device. 🛡️

## ⚠️ Limitations

- Only SMS is exported — MMS and group conversations are not included.

## 📂 Where exports are saved

Files are written to `Downloads/SMS Export`.

## 📋 Requirements

- Android 10 (API 29) or higher

## 🔧 Building from source

**Prerequisites**
- JDK 17
- Android SDK (`compileSdk 34`), with `sdk.dir` set in `local.properties`

**Debug build**
```
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/sms-exporter-v<versionName>-debug.apk`

**Release build**
```
./gradlew assembleRelease
```
Requires a signing configuration (not included in this repo) to produce an installable release APK.

## 🛠️ Tech stack

- Java, Android Views (no Compose)
- AndroidX AppCompat, Material Components, RecyclerView

