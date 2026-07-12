import os, numpy as np
os.environ["TF_CPP_MIN_LOG_LEVEL"]="3"
import tensorflow as tf
import train as T  # reuse generators

SC=T.SC; USAGE=T.USAGE
model=tf.keras.models.load_model(os.path.join(SC,"glyph_cnn.keras"))
Xva,Yva=T.build_dataset(200,999)
pred=model.predict(Xva,verbose=0).argmax(1)
acc=(pred==Yva).mean()
print(f"Fresh synthetic val accuracy: {acc*100:.2f}%  (n={len(Yva)})")

# Focus on the ㅓ-column glyphs ML Kit confuses with ㅣ/ㅗ/ㅜ.
print("\nPer-class recall on tricky glyphs:")
for ch in ["러","너","더","머","서","어","저","거"]:
    ci=USAGE.index(ch); mask=Yva==ci
    r=(pred[mask]==ci).mean()
    # what it confuses with
    wrong=pred[mask][pred[mask]!=ci]
    conf=""
    if len(wrong):
        vals,cnts=np.unique(wrong,return_counts=True)
        conf=" | confused→ "+", ".join(f"{USAGE[v]}×{c}" for v,c in sorted(zip(vals,cnts),key=lambda x:-x[1])[:3])
    print(f"  {ch}: recall={r*100:5.1f}%{conf}")

# TFLite parity check
interp=tf.lite.Interpreter(os.path.join(SC,"glyph_cnn.tflite")); interp.allocate_tensors()
inp=interp.get_input_details()[0]; out=interp.get_output_details()[0]
n=300; ok=0
for i in range(n):
    interp.set_tensor(inp["index"], Xva[i:i+1].astype(np.float32)); interp.invoke()
    if interp.get_tensor(out["index"]).argmax()==Yva[i]: ok+=1
print(f"\nTFLite accuracy on {n} val samples: {ok/n*100:.2f}% (parity with keras)")
