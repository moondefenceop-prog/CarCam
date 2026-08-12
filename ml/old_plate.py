# -*- coding: utf-8 -*-
"""Old-style (green, two-line) Korean plates.

These differ from modern plates in three ways that each break the normal pipeline:

1. **Inverted polarity.** White characters on a green field. Every stage downstream assumes
   dark ink on a bright plate — the line finder's contrast test, the ink profiles, and the
   CNN itself, which only ever saw dark glyphs. The whole region is inverted once, up front,
   after which everything behaves like a modern plate.
2. **Two lines**, so the glyph is not in a single left-to-right run of 7-8 characters.
3. **Two different layouts**, which put the usage glyph in different places:
     대구30 / 고5445   region name + 2 digits, then GLYPH + 4 digits  -> glyph = bottom row, first
     63다   / 6576     2 digits + GLYPH,       then 4 digits          -> glyph = top row, last
   They are told apart by counting characters per row, not by trying to read them.

Green is the cue for "old plate": the modern plates in this era are white or, for EVs,
a much lighter yellow-green with dark characters.
"""
import numpy as np, cv2


def find_green_plate(bgr, min_area_frac=0.002):
    """Bounding box of the largest plate-shaped saturated-green region, or None."""
    if bgr is None or bgr.ndim != 3:
        return None
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)
    # Deep green field. The saturation and value floors keep out foliage in shadow and the
    # pale yellow-green of a modern EV plate, whose characters are dark and which must keep
    # going down the normal path.
    mask = cv2.inRange(hsv, np.array([40, 70, 40]), np.array([90, 255, 220]))
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    n, lab, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
    h, w = mask.shape
    best = None
    for i in range(1, n):
        x, y, cw, ch, area = stats[i]
        if ch < 8 or cw < 16:
            continue
        if area < min_area_frac * h * w:
            continue
        ar = cw / ch
        if not (1.3 <= ar <= 3.2):            # old plates are roughly 2:1
            continue
        if area < 0.45 * cw * ch:             # must be a filled panel, not a leafy blob
            continue
        if best is None or area > best[0]:
            best = (area, (x, y, x + cw, y + ch))
    return best[1] if best else None


def _rows_of(bw, min_frac=0.04):
    """Split a binary crop into horizontal text bands."""
    prof = bw.sum(axis=1).astype(np.float32) / 255.0
    on = prof >= max(1.0, bw.shape[1] * min_frac)
    bands, i = [], 0
    while i < len(on):
        if on[i]:
            j = i
            while j + 1 < len(on) and on[j + 1]:
                j += 1
            if j - i >= 3:
                bands.append((i, j + 1))
            i = j + 1
        else:
            i += 1
    return bands


def _columns_in(bw, top, bot, min_frac=0.06):
    prof = bw[top:bot].sum(axis=0).astype(np.float32) / 255.0
    on = prof >= max(1.0, (bot - top) * min_frac)
    cols, i = [], 0
    while i < len(on):
        if on[i]:
            j = i
            while j + 1 < len(on) and on[j + 1]:
                j += 1
            if j - i >= 2:
                cols.append((i, j + 1))
            i = j + 1
        else:
            i += 1
    return cols


def _merge_close(cols, gap):
    """Join column runs separated by less than an inter-character gap (한글 자모 분리 대응)."""
    if not cols:
        return cols
    out = [list(cols[0])]
    for a, b in cols[1:]:
        if a - out[-1][1] <= gap:
            out[-1][1] = b
        else:
            out.append([a, b])
    return [tuple(c) for c in out]


WORK_H = 240      # plates are often ~45 px tall in frame; segmenting needs room


def _drop_specks(cols):
    """Remove rim slivers left by the plate border, which otherwise inflate the count."""
    if len(cols) < 2:
        return cols
    med = float(np.median([c[1] - c[0] for c in cols]))
    return [c for c in cols if (c[1] - c[0]) >= med * 0.35]


def locate_old_glyph(bgr):
    """Return (plate_inverted, top, bot, centre_x, pitch) for the usage glyph, or None."""
    box = find_green_plate(bgr)
    if box is None:
        return None
    x0, y0, x1, y1 = box
    pad = int((y1 - y0) * 0.06)
    x0 = max(0, x0 - pad); y0 = max(0, y0 - pad)
    x1 = min(bgr.shape[1], x1 + pad); y1 = min(bgr.shape[0], y1 + pad)
    plate = cv2.cvtColor(bgr[y0:y1, x0:x1], cv2.COLOR_BGR2GRAY)
    if plate.size == 0 or plate.shape[0] < 16:
        return None
    # Upscale first. At native size the gaps between characters are 2-3 px, so any merge
    # tolerance wide enough to join a Hangul's parts also joins whole characters, and the
    # count that identifies the layout comes out wrong.
    s = WORK_H / plate.shape[0]
    plate = cv2.resize(plate, (max(8, int(plate.shape[1] * s)), WORK_H), interpolation=cv2.INTER_CUBIC)
    inv = 255 - plate                                    # white-on-green -> dark-on-light
    bw = cv2.threshold(cv2.GaussianBlur(inv, (3, 3), 0), 0, 255,
                       cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)[1]
    rim = max(2, int(min(inv.shape) * 0.07))             # the border survives thresholding
    bw[:rim, :] = 0; bw[-rim:, :] = 0; bw[:, :rim] = 0; bw[:, -rim:] = 0

    bands = [b for b in _rows_of(bw) if b[1] - b[0] >= inv.shape[0] * 0.12]
    if len(bands) < 2:
        return None
    bands.sort(key=lambda b: b[1] - b[0], reverse=True)
    (t0, b0), (t1, b1) = sorted(bands[:2])               # the two tallest bands are the lines

    def cols_for(t, b):
        return _drop_specks(_merge_close(_columns_in(bw, t, b), max(2, int((b - t) * 0.10))))

    top_cols, bot_cols = cols_for(t0, b0), cols_for(t1, b1)
    # Layout by character count, never by trying to read them:
    #   bottom row of 5 = GLYPH + 4 digits   (대구30 / 고5445)
    #   bottom row of 4 = just the digits, so the glyph ends the top row (63다 / 6576)
    if len(bot_cols) == 5:
        t, b, cols, k = t1, b1, bot_cols, 0
    elif len(bot_cols) == 4 and len(top_cols) == 3:
        t, b, cols, k = t0, b0, top_cols, 2
    else:
        return None
    cl, cr = cols[k]
    centers = [(a + c) / 2 for a, c in cols]
    pitch = float(np.median(np.diff(centers))) if len(centers) > 1 else (cr - cl) * 1.2
    # Paint the border out of the image itself, not just out of the profiling mask. The crop
    # is framed on the glyph and can reach past the rim, and the border's dark L then reads
    # as strokes — it turned 고 into 모.
    interior = inv[rim:-rim, rim:-rim]
    bg = int(np.median(interior)) if interior.size else 255
    inv = inv.copy()
    inv[:rim, :] = bg; inv[-rim:, :] = bg; inv[:, :rim] = bg; inv[:, -rim:] = bg
    return inv, (cl, cr), (t, b), pitch


# How much of the model's input a glyph fills in training: the crop is 1.24 slot-pitches wide
# on a glyph about 0.75 of a pitch, and about 1.16 line-heights tall on a glyph about 0.85.
GLYPH_W_FRAC = 0.60
GLYPH_H_FRAC = 0.73


def crop_old_glyph(inv, cl, cr, t, b):
    """Frame the glyph the way training framed it, padding with background at the edges.

    Framing from the glyph's own box rather than the slot pitch is what makes the two lines
    comparable: the upper line's characters are much smaller, so a pitch-sized window there
    leaves the glyph a speck in a wide field. Padding rather than growing the window matters
    too — growing it pulls in the plate border and the row below, which put a stroke under
    다 and turned it into 두.
    """
    gw, gh = cr - cl, b - t
    if gw <= 0 or gh <= 0:
        return None
    cw, chh = gw / GLYPH_W_FRAC, gh / GLYPH_H_FRAC
    cx, cy = (cl + cr) / 2.0, (t + b) / 2.0
    x0, x1 = int(round(cx - cw / 2)), int(round(cx + cw / 2))
    y0, y1 = int(round(cy - chh / 2)), int(round(cy + chh / 2))
    H, W = inv.shape
    px0, py0 = max(0, -x0), max(0, -y0)
    px1, py1 = max(0, x1 - W), max(0, y1 - H)
    sub = inv[max(0, y0):min(H, y1), max(0, x0):min(W, x1)]
    if sub.size == 0:
        return None
    if px0 or px1 or py0 or py1:
        bg = int(np.median(sub))
        sub = cv2.copyMakeBorder(sub, py0, py1, px0, px1, cv2.BORDER_CONSTANT, value=bg)
    return sub
