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

## The crop is the bottleneck, not the classifier (2026-08)
Inspecting the exact crops handed to the CNN (contact sheet of every test image) showed most
"misclassifications" were the model being shown the wrong picture: half a glyph, or a digit.
Two causes, both fixed in `glyph_crop.py`:

1. **Confidence-scored sweeps select broken crops.** Cropping 나 down to its ㅏ scores *higher*
   than the whole glyph, so keeping the most confident window over a sweep actively prefers
   the clipped one. There is now one principled crop, not a sweep.
2. **Neither ink connectivity nor equal-width slots segment Hangul.** A glyph is several
   disconnected parts (나 = ㄴ + ㅏ, 머 = ㅁ + ㅓ) so grouping ink splits it, while blur and
   downscaling bridge neighbouring characters into one blob so grouping ink *merges* them
   (measured: the crop centre landing 0.66 pitch off). `snap_to_glyph` instead segments on the
   **valleys** of the ink profile about a half-pitch out on each side, which survives both.

3. **The vertical margin was dragging the plate frame into the crop.** The slot was cropped
   8% taller than the text line on each side, and that is exactly where the plate's border
   sits. The model reads a full-width bar as a stroke: 나 + bar above = 다, 바 + bar below =
   보 (confirmed from row ink profiles — the bottom three rows of the 바 crop were 100% ink).
   Dropping the margin to zero is the single biggest win in this whole effort, **21/28 →
   25/28**, fixing the tilted 호, 178호, 214머 and 392바 in one change.

Result on the scored real-plate set (28 images, excluding harness-localization artefacts and
one frame the user judged unreadable):

| configuration | middle accuracy |
| --- | --- |
| shipped model, equal-width slot + confidence sweep | 18/28 |
| shipped model + valley snap | 16/28 |
| slot-crop model + valley snap (8% band margin) | 21/28 |
| slot-crop model + valley snap + row tightening | 20/28 |
| model retrained through the snap crop policy | 19/28 |
| model retrained through snap + row tightening | 17/28 |
| **slot-crop model + valley snap, zero band margin** | **25/28** |
| …with row tightening | 23/28 |
| …with test-time augmentation (shift/scale vote) | 24/28 |
| shipped model, zero band margin | 21/28 |
| snap-crop model, zero band margin | 22/28 |

Note the pattern: **retraining to match a crop policy kept losing to the slot-crop model.**
Val accuracy was 97–98% in every case, so synthetic validation says nothing about which one
wins on real plates. More synthetic retraining is not the lever — every remaining gain came
from what the model is *shown*. `snap_rows` (aspect normalisation for tilted plates) and
test-time augmentation are both implemented but **off by default**, each having measured worse.

The three remaining scored failures are not classifier bugs:
- `177호1336`, `177호1336_2` — the harness locks onto the wrong plate region (the app uses
  ML Kit's box here, so these do not represent app behaviour).
- `12나3456` — a genuinely worn, low-resolution truck plate. Verified that the crop is clean
  and complete, that the plate-font assets are labelled correctly (`sk`=나, `ek`=다), and that
  the model scores 37/40 on synthetic 나 without ever confusing it for 다. It is a domain gap
  on that image, not a systematic weakness.
- The two `nobox` images (`30고5445`, `63다6576`) are old-style plates the harness's line
  finder does not detect at all.

Deployment still needs the on-device benchmark: the app's slot geometry (`GlyphClassifier.kt`)
uses the equal-width formula plus the confidence sweep that this work showed to be actively
harmful, so porting `snap_to_glyph` into the app is the change to make next.

## Binarisation, background trimming, and fixing plate detection (28/28)
Continuing from the crop work above, three more findings took the scored set from 25/28 to
**28/28** — every remaining error turned out to be preprocessing or plate detection, not the
classifier.

**Otsu binarisation, trained to match.** Feeding a binarised crop to a grey-trained model costs
points, but training through the same binarisation does not: `BINARIZE=otsu` on
`train_slotcrop.py` produced a model that reads the worn `12나3456` correctly (grey read it as
다 at 0.97, on a crop verified clean). Adaptive-mean and illumination-flattened variants both
scored lower. A model trained on a *mix* of grey and Otsu was much worse (20/28) — halving the
data per representation beat the benefit of covering both. Ensembling a grey model with an Otsu
model also failed (24/28): in each disputed image the wrong model was the more confident one.

**Trim the background before binarising.** The text-line box is axis-aligned, so a tilted plate
leaves wedges of dark scene inside the crop. Otsu is a single global threshold, so those wedges
dominate it and the glyph nearly vanishes — that is what turned the tilted 호 into 나.
`trim_dark_background` drops rows far darker than the plate face, and both the grey and Otsu
models then read that plate correctly (0.99 / 0.97).

**Three plate-detection bugs**, each found by looking at what the detector had actually locked
onto rather than at the scores:
- The component height cap assumed the plate is small in frame. A close-up plate has digits at
  60% of image height, so at `0.5` *every digit was discarded* and the KOR badge became "the
  plate". Raised to `0.85`.
- Hairline strips down an image edge (2–6 px wide) counted as characters and pushed the column
  count past its cap, disqualifying a good plate. Rejected below `cw < ch * 0.10`.
- The band re-collection step used a looser height tolerance (`0.55`) than the rule that built
  the chain (`0.65`), so it re-admitted the half-height KOR badge as a character and shifted
  every slot one place left — the crop landed between the third digit and the hangul. Tolerances
  now match.

Scored set: **28/28**. All 34 images: 29/34. The five remaining failures are all plate
*detection*: three lock onto background signage, one is a two-line plate whose region row is
picked, and two are old-style plates the line finder does not detect. Note the harness does this
localisation itself; in the app ML Kit does, so these do not predict app behaviour.

Best configuration: `glyph_cnn_otsu_candidate.tflite` with valley snap, zero band margin,
background trim, Otsu binarisation.

## Old-style green two-line plates (`old_plate.py`)
The two plates the line finder could not see at all are the pre-2004 green ones. The repo
already merged their two OCR lines (`PlateOcrEngine.stackedLinePairs`, `OLD_PATTERN`), but
nothing downstream could produce a glyph crop from them. Three properties break the modern
path, and only the first is obvious:

1. **Inverted polarity** — white characters on green. The line finder tests for dark ink on a
   bright field, the ink profiles assume it, and the CNN has only ever seen dark glyphs. The
   green region is inverted once, up front, after which everything behaves normally.
2. **Two lines**, so the glyph is not inside a single run of 7-8 characters.
3. **Two layouts**, placing the glyph differently:
   `대구30 / 고5445` (region + 2 digits, then GLYPH + 4 digits) → glyph is the bottom row's first;
   `63다 / 6576` (2 digits + GLYPH, then 4 digits) → glyph is the top row's last.
   They are separated by **counting characters per row**, never by reading them.

Details that mattered: the plate is ~100x47 px in frame, so character gaps are 2-3 px and any
merge tolerance wide enough to join a Hangul's parts also joins whole characters — the crop is
upscaled to 240 px tall before segmenting. The glyph is framed from **its own box** rather than
the slot pitch (the upper line's characters are much smaller, so a pitch-sized window leaves the
glyph a speck), padding with background rather than growing the window, which otherwise drags in
the plate border and the row below (that added a stroke under 다 and made it 두). The border is
painted out of the image, not just out of the profiling mask, or its dark L turns 고 into 모.

Result: both plates now localise correctly and one reads correctly (63다6576 → 다). `30고5445`
reads 모: in the old typeface the ㄱ's descending stroke nearly meets the ㅗ's bar, closing into
a ㅁ. All four model/binarisation combinations agree on 모 at 0.92-0.99, so this is the 1973-2004
letterform, which no training asset covers — not a borderline call. Scored set is now 29/30
(the two old plates joined the scored set, which was 28/28 over 28 images).

## Hole veto: stopping the model asserting strokes that are not there (30/30)
The one old-plate miss (`30고5445` → 모 at 0.93) survived every check: the crop was verified
clean and complete at every preprocessing stage, the plate-font assets are correctly labelled
(`rh`=고, `ah`=모), and all four model/binarisation combinations agreed on 모. The model was
inventing the closed box of a ㅁ that the image plainly does not contain — a 40-way softmax has
no way to say "none of these", so on an unfamiliar letterform it asserts the nearest shape
instead of admitting the mismatch.

A counter is countable, so the assertion can be checked against the image. Of the 40 usage
characters, exactly 16 have an initial consonant that encloses a counter (ㅁ, ㅂ, ㅇ, ㅎ:
마머모무 바버보부배 아어오우 하허호) and 24 are open. When the binarised crop contains no
enclosed region, `hole_count` == 0 and those 16 classes are zeroed before the argmax.

This is a check on the picture rather than a preference between classes, and it is
self-validating: 머, 호, 바 and 아 stay correct throughout, which means the counter detection
finds real holes where they exist. Scored set **30/30** (`HOLE_VETO=1`, default on).

## Known separate problem: ML Kit's line box can omit the usage glyph
On `10버7399_1.png` and `10버7399_2.png` — a tilted plate photographed off a monitor — ML Kit
reports the text as `10허7399` but its bounding box covers only the tail of the plate, leaving
the usage glyph outside the region entirely. Every stage downstream works inside that box, so
no crop, deskew or classifier change can reach the glyph: the character is lost during
*detection*, before classification begins.

This is a different piece of work (recovering the true text line by re-collecting components
beyond the reported box, as `eval_real.py` does) and is deliberately not mixed into the
middle-glyph verification. The two frames stay in `app/src/androidTest/assets/plates/` as a
record of the failure but are excluded from `MiddleVerificationTest` scoring, so they do not
mask changes to the part being measured.

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
