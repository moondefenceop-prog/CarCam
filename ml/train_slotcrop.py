# -*- coding: utf-8 -*-
"""40-class glyph CNN v2: trains on SLOT CROPS, not isolated glyphs.

Key change vs all prior attempts: the app feeds the CNN a slot crop (half-width
0.5-0.62 of the slot pitch, center jitter up to ±0.35 pitch) that routinely contains
edges of the NEIGHBORING DIGITS. Prior training rendered a single centered glyph on a
blank canvas — a framing the model never sees at inference. Here we compose a full
[digits][GLYPH][digits] line (real plate font 55% / system fonts 45%, real digit
images mixed in), apply geometric+photometric augmentation to the line, then take a
crop with the app's exact half-width/jitter distribution."""
import os, glob, sys, numpy as np, cv2
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
from PIL import Image, ImageDraw, ImageFont
import tensorflow as tf

SC = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(SC, "kor_plate")
OUTDIR = SC
USAGE = list("가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호배")
NCLS = len(USAGE); S = 48; H = 96  # line height in canvas px

FONT_DIR = r"C:\Windows\Fonts"
FONT_FILES = ["malgunbd.ttf","malgun.ttf","gulim.ttc","batang.ttc","NGULIM.TTF",
              "H2GTRM.TTF","H2GTRE.TTF","H2GPRM.TTF","H2MJRE.TTF","H2SA1M.TTF"]
FONTS = {px: [ImageFont.truetype(os.path.join(FONT_DIR,f),px) for f in FONT_FILES
              if os.path.exists(os.path.join(FONT_DIR,f))] for px in (64,72,80)}

CONS={'r':0,'s':2,'e':3,'f':5,'a':6,'q':7,'t':9,'d':11,'w':12,'g':18}; VOW={'k':0,'j':4,'h':8,'n':13}
def rom_to_char(n): return chr(0xAC00+(CONS[n[0]]*21+VOW[n[1]])*28) if len(n)==2 and n[0] in CONS and n[1] in VOW else None
def rd(p):
    img = cv2.imdecode(np.fromfile(p,np.uint8),cv2.IMREAD_GRAYSCALE)
    return img

REAL={ch:[] for ch in USAGE}
for sub in ("char1","char1_g","char1_y"):
    for f in glob.glob(os.path.join(REPO,sub,"*.jpg")):
        c=rom_to_char(os.path.splitext(os.path.basename(f))[0])
        if c in REAL:
            g=rd(f)
            if g is not None: REAL[c].append(g)
DIGITS={str(d):[] for d in range(10)}
for sub in ("num","num_g","num_y"):
    for f in glob.glob(os.path.join(REPO,sub,"*.jpg")):
        b=os.path.splitext(os.path.basename(f))[0]
        if b and b[0].isdigit() and b[0] in DIGITS:
            g=rd(f)
            if g is not None: DIGITS[b[0]].append(g)
print("real hangul coverage:", sum(1 for c in USAGE if REAL[c]), "/", NCLS,
      "| digit imgs:", sum(len(v) for v in DIGITS.values()))

def trim(g):
    ys,xs=np.where(g<128)
    return g[ys.min():ys.max()+1, xs.min():xs.max()+1] if len(xs) else g

def render_sys(ch, rng):
    px=int(rng.choice(list(FONTS)))
    f=FONTS[px][rng.integers(len(FONTS[px]))]
    c=Image.new("L",(140,140),255); d=ImageDraw.Draw(c)
    d.text((10,10),ch,font=f,fill=0)
    return trim(np.array(c))

def glyph_img(ch, rng, use_real):
    if use_real and REAL.get(ch):
        return trim(REAL[ch][rng.integers(len(REAL[ch]))])
    return render_sys(ch, rng)

def digit_img(rng, use_real):
    d=str(rng.integers(10))
    if use_real and DIGITS[d]:
        return trim(DIGITS[d][rng.integers(len(DIGITS[d]))])
    return render_sys(d, rng)

def scale_h(g, th):
    s=th/g.shape[0]
    return cv2.resize(g,(max(1,int(g.shape[1]*s)),th),interpolation=cv2.INTER_AREA)

def compose_line(ch, rng):
    """White canvas with [1-2 digits][GLYPH][2 digits] at plate-like pitch.
    Returns canvas, glyph slot center x, pitch."""
    use_real = rng.random() < 0.55
    dh = int(H*rng.uniform(0.88,1.0))            # digit height
    gh = int(dh*rng.uniform(0.72,0.98))          # hangul usually a bit smaller
    pitch = int(dh*rng.uniform(0.52,0.78))
    n_left = int(rng.integers(1,3)); n_right = 2
    total = n_left + 1 + n_right
    W = int(pitch*(total+1.2)); canvas = np.full((int(H*1.25), W), 255, np.uint8)
    y0 = (canvas.shape[0]-dh)//2
    cx0 = int(pitch*0.8)
    centers=[]
    for i in range(total):
        centers.append(cx0 + i*pitch)
    for i,c in enumerate(centers):
        if i == n_left:
            g = scale_h(glyph_img(ch, rng, use_real), gh)
            yy = y0 + (dh-gh)//2
        else:
            g = scale_h(digit_img(rng, use_real), dh)
            yy = y0
        x = c - g.shape[1]//2
        x0c, x1c = max(0,x), min(W, x+g.shape[1])
        if x1c <= x0c: continue
        region = canvas[yy:yy+g.shape[0], x0c:x1c]
        canvas[yy:yy+g.shape[0], x0c:x1c] = np.minimum(region, g[:, x0c-x:x1c-x])
    return canvas, centers[n_left], pitch, y0, dh

def photometric(img, rng):
    if rng.random()<0.7:
        f=rng.uniform(0.3,0.75)
        img=cv2.resize(cv2.resize(img,(max(4,int(img.shape[1]*f)),max(4,int(img.shape[0]*f)))),
                       (img.shape[1],img.shape[0]))
    if rng.random()<0.5: img=cv2.GaussianBlur(img,(int(rng.choice([3,3,5])),)*2,0)
    a=rng.uniform(0.6,1.3); b=rng.uniform(-40,40)
    img=np.clip(img.astype(np.float32)*a+b,0,255).astype(np.uint8)
    if rng.random()<0.5:
        img=np.clip(img.astype(np.int16)+rng.normal(0,rng.uniform(3,15),img.shape),0,255).astype(np.uint8)
    return img

def sample(ci, rng):
    ch=USAGE[ci]
    canvas, gcx, pitch, y0, dh = compose_line(ch, rng)
    h,w = canvas.shape
    # mild geometric distortion of the whole line
    m = pitch*0.10
    src=np.float32([[0,0],[w,0],[w,h],[0,h]]); dst=src+rng.uniform(-m,m,src.shape).astype(np.float32)
    M=cv2.getPerspectiveTransform(src,dst)
    canvas=cv2.warpPerspective(canvas,M,(w,h),borderValue=255)
    R=cv2.getRotationMatrix2D((gcx,h/2),rng.uniform(-7,7),rng.uniform(0.95,1.05))
    canvas=cv2.warpAffine(canvas,R,(w,h),borderValue=255)
    # slot crop with the app's exact distribution
    dx = rng.choice([-0.35,-0.15,0.0,0.15,0.35]) * rng.uniform(0.6,1.0)
    hw = rng.choice([0.5,0.62])
    cx = gcx + dx*pitch
    half = pitch*hw
    top = max(0, int(y0 - dh*0.08 - (h-dh)/2*rng.uniform(0,0.5)))
    bot = min(h, int(y0 + dh*1.08))
    l = max(0,int(cx-half)); r = min(w,int(cx+half))
    crop = canvas[top:bot, l:r]
    if crop.size==0: crop=canvas
    crop = photometric(crop, rng)
    g = cv2.resize(crop,(S,S),interpolation=cv2.INTER_AREA).astype(np.float32)
    return (g-g.mean())/(g.std()+1e-6)

def build(n,seed):
    rng=np.random.default_rng(seed)
    X=np.empty((n*NCLS,S,S,1),np.float32); Y=np.empty((n*NCLS,),np.int64); i=0
    for ci in range(NCLS):
        for _ in range(n): X[i,:,:,0]=sample(ci,rng); Y[i]=ci; i+=1
    return X,Y

if "--preview" in sys.argv:
    rng=np.random.default_rng(0)
    tiles=[]
    for ci in (0, 8, 9, 22, 33, 38):  # 가 러 머 조 바 호
        row=[((sample(ci,rng)*40+128).clip(0,255).astype(np.uint8)) for _ in range(8)]
        tiles.append(np.hstack(row))
    cv2.imencode(".png", np.vstack(tiles))[1].tofile(os.path.join(OUTDIR,"v2_preview.png"))
    print("preview written"); sys.exit()

print("Generating slot-crop data...")
Xtr,Ytr=build(int(os.environ.get("N_PER_CLASS","900")),1); Xva,Yva=build(150,2)
model=tf.keras.Sequential([
    tf.keras.layers.Input((S,S,1)),
    tf.keras.layers.Conv2D(16,3,padding="same",activation="relu"),tf.keras.layers.BatchNormalization(),tf.keras.layers.MaxPool2D(),
    tf.keras.layers.Conv2D(32,3,padding="same",activation="relu"),tf.keras.layers.BatchNormalization(),tf.keras.layers.MaxPool2D(),
    tf.keras.layers.Conv2D(64,3,padding="same",activation="relu"),tf.keras.layers.BatchNormalization(),tf.keras.layers.MaxPool2D(),
    tf.keras.layers.GlobalAveragePooling2D(),
    tf.keras.layers.Dense(96,activation="relu"),tf.keras.layers.Dropout(0.3),
    tf.keras.layers.Dense(NCLS,activation="softmax"),
])
model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),loss="sparse_categorical_crossentropy",metrics=["accuracy"])
model.fit(Xtr,Ytr,validation_data=(Xva,Yva),epochs=45,batch_size=128,
          callbacks=[tf.keras.callbacks.EarlyStopping(patience=6,restore_best_weights=True,monitor="val_accuracy")],verbose=2)
print(f"val acc: {model.evaluate(Xva,Yva,verbose=0)[1]*100:.2f}%")
tfl=tf.lite.TFLiteConverter.from_keras_model(model).convert()  # float32
open(os.path.join(OUTDIR,"glyph_cnn_slotcrop.tflite"),"wb").write(tfl)
print("Saved glyph_cnn_slotcrop.tflite",len(tfl),"bytes")
