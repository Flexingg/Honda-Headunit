# Civic Launcher

Glass-style, ColorOS-inspired home screen for the **2016 Honda Civic Display Audio** (Android 4.2.2, API 17, 800×480).

## Build
Open this folder in Android Studio (it creates the Gradle wrapper), then **Build → Build APK(s)**,
or run `gradle wrapper && ./gradlew assembleRelease`. The release APK is R8-shrunk and has no libraries besides the Kotlin stdlib.

## Install
Sideload the APK with your Honda hack, press Home, pick **Civic** and choose "Always".

## What it does
- Home: clock/date, now playing with prev/play/next, Maps card with Home/Work navigation, live car tile, phone, app drawer
- Car stats: speed, RPM, coolant, intake, throttle, load, fuel, battery voltage from a Bluetooth ELM327 adapter
- Settings: OBD adapter, mph/km/h, 3 themes, saved addresses

## Limits on this head unit
- **HVAC**: the climate strip talks to the climate module over the car's B-CAN bus. The head unit gives apps no way to send commands, so the launcher can't control HVAC.
- **Rear camera**: the factory firmware shows it automatically in reverse, on top of any app. Leave it to the factory.
- **Maps**: the installed Google Maps must be an old version that still runs on 4.2. The launcher just sends the navigation request to it.
- **Media**: track info depends on the player sending the old "metachanged" broadcasts. Spotify only does this when *Device Broadcast Status* is turned on in its settings.

## Lightweight by design
- No AndroidX and no XML layouts. Screens are built in code.
- The background is blurred once with RenderScript. Each glass panel then reuses that blurred image at its own position, so the blur costs nothing per frame.
- OBD polling runs on one low-priority thread. Animations only run when you touch something or a value changes.
