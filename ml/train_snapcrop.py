# -*- coding: utf-8 -*-
"""40-class glyph CNN trained through the SAME crop policy used at inference.

Pipeline per sample: compose a full [digits][GLYPH][digits] plate line (real plate-font
glyphs 55% / system fonts 45%, real digit images mixed in) -> geometric warp -> photometric
degradation of the whole line -> `snap_to_glyph` finds the glyph's real extent exactly as
`eval_real.py` does at inference -> `crop_for_model` takes the window -> 48x48.

Closing this loop is the point: every earlier trainer cropped differently from the app, so
the model met an unfamiliar framing on device (a half-clipped 나 reads as ㅏ).
"""
import os, glob, sys, numpy as np, cv2
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
from PIL import Image, ImageDraw, ImageFont
import tensorflow as tf
from glyph_crop import snap_to_glyph, crop_for_model

SC = os.path.dirname(os.path.abspath(__file__))
REPO = os.environ.get("KOR_PLATE_REPO", os.path.join(SC, "kor_plate"))
USAGE = list("가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호배")
NCLS = len(USAGE); S = 48; H = 96
HALF_W = float(os.environ.get("HALF_W", "0.62"))

FONT_DIR = r"C:\Windows\Fonts"
FONT_FILES = ["malgunbd.ttf","malgun.ttf","gulim.ttc","batang.ttc","NGULIM.TTF",
              "H2GTRM.TTF","H2GTRE.TTF","H2GPRM.TTF","H2MJRE.TTF","H2SA1M.TTF"]
FONTS = {px: [ImageFont.truetype(os.path.join(FONT_DIR,f),px) for f in FONT_FILES
              if os.path.exists(os.path.join(FONT_DIR,f))] for px in (64,72,80)}

CONS={'r':0,'s':2,'e':3,'f':5,'a':6,'q':7,'t':9,'d':11,'w':12,'g':18}
VOW={'k':0,'j':4,'h':8,'n':13}
def rom_to_char(n):
    return chr(0xAC00+(CONS[n[0]]*21+VOW[n[1]])*28) if len(n)==2 and n[0] in CONS and n[1] in VOW else None
def rd(p): return cv2.imdecode(np.fromfile(p,np.uint8),cv2.IMREAD_GRAYSCALE)

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
        if b and b[0].isdigit():
            g=rd(f)
            if g is not None: DIGITS[b[0]].append(g)
print("real hangul coverage:", sum(1 for c in USAGE if REAL[c]), "/", NCLS,
      "| digit imgs:", sum(len(v) for v in DIGITS.values()))

def trim(g):
    ys,xs=np.where(g<128)
    return g[ys.min():ys.max()+1, xs.min():xs.max()+1] if len(xs) else g

def render_sys(ch, rng):
    px=int(rng.choice(list(FONTS))); f=FONTS[px][rng.integers(len(FONTS[px]))]
    c=Image.new("L",(160,160),255); ImageDraw.Draw(c).text((15,15),ch,font=f,fill=0)
    return trim(np.array(c))

def glyph_img(ch,rng,real): return trim(REAL[ch][rng.integers(len(REAL[ch]))]) if (real and REAL.get(ch)) else render_sys(ch,rng)
def digit_img(rng,real):
    d=str(rng.integers(10))
    return trim(DIGITS[d][rng.integers(len(DIGITS[d]))]) if (real and DIGITS[d]) else render_sys(d,rng)
def scale_h(g,th):
    s=th/g.shape[0]; return cv2.resize(g,(max(1,int(g.shape[1]*s)),th),interpolation=cv2.INTER_AREA)

def compose_line(ch, rng):
    real = rng.random() < 0.55
    dh = int(H*rng.uniform(0.88,1.0))
    gh = int(dh*rng.uniform(0.72,0.98))
    pitch = int(dh*rng.uniform(0.52,0.78))
    n_left = int(rng.integers(2,4))                 # real plates: 2-3 leading digits
    total = n_left + 5
    W = int(pitch*(total+1.6)); canvas = np.full((int(H*1.3), W), 255, np.uint8)
    y0 = (canvas.shape[0]-dh)//2
    centers=[int(pitch*0.9)+i*pitch for i in range(total)]
    for i,c in enumerate(centers):
        if i == n_left:
            g = scale_h(glyph_img(ch,rng,real), gh); yy = y0+(dh-gh)//2
        else:
            g = scale_h(digit_img(rng,real), dh); yy = y0
        x = c-g.shape[1]//2
        a,b = max(0,x), min(W, x+g.shape[1])
        if b<=a: continue
        canvas[yy:yy+g.shape[0], a:b] = np.minimum(canvas[yy:yy+g.shape[0], a:b], g[:, a-x:b-x])
    return canvas, centers[n_left], pitch, y0, dh

def photometric(img,rng):
    if rng.random()<0.7:
        f=rng.uniform(0.3,0.8)
        img=cv2.resize(cv2.resize(img,(max(4,int(img.shape[1]*f)),max(4,int(img.shape[0]*f)))),
                       (img.shape[1],img.shape[0]))
    if rng.random()<0.5: img=cv2.GaussianBlur(img,(int(rng.choice([3,3,5])),)*2,0)
    img=np.clip(img.astype(np.float32)*rng.uniform(0.6,1.3)+rng.uniform(-40,40),0,255).astype(np.uint8)
    if rng.random()<0.5:
        img=np.clip(img.astype(np.int16)+rng.normal(0,rng.uniform(3,15),img.shape),0,255).astype(np.uint8)
    return img

def sample(ci, rng):
    ch=USAGE[ci]
    canvas,gcx,pitch,y0,dh = compose_line(ch,rng)
    h,w = canvas.shape
    m = pitch*0.10
    src=np.float32([[0,0],[w,0],[w,h],[0,h]]); dst=src+rng.uniform(-m,m,src.shape).astype(np.float32)
    canvas=cv2.warpPerspective(canvas,cv2.getPerspectiveTransform(src,dst),(w,h),borderValue=255)
    R=cv2.getRotationMatrix2D((gcx,h/2),rng.uniform(-7,7),rng.uniform(0.95,1.05))
    canvas=cv2.warpAffine(canvas,R,(w,h),borderValue=255)
    canvas=photometric(canvas,rng)                   # degrade the LINE, then detect on it
    top=max(0,int(y0-dh*0.08)); bot=min(h,int(y0+dh*1.08))
    # the slot estimate the app has is imperfect: jitter the centre before snapping
    est_cx = gcx + rng.uniform(-0.30,0.30)*pitch
    snap = snap_to_glyph(canvas, top, bot, est_cx, pitch)
    cx = (snap[0]+snap[1])/2 if snap else est_cx
    # A snap that walked onto a neighbouring digit would train the label onto the wrong
    # picture, so fall back to the true centre rather than emit a mislabelled sample.
    if abs(cx-gcx) > 0.45*pitch:
        snap = snap_to_glyph(canvas, top, bot, gcx, pitch)
        cx = (snap[0]+snap[1])/2 if snap else gcx
    got = crop_for_model(canvas, top, bot, cx, pitch, HALF_W)
    crop = got[0] if got else canvas[top:bot, max(0,int(gcx-pitch*0.6)):int(gcx+pitch*0.6)]
    if crop.size == 0: crop = canvas
    g = cv2.resize(crop,(S,S),interpolation=cv2.INTER_AREA).astype(np.float32)
    return (g-g.mean())/(g.std()+1e-6)

def build(n,seed):
    rng=np.random.default_rng(seed)
    X=np.empty((n*NCLS,S,S,1),np.float32); Y=np.empty((n*NCLS,),np.int64); i=0
    for ci in range(NCLS):
        for _ in range(n): X[i,:,:,0]=sample(ci,rng); Y[i]=ci; i+=1
    return X,Y

if "--preview" in sys.argv:
    rng=np.random.default_rng(0); tiles=[]
    for ci in (0,1,8,9,22,33,38):
        tiles.append(np.hstack([((sample(ci,rng)*40+128).clip(0,255).astype(np.uint8)) for _ in range(8)]))
    cv2.imencode(".png",np.vstack(tiles))[1].tofile(os.path.join(SC,"snap_preview.png"))
    print("preview written"); sys.exit()

N=int(os.environ.get("N_PER_CLASS","1200"))
print(f"Generating snap-crop data ({N}/class)...")
Xtr,Ytr=build(N,1); Xva,Yva=build(150,2)
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
model.fit(Xtr,Ytr,validation_data=(Xva,Yva),epochs=40,batch_size=128,
          callbacks=[tf.keras.callbacks.EarlyStopping(patience=6,restore_best_weights=True,monitor="val_accuracy")],verbose=2)
print(f"val acc: {model.evaluate(Xva,Yva,verbose=0)[1]*100:.2f}%")
tfl=tf.lite.TFLiteConverter.from_keras_model(model).convert()
out=os.path.join(SC,"glyph_cnn_snapcrop.tflite")
open(out,"wb").write(tfl); print("Saved",out,len(tfl),"bytes")
