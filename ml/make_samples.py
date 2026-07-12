# -*- coding: utf-8 -*-
"""Render 50 diverse Korean license plates using the real plate-font glyph/digit images
(kade93/kor_license_plate_generator, MIT). Output: individual PNGs + a contact sheet + gallery.html."""
import os, glob, numpy as np, cv2

SC = os.path.dirname(os.path.abspath(__file__))
REPO = os.environ.get("KOR_PLATE_REPO", os.path.join(SC, "kor_plate"))  # git clone kade93/kor_license_plate_generator (MIT)
OUT = os.environ.get("PLATE_SAMPLES_OUT", os.path.join(SC, "plate_samples"))
os.makedirs(OUT, exist_ok=True)

CONS = {'r':0,'s':2,'e':3,'f':5,'a':6,'q':7,'t':9,'d':11,'w':12,'g':18}
VOW  = {'k':0,'j':4,'h':8,'n':13}
def rom_to_char(n):
    if n[0] in CONS and n[1] in VOW: return chr(0xAC00 + (CONS[n[0]]*21+VOW[n[1]])*28)
    return None
CHAR_ROM = {}                                   # char -> romanization filename
for f in glob.glob(os.path.join(REPO,"char1","*.jpg")):
    n=os.path.splitext(os.path.basename(f))[0]; c=rom_to_char(n)
    if c: CHAR_ROM[c]=n
USAGE = [c for c in "가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호" if c in CHAR_ROM]

def rd(p): return cv2.imdecode(np.fromfile(p,np.uint8),cv2.IMREAD_GRAYSCALE)
def glyph_img(ch):   return rd(os.path.join(REPO,"char1",CHAR_ROM[ch]+".jpg"))
def digit_img(d):    return rd(os.path.join(REPO,"num",d+".jpg"))

def trim(g):
    ys,xs=np.where(g<128)
    return g[ys.min():ys.max()+1, xs.min():xs.max()+1] if len(xs) else g

def render_plate(text, H=260, ch_h=168, gap=16, hangul_extra=18, margin=46):
    imgs=[]
    for c in text:
        g = trim(glyph_img(c) if c in CHAR_ROM else digit_img(c))
        s = ch_h/g.shape[0]; g=cv2.resize(g,(max(1,int(g.shape[1]*s)), ch_h))
        imgs.append((c,g))
    widths=[g.shape[1] for _,g in imgs]
    total = sum(widths) + gap*(len(imgs)-1) + 2*margin
    # extra spacing around the Hangul
    total += 2*hangul_extra
    W=total
    plate=np.full((H,W),255,np.uint8)
    x=margin; y=(H-ch_h)//2
    for i,(c,g) in enumerate(imgs):
        if c in CHAR_ROM: x+=hangul_extra
        plate[y:y+ch_h, x:x+g.shape[1]] = g
        x+=g.shape[1]
        if c in CHAR_ROM: x+=hangul_extra
        x+=gap
    plate=cv2.cvtColor(plate,cv2.COLOR_GRAY2BGR)
    cv2.rectangle(plate,(6,6),(W-7,H-7),(30,30,30),6)          # black border
    return plate

# ---- 50 diverse plate strings ----
rng=np.random.default_rng(7)
def tail(): return f"{rng.integers(1000,10000)}"
def head3(): return f"{rng.integers(100,500)}"
def head2(): return f"{rng.integers(10,100)}"
plates=[]
for i,ch in enumerate(USAGE):                    # each usage char once (39)
    plates.append((head3() if i%2==0 else head2())+ch+tail())
extra_chars=list("러머너서저거가나다바조호아사하")   # popular / tricky, to reach 50
for i in range(50-len(plates)):
    ch=extra_chars[i%len(extra_chars)]
    plates.append((head3() if i%2 else head2())+ch+tail())
plates=plates[:50]

cells=[]
for i,p in enumerate(plates,1):
    img=render_plate(p)
    fn=f"{i:02d}_{p}.png"
    cv2.imencode(".png",img)[1].tofile(os.path.join(OUT,fn))
    lab=cv2.resize(img,(520,int(520*img.shape[0]/img.shape[1])))
    cv2.putText(lab,f"{i:02d}",(8,26),cv2.FONT_HERSHEY_SIMPLEX,0.8,(0,0,220),2)
    cells.append(lab)

# contact sheet (5 cols)
h=max(c.shape[0] for c in cells); cells=[cv2.copyMakeBorder(c,0,h-c.shape[0],0,0,cv2.BORDER_CONSTANT,value=(255,255,255)) for c in cells]
cols=5; rows=[np.hstack(cells[i:i+cols]) for i in range(0,len(cells),cols)]
w=max(r.shape[1] for r in rows); rows=[cv2.copyMakeBorder(r,0,0,0,w-r.shape[1],cv2.BORDER_CONSTANT,value=(255,255,255)) for r in rows]
sheet=np.vstack(rows); cv2.imencode(".png",sheet)[1].tofile(os.path.join(OUT,"_contact_sheet.png"))

# gallery.html — one plate per screen for easy photographing
html=["<html><head><meta charset='utf-8'><style>body{background:#333;margin:0}",
      ".p{height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center}",
      "img{width:80%;max-width:1100px;background:#fff}h2{color:#ccc;font-family:sans-serif}</style></head><body>"]
for i,p in enumerate(plates,1):
    html.append(f"<div class='p'><h2>{i:02d} / 50 &nbsp; {p}</h2><img src='{i:02d}_{p}.png'></div>")
html.append("</body></html>")
open(os.path.join(OUT,"gallery.html"),"w",encoding="utf-8").write("\n".join(html))

print("wrote", len(plates), "plates to", OUT)
print("plates:", ", ".join(plates))
