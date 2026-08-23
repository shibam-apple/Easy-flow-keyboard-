# Easy Flow

Easy Flow is a local-first Android voice keyboard with a matching install-free web prototype. It has no QWERTY layout: speak naturally, review the cleaned sentence, then insert it into any app.

The Android app now supports a downloaded whisper.cpp `base.en` Q5_1 model for private on-device transcription. Until the model is installed it uses Android speech recognition, preferring the on-device service when the phone provides one.

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

Open the app once after installation and select **Download local model**. The verified model is about 60 MB and remains in the app's private storage.

## Project structure

- `web/` — instant, dependency-free web prototype
- `android/` — native Kotlin Android keyboard
- `ARCHITECTURE.md` — speech, correction, confidence, and privacy design
- `.github/workflows/` — cloud APK build and GitHub Pages deployment

## Privacy

The prototype stores no transcript history. Local Whisper audio and drafts remain in memory, and the final text is sent to the focused field only when **Insert** is pressed.
