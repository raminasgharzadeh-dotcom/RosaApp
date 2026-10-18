# Rosa app progress

## Done (confirmed on the phone)
- Stage 1: minimal app, GitHub Actions builds the APK, app installs and opens
- Stage 2: galaxy background, round avatar, breathing ring, Persian greeting
- Stage 3: typed chat with OpenRouter free models
- Stage 4: speech input in Persian and English (some recognition mistakes remain)
- Stage 5: on/off switch and wake phrase work, but every restart of the recognizer beeps (to be replaced by a silent offline wake word)
- Stage 6: app icon made from the avatar photo by tools/make_icons.py during the build
- Stage 7a: open allowed apps, alarm, timer, date, time, navigation work

## Stage 7b (built, untested until confirmed on the phone)
- Playing from Spotify or Music now sends the key to that app only (the first version played in the wrong app)
- Volume commands with a step count, for example "صدا را ۳ بار کم کن"

## Next
- Contacts lookup and calls through the Phone app
- Silent offline wake word (Vosk)
- Weather (Open-Meteo), WhatsApp video call, voice output with a female Persian voice, reading Google Messages

## Files
settings.gradle.kts, build.gradle.kts, gradle.properties,
.github/workflows/build.yml, PROGRESS.md, tools/make_icons.py,
app/build.gradle.kts, app/debug.keystore, app/src/main/AndroidManifest.xml,
app/src/main/java/com/roza/assistant/MainActivity.kt,
app/src/main/java/com/roza/assistant/OpenRouterClient.kt,
app/src/main/java/com/roza/assistant/PersianUtils.kt,
app/src/main/java/com/roza/assistant/AllowedApps.kt,
app/src/main/java/com/roza/assistant/CommandRouter.kt,
app/src/main/java/com/roza/assistant/Actions.kt,
app/src/main/res/layout/activity_main.xml,
app/src/main/res/values/strings.xml, app/src/main/res/values/themes.xml,
app/src/main/res/drawable/ring.xml, bubble_user.xml, bubble_rosa.xml,
app/src/main/assets/README.txt plus the two images
