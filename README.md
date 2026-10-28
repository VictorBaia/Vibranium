<div align="center">

<img src="Logo.png" alt="Vibranium logo" width="150" />

# Vibranium

### Music, your way. Everywhere.

A colorful, open source music player built on [BitChord](https://github.com/kushagrasinghx/BitChord).

<p>
  <a href="https://github.com/VictorBaia/Vibranium/releases"><img alt="Releases" src="https://img.shields.io/github/v/release/VictorBaia/Vibranium?style=for-the-badge&label=release&color=8b5cf6" /></a>
  <a href="LICENSE"><img alt="GPL-3.0 license" src="https://img.shields.io/badge/license-GPL--3.0-6366f1?style=for-the-badge" /></a>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-ec4899?style=for-the-badge&logo=kotlin&logoColor=white" />
</p>

[Explore features](#-what-you-can-do) · [Get Vibranium](#-get-vibranium) · [Build it](#-build-from-source) · [Contribute](#-contributing)

<img src="Banner.png" alt="Vibranium app banner" width="100%" />

</div>

> [!NOTE]
> Vibranium is an independent fork of BitChord. It is not affiliated with YouTube, Google, Spotify, Apple, or other music services.

## ✨ What you can do

| Listen | Make it yours |
| :--- | :--- |
| Search and play music from YouTube Music; listen to local files and configured library sources. | Enjoy artwork driven colors, animated canvas, synced lyrics, and a translucent interface. |
| Download for offline listening and manage playback in the background. | Tune audio quality, playback speed, crossfade, sleep timer, and equalizer settings. |
| Connect supported services and sources, including Spotify, Navidrome, WebDAV, and SMB. | Use scrobbling, Discord Rich Presence, listening stats, and Replay. |

Some features require an account, a compatible server, or additional configuration. Source availability and playback behavior depend on the service you connect.

## 📱 Where it runs

| Platform | Project area | Learn more |
| :--- | :--- | :--- |
| Android phone | `app/` | [Android builds](#-build-from-source) |
| Wear OS | `wearos/` | [Watch setup](wearos/README.md) |
| Android TV / Google TV | `tv/` | [TV build](tv/README.md) |
| Windows, macOS, Linux | `desktopApp/` | [Desktop guide](DESKTOP.md) |
| Web player and browser extension | `web/` | [Web guide](web/README.md) |

## ⬇️ Get Vibranium

Check the [Vibranium releases](https://github.com/VictorBaia/Vibranium/releases) for published builds and their installation notes. Available packages can vary by release. For desktop installation and platform specific requirements, see the [desktop guide](DESKTOP.md).

> [!IMPORTANT]
> Download builds only from a source you trust. The Android app may require permission to install apps from your browser or file manager.

## 🛠️ Build from source

For Android, use **JDK 17** and the Android SDK. The project currently compiles against **API 37**, targets **API 36**, and supports **API 26+**. Native audio components also require the Android NDK and CMake. Set `sdk.dir` in your local `local.properties` or configure `ANDROID_HOME` / `ANDROID_SDK_ROOT`.

```bash
# Android development APK
./gradlew :app:assembleDevDebug

# Android unit tests
./gradlew :app:testDevDebugUnitTest
```

On Windows, use `gradlew.bat` instead of `./gradlew`. For desktop builds, use **JDK 21** and follow [DESKTOP.md](DESKTOP.md). The Android modules are included only when an Android SDK is available.

The repository also contains an optional [Listen Together backend](backend/README.md).

## 🤝 Contributing

Contributions are welcome. Start with the [contributing guide](CONTRIBUTING.md), then read the [code of conduct](CODE_OF_CONDUCT.md). For project context, see [additional documentation](ADDITIONAL.md) and the platform guides above.

## 💜 Credits and license

Vibranium builds on the work of the [BitChord project](https://github.com/kushagrasinghx/BitChord) and its contributors. Thanks also to the open source libraries and lyric providers used throughout the app.

Licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for the full terms. Vibranium does not host music files; you are responsible for using connected services and media in accordance with their terms and applicable law.
