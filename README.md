# Hy2droid — a native Hysteria 2 client for Android

> Applies to release `v0.9.0` and the `main` that followed it. Behaviour that
> changes in a later release is documented under `docs/versions/<tag>/`.

[![Release](https://img.shields.io/github/v/release/noelbennettz/Hy2droid?label=release&logo=github)](https://github.com/noelbennettz/Hy2droid/releases)
[![Website](https://img.shields.io/badge/website-noelbennettz.github.io%2FHy2droid-5db2ff)](https://noelbennettz.github.io/Hy2droid/)
[![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Hysteria core](https://img.shields.io/badge/Hysteria%20core-2.14.0-blue?logo=go&logoColor=white)](core/core.properties)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)

Hy2droid is a native Android client for the [Hysteria 2](https://v2.hysteria.network/)
proxy protocol: a Kotlin/Jetpack Compose (Material 3) interface in front of an
Android `VpnService`, with the official Go core running as a child process and
`libtun2socks.so` bridging the TUN device to it. A profile holds only the six
values a `hysteria2://` share link already carries — server, port, password, SNI,
obfuscation password, certificate policy — so connecting means pasting a link,
scanning a QR code or tapping a link in a browser; there is no YAML or config
file to edit. It needs no root, no Google Play Services and no account, and it
declares no analytics, no crash reporting and no ads. The app is free software
under AGPL-3.0; using it needs an Android 11+ device and a Hysteria 2 server that
you supply.

<p>
  <img src="screenshots/s1.png" width="32%" alt="Hy2droid home screen: a Connected status card naming the profile Home, above a Profiles list where Home is marked In use with a 2248 ms latency reading, and the power and add-profile buttons at the bottom right.">
  <img src="screenshots/s2.png" width="32%" alt="Hy2droid settings screen: an Appearance section with Dynamic color, Show server address and Live traffic, then a Network section with Split tunneling, Blocked traffic, Bypass local network and Tunnel MTU, under the note that changes apply the next time you connect.">
</p>

## Requirements

- Android 11 (API 30) or newer, on `arm64-v8a`, `armeabi-v7a`, `x86` or `x86_64`.
- JDK 17 to build the app.
- Android SDK platform 37 — `compileSdk 37`, `targetSdk 37` in `app/build.gradle`.
- No NDK: `libtun2socks.so` is a committed prebuilt and the Hysteria core is a
  downloaded binary.
- Internet access on the build machine: the core is fetched from the upstream
  release and checksum-verified during the build.
- A Hysteria 2 server URI (`hysteria2://…`), or its QR code, to connect to.

## Install

Take an APK from the [Releases](https://github.com/noelbennettz/Hy2droid/releases)
page: `app-arm64-v8a-release.apk` fits nearly every phone,
`app-universal-release.apk` runs on every ABI, and
`app-armeabi-v7a-release.apk`, `app-x86-release.apk` and
`app-x86_64-release.apk` are the remaining splits —
`adb shell getprop ro.product.cpu.abi` names your device's ABI. Open the file and
allow the browser or file manager to install unknown apps when Android asks; that
permission is per-app and can be revoked afterwards. Add a profile via
*Add profile → From link*, by scanning its QR code, or by opening the link on the
device and letting the browser hand it to the app, then press the power button
and accept the VPN consent dialog, which appears once per app. Every query
parameter of a share link is optional:

```
hysteria2://password@server.example.com:443/?sni=server.example.com&obfs=salamander&obfs-password=secret&insecure=1#My%20node
```

Installing a newer APK over the installed one keeps profiles and settings, because
every release is signed with the same key.

## Build from source

```bash
export JAVA_HOME=/path/to/jdk17                  # Gradle needs JDK 17
echo "sdk.dir=$ANDROID_HOME" > local.properties  # gitignored; points at the SDK
./gradlew assembleDebug                          # app/build/outputs/apk/debug/
```

Both of the first two lines are needed on a fresh clone: the wrapper refuses to
start without a JDK 17, and it cannot find the SDK without `local.properties`.
Also available: `./gradlew testDebugUnitTest`, `./gradlew lintDebug` (errors fail
the build), `./gradlew assembleRelease` (signed when a keystore is present) and
`scripts/check-style.sh` (`--fix` rewrites the mechanical violations). The first
build downloads the pinned core into `app/src/main/jniLibs/<abi>/libhysteria.so`;
see [DOCS.md](DOCS.md) for the core tooling, the signing secrets and the CI jobs.

## Documentation

| Document | Covers |
| --- | --- |
| [DOCS.md](DOCS.md) | Features in detail, every setting and profile field, blocked-port rules, split tunnelling, internals, repository layout, build and core scripts, CI and signing, security notes, troubleshooting, FAQ |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Development setup, the checks to run before opening a pull request, the DCO sign-off every commit needs, and what the project will not accept |
| [native/tun2socks/README.md](native/tun2socks/README.md) | The patches applied to hev-socks5-tunnel, and how to rebuild `libtun2socks.so` |
| [`docs/`](docs/) | Project site in English and Traditional Chinese, published at <https://noelbennettz.github.io/Hy2droid/> |

## Security

Report a vulnerability through GitHub's **Security → Report a vulnerability** form
on this repository, never in a public issue; if that form is unavailable, open an
issue titled `Security contact request` with no technical detail. There is no
security email address and no promised response time. [SECURITY.md](SECURITY.md)
lists what is in scope (the app, the `VpnService` and TUN handling, share-link
parsing, the certificate policy behind `insecure=1`, and the signing and release
chain) and what is not (upstream Hysteria core bugs, settings you chose on
purpose, the server side). [DOCS.md](DOCS.md#security-notes) describes where the
secrets actually live and which threats the app does not defend against.

## Licence

Hy2droid is released under the **GNU Affero General Public License v3.0**
(AGPL-3.0); the full text is in [LICENSE](LICENSE). The project notice and the
third-party MIT notice are in [NOTICE](NOTICE). Hy2droid runs on the device, so
section 13 (network use) does not apply to it; what applies is the distribution
side of the licence: every APK is published together with this source tree, and
if you modify the app and distribute the result, you must offer the source of
your version too.
