"""Synthetic glyph data generator for the 40 Korean plate usage characters.
Domain-randomized: multiple Korean fonts + heavy augmentation to bridge the gap to
real plate photos (moire, low-res, blur, perspective, lighting) without the exact plate font.
"""
import os, random, numpy as np, cv2
from PIL import Image, ImageDraw, ImageFont

USAGE = list("가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호배")
FONT_DIR = r"C:\Windows\Fonts"
FONT_FILES = ["malgunbd.ttf","malgun.ttf","gulim.ttc","batang.ttc","NGULIM.TTF",
              "H2GTRM.TTF","H2GTRE.TTF","H2GPRM.TTF","H2MJRE.TTF","H2SA1M.TTF"]
S = 48  # output size SxS grayscale

def load_fonts(px):
    fonts = []
    for f in FONT_FILES:
        p = os.path.join(FONT_DIR, f)
        if os.path.exists(p):
            try: fonts.append(ImageFont.truetype(p, px))
            except Exception: pass
    return fonts

def render_glyph(ch, font):
    canvas = Image.new("L", (96, 96), 255)
    d = ImageDraw.Draw(canvas)
    bb = d.textbbox((0,0), ch, font=font)
    w, h = bb[2]-bb[0], bb[3]-bb[1]
    d.text(((96-w)/2 - bb[0], (96-h)/2 - bb[1]), ch, font=font, fill=0)
    return np.array(canvas)

def augment(img, rng):
    h, w = img.shape
    # perspective / affine
    m = 12
    src = np.float32([[0,0],[w,0],[w,h],[0,h]])
    dst = src + rng.uniform(-m, m, src.shape).astype(np.float32)
    M = cv2.getPerspectiveTransform(src, dst)
    img = cv2.warpPerspective(img, M, (w,h), borderValue=255)
    # rotation
    ang = rng.uniform(-9, 9)
    R = cv2.getRotationMatrix2D((w/2,h/2), ang, 1.0)
    img = cv2.warpAffine(img, R, (w,h), borderValue=255)
    # low-res / moire-ish: downscale then up
    if rng.random() < 0.7:
        f = rng.uniform(0.25, 0.6)
        img = cv2.resize(cv2.resize(img,(max(4,int(w*f)),max(4,int(h*f)))),(w,h))
    # blur
    if rng.random() < 0.6:
        k = rng.choice([3,3,5]); img = cv2.GaussianBlur(img,(k,k),0)
    # brightness / contrast
    a = rng.uniform(0.6,1.3); b = rng.uniform(-40,40)
    img = np.clip(img.astype(np.float32)*a+b, 0,255).astype(np.uint8)
    # noise
    if rng.random() < 0.6:
        img = np.clip(img.astype(np.int16)+rng.normal(0,rng.uniform(4,18),img.shape),0,255).astype(np.uint8)
    return img

def make_sample(ch, fonts, rng):
    px = rng.choice([56,64,72])
    f = rng.choice(load_fonts(px)) if False else rng.choice(fonts[px])
    g = render_glyph(ch, f)
    g = augment(g, rng)
    g = cv2.resize(g,(S,S))
    return g

if __name__ == "__main__":
    rng = np.random.default_rng(0)
    fonts = {px: load_fonts(px) for px in (56,64,72)}
    print("fonts loaded:", len(fonts[64]))
    # preview grid: several augmented samples for a few tricky glyphs
    preview_chars = ["러","너","서","머","저","가","조","바"]
    rows = []
    for ch in preview_chars:
        cells = [make_sample(ch, fonts, rng) for _ in range(12)]
        rows.append(np.hstack(cells))
    grid = np.vstack(rows)
    grid = cv2.resize(grid, (grid.shape[1]*2, grid.shape[0]*2), interpolation=cv2.INTER_NEAREST)
    out = os.path.join(SC, "glyph_preview.png")
    cv2.imwrite(out, grid)
    print("wrote", out, grid.shape, "| rows=", preview_chars)
