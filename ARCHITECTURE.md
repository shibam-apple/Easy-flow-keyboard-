# Easy Flow architecture

Easy Flow is a local-first voice input method. The keyboard UI does not own transcription logic; it consumes a small `SpeechEngine` contract so engines can be benchmarked and replaced independently.

## Runtime pipeline

1. **Capture** — Moonshine's microphone processor records 16 kHz mono audio only while listening.
2. **Speech engine** — Moonshine v2 Small Streaming is primary after installation; Android's preferably on-device recognizer is the zero-setup fallback.
3. **Transcript stabilizer** — retains the common prefix between partial hypotheses to reduce visible word flicker.
4. **Flow text processor** — removes fillers, applies spoken backtracks, converts formatting commands, applies the personal dictionary, and restores punctuation.
5. **Confidence gate** — preserves numbers and flags risky edits for review instead of silently changing meaning.
6. **Input method adapter** — commits the final string through `InputConnection`; undo deletes exactly the last inserted string.

## Privacy boundary

- Audio is captured only while the keyboard shows `Listening`.
- Moonshine inference runs in the app process using a model stored in private app files.
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

Moonshine replaces the previous batch-style Whisper integration because it is designed for edge streaming. The Small model is selected instead of Medium to keep the keyboard responsive while preserving a clear future quality upgrade path.

## Next inference layer

The correction interface is deliberately separate from ASR. A future small local language model can replace `FlowTextProcessor` for context-aware rewrites while the current deterministic layer remains the safety gate for numbers, URLs, names, and semantic changes.
