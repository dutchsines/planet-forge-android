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

## Phone mode, news and photos (build 2026110422+)

* **Phone mode**: inside the app the game always starts from a light phone profile (it ignores older saved graphics settings). More menu → *Graphics* cycles Lite / Balanced / Pretty. No live blur over the canvas, 30 fps cap, 60 Hz display request.
* **Background world**: every ~15 minutes the app (without opening) winds the planet forward and posts playful notifications: new eras, minds and super minds, wars and empires, supervolcanoes, impacts, extinctions, moon bases, starships. Big moments use a separate high-priority channel ("Big moments").
* **World photos**: every 3 / 6 / 12 / 24 hours (More menu → *World photos*) the app opens the page off-screen once, takes a picture of the planet and posts it with a few facts. If graphics can't start in time, the facts are posted without the picture.
* **Permissions**: notifications, run-in-background (battery optimisation exemption, asked once; also More menu → *Allow background running*), internet (only for the in-game updater).
* The live wallpaper is unchanged (it never uses phone mode).

## Live wallpaper (rewritten) and quieter notifications (build 2026110423+)

* The wallpaper no longer runs the game in a hidden WebView (that drew black and starved the app). More menu -> *Set as live wallpaper* makes the game record 36 pictures of your planet turning (you see it spin for a few seconds), then opens Android's wallpaper preview. The wallpaper plays them back with cross-fades, about one turn every 90 s, and never shows black (it falls back to stars and a simple planet). The background photo job refreshes the set every few hours.
* Notifications: one notification at a time (a new one replaces the last), at most 6 a day, 45 minutes apart (15 for big moments), the same headline at most every 6 hours, silent channels (big moments are normal priority). More menu -> *Notifications* cycles all news / big moments only / off. The background check only runs while the screen is off and the battery is not low, every 30 minutes, and never works for more than ~7 s per run.
