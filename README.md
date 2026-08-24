# Easy Flow

Easy Flow is a local-first Android voice keyboard with a matching install-free web prototype. It has no QWERTY layout: tap once, speak naturally, and the cleaned sentence is inserted into any app.

The Android app uses Moonshine v2 Medium Streaming for private live transcription and Gemma 3 1B int4 through LiteRT-LM for local sentence cleanup. Existing Moonshine Small installations remain a compact ASR fallback; until either ASR model is installed it uses Android speech recognition.

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

Open the app once after installation and install Moonshine. For the optional Gemma cleanup stage, accept the official Gemma license, download the Android LiteRT-LM model, then use **Import**. Both models remain in private app storage and their status is shown separately.

## Project structure

- `web/` — instant, dependency-free web prototype
- `android/` — native Kotlin Android keyboard
- `ARCHITECTURE.md` — speech, correction, confidence, and privacy design
- `.github/workflows/` — cloud APK build and GitHub Pages deployment

## Privacy

The prototype stores no transcript history. Moonshine and Gemma run on the phone, drafts remain in memory, and only the safely finalized text is committed to the focused field.
