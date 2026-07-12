"""Train a small 40-class CNN for Korean plate usage glyphs; export TFLite.
Domain-randomized synthetic data (multiple Korean fonts + heavy augmentation)."""
import os, numpy as np, cv2
os.environ["TF_CPP_MIN_LOG_LEVEL"]="2"
from PIL import Image, ImageDraw, ImageFont
import tensorflow as tf

SC = os.path.dirname(os.path.abspath(__file__))
USAGE = list("가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호배")
NCLS = len(USAGE)
S = 48
FONT_DIR = r"C:\Windows\Fonts"
FONT_FILES = ["malgunbd.ttf","malgun.ttf","gulim.ttc","batang.ttc","NGULIM.TTF",
              "H2GTRM.TTF","H2GTRE.TTF","H2GPRM.TTF","H2MJRE.TTF","H2SA1M.TTF"]

def load_fonts():
    d={}
    for px in (52,58,64,72):
        fs=[]
        for f in FONT_FILES:
            p=os.path.join(FONT_DIR,f)
            if os.path.exists(p):
                try: fs.append(ImageFont.truetype(p,px))
                except Exception: pass
        d[px]=fs
    return d
FONTS=load_fonts()

def render(ch,font):
    c=Image.new("L",(96,96),255); d=ImageDraw.Draw(c)
    bb=d.textbbox((0,0),ch,font=font); w,h=bb[2]-bb[0],bb[3]-bb[1]
    d.text(((96-w)/2-bb[0],(96-h)/2-bb[1]),ch,font=font,fill=0)
    return np.array(c)

def augment(img,rng):
    h,w=img.shape; m=12
    src=np.float32([[0,0],[w,0],[w,h],[0,h]])
    dst=src+rng.uniform(-m,m,src.shape).astype(np.float32)
    img=cv2.warpPerspective(img,cv2.getPerspectiveTransform(src,dst),(w,h),borderValue=255)
    # translation + scale jitter: the on-device crop is rarely perfectly centered/sized, so make
    # the classifier robust to the same misalignment we observe in real slot crops.
    sc=rng.uniform(0.8,1.18); tx=rng.uniform(-0.16,0.16)*w; ty=rng.uniform(-0.12,0.12)*h
    R=cv2.getRotationMatrix2D((w/2,h/2),rng.uniform(-9,9),sc); R[0,2]+=tx; R[1,2]+=ty
    img=cv2.warpAffine(img,R,(w,h),borderValue=255)
    if rng.random()<0.75:
        f=rng.uniform(0.22,0.6); img=cv2.resize(cv2.resize(img,(max(4,int(w*f)),max(4,int(h*f)))),(w,h))
    if rng.random()<0.6:
        k=int(rng.choice([3,3,5])); img=cv2.GaussianBlur(img,(k,k),0)
    a=rng.uniform(0.55,1.35); b=rng.uniform(-45,45)
    img=np.clip(img.astype(np.float32)*a+b,0,255).astype(np.uint8)
    if rng.random()<0.6:
        img=np.clip(img.astype(np.int16)+rng.normal(0,rng.uniform(4,20),img.shape),0,255).astype(np.uint8)
    return img

def sample(ch,rng):
    px=int(rng.choice([52,58,64,72])); f=FONTS[px][rng.integers(len(FONTS[px]))]
    g=augment(render(ch,f),rng)
    g=cv2.resize(g,(S,S)).astype(np.float32)
    g=(g-g.mean())/(g.std()+1e-6)   # per-image standardization (must match on-device)
    return g

def build_dataset(n_per,seed):
    rng=np.random.default_rng(seed)
    X=np.empty((n_per*NCLS,S,S,1),np.float32); Y=np.empty((n_per*NCLS,),np.int64); i=0
    for ci,ch in enumerate(USAGE):
        for _ in range(n_per):
            X[i,:,:,0]=sample(ch,rng); Y[i]=ci; i+=1
    return X,Y

print("Generating synthetic data...")
Xtr,Ytr=build_dataset(700,1); Xva,Yva=build_dataset(120,2)
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
model.summary(print_fn=lambda s: print(s) if "params" in s.lower() or "Total" in s else None)
cbs=[tf.keras.callbacks.EarlyStopping(patience=4,restore_best_weights=True,monitor="val_accuracy")]
model.fit(Xtr,Ytr,validation_data=(Xva,Yva),epochs=30,batch_size=128,callbacks=cbs,verbose=2)

vloss,vacc=model.evaluate(Xva,Yva,verbose=0)
print(f"\nSynthetic val accuracy: {vacc*100:.2f}%")

model.save(os.path.join(SC,"glyph_cnn.keras"))
conv=tf.lite.TFLiteConverter.from_keras_model(model)
conv.optimizations=[tf.lite.Optimize.DEFAULT]
tfl=conv.convert()
open(os.path.join(SC,"glyph_cnn.tflite"),"wb").write(tfl)
np.save(os.path.join(SC,"labels.npy"),np.array(USAGE))
print("Saved glyph_cnn.tflite", len(tfl),"bytes; labels:",len(USAGE))
