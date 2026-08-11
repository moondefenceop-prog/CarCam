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

## Status of the CNN experiments
Shipped: `train.py` (system-font synthetic, 40-class). Wired in the **numeric-only** fallback
(MainActivity), benchmark exact 80%. This is the current production model.

Tried and NOT shipped (all underperformed the 80% baseline on real degraded frames):
- **Reject class** (41st non-Hangul class + wide sweep): localizes/rejects well and fixed 214머,
  but the 41-class retrain regressed 러/로 net (`experimentCnnMiddle`: 8/26 vs ML Kit 18/26).
- **Real plate font** (`train_realfont.py`, glyphs from kade93/kor_license_plate_generator, MIT):
  improved 호/조/가/다 but regressed the moiré 러 frames (러→모), benchmark 70% < 80%.
- **Mixed system+real fonts** (`train_mixed.py`, 55% real plate glyphs / 45% system fonts, same
  framing as the shipped model): 63.2% vs the baseline's 68.4% on the same set — still a net
  regression. Real-font shapes help clean glyphs but hurt the moiré-degraded frames.
- **Real font + explicit moiré/JPEG degradation augmentation** (to model screen-photo interference):
  made it *worse* — on `experimentCnnMiddle`, CNN-picks-middle fell to 9/31 vs ML Kit 21/31. Heavy
  synthetic degradation destroys the fine features the model needs to discriminate the glyph.

**Global "always use the CNN for the middle" is not viable with current data/methods.** Diagnosis
of the failures: 호 fails on *localization* (ML Kit drops the leading digit → crop lands on a
digit), but 머/바/조/너 fail on *classification* — the crop is fine but the model can't read the
real plate-font glyph. No training variant (system font, real font, reject class, moiré aug) got
the CNN's real-plate middle accuracy above ML Kit's (~50% vs ~70%). The CNN stays in the narrow
numeric-only fallback (where any recovery beats bare digits), not global correction. Beating this
needs a large *real* labeled degraded dataset (plates photographed individually in real conditions),
which synthetic degradation does not substitute for.

Key finding: the bottleneck is **degradation** (moiré / blur / small, low-res crops), not glyph
shape. Clean plate-font renders + augmentation don't reproduce real screen-photo moiré, so global
middle-correction and full 40-glyph reliability need a **real degraded labeled dataset at scale**
(many plates, many conditions) — not just the font. `harvestGlyphs` (androidTest) collects such
crops from the app pipeline; combine those with `train_realfont.py` glyphs once enough are gathered.

## Offline real-plate harness + the slot-crop framing fix (2026-08)
`eval_real.py` evaluates any .tflite candidate on the labeled androidTest plate images
**without a device**: it finds the plate text line as a horizontal chain of similar-height
dark components (digit-dominated aspect, bright background — approximating ML Kit's line box),
applies the app's exact slot geometry + sweep, and reports per-image prediction/confidence
plus a "valid-localization" accuracy that excludes images where the harness's own plate
detection is known-bad (background-sign locks, a screenshot, a two-line plate).

Findings from it:
- **Confidence gating is useless**: wrong predictions come at softmax 1.000 as often as right
  ones (batch of 배/호 errors at conf ≥0.99). A "trust CNN only when confident" override has
  no threshold that helps.
- **Framing mismatch found**: the app feeds the CNN slot crops (half-width 0.5–0.62 pitch,
  ±0.35-pitch jitter) that contain edges of the neighboring digits; every prior trainer
  rendered one centered glyph on a blank canvas. `train_slotcrop.py` composes a full
  [digits][GLYPH][digits] line (real plate font + system fonts + real digit images), augments
  the line, then crops with the app's exact distribution. Valid-localization accuracy:
  **20/27 vs the shipped model's 18/27** — the first variant to match/beat ML Kit's middle
  accuracy on this set, and it holds all the moiré 러 frames. A 1500/class rerun scored 19/27
  and regressed 러→보, so the 900/class weights are kept (seed variance is real at this scale;
  candidate saved as `glyph_cnn_slotcrop.tflite`, not committed). Not yet deployed: needs the
  on-device benchmark (phone) to confirm no end-to-end regression before replacing
  `app/src/main/assets/glyph_cnn.tflite`.
- Probability-sum voting across the sweep (instead of keeping the max-confidence crop) helps
  the shipped model (18→20) but not the slot-crop model (20→19); cross-model ensembling adds
  nothing. Not worth app changes on its own.

## Files (extra)
- `train_realfont.py` — trains on real plate-font glyph images + real digits (reject class).
  Requires `KOR_PLATE_REPO` env var or a `kor_plate/` clone of kade93/kor_license_plate_generator.
- `train_slotcrop.py` — trains on simulated slot crops (see above). `N_PER_CLASS` env var
  scales data (default 1500/class). Requires the `kor_plate/` clone.
- `eval_real.py [model.tflite]` — offline real-plate eval harness (see above); defaults to
  the shipped app asset. Writes localization visualizations to `chaindump/`.
- `make_samples.py` — renders 50 diverse plates (all 39 usage glyphs, 2/3-digit formats) in the
  real plate font as PNGs + a contact sheet + `gallery.html` (one plate per screen), for manual
  recognition testing. Photograph plates **individually** (fill the frame), not the contact sheet.

Requires: `pip install tensorflow opencv-python pillow numpy` (Python 3.11 tested, TF 2.21).
