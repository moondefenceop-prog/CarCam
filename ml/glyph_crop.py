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
