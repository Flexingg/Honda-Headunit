# Hacking the 2016 Civic Display Audio head unit

Consolidated notes from community research (XDA, CivicX, autohack.org, the
`librick/ic1101` project) plus our own findings. This is for getting deeper
access to **your own vehicle's** head unit — to sideload apps, root, and
eventually reach the factory APIs that gate HVAC/camera/media so this
launcher can stop working around them.

**Risk first:** several steps below can soft- or hard-brick the unit.
Keep a known-good stock OTA package for your exact firmware version before
flashing anything, and read the "Brick traps" section before touching
recovery or `/cache`.

## Hardware / platform

- SoC: **NVIDIA Tegra 3** (quad-core ARM Cortex-A9, ARMv7, c. 2012)
- OS: **Android 4.2.2 (Jelly Bean)**, API 17
- UI framework: Mitsubishi's **"Andromeda"** fork of AOSP — not Clarion,
  not Garmin. Guides written for Pilot/Accord Clarion units (e.g.
  autohack.org's browser-based method) target different hardware and may
  not transfer directly.
- Screen: 800×480

## Layer 1 — Engineering / developer menu

Reported button combo (accessory mode): hold **MENU + Day/Night + POWER**
for ~5 seconds to reach a Diagnostic/Engineering menu.

Alternate path reported for some units: in Android Settings, tap the build
number 7×, then tap the top four items in order **1, 4, 2, 3** to reveal
Developer Options.

Inside developer options / the engineering menu:
- Enable **USB debugging**.
- The **"Honda Permission" app** whitelists sideloaded packages by
  *signature*, not filename, on US units — add an allow-install rule here
  or sideloading stays blocked even with debugging on.

Exact combos and menu layouts are **firmware-version-dependent** and
reported inconsistently across threads — treat as a starting point to
verify against your own unit, not a guarantee.

## Layer 2 — ADB

- Requires USB debugging on (Layer 1) **and** the USB port role flipped
  from **Host to Device** (there's a setting for this; it's normally Host
  so the unit can read phones/USB drives).
- Connect an **A-to-A USB cable** to the front Android Auto / CarPlay
  port.
- **ADB listens on port 5000 on this unit, not the default 5555.**
  ```
  adb connect <host-unit-ip>:5000      # if network adb is reachable
  adb devices                           # over USB it should just show up
  ```
- Non-root ADB (install, pull, logcat, `am`/`pm`) works before rooting.
  Remember to flip USB role back to **Host** afterward or Android
  Auto/CarPlay stops working.

Recon once ADB works (ties into the probe app below):
```
adb shell getprop ro.build.fingerprint
adb shell getprop ro.secure
adb shell getprop ro.debuggable
adb shell getenforce
adb shell cat /proc/version
adb shell mount | grep system
adb shell pm list packages -f
adb shell service list
```

## Layer 3 — Root (HondaHack)

- Community one-click tool: `HondaHack.exe` (Windows) or `HondaHack.apk`.
  Requires the Host→Device USB flip from Layer 2.
- Antivirus will flag it as a hacktool; that's expected for this class of
  tool, verify the source before running anything downloaded.
- **Reliability is mixed and firmware-version-dependent.** Reports include:
  - dirtycow-based variants reportedly **block future rooting** on the
    same unit once applied.
  - The Civic's firmware lacked a `factory_reset.sh` that an
    Accord/Pilot-derived method depended on — that route failed outright
    on Civic units.
  - Bootloops reported after running "nefarious" repack scripts bundled
    with some tool versions.
- Treat as the fallback, not the first choice — Layer 4 is more
  reliable and better documented against this exact SoC.

## Layer 4 — OTA update signing weakness (preferred root path)

Documented against this exact Tegra 3 / Andromeda platform by the
`librick/ic1101` project (https://github.com/librick/ic1101).

**The pipeline:**
1. `daupdater` is a disabled init service, started explicitly by a
   patched `libsurfaceflinger.so` on every full boot.
2. It scans a FAT32 USB drive for an update trigger file, validates
   package metadata, and if checks pass, writes a recovery command and
   reboots into recovery.
3. A modified AOSP 4.2.2 recovery does Honda-specific checks, then applies
   the package.
4. A separate app-level path, `SystemUpdateApService`, has stricter
   version checks and is mostly out of scope for exploitation.

**The weakness:** the only cryptographic check in the main path is
recovery's signature verification — against the **publicly known AOSP
test key**. A package signed with that test key passes. `daupdater`
itself doesn't verify installed-vs-package metadata consistency and its
version checks aren't security controls. Net effect: **front-USB access +
power to the unit ⇒ arbitrary code execution**, without needing the
HondaHack binary at all.

**Tooling** (from `ic1101`):
- `ota-builder` — builds a test-key-signed update package.
- `apk-rebuilder` — unpacks an update into smali, repacked APKs, and
  ramdisk contents.
- `aidl-rebuilder` — extracts the AIDL interfaces the Mitsubishi
  system apps/services expose. **This is the lead for HVAC/camera/media
  access** — the factory climate app talks to the vehicle bus through one
  of these interfaces.

### Brick traps — read before flashing anything

- The staged recovery command (and a version file) land in **`/cache`**
  (ext4, on flash) — **this persists across a battery disconnect.** A bad
  staged command will keep re-triggering recovery after you've pulled the
  battery. Clear/repair `/cache` from a working shell before disconnecting
  power if a flash goes wrong.
- `daupdater` only checks that the package's build ID *differs* from the
  installed one and that rom-type matches — it does **not** verify the
  package actually installed. The unit's own version string is **not**
  bumped by these updates, so leaving the USB drive connected after a
  successful flash can cause **boot loops** (it keeps re-triggering).
  Remove the USB drive once the update applies.
- Recovery enforces a rom-type/"release-keys" consistency check
  independently of `daupdater`. A mismatch here can leave the unit stuck
  in a **recovery loop**.
- Keep the stock signed OTA for your exact firmware version as the
  recovery path — a mis-signed or malformed custom package is the single
  highest-risk step in this whole chain.

## Layer 5 — Reaching HVAC / camera / media (the actual goal)

No public write-up documents the head-unit-side HVAC API for this exact
Tegra 3 unit. What's known:

- Honda vehicles run two CAN networks, **F-CAN** and **B-CAN**. HVAC sits
  on B-CAN with proprietary (non-OBD-standard) message formats — expect to
  reverse-engineer frame formats, not find documentation.
- The head unit does not talk to CAN directly; a companion module/MCU
  bridges it. Other Honda models have bridges exposed as a single-wire
  UART stream from the HVAC micro (one owner built an MCU that converts
  this to RS232-over-USB) or CANBOX-style aftermarket interfaces at
  ~38400 baud — but those bypass the head unit entirely, which isn't what
  we want (we want the *head unit* to be able to issue the commands).
- **The real lead for our case:** run `aidl-rebuilder` against a pulled
  `/system` image, find the AIDL interface(s) the factory climate app
  binds to, then call that service from our own code instead of going
  around the head unit. Camera-overlay and media `metachanged` dependency
  likely have similar internal-service answers once system apps are
  reversed with `jadx`/`apktool`.

### How this maps to this launcher's README limits

| README limit | Unlock path |
|---|---|
| HVAC (no app API) | root → `aidl-rebuilder` on vehicle-service/climate system app → bind the internal AIDL service from `ObdService` or a new service |
| Rear camera overlay | likely leave to factory — low value, well-handled already |
| Maps stuck on old APK | root removes the signature-whitelist restriction entirely |
| Media needs `metachanged` broadcast | root/Xposed → read player state directly (`MediaSessionManager`-equivalent on 4.2 predates that API, so likely IPC/binder hook) instead of relying on broadcasts |

## Next steps in this repo

- `ProbeActivity` / `ProbeService` (being added) — run after Layer 2 ADB
  access to dump installed packages, registered services, open sockets,
  and `/dev` nodes, to find the HVAC/vehicle-service channel without
  needing a full `/system` pull first.
- Once root is available: pull `/system/app` and `/system/priv-app`,
  run `jadx`/`apktool` + `ic1101`'s `aidl-rebuilder` against the climate
  and vehicle-service APKs.

## Sources

- https://xdaforums.com/t/guide-how-to-enter-developer-mode-on-2017-honda-civic-and-now-root-install-apps.3621582/
- https://xdaforums.com/t/2016-honda-civic-android-based-headunit-north-american-version.3416177/
- https://www.civicx.com/forum/threads/how-to-establish-an-adb-connection-us-models.12479/
- https://www.civicx.com/forum/threads/android-head-unit-developer-mode.14491/
- http://www.autohack.org/vehicles/pilot/ (Clarion/Pilot variant — different hardware)
- https://github.com/librick/ic1101
- https://github.com/librick/ic1101/blob/main/docs/updates.md
- https://www.hondata.com/forum/viewtopic.php?t=21099
- https://juniperspring.org/posts/honda-reverse-engineering/
- https://driveaccord.net/forums/154-audio-electronics-lighting/352593-made-head-unit-8-display-dual-climate.html
