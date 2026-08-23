# Easy Flow

Easy Flow is a voice-first Android keyboard with a matching install-free web prototype. It has no QWERTY layout: speak, review the cleaned sentence, then insert it into any app.

## Try the web prototype

After GitHub Pages finishes deploying, open:

`https://shibam-apple.github.io/Easy-flow-keyboard-/`

Chrome on Android supports live browser speech recognition. Other browsers automatically fall back to a scripted demo so every control can still be tested.

## Install the Android APK

1. Open **Actions** in this repository.
2. Open the latest successful **Build Android APK** run.
3. Download the `easy-flow-debug-apk` artifact and unzip it.
4. Install `app-debug.apk` on the Android phone (allow installation from the browser/files app if Android asks).
5. Open **Easy Flow**, tap **Enable Easy Flow**, enable it in Android's keyboard settings, then tap **Choose Easy Flow**.

The APK uses Android's on-device/system `SpeechRecognizer`. Speech availability and whether audio leaves the device depend on the speech service installed on the phone.

## Project structure

- `web/` — instant, dependency-free web prototype
- `android/` — native Kotlin Android keyboard
- `.github/workflows/` — cloud APK build and GitHub Pages deployment

## Privacy

The prototype stores no transcript history. The Android keyboard only keeps the current draft in memory and sends it to the focused text field when **Insert** is pressed.
