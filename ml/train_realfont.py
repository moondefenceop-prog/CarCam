"""Train the 40+reject glyph CNN on REAL Korean plate-font glyph images
(kade93/kor_license_plate_generator, MIT) instead of system fonts — closes the domain gap.
Reject class uses the repo's real plate digits + blanks + character seams."""
import os, glob, numpy as np, cv2
os.environ["TF_CPP_MIN_LOG_LEVEL"]="2"
from PIL import Image, ImageDraw, ImageFont
import tensorflow as tf

SC = os.path.dirname(os.path.abspath(__file__))
REPO = os.environ.get("KOR_PLATE_REPO", os.path.join(SC, "kor_plate"))  # git clone https://github.com/kade93/kor_license_plate_generator (MIT)
USAGE = list("가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호배")
REJECT = len(USAGE); NCLS = len(USAGE)+1
S = 48

# keyboard romanization used by the repo's filenames -> Hangul
CONS = {'r':0,'s':2,'e':3,'f':5,'a':6,'q':7,'t':9,'d':11,'w':12,'g':18}
VOW  = {'k':0,'j':4,'h':8,'n':13}
def rom_to_char(name):
    c,v = name[0], name[1]
    if c in CONS and v in VOW:
        return chr(0xAC00 + (CONS[c]*21 + VOW[v])*28)
    return None

def load_gray(path):
    return cv2.imdecode(np.fromfile(path, np.uint8), cv2.IMREAD_GRAYSCALE)

# Map each usage char -> list of real glyph images (white plate set char1).
GLYPHS = {ch: [] for ch in USAGE}
for f in glob.glob(os.path.join(REPO, "char1", "*.jpg")):
    ch = rom_to_char(os.path.splitext(os.path.basename(f))[0])
    if ch in GLYPHS: GLYPHS[ch].append(load_gray(f))
missing = [ch for ch in USAGE if not GLYPHS[ch]]
print("real glyphs loaded; missing:", "".join(missing))
# Fallback (only 배): render with a system Korean font.
_fallback_font = ImageFont.truetype(r"C:\Windows\Fonts\malgunbd.ttf", 90)
def render_fallback(ch):
    c=Image.new("L",(120,120),255); d=ImageDraw.Draw(c)
    bb=d.textbbox((0,0),ch,font=_fallback_font); w,h=bb[2]-bb[0],bb[3]-bb[1]
    d.text(((120-w)/2-bb[0],(120-h)/2-bb[1]),ch,font=_fallback_font,fill=0)
    return np.array(c)
for ch in missing: GLYPHS[ch].append(render_fallback(ch))

DIGITS = [load_gray(f) for f in sorted(glob.glob(os.path.join(REPO,"num","*.jpg")))]
print("real digits:", len(DIGITS))

def canvas_center(glyph, rng=None):
    """Frame the glyph like the on-device slot crop: a tallish canvas (aspect ~0.6 W/H) with the
    glyph centered, filling ~72% of the height and leaving side margins. Inference then resizes this
    (aspect-distorting) to 48x48, so training must use the same framing or the model collapses."""
    g = glyph
    ys, xs = np.where(g < 128)
    if len(xs): g = g[ys.min():ys.max()+1, xs.min():xs.max()+1]
    h, w = g.shape
    fill_h = rng.uniform(0.62, 0.80) if rng is not None else 0.72
    aspect = rng.uniform(0.52, 0.70) if rng is not None else 0.6   # canvas W/H
    H = int(h / fill_h); W = int(H * aspect)
    if w > 0.92 * W:                       # keep wide glyphs from overflowing the side margins
        s = 0.92 * W / w; g = cv2.resize(g, (max(1,int(w*s)), max(1,int(h*s)))); h, w = g.shape
    cv = np.full((H, W), 255, np.uint8)
    oy, ox = (H-h)//2, (W-w)//2
    cv[oy:oy+h, ox:ox+w] = g
    return cv

def augment(img, rng):
    h, w = img.shape; m = 12
    src = np.float32([[0,0],[w,0],[w,h],[0,h]])
    dst = src + rng.uniform(-m, m, src.shape).astype(np.float32)
    img = cv2.warpPerspective(img, cv2.getPerspectiveTransform(src,dst), (w,h), borderValue=255)
    sc = rng.uniform(0.8,1.18); tx=rng.uniform(-0.16,0.16)*w; ty=rng.uniform(-0.12,0.12)*h
    R = cv2.getRotationMatrix2D((w/2,h/2), rng.uniform(-9,9), sc); R[0,2]+=tx; R[1,2]+=ty
    img = cv2.warpAffine(img, R, (w,h), borderValue=255)
    if rng.random()<0.75:
        f=rng.uniform(0.22,0.6); img=cv2.resize(cv2.resize(img,(max(4,int(w*f)),max(4,int(h*f)))),(w,h))
    if rng.random()<0.6:
        k=int(rng.choice([3,3,5])); img=cv2.GaussianBlur(img,(k,k),0)
    a=rng.uniform(0.55,1.35); b=rng.uniform(-45,45)
    img=np.clip(img.astype(np.float32)*a+b,0,255).astype(np.uint8)
    if rng.random()<0.6:
        img=np.clip(img.astype(np.int16)+rng.normal(0,rng.uniform(4,20),img.shape),0,255).astype(np.uint8)
    return img

def finalize(g, rng):
    g = augment(canvas_center(g, rng), rng); g = cv2.resize(g,(S,S)).astype(np.float32)
    return (g-g.mean())/(g.std()+1e-6)

def sample_usage(ci, rng):
    imgs = GLYPHS[USAGE[ci]]; return finalize(imgs[rng.integers(len(imgs))], rng)

def sample_reject(rng):
    r = rng.random()
    if r<0.55 and DIGITS:                    # real plate digit
        return finalize(DIGITS[rng.integers(len(DIGITS))], rng)
    elif r<0.82 and DIGITS:                   # seam of two real glyphs
        a = DIGITS[rng.integers(len(DIGITS))]
        pool = DIGITS + [im for v in GLYPHS.values() for im in v]
        b = pool[rng.integers(len(pool))]
        a,b = canvas_center(a, rng), canvas_center(b, rng)
        hh = 96; a=cv2.resize(a,(hh,hh)); b=cv2.resize(b,(hh,hh))
        strip = np.hstack([a,b]); cx=rng.integers(hh-24,hh+24)
        return finalize(strip[:, max(0,cx-48):cx+48], rng)
    else:                                     # near-blank
        base=int(rng.uniform(150,255)); return finalize(np.full((96,96),base,np.uint8), rng)

def build(n_per, seed):
    rng=np.random.default_rng(seed); tot=n_per*NCLS
    X=np.empty((tot,S,S,1),np.float32); Y=np.empty((tot,),np.int64); i=0
    for ci in range(NCLS):
        for _ in range(n_per):
            X[i,:,:,0]=(sample_reject(rng) if ci==REJECT else sample_usage(ci,rng)); Y[i]=ci; i+=1
    return X,Y

print("Generating from real plate glyphs...")
Xtr,Ytr=build(800,1); Xva,Yva=build(150,2)
print("train",Xtr.shape,"val",Xva.shape)

model=tf.keras.Sequential([
    tf.keras.layers.Input((S,S,1)),
    tf.keras.layers.Conv2D(16,3,padding="same",activation="relu"), tf.keras.layers.BatchNormalization(), tf.keras.layers.MaxPool2D(),
    tf.keras.layers.Conv2D(32,3,padding="same",activation="relu"), tf.keras.layers.BatchNormalization(), tf.keras.layers.MaxPool2D(),
    tf.keras.layers.Conv2D(64,3,padding="same",activation="relu"), tf.keras.layers.BatchNormalization(), tf.keras.layers.MaxPool2D(),
    tf.keras.layers.GlobalAveragePooling2D(),
    tf.keras.layers.Dense(96,activation="relu"), tf.keras.layers.Dropout(0.3),
    tf.keras.layers.Dense(NCLS,activation="softmax"),
])
model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),loss="sparse_categorical_crossentropy",metrics=["accuracy"])
model.fit(Xtr,Ytr,validation_data=(Xva,Yva),epochs=30,batch_size=128,
          callbacks=[tf.keras.callbacks.EarlyStopping(patience=4,restore_best_weights=True,monitor="val_accuracy")],verbose=2)
print(f"Synthetic(real-font) val accuracy: {model.evaluate(Xva,Yva,verbose=0)[1]*100:.2f}%")

model.save(os.path.join(SC,"glyph_cnn.keras"))
c=tf.lite.TFLiteConverter.from_keras_model(model); tfl=c.convert()   # float32 for runtime compat
open(os.path.join(SC,"glyph_cnn.tflite"),"wb").write(tfl)
np.save(os.path.join(SC,"labels.npy"),np.array(USAGE+["·"]))
print("Saved glyph_cnn.tflite", len(tfl),"bytes; classes:",NCLS)
