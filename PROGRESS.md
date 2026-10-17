# Rosa app progress

## Done (confirmed on the phone)
- Stage 1: minimal app, GitHub Actions builds the APK, app installs and opens
- Stage 2: galaxy background, round avatar, breathing ring, Persian greeting
- Stage 3: typed chat with OpenRouter free models

## Stage 4 (built, untested until it is confirmed on the phone)
- Mic button: speech recognition in Persian or English, result is sent automatically
- Language button switches between Persian and English
- Workflow now reuses the same debug signing key between builds
- No voice output yet

## Files
settings.gradle.kts, build.gradle.kts, gradle.properties,
.github/workflows/build.yml, PROGRESS.md,
app/build.gradle.kts, app/src/main/AndroidManifest.xml,
app/src/main/java/com/roza/assistant/MainActivity.kt,
app/src/main/java/com/roza/assistant/OpenRouterClient.kt,
app/src/main/res/layout/activity_main.xml,
app/src/main/res/values/strings.xml, app/src/main/res/values/themes.xml,
app/src/main/res/drawable/ring.xml, bubble_user.xml, bubble_rosa.xml,
app/src/main/assets/README.txt plus the two images
