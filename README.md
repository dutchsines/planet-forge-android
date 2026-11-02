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

## Live wallpaper
Open the in-game Menu (☰) and tap "Set as live wallpaper", or pick Planet Forge in Android's Wallpaper settings. It runs the game in a hidden WebView at the lightest graphics and about 15 frames a second (experimental: some phones may show black or run slowly).

## Notes
- Back button: first press = Esc in game, second within 2 s = leave. Quit on the title screen closes the app.
- Phones are slower than PCs: lower the quality setting in the game's Settings if it stutters.
- Release signing: the release build uses the debug key so it sideloads; make your own keystore (`keytool -genkey ...`) before publishing to Play.

## Autosave, real time and notifications (v1.1)
- The planet autosaves (seed, age, speed, minds) every few seconds and when you leave; reopening gives you the same planet, caught up with the time that passed.
- The phone starts in **Real time** (the planet turns once a day, which costs almost nothing). History moves at the **Away pace** (Menu → More: Slow 0.5 / Normal 1 / Fast 3 Gyr per day, or Off). The other speeds still work.
- Every ~15 minutes a tiny job (no graphics) winds the planet forward and posts a notification for each **new era** and for **intelligent life**. Turn it off with Menu → More → Notifications. Android 13+ asks for notification permission on first launch.
- Android may delay the job in battery saver / Doze; the planet always catches up when you open the app.
