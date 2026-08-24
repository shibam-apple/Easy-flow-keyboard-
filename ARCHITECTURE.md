# Easy Flow architecture

Easy Flow is a local-first voice input method. The keyboard UI does not own transcription logic; it consumes a small `SpeechEngine` contract so engines can be benchmarked and replaced independently.

## Runtime pipeline

1. **Capture** — Moonshine's microphone processor records 16 kHz mono audio only while listening.
2. **Speech engine** — Moonshine v2 Medium Streaming is the high-accuracy default; Small remains a compact fallback and Android's preferably on-device recognizer is the zero-setup fallback.
3. **Transcript stabilizer** — retains the common prefix between partial hypotheses to reduce visible word flicker.
4. **Gemma refiner** — Gemma 3 1B int4 runs through LiteRT-LM and conservatively cleans grammar, punctuation, fillers and false starts using only local app/cursor context.
5. **Flow safety gate** — preserves numbers, rejects suspiciously long/short rewrites and falls back to deterministic formatting if Gemma fails or exceeds four seconds.
6. **Input method adapter** — automatically commits the safe final string through `InputConnection`; Backspace and Enter remain available in both keyboard states.

The keyboard starts as a 62 dp Flow Bar. Tapping its nested transcript lens morphs the same controls into a 218 dp review surface using a shared-geometry spring curve. Partial text receives a restrained per-update rise/fade, finalized text becomes fully opaque, and the collapsed lens always exposes the newest words.

## Privacy boundary

- Audio is captured only while the keyboard shows `Listening`.
- Moonshine inference runs in the app process using a model stored in private app files.
- Gemma inference runs in the same app process through LiteRT-LM; text never crosses the network boundary.
- Drafts and audio are not persisted.
- The first model installation is a resumable network download; completion is recorded only after the model loads successfully.
- The fallback asks Android to prefer offline recognition, but behavior depends on the recognition service installed on the phone. The UI always identifies the active engine.

## Wispr Flow benchmark

Product parity is evaluated on experience, not identical implementation:

- low perceived start latency;
- stable partial text;
- filler removal;
- “actually / I mean / sorry” backtracking;
- paragraphs and numbered lists;
- personal terms and names;
- one-action insert and exact undo;
- graceful behavior in poor connectivity.

Moonshine replaces the previous batch-style Whisper integration because it is designed for edge streaming. Medium is selected for maximum local accuracy. The active app and up to 600 characters before the cursor are passed only to the local model so ambiguous words can be resolved without uploading context.

## Two-model refinement layer

The correction interface is separate from ASR. Moonshine produces a faithful raw transcript, then Gemma rewrites only that text with app and cursor context. `FlowTextProcessor` is the final safety gate for numbers and semantic changes. If Gemma is unavailable, fails, or exceeds its latency budget, deterministic cleanup completes normally. Gemma's official files are license-gated, so the companion app links to the official model page and imports the user-approved `.litertlm` file while hashing it into private app storage.
