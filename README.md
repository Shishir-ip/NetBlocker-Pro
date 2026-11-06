<div align="center">

# 🛡️ NetBlocker Pro

**Per-app network firewall for Android — block internet, Wi-Fi or mobile data for any app. Works with or without root.**

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](#)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](#)
[![Release](https://img.shields.io/badge/release-v1.4.0-007AFF)](#)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

</div>

---

## 📖 About

**NetBlocker Pro** is a lightweight firewall that lets you cut network access for any app installed
on your device — completely, or only over **Wi-Fi** or only over **mobile data**.

It ships with **two blocking engines** and picks the best one automatically:

| Engine | Needs root? | Technique |
|---|---|---|
| 🔩 **Root / iptables** | ✅ Root | Per-UID kernel firewall rules (`-m owner --uid-owner … -j REJECT`) pushed through `su` — the DroidWall / AFWall+ technique. Zero overhead, enforced by the Linux kernel. |
| 📡 **VPN (no root)** | ❌ None | A local VPN whose per-app filter **excludes every allowed app** from the tunnel, while blocked apps' packets enter it and are **discarded**. No user-space TCP stack, no traffic forwarding — the kernel does the per-app routing. |

With **Automatic** mode (default): rooted phones get kernel-level blocking, and everyone else gets
the VPN engine — so **normal users can use the app too**. You can force either engine in Settings.

The UI is intentionally simple and follows the **iOS look & feel**: large titles, grouped rounded
cards, iOS-style switches and segmented controls, with full dark-mode support.

## ✨ Features

- 🔌 **Block internet entirely** for any app with one tap
- 📶 **Block Wi-Fi only** or **📱 block mobile data only** (long-press an app / detail sheet)
- 🔄 **Blacklist mode** (block selected apps) and **Whitelist mode** (allow only selected apps, block the rest)
- 🧠 **Dual engines with automatic selection** — root (iptables) or VPN (no root), switchable in Settings
- ⚡ **Quick Settings tile** — pull down the notification shade and toggle the **NetBlocker** tile:
  ON = blocked apps lose internet (VPN starts on unrooted phones), OFF = everything back to
  normal and the VPN disconnects
- 🪟 **Home screen widgets**:
  - **Protection toggle widget** (2×1, resizable) — tap to flip protection on/off with a live On/Off state
  - **Blocked apps widget** — resizable; the bigger it is, the more apps it shows. Header has an
    On/Off pill and a **"+"** button that opens a picker with a switch for every app; tapping a
    row removes that app from the list instantly
- 🔍 Instant **search** + filters for **All / User / System / Blocked** apps
- 🟢 Live **status card**: protection state, active engine, firewall rule state
- ♻️ **Apply rules on boot** (both engines — the VPN auto-starts after consent was granted)
- 🔔 Optional **persistent notification** while protection is active
- 🌗 **Automatic / Light / Dark** theme (true iOS dark palette)
- 💾 **Export / Import rules** as JSON (Storage Access Framework)
- 🧹 **Reset all rules** and **view live iptables rules** from Settings
- 🚫 **No tracking, no ads, works 100% offline**

## 🧠 How it works

### 🔩 Root engine (iptables)

Dedicated iptables chains are attached to `OUTPUT` and dispatch by output interface:

```
OUTPUT ──► NB_OUT ──o wlan+ / eth+──► NB_WIFI   (Wi-Fi / ethernet rules)
                 ──o rmnet+ / ccmni+──► NB_DATA (mobile data rules)
```

Each app runs under its own Linux **UID**, so rules like
`iptables -A NB_OUT -m owner --uid-owner 10123 -j REJECT`
block that app (and only that app) at the kernel level. In whitelist mode the chains `RETURN`
allowed UIDs and `REJECT` everything else.

### 📡 VPN engine (no root)

A local VPN (one-time system consent dialog) creates a tunnel that would normally capture all
traffic. NetBlocker Pro flips that filter:

- **Allowed apps** are added with `addDisallowedApplication()` → their traffic **bypasses the
  tunnel completely** and flows over the normal network, untouched.
- **Blocked apps** are *not* excluded → their packets enter the tunnel, where the service simply
  **reads and discards them**. No packets are ever forwarded — the app has no network.

Wi-Fi vs mobile-data rules are applied by **re-establishing the tunnel with a different exclusion
list whenever the default network type changes** (Wi-Fi ↔ mobile data). New app installs also
trigger a re-apply. The whole thing costs a few lines of kernel routing — there is no TCP stack,
no proxying and no traffic ever leaves through the app.

Turning **Protection off** (switch or Quick Settings tile) stops the service and tears the tunnel
down — the VPN disconnects immediately and every app goes back to normal.

### ⚡ Quick Settings tile

Add the **NetBlocker** tile from the notification shade (edit / pencil button) and flip it anytime:

- **Tile ON** → protection starts with your configured engine. On unrooted phones the local VPN
  comes up; if the system has not granted VPN permission yet (or Android blocks the background
  start), the app opens once to complete the setup — after that the tile works standalone.
- **Tile OFF** → rules are removed / the VPN disconnects — instant back-to-normal.

> The single master **Protection switch** lives on the Apps tab's status card. Settings intentionally
> does not duplicate it.

All toggle paths (app switch, tile, notification stop-action, boot restore) are verified against a
live Android 14 emulator: tunnel up on enable, tunnel torn down on disable, blocked apps' packets
captured inside the tunnel while allowed apps bypass it entirely.

> The single master **Protection switch** lives on the Apps tab's status card. Settings intentionally
> does not duplicate it.

> ℹ️ VPN-engine notes: Android shows a key 🔑 icon in the status bar while a VPN is active, and
> the VPN uses the system's single VPN slot. On Wi-Fi↔data switches blocked connections briefly
> re-negotiate.

## 📲 Installation

1. **Download** the latest APK from the [Releases](https://github.com/Shishir-ip/NetBlocker-Pro/releases) page.
2. **Install** it (enable *Install unknown apps* if prompted).
3. Flip the **Protection** switch on:
   - **Rooted phone** → grant superuser when asked → kernel firewall active.
   - **Normal phone** → accept the one-time **VPN connection request** → VPN firewall active.
4. Tap any app's switch to cut its internet. Done ✅

**Requirements:** Android 8.0+ (API 26). Root is *optional*.

## 🛠️ Build from source

```bash
git clone https://github.com/Shishir-ip/NetBlocker-Pro.git
cd NetBlocker-Pro
./gradlew assembleRelease
```

The project ships with a Gradle wrapper (Gradle 8.12) and builds with the Android Gradle Plugin 8.7.3 (JDK 17+).

### 🔑 Signing

Signing credentials are **not** part of this repository — the keystore lives outside the project
and is referenced through a gitignored `signing.properties` file:

1. Generate your own keystore (kept **outside** the repo, never committed):
   ```bash
   keytool -genkeypair -v -keystore C:/secrets/netblocker.jks \
     -alias netblocker -keyalg RSA -keysize 2048 -validity 10950
   ```
2. Copy `signing.properties.example` to `signing.properties` and fill in your paths/passwords.
3. `./gradlew assembleRelease` — done.

Without a `signing.properties`, release builds fall back to the debug keystore (for local builds
only). APKs are attached to [GitHub Releases](https://github.com/Shishir-ip/NetBlocker-Pro/releases)
and are never committed to the repository.

## 🗺️ Project structure

```
app/src/main/java/com/netblocker/pro/
├── App.kt                      # Application (theme + notification channel)
├── MainActivity.kt             # Tab navigation (Apps / Settings)
├── model/AppEntry.kt           # App + policy model
├── data/Prefs.kt               # Settings + per-UID rule storage
├── data/AppRepository.kt       # Installed-apps loader
├── firewall/Engine.kt          # Engine selection & orchestration (root/VPN)
├── firewall/RootShell.kt       # su shell runner
├── firewall/RuleApplier.kt     # iptables rule builder & applier (root engine)
├── firewall/VpnFirewallService.kt  # Local-VPN blocking engine (no root)
├── receiver/BootReceiver.kt    # Re-apply rules on boot (both engines)
├── receiver/ProtectionTileService.kt  # Quick Settings on/off tile
├── util/NotifHelper.kt         # Persistent protection notification
├── util/Backup.kt              # JSON export/import
└── ui/
    ├── AppsFragment.kt         # App list + status card + search/filters
    ├── AppAdapter.kt           # App rows (iOS grouped-list style)
    ├── AppDetailSheet.kt       # Per-app Wi-Fi / Data bottom sheet
    └── SettingsFragment.kt     # All settings (advanced options)
```

## ❓ FAQ

**Do I need root?**
No. Without root the app uses its local-VPN engine (a one-time consent dialog). With root you get
kernel-level iptables blocking instead — the engine is selected automatically, or force it in
**Settings → Blocking Method**.

**How is this different from a "VPN app" that routes my traffic?**
The VPN engine never forwards or proxies any traffic. It uses the VPN's per-app filter to keep
allowed apps *outside* the tunnel and simply discards blocked apps' packets inside it. Nothing is
sent to any server — everything happens inside your phone.

**Will this drain my battery?**
The root engine: no — pure kernel rules. The VPN engine: a small overhead (typically a few %),
same as any VPN-based firewall.

**My app still connects after being blocked!**
- Make sure **Protection is on** and the status card shows the engine as active.
- Open **Settings → Apply Rules Now**.
- Root engine: enable **Block IPv6** if the network uses IPv6.

**Why do connections stutter when I switch between Wi-Fi and mobile data?**
The VPN engine re-establishes its tunnel with a different per-app exclusion list on network
changes — blocked connections re-negotiate for a moment. Allowed apps are unaffected.

## ⚖️ Disclaimer

NetBlocker Pro is provided as-is, for educational and personal use. Blocking system components
from the network can cause unexpected behavior — use common sense. The developer is not
responsible for lost connectivity, broken apps, or any other damage.

## 📄 License

Released under the [MIT License](LICENSE).
