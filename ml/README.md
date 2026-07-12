# Plate usage-glyph classifier (40-class on-device CNN)

ML Kit reads the plate digits well but systematically misreads the middle **usage glyph**
(용도기호), especially the ㅓ-column (러·너·서·머·저) whose short tick it drops — reading
`러` as `리`/`로`/a digit even on a crystal-clean binarized image. This is a limitation of
the general OCR model, not of image quality.

This is a dedicated classifier for the 40 usage characters. Intended architecture:
ML Kit does plate detection + digit reading; this CNN classifies the middle glyph slot.

## Files
- `gen_glyphs.py` — synthetic data generator (10 Korean fonts + heavy augmentation:
  perspective, rotation, translation/scale jitter, low-res/moiré, blur, brightness, noise).
  `python gen_glyphs.py` writes `glyph_preview.png`.
- `train.py` — trains a small CNN (48×48×1 → 40 classes, ~45k-param), exports `glyph_cnn.tflite`.
- `eval_synth.py` — fresh synthetic accuracy + per-class recall on the tricky ㅓ-column.
- `glyph_cnn.tflite` (44 KB) + `labels.npy` — the trained model and its class order.

## Preprocessing contract (must match on-device)
Grayscale crop → resize 48×48 → **per-image standardization** `(x - mean) / (std + 1e-6)`.
Labels order = `labels.npy` = the VALID_MIDDLE set in `KoreanPlateRecognizer`.

## Status (validated)
- Synthetic val accuracy ~99%; tricky ㅓ-column recall 97–100%.
- Real app-pipeline crops of `154러7070`: 4/5 classified `러` (clean + 3/4 moiré frames),
  vs the template matcher's 0/4 confident `러` on the same moiré frames.

## Next steps
1. Android integration: bundle `glyph_cnn.tflite`, run in the numeric-only glyph fallback
   (sweep offsets like the template matcher, take max-confidence class).
2. Close the sim-to-real gap: real plate font, and fine-tune on captured real crops
   (MainActivity already captures hard frames in debug builds).

Requires: `pip install tensorflow opencv-python pillow numpy` (Python 3.11 tested, TF 2.21).
