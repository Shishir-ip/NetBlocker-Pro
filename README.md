<div align="center">

# 🛡️ NetBlocker Pro

**Per-app network firewall for Android — block internet, Wi-Fi or mobile data for any app. No VPN.**

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](#)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](#)
[![Release](https://img.shields.io/badge/release-v1.0.0-007AFF)](#)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

</div>

---

## 📖 About

**NetBlocker Pro** is a lightweight, root-based firewall that lets you cut network access for any app
installed on your device — completely, or only over **Wi-Fi** or only over **mobile data**.

Instead of a VPN, it uses **per-UID `iptables` rules pushed through `su`** — the same technique
used by classic Android firewalls (DroidWall / AFWall+). This means **zero VPN slot usage, zero
traffic routing overhead, and no reconnection quirks** — rules are enforced directly by the Linux kernel.

The UI is intentionally simple and follows the **iOS look & feel**: large titles, grouped rounded
cards, iOS-style switches and segmented controls, with full dark-mode support.

## ✨ Features

- 🔌 **Block internet entirely** for any app with one tap
- 📶 **Block Wi-Fi only** or **📱 block mobile data only** (long-press an app / detail sheet)
- 🔄 **Blacklist mode** (block selected apps) and **Whitelist mode** (allow only selected apps, block the rest)
- 🔍 Instant **search** + filters for **All / User / System / Blocked** apps
- 🟢 Live **status card**: protection state, root availability, firewall rule state
- ♻️ **Apply rules on boot** (auto re-apply after restart)
- 🔔 Optional **persistent notification** while protection is active
- 🌐 Optional **IPv6 blocking** (ip6tables)
- 🧩 **System apps allowed by default in whitelist mode** so system services never break
- 💾 **Export / Import rules** as JSON (Storage Access Framework)
- 🧹 **Reset all rules** and **view live iptables rules** from Settings
- 🌗 **Automatic / Light / Dark** theme (true iOS dark palette)
- 🚫 **No VPN service, no tracking, no internet permission abuse** — works 100% offline

## 🧠 How it works

NetBlocker Pro creates dedicated iptables chains and attaches them to the `OUTPUT` chain:

```
OUTPUT ──► NB_OUT ──o wlan+ / eth+──► NB_WIFI   (Wi-Fi / ethernet rules)
                 ──o rmnet+ / ccmni+──► NB_DATA (mobile data rules)
```

Each app runs under its own Linux **UID**, so rules like
`iptables -A NB_OUT -m owner --uid-owner 10123 -j REJECT`
block that app (and only that app) on the kernel level. In whitelist mode the chains `RETURN`
allowed UIDs and `REJECT` everything else.

> ⚠️ **Root (superuser) access is required.** This is the only reliable non-VPN way to enforce
> per-app network blocking on Android. The app will ask your root manager (Magisk / KernelSU /
> SuperSU) for permission on first use.

## 📲 Installation

1. **Download** the latest APK from the [Releases](https://github.com/Shishir-ip/NetBlocker-Pro/releases) page.
2. **Install** it (enable *Install unknown apps* if prompted).
3. **Grant root** to NetBlocker Pro when your superuser manager asks.
4. Flip the **Protection** switch on → done. Tap any app's switch to cut its internet.

**Requirements:** Android 8.0+ (API 26), rooted device (Magisk / KernelSU / SuperSU).

## 🛠️ Build from source

```bash
git clone https://github.com/Shishir-ip/NetBlocker-Pro.git
cd NetBlocker-Pro
./gradlew assembleRelease
```

The project ships with a Gradle wrapper (Gradle 8.12) and builds with the Android Gradle Plugin 8.7.3 (JDK 17+).

### 🔑 Signing

A development keystore is included at `keystore/netblocker-release.jks` so the project builds
out of the box:

| Property      | Value              |
|---------------|--------------------|
| Keystore      | `keystore/netblocker-release.jks` |
| Store password| `NetBlocker2025!`  |
| Key alias     | `netblocker`       |
| Key password  | `NetBlocker2025!`  |

> ⚠️ This is fine for this open-source hobby project, but **never commit a real production keystore**.
> For your own production builds, generate a private keystore and keep it secret.

## 🗺️ Project structure

```
app/src/main/java/com/netblocker/pro/
├── App.kt                      # Application (theme + notification channel)
├── MainActivity.kt             # Tab navigation (Apps / Settings)
├── model/AppEntry.kt           # App + policy model
├── data/Prefs.kt               # Settings + per-UID rule storage
├── data/AppRepository.kt       # Installed-apps loader
├── firewall/RootShell.kt       # su shell runner
├── firewall/RuleApplier.kt     # iptables rule builder & applier
├── receiver/BootReceiver.kt    # Re-apply rules on boot
├── util/NotifHelper.kt         # Persistent protection notification
├── util/Backup.kt              # JSON export/import
└── ui/
    ├── AppsFragment.kt         # App list + status card + search/filters
    ├── AppAdapter.kt           # App rows (iOS grouped-list style)
    ├── AppDetailSheet.kt       # Per-app Wi-Fi / Data bottom sheet
    └── SettingsFragment.kt     # All settings (advanced options)
```

## ❓ FAQ

**Is root really required?**
Yes. Android simply does not allow an ordinary app to intercept or filter other apps' traffic
without a VPN. NetBlocker Pro avoids the VPN approach entirely and therefore needs `su` to install
kernel-level iptables rules.

**Will this drain my battery?**
No. Once the rules are installed, enforcement happens in the Linux kernel. The app can be closed
afterwards.

**What's the difference between blocking Wi-Fi and mobile data?**
Wi-Fi rules match traffic leaving through `wlan+`/`eth+` interfaces; mobile-data rules match
`rmnet+`/`ccmni+` interfaces. You can block either one, both, or everything.

**My app still connects after being blocked!**
- Make sure **Protection is on** and the status card shows *Firewall active*.
- Open **Settings → Apply Rules Now**.
- Enable **Block IPv6** if the network uses IPv6.

## ⚖️ Disclaimer

NetBlocker Pro is provided as-is, for educational and personal use. Blocking system components
from the network can cause unexpected behavior — use common sense. The developer is not
responsible for lost connectivity, broken apps, or any other damage.

## 📄 License

Released under the [MIT License](LICENSE).
