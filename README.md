# Planet Forge for Android

A WebView wrapper: the game page lives in `app/src/main/assets/index.html`.

## Build (pick one)
**A. Android Studio** – File > Open this folder, wait for sync, Build > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`.

**B. Command line** (JDK 17+, Android SDK with platform 34 + build-tools, `ANDROID_HOME` set):
    gradle assembleDebug        # or ./gradlew assembleDebug if the wrapper is present

**C. No install** – push this folder to a GitHub repo; Actions > "Build APK" > Run; download the artifact.

## Install
Copy `app-debug.apk` to the phone, open it, allow "install unknown apps". Or `adb install app-debug.apk`.

## Updating the game
    ./update_game.sh path/to/new/index_hud.html
then rebuild.

## Notes
- Back button: first press = Esc in game, second within 2 s = leave. Quit on the title screen closes the app.
- Phones are slower than PCs: lower the quality setting in the game's Settings if it stutters.
- Release signing: the release build uses the debug key so it sideloads; make your own keystore (`keytool -genkey ...`) before publishing to Play.
