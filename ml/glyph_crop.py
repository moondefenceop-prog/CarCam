# -*- coding: utf-8 -*-
"""Shared glyph-crop policy: locate the usage glyph's real extent inside a plate text line.

Training and inference MUST crop the same way. Every earlier model failed partly because
they disagreed: trainers rendered a lone centred glyph while the app fed a fixed-width slot
that clipped 나 into ㅏ. This module is the single definition of the crop, imported by both
`eval_real.py` (inference) and `train_snapcrop.py` (data generation).
"""
import numpy as np, cv2


def ink_columns(band, thresh_frac=0.06):
    """Boolean per-column ink mask for a text-line band (dark glyphs on a bright plate)."""
    bw = cv2.adaptiveThreshold(band, 255, cv2.ADAPTIVE_THRESH_MEAN_C,
                               cv2.THRESH_BINARY_INV, 31, 15) > 0
    return bw.sum(axis=0) >= max(1, int(band.shape[0] * thresh_frac))


def snap_to_glyph(gray, top, bot, cx, pitch):
    """Return (left, right) of the character cell around slot centre [cx], or None.

    Segments by the VALLEYS between characters rather than by ink connectivity. Two effects
    defeat connectivity: a Hangul glyph is several disconnected parts (나 = ㄴ + ㅏ), and
    blur or downscaling bridges neighbouring characters into one blob — so grouping ink
    either splits one glyph or swallows two. A valley survives both: the profile minimum
    roughly one half-pitch out on each side is the character boundary, whatever happened to
    the strokes in between.
    """
    band = gray[top:bot, :]
    if band.size == 0 or pitch <= 0:
        return None
    x0 = max(0, int(cx - pitch * 1.2)); x1 = min(gray.shape[1], int(cx + pitch * 1.2))
    if x1 - x0 < 6:
        return None
    bw = cv2.adaptiveThreshold(band, 255, cv2.ADAPTIVE_THRESH_MEAN_C,
                               cv2.THRESH_BINARY_INV, 31, 15) > 0
    prof = bw[:, x0:x1].sum(axis=0).astype(np.float32)
    if prof.max() <= 0:
        return None
    k = max(1, int(round(pitch * 0.08)))
    prof = cv2.blur(prof.reshape(1, -1), (k, 1)).ravel()
    ci = min(max(int(round(cx)) - x0, 0), len(prof) - 1)

    def valley(lo, hi):
        lo = max(0, int(lo)); hi = min(len(prof), int(hi))
        if hi - lo < 1:
            return None
        seg = prof[lo:hi]
        return lo + int(np.argmin(seg))

    l = valley(ci - pitch * 0.80, ci - pitch * 0.28)
    r = valley(ci + pitch * 0.28, ci + pitch * 0.80)
    if l is None or r is None:
        return None
    L, R = x0 + l, x0 + r
    if R - L < 3:
        return None
    return L, R


# Usage glyphs whose initial consonant encloses a counter: ㅁ, ㅂ, ㅇ, ㅎ. Everything else in
# the 40-character set is open. A softmax over 40 classes cannot say "none of these", so on an
# unfamiliar letterform it will happily assert a closed shape the image does not contain —
# a clean 고 came back as 모 at 0.93. A hole is countable, so that assertion can be checked.
CLOSED_GLYPHS = set("마머모무바버보부배아어오우하허호")


def hole_count(binary, min_frac=0.012):
    """Number of enclosed counters in a binarised glyph crop (dark strokes on light)."""
    if binary is None or binary.size == 0:
        return 0
    ink = (binary < 128).astype(np.uint8)
    if ink.sum() < 20:
        return 0
    # A one-pixel break in a stroke opens a counter, and blur can pinch one shut; close the
    # mask slightly so the count reflects the shape rather than the sampling.
    k = max(1, int(round(min(binary.shape) * 0.03)))
    ink = cv2.morphologyEx(ink, cv2.MORPH_CLOSE, np.ones((k, k), np.uint8))
    cnts, hier = cv2.findContours(ink, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)
    if hier is None:
        return 0
    area = float(ink.shape[0] * ink.shape[1])
    n = 0
    for i, h in enumerate(hier[0]):
        if h[3] != -1 and cv2.contourArea(cnts[i]) >= area * min_frac:
            n += 1                       # a child contour is a hole in its parent
    return n


def trim_dark_background(g, keep=0.55):
    """Drop rows of dark scene that sit outside the plate, above or below the glyph.

    The text-line box is axis-aligned, so a tilted plate leaves wedges of background inside
    the crop. Those wedges are much darker than the plate face, and a global threshold pools
    them with the strokes — which is what turned the tilted 호 into 나. Trimming them first
    also moves the crop closer to the training composition, whose background is plain white.
    """
    if g.size == 0 or g.shape[0] < 6:
        return g
    plate = float(np.percentile(g, 85))
    if plate <= 1:
        return g
    med = np.median(g, axis=1)
    keep_rows = med >= plate * keep
    idx = np.flatnonzero(keep_rows)
    if len(idx) < max(4, g.shape[0] * 0.35):
        return g                      # not a clear plate/background split; leave it alone
    return g[idx[0]:idx[-1] + 1, :]


def binarize(g, mode):
    """Optional preprocessing of the final crop, applied identically in training and eval.

    Binarising discards dirt, shadow and worn-paint texture and keeps only stroke structure —
    on a weathered plate that is the difference between reading 나 and reading 다. It only
    pays off if the model is TRAINED on the same representation; feeding a binary crop to a
    grey-trained model costs several points.
    """
    if mode in (None, "", "gray"):
        return g
    h, w = g.shape
    blk = max(3, int(min(h, w) * 0.6) | 1)
    if mode == "otsu":
        return cv2.threshold(g, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
    if mode == "flat_otsu":
        # Otsu is a single global threshold, so it breaks down on a tilted, low-contrast
        # embossed plate where the crop's own brightness ramps across it. Dividing out a
        # heavily blurred copy flattens that ramp first, leaving Otsu a clean bimodal crop.
        bg = cv2.GaussianBlur(g, (0, 0), max(2.0, min(h, w) * 0.5))
        flat = cv2.divide(g, bg + 1, scale=192)
        return cv2.threshold(flat, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
    if mode == "adapt_mean":
        return cv2.adaptiveThreshold(g, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, blk, 7)
    if mode == "adapt_gauss":
        return cv2.adaptiveThreshold(g, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, blk, 7)
    if mode == "adapt_soft":
        bw = cv2.adaptiveThreshold(g, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, blk, 7)
        return cv2.addWeighted(g, 0.35, bw, 0.65, 0)
    raise ValueError(f"unknown binarize mode: {mode}")


def snap_rows(gray, top, bot, l, r, frac=0.10):
    """Tighten a slot's vertical extent onto the glyph's own rows.

    The text line's bounding box grows tall when the plate is tilted, so a slot cropped over
    the full band can come out twice as tall as it is wide, and everything is squashed into
    a square for the model.

    Measured, and it does NOT pay off: off by default. Enabling it costs a point with the
    slot-crop model (21/28 -> 20/28), and a model retrained through the tightened policy
    scored 17/28 — the row trim latches onto the plate frame or a neighbour's stroke often
    enough to lose more than the aspect fix gains. Kept for the record and for re-testing
    against a future model.
    """
    sub = gray[top:bot, l:r]
    if sub.size == 0:
        return top, bot
    bw = cv2.adaptiveThreshold(sub, 255, cv2.ADAPTIVE_THRESH_MEAN_C,
                               cv2.THRESH_BINARY_INV, 31, 15) > 0
    rows = bw.sum(axis=1) >= max(1, int((r - l) * frac))
    idx = np.flatnonzero(rows)
    if len(idx) < 3:
        return top, bot
    pad = max(1, int((idx[-1] - idx[0]) * 0.08))
    return (max(top, top + int(idx[0]) - pad), min(bot, top + int(idx[-1]) + 1 + pad))


def crop_for_model(gray, top, bot, gcx, pitch, half_w=0.62, tighten_rows=False):
    """The window handed to the CNN: centred on the glyph, a bit wider, rows trimmed to it.

    Keeping the window wider than the glyph (so neighbouring strokes graze the edges) is
    part of the contract — a tight glyph-hugging box is a different distribution, and the
    model must be trained on whichever one it will be given.
    """
    half = pitch * half_w
    l = max(0, int(gcx - half)); r = min(gray.shape[1], int(gcx + half))
    if r - l < 4 or bot - top < 4:
        return None
    if tighten_rows:
        top, bot = snap_rows(gray, top, bot, l, r)
        if bot - top < 4:
            return None
    return gray[top:bot, l:r], l, r
