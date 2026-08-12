# CarCam

**Current state and what to pick up next: [STATUS.md](STATUS.md).**

## What this app is for

**Finding vehicles parked without permission in a large residential space** — an apartment
complex, an office car park. A warden walks or drives the lot with the phone; the app reads
plates from the camera and flags the ones that are **not** on the resident list.

Two consequences shape every design decision here:

- **The miss is the interesting case.** Most scans match a resident and are unremarkable; the
  ones that matter are the plates that fail to match. Anything that costs time only on a miss
  is on the hot path, not an edge case.
- **It is used one-handed while walking, or at a barrier with a driver waiting.** Confirmation
  dialogs, typing, and anything that blocks the next scan get abandoned in real use. Prefer an
  action that happens immediately with a short window to undo it.

The resident list comes from a spreadsheet the manager already keeps, so it is **routinely
thousands of rows**. Every lookup path must stay an indexed query at that size.

## Modes

- **확인 (check)** — default. A scan only answers "is this car registered?".
- **입출차 (parking)** — a scan toggles the car in or out and records the stay, for working out
  how long a visitor has been there.

## Recognition

ML Kit reads the plate; a 40-class on-device CNN reads the **usage glyph** (용도기호), the
single Hangul character between the digits. That character is what OCR gets wrong — it will
confidently return a different valid glyph (버 as 허, 머 as 허), which then looks like a
legitimate reading of a different car.

The pipeline and the reasoning behind each stage are in [ml/README.md](ml/README.md). Two
things worth knowing before changing anything there:

- **Almost every "misclassification" turned out to be a bad crop**, not a bad model. Before
  concluding the model is wrong, dump what it was actually shown
  (`GlyphClassifier.debugCapture` + `DumpCropTest`). Reimplementing the crop to inspect it has
  already sent one investigation down the wrong path.
- **Synthetic validation accuracy does not predict real-plate accuracy.** Every training
  variant scored 97–98% on synthetic data while ranging from 17/28 to 31/31 on real plates.
  Measure on `app/src/androidTest/assets/plates/`, never on synthetic data alone.

## Working agreements

- When reporting recognition results, **attach the failing images** — annotated with the case
  number, ground truth, prediction, and the crop the model was fed. A text table hides whether
  a failure is cropping, detection, or classification, and that distinction decides the fix.
- Report results **before** starting the next experiment.
- Deploy only what measures better. `MiddleVerificationTest` is the gate for glyph changes:
  it must not break a plate ML Kit already reads correctly.

## Data

Room, `plate_db`. `plates` (the resident list) and `visits` (entry/exit records). Migrations
are written out rather than destructive: the resident list is imported from a spreadsheet and
losing it on an app update would be unrecoverable for the user.
