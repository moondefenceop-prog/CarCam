# -*- coding: utf-8 -*-
"""Offline eval of a glyph CNN on the labeled real test plates — no device needed.

Locates the plate text line as a horizontal chain of similar-height dark components
(approximating ML Kit's line box), then classifies the middle usage glyph.

Slot location: when the chain yields exactly the expected number of character columns
(leading digits + 1 hangul + 4 digits), the hangul is cropped from its ACTUAL column
rather than from the width-division formula. Column-based location is what saves plates
whose leading digit is faint (low-contrast embossed plates), where an equal-width split
of a box that starts at the wrong character lands the crop on a digit.
"""
import os, glob, re, sys, io
import numpy as np, cv2
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
import tensorflow as tf
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyph_crop import (snap_to_glyph, crop_for_model, binarize, trim_dark_background,
                        hole_count, CLOSED_GLYPHS)
from old_plate import locate_old_glyph, crop_old_glyph

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
SC = os.path.dirname(os.path.abspath(__file__))
PLATES = os.path.join(SC, "..", "app", "src", "androidTest", "assets", "plates")
MODEL = sys.argv[1] if len(sys.argv) > 1 else os.path.join(SC, "..", "app", "src", "main", "assets", "glyph_cnn.tflite")
LABELS = list("가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호배")
S = 48
CENTER_OFFSETS = [-0.35, -0.15, 0.0, 0.15, 0.35]
HALF_WIDTHS = [0.5, 0.62]
DUMP = os.path.join(SC, "chaindump"); os.makedirs(DUMP, exist_ok=True)

itp = tf.lite.Interpreter(model_path=MODEL)
itp.allocate_tensors()
inp = itp.get_input_details()[0]; out = itp.get_output_details()[0]

def run_one(gray):
    g = cv2.resize(gray, (S, S), interpolation=cv2.INTER_AREA).astype(np.float32)
    g = (g - g.mean()) / (g.std() + 1e-6)
    itp.set_tensor(inp["index"], g.reshape(1, S, S, 1))
    itp.invoke()
    p = itp.get_tensor(out["index"])[0].copy()
    if os.environ.get("HOLE_VETO", "1") == "1":
        # Veto characters whose consonant encloses a counter when the image plainly has none.
        # This is a check on the picture, not a preference between classes: without it the
        # model asserts strokes that are not there rather than admit an unfamiliar shape.
        bwc = cv2.threshold(cv2.resize(gray, (S * 2, S * 2), interpolation=cv2.INTER_AREA),
                            0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
        if hole_count(bwc) == 0:
            for j, ch in enumerate(LABELS):
                if ch in CLOSED_GLYPHS:
                    p[j] = 0.0
    i = int(np.argmax(p))
    return LABELS[i], float(p[i])

def merge_columns(chain):
    """Collapse components that overlap horizontally into one character column.
    Embossed/outlined glyphs fragment into several components (e.g. the ring of 6)."""
    cols = []
    for x, y, cw, ch in sorted(chain, key=lambda c: c[0]):
        if cols and x < cols[-1][1] - min(cw, cols[-1][1] - cols[-1][0]) * 0.35:
            cols[-1] = (cols[-1][0], max(cols[-1][1], x + cw))
        else:
            cols.append((x, x + cw))
    return cols

def find_line_box(gray):
    """Return (box, columns) for the best plate-line candidate, else (None, None)."""
    h, w = gray.shape
    bw = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C,
                               cv2.THRESH_BINARY_INV, 31, 15)
    nlab, lab, stats, _ = cv2.connectedComponentsWithStats(bw, 8)
    comps = []
    for i in range(1, nlab):
        x, y, cw, ch, area = stats[i]
        # The height cap only exists to drop scenery-sized blobs. It must not assume the
        # plate is small in frame: a close-up fills it, and at 0.5 every digit of a
        # frame-filling plate was thrown away, leaving the KOR badge to be "the plate".
        if ch < 10 or ch > h * 0.85: continue
        if cw > ch * 1.6 or cw < 2: continue        # glyphs are tallish
        # Hairline strips down an image edge are not glyphs; even a '1' is ~35% as wide as
        # it is tall. Two such strips were padding the column count past its cap and
        # disqualifying a perfectly good plate.
        if cw < ch * 0.10: continue
        # A glyph is strokes, not a filled block. This drops the solid KOR badge, which was
        # being counted as a character and shifting every slot one place to the left.
        if area > 0.85 * cw * ch: continue
        # Density floor only rejects hairline noise. An embossed (unpainted) plate
        # thresholds into thin broken outlines — 0.15 threw those glyphs away.
        if area < 0.05 * cw * ch: continue
        comps.append((x, y, cw, ch))
    best = None
    for a in comps:
        ax, ay, aw, ah = a
        chain = [a]
        for b in comps:
            if b is a: continue
            bx, by, bw2, bh = b
            if abs(bh - ah) > ah * 0.35: continue                     # similar height
            if abs((by + bh / 2) - (ay + ah / 2)) > ah * 0.4: continue  # same row
            if abs(bx - ax) > ah * 9: continue                        # near horizontally
            chain.append(b)
        cols = merge_columns(chain)
        if not (5 <= len(cols) <= 9): continue                        # plate line: 7-8 chars
        xs0 = min(c[0] for c in chain); xs1 = max(c[0] + c[2] for c in chain)
        ys0 = min(c[1] for c in chain); ys1 = max(c[1] + c[3] for c in chain)
        span = xs1 - xs0
        if not (3.0 <= span / ah <= 9.0): continue                    # plate line aspect
        cxs = [(c[0] + c[1]) / 2 for c in cols]
        gaps = np.diff(cxs)
        if len(gaps) and gaps.mean() > 0 and gaps.std() / gaps.mean() > 0.75: continue
        # plate is dark-on-bright: bbox background must be bright vs glyph pixels
        roi = gray[ys0:ys1, xs0:xs1]
        broi = bw[ys0:ys1, xs0:xs1] > 0
        if roi.size == 0 or broi.mean() > 0.6: continue
        bgm = roi[~broi].mean() if (~broi).any() else 0
        fgm = roi[broi].mean() if broi.any() else 255
        if bgm < 110 or bgm - fgm < 40: continue                      # not a bright plate
        med_ar = float(np.median([c[2] / c[3] for c in chain]))
        if med_ar > 0.8: continue                                     # digit-dominated
        count_fit = 1.0 - min(abs(len(cols) - 7.5), 4) / 4.0
        score = ah * (1 + count_fit) * (1 + (bgm - fgm) / 255.0)
        if best is None or score > best[0]:
            best = (score, (xs0, ys0, xs1, ys1), chain)
    if best is None: return (None, None)
    # Re-collect every component sitting in the winning chain's row band. Seeded chaining
    # is order-dependent and routinely drops a glyph (the 호 of 177호1336_3), which shifts
    # the column indices and sends the hangul crop onto a digit.
    chain = best[2]
    mh = float(np.median([c[3] for c in chain]))
    cy = float(np.median([c[1] + c[3] / 2 for c in chain]))
    L0 = min(c[0] for c in chain); R0 = max(c[0] + c[2] for c in chain)
    band = [c for c in comps
            if abs((c[1] + c[3] / 2) - cy) < mh * 0.5
            # Match the chain's own height tolerance. At 0.55 this window was looser than the
            # rule that built the chain, so it re-admitted the KOR badge (half the height of
            # a digit) as a character and pushed every slot one place left.
            and 0.65 * mh < c[3] < 1.35 * mh
            and L0 - mh * 1.5 < c[0] and c[0] + c[2] < R0 + mh * 1.5]
    if len(band) >= len(chain): chain = band
    xs0 = min(c[0] for c in chain); xs1 = max(c[0] + c[2] for c in chain)
    ys0 = min(c[1] for c in chain); ys1 = max(c[1] + c[3] for c in chain)
    return ((xs0, ys0, xs1, ys1), merge_columns(chain))

def parse_label(name):
    m = re.match(r"(\d{2,3})([가-힣])(\d{4})", name)
    return m.groups() if m else None

rows = []
for f in sorted(glob.glob(os.path.join(PLATES, "*.png"))):
    name = os.path.splitext(os.path.basename(f))[0]
    p = parse_label(name)
    if not p: continue
    lead, mid, tail = p
    # Decode grayscale for the modern path (cvtColor from BGR rounds differently and moves
    # borderline cases); colour is only needed to spot a green old-style plate.
    raw = np.fromfile(f, np.uint8)
    img = cv2.imdecode(raw, cv2.IMREAD_GRAYSCALE)
    if img is None: continue
    box, cols = find_line_box(img)
    vis = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
    if box is None:
        # Old-style green plates are white-on-green and two-line; the modern path cannot see
        # them at all, since it tests for dark ink on a bright field.
        old = locate_old_glyph(cv2.imdecode(raw, cv2.IMREAD_COLOR))
        if old is not None:
            inv, (cl, cr), (t, b), pitch = old
            sub = crop_old_glyph(inv, cl, cr, t, b)
            if sub is not None and sub.size:
                ch, cf = run_one(binarize(sub, os.environ.get("BINARIZE", "gray")))
                rows.append((name, mid, ch, cf, "old"))
                continue
    if box is None:
        rows.append((name, mid, "?", 0.0, "nobox"))
    else:
        L, T, R, B = box
        total = len(lead) + 5
        pitch = (R - L) / total
        base_cx = L + (len(lead) + 0.5) * pitch
        # No vertical margin: the plate frame sits just outside the text line, and a widened
        # band pulls that full-width bar into the crop, where the model reads it as a stroke
        # (나 + bar above = 다, 바 + bar below = 보). Measured 21/28 at 0.08 vs 25/28 at 0.
        BM = float(os.environ.get("BAND_MARGIN", "0.0"))
        top = max(0, int(T - (B - T) * BM)); bot = min(img.shape[0], int(B + (B - T) * BM))
        snap = snap_to_glyph(img, top, bot, base_cx, pitch)
        if snap is not None:
            # Snap fixes the CENTRE only. The crop keeps the trained slot framing (a bit
            # wider than the glyph, so neighbouring strokes graze the edges) — feeding a
            # tight glyph-hugging box instead collapses confidence, because the model has
            # never seen that framing. Deliberately not a confidence-scored sweep: a crop
            # that clips a glyph in half (나 → ㅏ) outscores the whole glyph, so picking the
            # most confident window actively selects the broken one.
            gcx = (snap[0] + snap[1]) / 2
            got = crop_for_model(img, top, bot, gcx, pitch,
                                 float(os.environ.get("HALF_W", "0.62")),
                                 os.environ.get("TIGHTEN_ROWS", "0") == "1")
            if got is None:
                rows.append((name, mid, "?", 0.0, "nocrop")); continue
            sub, l, r = got
            if os.environ.get("TRIM_BG", "1") == "1":
                sub = trim_dark_background(sub)
            ch, cf = run_one(binarize(sub, os.environ.get("BINARIZE", "gray")))
            best = (ch, cf, l, r); mode = "snap"
        else:
            best = ("?", 0.0, 0, 0); mode = "fit"
            for dx in CENTER_OFFSETS:
                cx = base_cx + dx * pitch
                for hw in HALF_WIDTHS:
                    half = pitch * hw
                    l = max(0, int(cx - half)); r = min(img.shape[1], int(cx + half))
                    if r - l < 4 or bot - top < 4: continue
                    ch, cf = run_one(img[top:bot, l:r])
                    if cf > best[1]: best = (ch, cf, l, r)
        rows.append((name, mid, best[0], best[1], mode))
        cv2.rectangle(vis, (L, T), (R, B), (0, 0, 255), 2)
        for cl, cr in cols:
            cv2.rectangle(vis, (cl, T), (cr, B), (255, 160, 0), 1)
        cv2.rectangle(vis, (best[2], top), (best[3], bot), (0, 255, 0), 2)
    small = cv2.resize(vis, (480, int(480 * vis.shape[0] / vis.shape[1])))
    cv2.imencode(".png", small)[1].tofile(os.path.join(DUMP, name + ".png"))

# Images the user reviewed and classified as genuinely unreadable by eye.
UNREADABLE = {"154러7070_7"}
# Images where THIS harness locks onto background signage instead of the plate
# (the app uses ML Kit's box, so these are harness artifacts, not classifier errors).
EXCLUDE = {"56너9876", "325바8419", "12가3456_2", "154러7070_6", "80아7890"}
correct = 0; n = 0; vc = 0; vn = 0
for name, gt, pr, cf, st in rows:
    ok = "O" if pr == gt else "x"
    tag = " EXCL" if name in EXCLUDE else (" UNREADABLE" if name in UNREADABLE else "")
    if st != "nobox":
        n += 1; correct += (pr == gt)
        if name not in EXCLUDE and name not in UNREADABLE:
            vn += 1; vc += (pr == gt)
    print(f"{name:22s} {gt} -> {pr} {cf:.3f} {ok} {st if st=='nobox' else '['+st+']'}{tag}")
print(f"\nmiddle accuracy (all): {correct}/{n} (nobox {len(rows)-n})")
print(f"middle accuracy (scored set): {vc}/{vn}")
