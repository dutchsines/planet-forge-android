#!/bin/sh
# Copy a newer Planet Forge page into the app:   ./update_game.sh path/to/index_hud.html
# It adds a tiny shim so the page knows it is inside the app (Quit button, wallpaper key).
set -e
SRC="${1:-../index_hud.html}"
OUT="$(dirname "$0")/app/src/main/assets/index.html"
python3 - "$SRC" "$OUT" <<'PY'
import sys
src, out = sys.argv[1], sys.argv[2]
s = open(src, encoding="utf-8").read()
shim = '<script>window.pfApp={isApp:true,android:true,wallpaper:function(on){try{setWallpaper(!!on)}catch(e){}},recording:function(){},quit:function(){try{AndroidBridge.quit()}catch(e){window.close()}},saveUpdate:function(t,b){return AndroidBridge.saveUpdate(t,b)},applyUpdate:function(){AndroidBridge.applyUpdate()}};</script>'
assert "<head>" in s, "no <head> tag found"
open(out, "w", encoding="utf-8").write(s.replace("<head>", "<head>" + shim, 1))
print("wrote", out)
PY
