# BlindNav Android prototype (v0.3)

**Assistive only. Not a substitute for a white cane or guide dog. Not safety-certified, not field-validated.**
Everything here compiled and its unit tests pass, but the app has **never been run on a phone**. Expect to find bugs when you test.

## Install and run
1. Easiest: open this folder in Android Studio and press Run with your phone connected (USB debugging on, Android 12+).
2. Or install the APK built here: `app/build/outputs/apk/debug/app-debug.apk` (about 149 MB, debug build).
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Rebuild from a terminal: `gradlew.bat :app:assembleDebug` (needs JDK 17+ and the Android SDK; `local.properties` points at `C:/android-sdk`). The wrapper itself was generated but never run here; I built with a separately downloaded Gradle 8.9 at `C:\gradle-8.9`.
   On this PC Gradle only runs with JDK 21 plus `-Dsun.nio.ch.PipeImpl.noUnixDomainSockets=true` in `JAVA_TOOL_OPTIONS`; JDK 17 fails with "Unable to establish loopback connection".
4. Backend (needed for describe, ask, navigation, Kannada/Telugu reading, SOS texts): see `../backend/README.md`. In the app: Settings > Server address, then turn on Cloud features.

## What works (built)
| Feature | How | Needs internet? |
|---|---|---|
| Obstacle warnings | Camera → EfficientDet-Lite0 → rules in `core/HazardEngine.kt`. Tone + vibration first, then a short English phrase | No |
| Watchdog / dark | Says "Obstacle detection stopped" if frames stall; says "too dark" instead of staying silent | No |
| Voice commands | Tap "Speak a command". Fixed phrases in English, Hindi, Kannada, Telugu; anything else is a question | Depends on phone speech engine |
| Read text | English and Hindi on-device (ML Kit). Kannada and Telugu via cloud OCR | Kannada/Telugu only |
| Describe / ask | Photo + question to your server's AI. Keeps the last 3 exchanges | Yes |
| Currency | On-device classifier **if you train and add `currency.tflite`**; otherwise a cloud guess; always says "confirm by feel" | Optional |
| Navigation | Voice destination → place search → walking route → spoken turns, weak-GPS warning, off-route warning, arrival | Yes |
| SOS | 5 s countdown with cancel (voice, any volume key, or button). Then server text to saved contacts (if online) and a phone call to the first contact | Texts only |
| Triple volume-down = SOS | Works only while the app is open and the screen is on (not in the background) | No |
| Background guidance | Foreground service + notification; guidance and navigation continue with the screen off | No / Yes for navigation |
| Settings / privacy | Language, speech speed, cloud on/off (default **off**), server address, emergency contacts, delete all data | No |
| Speech priority | SOS > hazard > your request > navigation > ambient; lower never cuts off higher | No |

## Tests
`gradlew.bat :app:testDebugUnitTest`: 16 tests (hazard rules, route following, commands, script detection, SOS countdown, key presses). All pass.
Backend: `python -m pytest -q` in `../backend`: 13 tests, all pass (AI and SMS providers are mocked).
**Not tested:** anything that needs a phone, camera, microphone, GPS, speech engine, or the real AI/SMS/routing services.

## Not done, and known problems
- **Currency model is not included.** Train one with `../tools/train_currency.py` (untested script) and photos of real notes. Until then currency uses a cloud guess or says it is unavailable.
- **Hazard detection is limited:** COCO classes only. It will not recognise poles, potholes, open drains, stairs, auto-rickshaws, glass, or anything unknown. "Proximity" is how much of the frame an object fills, not metres. Thresholds in `HazardConfig` are uncalibrated guesses: tune them on your phone.
- **Background running (new, untested on a phone):** a foreground service keeps obstacle guidance (camera) and navigation (GPS) going with the screen off or the app in the background, with a permanent notification and a "Stop guidance" button. It must be started while the app is on screen, and it is **not restarted** if Android kills it (the app then says "Obstacle guidance stopped. Use your cane" if it can). Some phone makers kill background apps aggressively: exempt BlindNav from battery optimisation. Still **not** working with the screen off: triple volume-down SOS, the "Speak a command" button, and reading/describe (they need the screen).
- **Navigation stops if you close the app screen** (swipe it away); only guidance survives that.
- **SOS limits:** the app cannot send SMS by itself offline (Google Play restricts that permission). Offline it only calls the first contact. Text alerts need the server, internet, and a Twilio account. Triple-press needs the app open. Test it with a contact who knows.
- **Languages:** hazard phrases are English only. Other speech needs the matching voice installed in the phone's text-to-speech settings; if it's missing the app says so and falls back to English. Offline speech recognition for Kannada/Telugu is unconfirmed and often needs internet.
- **Security:** debug build, HTTP to your server is allowed (`usesCleartextTraffic`). The server login is anonymous with no device attestation. Fine for a test, not for release.
- **Not validated** in the dark, rain, glare, crowds, or with blind users or an orientation-and-mobility instructor. Do that before relying on it for anything.
- Model licence: `efficientdet_lite0.tflite` (Google MediaPipe bucket) licence is unconfirmed (third-party sources say Apache-2.0).

## First things to test (in a safe place, with a sighted helper)
1. Home screen with TalkBack on: can every button be reached and understood?
2. Live guidance indoors: does a person, chair, door, wall trigger? Count false alarms per minute. Check heat and battery after 10 minutes.
3. Tune `HazardConfig.urgentArea/cautionArea` and `LiveGuidance.DARK_LUMA`.
4. Read text (English, Hindi) with the server off, then Kannada/Telugu with it on.
5. Triple volume-down, then cancel by pressing a volume key. Then let one fire with a friend as the contact.
6. Navigation to a nearby place outdoors; compare spoken distances with reality.
7. Start guidance, turn the screen off, walk 5 minutes: do alerts continue? Does the notification stay? Does it survive 15+ minutes (battery optimisation)? Use the notification's Stop button and swipe the app away: does guidance behave as described?
