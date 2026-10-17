# Rosa app progress

## Done (confirmed on the phone)
- Stage 1: minimal app, GitHub Actions builds the APK, app installs and opens
- Stage 2: galaxy background, round avatar, breathing ring, Persian greeting
- Stage 3: typed chat with OpenRouter free models
- Stage 4: speech input in Persian and English (some recognition mistakes remain)
- Stage 6: app icon made from the avatar photo by tools/make_icons.py during the build

## Stage 5 (built, untested until confirmed on the phone)
- Microphone starts by itself while the app is on screen; recognizer is fully released when the app leaves the screen
- Wake phrase (Persian or English), on/off switch
- Not voice-specific: anyone saying the wake phrase activates her

## Next
- Allow-list of apps Rosa may open, voice commands (alarm, dial, SMS draft, music)
- Later: voice output with a female Persian voice, and a driving mode

## Files
settings.gradle.kts, build.gradle.kts, gradle.properties,
.github/workflows/build.yml, PROGRESS.md, tools/make_icons.py,
app/build.gradle.kts, app/src/main/AndroidManifest.xml,
app/src/main/java/com/roza/assistant/MainActivity.kt,
app/src/main/java/com/roza/assistant/OpenRouterClient.kt,
app/src/main/res/layout/activity_main.xml,
app/src/main/res/values/strings.xml, app/src/main/res/values/themes.xml,
app/src/main/res/drawable/ring.xml, bubble_user.xml, bubble_rosa.xml,
app/src/main/assets/README.txt plus the two images
