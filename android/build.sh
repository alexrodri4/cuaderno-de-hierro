#!/usr/bin/env bash
# Compila FOKUS.apk a partir de ../web (Linux o WSL; requiere java 17+ y python3)
set -e
cd "$(dirname "$0")"
T=tools; ROOT=..
rm -rf build && mkdir -p build/classes build/dex build/gen build/assets
cp -r $ROOT/web/img $ROOT/web/fonts build/assets/
python3 - <<'EOF'
s=open('../web/index.html',encoding='utf-8').read()
def rep(a,b):
  global s
  assert s.count(a)==1,a[:50]; s=s.replace(a,b)
rep("mode==='local'?'Guardado solo en este navegador'","mode==='local'?'Guardado en este móvil'")
rep("render();__splashStep(70);\n","""window.__cdhBack=function(){
  try{
    if(typeof kb!=='undefined'&&kb){closeKb(true);return true}
    if(typeof kp!=='undefined'&&kp){closePad(true);return true}
    if(typeof ai!=='undefined'&&ai.open){aiClose();return true}
    if(typeof picker!=='undefined'&&picker){closePicker();return true}
    if(!sheet.hidden){closeSheet();return true}
    if(ui.tab!=='hoy'){ui.tab='hoy';ui.confirm=null;window.scrollTo(0,0);render();return true}
  }catch(e){}
  return false;
};
render();__splashStep(70);
""")
html='<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#080C0E"><style>:root{--sat:0px!important;--sab:0px!important}:root{color-scheme:dark}body{margin:0;font:14px system-ui,-apple-system,sans-serif;background:#080C0E}img{max-width:100%}[hidden]{display:none!important}</style></head><body>'+s+'</body></html>'
open('build/assets/index.html','w',encoding='utf-8').write(html)
EOF
chmod +x $T/aapt2_64
$T/aapt2_64 compile --dir proj/res -o build/res.zip
$T/aapt2_64 link -o build/base.apk -I $T/android.jar --manifest proj/AndroidManifest.xml -A build/assets build/res.zip --java build/gen --min-sdk-version 24 --target-sdk-version 35
javac --release 11 -nowarn -Xlint:none -cp $T/android.jar -d build/classes $(find proj/src build/gen -name '*.java') 2>&1 | grep -v "Picked\|^Note" > build/javac.log || true; if grep -q error build/javac.log; then cat build/javac.log; exit 1; fi
java -cp $T/d8.jar com.android.tools.r8.D8 --release --min-api 24 --lib $T/android.jar --output build/dex $(find build/classes -name '*.class') 2>&1 | grep -v Picked || true
python3 - <<'EOF'
import zipfile
src=zipfile.ZipFile('build/base.apk');out=zipfile.ZipFile('build/aligned.apk','w')
entries=[(i,src.read(i.filename)) for i in src.infolist()]
entries.append((zipfile.ZipInfo('classes.dex',date_time=(2026,9,28,0,0,0)),open('build/dex/classes.dex','rb').read()))
for info,data in entries:
  zi=zipfile.ZipInfo(info.filename,date_time=info.date_time if info.date_time[0]>=1980 else (1980,1,1,0,0,0))
  stored=info.filename=='resources.arsc' or info.filename.endswith('.png')
  zi.compress_type=zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED;zi.extra=b''
  if stored:
    off=out.fp.tell()+30+len(zi.filename.encode());zi.extra=b'\0'*((-off)%4)
  out.writestr(zi,data)
out.close()
EOF
PW="${CDH_KS_PASS:-$(grep -m1 'clave:' "$ROOT/signing-key-info.txt" | sed 's/.*: *//' | tr -d '\r')}"  # contraseña: variable CDH_KS_PASS o signing-key-info.txt (fuera de git)
java -jar $T/apksigner.jar sign --ks $ROOT/cuaderno-hierro.keystore --ks-key-alias cuaderno --ks-pass pass:$PW --key-pass pass:$PW --min-sdk-version 24 --out $ROOT/FOKUS.apk build/aligned.apk 2>&1 | grep -v Picked || true
rm -f $ROOT/FOKUS.apk.idsig
rm -rf build
java -jar $T/apksigner.jar verify --print-certs $ROOT/FOKUS.apk 2>&1 | grep "SHA-256"
$T/aapt2_64 dump badging $ROOT/FOKUS.apk | head -1
echo "OK -> FOKUS.apk"
