# -*- coding: utf-8 -*-
"""Offline eval of glyph CNN on labeled real test plates.
Locates the plate text line as a horizontal chain of similar-height dark components
(approximating ML Kit's line box), then applies the app's slot geometry + sweep."""
import os, glob, re, sys, io
import numpy as np, cv2
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
import tensorflow as tf

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
    p = itp.get_tensor(out["index"])[0]
    i = int(np.argmax(p))
    return LABELS[i], float(p[i])

def find_line_box(gray):
    """Find the longest horizontal chain of similar-height dark components (the digit line)."""
    h, w = gray.shape
    bw = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C,
                               cv2.THRESH_BINARY_INV, 31, 15)
    nlab, lab, stats, _ = cv2.connectedComponentsWithStats(bw, 8)
    comps = []
    for i in range(1, nlab):
        x, y, cw, ch, area = stats[i]
        if ch < 10 or ch > h * 0.5: continue
        if cw > ch * 1.6 or cw < 2: continue        # glyphs are tallish
        if area < 0.15 * cw * ch: continue          # too sparse = noise
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
        if not (5 <= len(chain) <= 10): continue                      # plate line: 7-8 comps
        xs0 = min(c[0] for c in chain); xs1 = max(c[0] + c[2] for c in chain)
        ys0 = min(c[1] for c in chain); ys1 = max(c[1] + c[3] for c in chain)
        span = xs1 - xs0
        if not (3.0 <= span / ah <= 9.0): continue                    # plate line aspect
        # spacing uniformity of component centers
        cxs = sorted(c[0] + c[2] / 2 for c in chain)
        gaps = np.diff(cxs)
        if len(gaps) and gaps.mean() > 0 and gaps.std() / gaps.mean() > 0.75: continue
        # plate is dark-on-bright: bbox background must be bright vs glyph pixels
        roi = gray[ys0:ys1, xs0:xs1]
        broi = bw[ys0:ys1, xs0:xs1] > 0
        if roi.size == 0 or broi.mean() > 0.6: continue
        bgm = roi[~broi].mean() if (~broi).any() else 0
        fgm = roi[broi].mean() if broi.any() else 255
        if bgm < 110 or bgm - fgm < 50: continue                      # not a bright plate
        # plates are digit-dominated: components are tallish (median w/h well below 1)
        med_ar = float(np.median([c[2] / c[3] for c in chain]))
        if med_ar > 0.8: continue
        count_fit = 1.0 - min(abs(len(chain) - 7.5), 4) / 4.0
        score = ah * (1 + count_fit) * (1 + (bgm - fgm) / 255.0)
        if best is None or score > best[0]:
            best = (score, (xs0, ys0, xs1, ys1))
    return best[1] if best else None

def parse_label(name):
    m = re.match(r"(\d{2,3})([가-힣])(\d{4})", name)
    return m.groups() if m else None

rows = []
for f in sorted(glob.glob(os.path.join(PLATES, "*.png"))):
    name = os.path.splitext(os.path.basename(f))[0]
    p = parse_label(name)
    if not p: continue
    lead, mid, tail = p
    img = cv2.imdecode(np.fromfile(f, np.uint8), cv2.IMREAD_GRAYSCALE)
    if img is None: continue
    box = find_line_box(img)
    vis = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
    if box is None:
        rows.append((name, mid, "?", 0.0, "nobox"))
    else:
        L, T, R, B = box
        total = len(lead) + 5
        pitch = (R - L) / total
        base_cx = L + (len(lead) + 0.5) * pitch
        top = max(0, int(T - (B - T) * 0.08)); bot = min(img.shape[0], int(B + (B - T) * 0.08))
        best = ("?", 0.0, 0, 0)
        for dx in CENTER_OFFSETS:
            cx = base_cx + dx * pitch
            for hw in HALF_WIDTHS:
                half = pitch * hw
                l = max(0, int(cx - half)); r = min(img.shape[1], int(cx + half))
                if r - l < 8 or bot - top < 8: continue
                ch, cf = run_one(img[top:bot, l:r])
                if cf > best[1]: best = (ch, cf, l, r)
        rows.append((name, mid, best[0], best[1], "ok"))
        cv2.rectangle(vis, (L, T), (R, B), (0, 0, 255), 2)
        cv2.rectangle(vis, (best[2], top), (best[3], bot), (0, 255, 0), 2)
    small = cv2.resize(vis, (480, int(480 * vis.shape[0] / vis.shape[1])))
    cv2.imencode(".png", small)[1].tofile(os.path.join(DUMP, name + ".png"))

# Images where THIS harness's plate localization is known-bad (verified visually):
# background-sign locks, a file-explorer screenshot, and a two-line plate whose slot
# formula differs. Excluded from the classifier metric — their errors are not the CNN's.
EXCLUDE = {"56너9876", "325바8419", "12가3456_2", "154러7070_6", "80아7890"}
correct = 0; n = 0; vc = 0; vn = 0
for name, gt, pr, cf, st in rows:
    ok = "O" if pr == gt else "x"
    if st == "ok":
        n += 1; correct += (pr == gt)
        if name not in EXCLUDE: vn += 1; vc += (pr == gt)
    print(f"{name:22s} {gt} -> {pr} {cf:.3f} {ok} {st if st!='ok' else ''}{' EXCL' if name in EXCLUDE else ''}")
print(f"\nmiddle accuracy (all): {correct}/{n} (nobox {len(rows)-n})")
print(f"middle accuracy (valid-localization): {vc}/{vn}")
for tau in (0.5, 0.8, 0.9, 0.95, 0.99):
    sel = [(gt, pr) for _, gt, pr, cf, st in rows if st == "ok" and cf >= tau]
    hits = sum(1 for gt, pr in sel if gt == pr)
    print(f"gate conf>={tau:.2f}: fires {len(sel):2d}/{n}, precision {hits}/{len(sel) if sel else 0}")
