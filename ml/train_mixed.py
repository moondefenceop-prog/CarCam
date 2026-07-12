# -*- coding: utf-8 -*-
"""40-class glyph CNN trained on a MIX of system Korean fonts + real plate-font glyph images
(kade93/kor_license_plate_generator, MIT). Keeps the shipped model's framing (glyph centered on a
96x96 canvas) so it's a drop-in for the numeric-only fallback; adds plate-font shapes for the
ㅓ-column. No reject class (matches production)."""
import os, glob, numpy as np, cv2
os.environ["TF_CPP_MIN_LOG_LEVEL"]="2"
from PIL import Image, ImageDraw, ImageFont
import tensorflow as tf

SC = os.path.dirname(os.path.abspath(__file__))
REPO = os.environ.get("KOR_PLATE_REPO", os.path.join(SC, "kor_plate"))  # kade93/kor_license_plate_generator (MIT)
USAGE = list("가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호배")
NCLS = len(USAGE); S = 48
FONT_DIR = r"C:\Windows\Fonts"
FONT_FILES = ["malgunbd.ttf","malgun.ttf","gulim.ttc","batang.ttc","NGULIM.TTF",
              "H2GTRM.TTF","H2GTRE.TTF","H2GPRM.TTF","H2MJRE.TTF","H2SA1M.TTF"]

def load_fonts():
    d={}
    for px in (52,58,64,72):
        d[px]=[ImageFont.truetype(os.path.join(FONT_DIR,f),px) for f in FONT_FILES if os.path.exists(os.path.join(FONT_DIR,f))]
    return d
FONTS=load_fonts()

CONS={'r':0,'s':2,'e':3,'f':5,'a':6,'q':7,'t':9,'d':11,'w':12,'g':18}; VOW={'k':0,'j':4,'h':8,'n':13}
def rom_to_char(n): return chr(0xAC00+(CONS[n[0]]*21+VOW[n[1]])*28) if n[0] in CONS and n[1] in VOW else None
def rd(p): return cv2.imdecode(np.fromfile(p,np.uint8),cv2.IMREAD_GRAYSCALE)
REAL={ch:[] for ch in USAGE}
for f in glob.glob(os.path.join(REPO,"char1","*.jpg")):
    c=rom_to_char(os.path.splitext(os.path.basename(f))[0])
    if c in REAL: REAL[c].append(rd(f))
print("real glyphs for", sum(1 for c in USAGE if REAL[c]), "/", len(USAGE), "chars")

def render_font(ch,rng):
    px=int(rng.choice([52,58,64,72])); f=FONTS[px][rng.integers(len(FONTS[px]))]
    c=Image.new("L",(96,96),255); d=ImageDraw.Draw(c)
    bb=d.textbbox((0,0),ch,font=f); w,h=bb[2]-bb[0],bb[3]-bb[1]
    d.text(((96-w)/2-bb[0],(96-h)/2-bb[1]),ch,font=f,fill=0)
    return np.array(c)

def place_real(glyph, rng, size=96):
    ys,xs=np.where(glyph<128); g=glyph[ys.min():ys.max()+1,xs.min():xs.max()+1] if len(xs) else glyph
    fill=rng.uniform(0.55,0.72); th=int(size*fill); s=th/g.shape[0]
    g=cv2.resize(g,(max(1,int(g.shape[1]*s)),th)); cv=np.full((size,size),255,np.uint8)
    oy=(size-g.shape[0])//2; ox=(size-g.shape[1])//2; cv[oy:oy+g.shape[0],ox:ox+g.shape[1]]=g
    return cv

def augment(img,rng):
    h,w=img.shape; m=12
    src=np.float32([[0,0],[w,0],[w,h],[0,h]]); dst=src+rng.uniform(-m,m,src.shape).astype(np.float32)
    img=cv2.warpPerspective(img,cv2.getPerspectiveTransform(src,dst),(w,h),borderValue=255)
    sc=rng.uniform(0.8,1.18); tx=rng.uniform(-0.16,0.16)*w; ty=rng.uniform(-0.12,0.12)*h
    R=cv2.getRotationMatrix2D((w/2,h/2),rng.uniform(-9,9),sc); R[0,2]+=tx; R[1,2]+=ty
    img=cv2.warpAffine(img,R,(w,h),borderValue=255)
    if rng.random()<0.75:
        f=rng.uniform(0.22,0.6); img=cv2.resize(cv2.resize(img,(max(4,int(w*f)),max(4,int(h*f)))),(w,h))
    if rng.random()<0.6: img=cv2.GaussianBlur(img,(int(rng.choice([3,3,5])),)*2,0)
    a=rng.uniform(0.55,1.35); b=rng.uniform(-45,45); img=np.clip(img.astype(np.float32)*a+b,0,255).astype(np.uint8)
    if rng.random()<0.6: img=np.clip(img.astype(np.int16)+rng.normal(0,rng.uniform(4,20),img.shape),0,255).astype(np.uint8)
    return img

def sample(ci,rng):
    ch=USAGE[ci]
    # 55% real plate font (if available), else system font
    if REAL[ch] and rng.random()<0.55:
        base=place_real(REAL[ch][rng.integers(len(REAL[ch]))],rng)
    else:
        base=render_font(ch,rng)
    g=cv2.resize(augment(base,rng),(S,S)).astype(np.float32)
    return (g-g.mean())/(g.std()+1e-6)

def build(n,seed):
    rng=np.random.default_rng(seed); X=np.empty((n*NCLS,S,S,1),np.float32); Y=np.empty((n*NCLS,),np.int64); i=0
    for ci in range(NCLS):
        for _ in range(n): X[i,:,:,0]=sample(ci,rng); Y[i]=ci; i+=1
    return X,Y

print("Generating mixed data...")
Xtr,Ytr=build(800,1); Xva,Yva=build(150,2)
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
model.fit(Xtr,Ytr,validation_data=(Xva,Yva),epochs=30,batch_size=128,
          callbacks=[tf.keras.callbacks.EarlyStopping(patience=4,restore_best_weights=True,monitor="val_accuracy")],verbose=2)
print(f"val acc: {model.evaluate(Xva,Yva,verbose=0)[1]*100:.2f}%")
c=tf.lite.TFLiteConverter.from_keras_model(model); tfl=c.convert()   # float32
open(os.path.join(SC,"glyph_cnn.tflite"),"wb").write(tfl)
np.save(os.path.join(SC,"labels.npy"),np.array(USAGE))
print("Saved glyph_cnn.tflite",len(tfl),"bytes; classes",NCLS)
