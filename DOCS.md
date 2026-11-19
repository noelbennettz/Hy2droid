# Hy2droid documentation

> Applies to release `v1.0.0` and the `main` that followed it. An older release
> keeps its documentation under `docs/versions/<tag>/`.

Everything beyond the quick start: features in detail, every setting and profile
field, the blocked-port rules, split tunnelling, how the tunnel is put together,
the repository layout, the build and core scripts, the CI jobs, the security
model, diagnostics and the FAQ. [README.md](README.md) is the short version and
[docs/index.html](docs/index.html) is the landing page.

## Contents

- [Features in detail](#features-in-detail)
- [Settings](#settings)
- [Profile fields](#profile-fields)
- [Blocked traffic (blocked-port rules)](#blocked-traffic-blocked-port-rules)
- [Split tunnelling and local network](#split-tunnelling-and-local-network)
- [Languages](#languages)
- [How it works](#how-it-works)
- [Repository layout](#repository-layout)
- [Building and tooling](#building-and-tooling)
- [Bundled core](#bundled-core)
- [Scripts](#scripts)
- [Continuous integration and releases](#continuous-integration-and-releases)
- [Setting up signing](#setting-up-signing)
- [Updating](#updating)
- [Security notes](#security-notes)
- [Troubleshooting and diagnostics](#troubleshooting-and-diagnostics)
- [FAQ](#faq)
- [Credits](#credits)

## Features in detail

- Full-tunnel `VpnService` backed by Hysteria 2, dual-stack (IPv4 + IPv6).
- Multiple saved profiles. Import by pasting a `hysteria2://` link or by scanning
  a QR code (an offline scanner, no Play Services needed). Tapping a profile
  while connected switches to it: the service tears the old tunnel down and
  rebuilds it, so no manual disconnect is needed.
- Share a profile as a QR code.
- Salamander obfuscation, custom SNI, optional "allow insecure certificate".
- Brutal congestion control as an explicit switch with an upload/download hint.
  It is off by default because Brutal paces at a fixed rate and does not back off
  on loss; BBR adapts by itself.
- `fastOpen` is **on** by default: the client accepts each new connection without
  waiting for the server to confirm the destination, which removes one round trip
  per connection. That is invisible in a bulk download but is what makes browsing
  many small connections feel fast (measured on a ~150 ms node: 580 → 230 ms to
  open a connection, 1.27 → 0.76 s to first byte). The trade-off, per upstream, is
  that a dead destination is closed instead of reporting an error; the
  per-profile switch turns it off. `lazy` is always on (it only defers the
  *first* connection — upstream: "the client will not connect until the first
  call to TCP() or UDP()"; after that the session is held open), with a 20 s QUIC
  heartbeat.
- **16 KB page size**: `libtun2socks.so` is 16 KB aligned on all four ABIs, and
  the bundled Hysteria core is 16 KB aligned on the 64-bit ones (its 32-bit
  builds stay at 4 KB, which is what those devices expect).
  `scripts/core.sh verify` fails if the core regresses on `arm64-v8a` or `x86_64`,
  `scripts/tun2socks.sh verify` if `libtun2socks.so` regresses on any of the four,
  and Android 15+ on a 16 KB-page device loads the 64-bit libraries without a
  compatibility warning.
- **Failures are named**, not just reported: the status card translates the core's
  own log line into one sentence (name does not resolve, TLS failed, password
  rejected, nothing came back, something else answered, obfuscation mismatch)
  while the raw line stays in logcat and the diagnostics export.
- **Answer ping locally** (Settings → Network, off by default): Hysteria carries
  TCP and UDP only, so ICMP cannot cross it; this makes the engine answer echo
  itself. It is off by default because a fake reply makes ping meaningless.
- DNS, three modes (`DnsMode` in
  `app/src/main/java/us/leaf3stones/hy2droid/proxy/DnsMode.kt`), decided by two
  controls in Settings → Network. **Answer DNS in the tunnel** is on by default
  and is answered by the tunnel engine (hev's `mapdns`): the VPN pushes one
  resolver (`192.0.2.53`), the engine intercepts queries sent to it, answers A
  queries instantly with an address from `198.18.0.0/15`, and forwards the *name*
  over SOCKS5 so the server resolves it. That removes a round trip per name, the
  largest DNS cost on a high-latency tunnel. The trade-offs are real: a mapped
  address only means something while the app is connected, so a client that
  resolves early and connects much later can fail, and IPv6-only destinations do
  not resolve in this mode. With the switch off, the tun gets real resolvers —
  Quad9 (`9.9.9.9`) with Cloudflare (`1.1.1.1`) as the backup — because Android
  has no "no DNS means use the tunnel" mode: with an empty DNS list it resolves
  through the underlying network, so the queries leave over Wi-Fi and never reach
  the server. **Custom DNS server** (off by default) overrides the switch: the
  primary and the optional secondary replace that list, and a list that ends up
  empty falls back to Quad9/Cloudflare. With the system Private DNS setting on,
  names leave the tunnel past the in-tunnel cache and the switch has no effect;
  Settings shows a hint under the switch in that case. A local resolver on port
  53 is not an option: Android reserves privileged ports and `bind()` fails with
  `EACCES`.
- **Network handover**: switching Wi-Fi ↔ mobile restarts the Hysteria core, so
  the session is back within a second instead of waiting out the 30 s QUIC idle
  timeout. The TUN and the tunnel engine stay up; flows in flight during the
  restart are reset and retried by the apps.
- Split tunnelling: pick apps that bypass the VPN (see
  [Split tunnelling and local network](#split-tunnelling-and-local-network)).
- **Blocked traffic**: refuse flows by protocol and port inside the tunnel — QUIC
  (UDP 443) is blocked by default, because Hysteria 2 cannot accelerate it (see
  [Blocked traffic](#blocked-traffic-blocked-port-rules)).
- Optional **Bypass local network** (Android 13+): local-network traffic —
  RFC1918, link-local, CGNAT and multicast (mDNS/SSDP) — stays outside the VPN, so
  NAS boxes, printers, router admin pages and casting keep working.
- **Disable path MTU discovery** (Settings → Network, off by default): emits
  `quic.disablePathMTUDiscovery: true`, the standard workaround for networks that
  silently drop large QUIC packets (MTU black holes), where connections otherwise
  stall.
- **Tunnel MTU** (Settings → Network, default 1500, range 1280–1500): the same
  value goes into the TUN interface and the tun2socks config, so the two cannot
  drift apart. 1500 is the largest standard frame and the TUN only feeds the local
  tunnel engine — the payload leaves as the core's own QUIC, which does its own
  path MTU discovery — so there is no outer header to reserve room for. Lower it
  only on networks that stall on full-size packets.
- **TCP buffer per connection** (Settings → Network, 64 / 128 / 256 KB, default
  128): the engine's send buffer on the tun hop. That hop is local (sub-ms), so
  the buffer absorbs bursts and bounds what can queue for a slow reader; it is not
  a WAN window and does not set the ceiling of a fast link. Bigger costs memory
  per open connection (the buffer plus the engine's per-connection task stack),
  which is why the engine's own default is 64 KB.
- Deleting a profile asks first — the auth secret cannot be recovered; the profile
  carrying the live connection is refused until you disconnect.
- Quick Settings tile to connect/disconnect, kept in sync with the tunnel state; a
  tap connects the last selected profile (with an empty list it does nothing), and
  a long press opens the app instead of the system's App info screen.
- **Latency test** per node from the profile menu, measured by a real request
  through a throwaway core instance (a Hysteria 2 server only answers UDP, so a
  TCP ping would be meaningless). Optional *Test latency on launch* (Settings →
  Network, off by default) measures the nodes that have no result yet and then
  lists the fastest one first — it never switches the node for you, and the
  numbers survive a restart (they expire after 24 h, so the list is never sorted
  by yesterday's numbers).

- **Share-link deep link**: tapping a `hysteria2://` (or `hy2://`) link in a
  browser opens the app and adds the profile straight away — no pasting, no
  editor, no manual file.
- **Export logs** (Settings → Diagnostics): one zip with the app logcat buffer,
  the persisted Hysteria core log, a build/device summary and the running config
  with `auth` / obfs passwords masked — no storage permission needed.
- **About** page (Settings → About): app version, the core version read from the
  bundled binary itself, and links to the repository, the issue tracker and the
  licence.
- Always-on VPN support: a system-initiated start connects normally instead of
  refusing (refusing only makes Android report "always-on VPN is not connected"
  and retry), and disconnecting while it is on explains that Android will start
  the tunnel again.
- Optional **live traffic** (Settings → Appearance, off by default): throughput on
  the status card, where the counters are polled once a second, and in the
  notification, which is refreshed every fifth poll (5 s) so the shade does not
  flicker. Off by default because on Android 14 the ongoing notification can be
  swiped away, and a throughput update would immediately bring it back.
- Dynamic colour (Material You) toggle, custom DNS and split tunnelling in
  Settings. Without it — Android 11, or the switch turned off — the theme falls
  back to plain monochrome: black on white in the light theme, white on near-black
  in the dark one.
- Profile cards show only the profile name by default; the server address is one
  switch away (Settings → Appearance → *Show server address*).
- Profiles and settings are included in Android Auto Backup (secrets too, so a
  restore on a new phone keeps the profiles usable — see
  [Security notes](#security-notes)).

## Settings

| Section | What is there |
| --- | --- |
| Appearance | *Dynamic color (Material You)* (on by default), *Show server address* (off), *Live traffic* (off; throughput on the status card once a second and in the notification every 5 s) |
| Network | *Split tunneling*, *Blocked traffic*, *Bypass local network* (off, Android 13+), *Tunnel MTU* (default 1500, 1280–1500), *TCP buffer per connection* (64 / 128 / 256 KB, default 128), *Disable path MTU discovery* (off), *Answer ping locally* (off), *Answer DNS in the tunnel* (on), *Custom DNS server* (off; primary plus optional secondary), *Test latency on launch* (off) |
| Diagnostics | *Test tunnel* (measures name lookup, connect and download through the running tunnel), *Export logs* (the zip described in [Troubleshooting](#troubleshooting-and-diagnostics)) |
| About | Opens the About screen: app version, bundled core version read from the binary, repository / issues / licence links |

Everything here is stored in the app's private DataStore and survives a restart; the
app sends none of it anywhere. Android Auto Backup can include the DataStore in a
device backup (see [Security notes](#security-notes)).

## Profile fields

- **Name** — the label on the profile card; a link without a fragment uses the
  host as the name
- **Server (host)** — domain or IP, plus a separate **Port** field (default `443`,
  also used when a share link carries no port)
- **Auth / password** — the Hysteria 2 password
- **SNI (optional)** — TLS server name; blank means "derive from the server host".
  Needed when the server is addressed by IP but the certificate is issued to a
  hostname
- **Obfuscation password (optional)** — Salamander password; blank disables obfs
- **Brutal congestion control** — the switch above the two rate fields. Off keeps
  BBR, which adapts to whatever the path is doing; on, the core paces at exactly
  **Upload Mbps** / **Download Mbps** and ignores loss, so use about 80 percent of
  your measured speed and keep it off on unstable networks (see the
  [official docs](https://v2.hysteria.network/docs/advanced/Full-Server-Config/)).
  With both rates at 0 the `bandwidth` section is not emitted at all, so the core
  stays on BBR even with the switch on
- **Allow insecure certificate** — skips TLS verification; keep it off unless you
  know what you are doing
- **QUIC fast open** — on by default; turn it off on a network that mishandles
  QUIC 0-RTT, at the cost of one extra round trip per connection

## Blocked traffic (blocked-port rules)

Hysteria 2 forwards UDP through its own QUIC connection and has no UDP
acceleration, so QUIC inside the tunnel (QUIC over QUIC) is markedly slower than
TCP on lossy networks — a limitation of the protocol, not of this app. Refusing
those flows makes browsers and apps fall back to TCP/TLS, which the core handles
much better.

`Settings → Blocked traffic` keeps a list of `protocol + port` rules (a single port
or an inclusive range, `TCP`, `UDP` or both). **QUIC (UDP 443) is the whole list on
a fresh install** (`DEFAULT_BLOCK_RULES` in
`app/src/main/java/us/leaf3stones/hy2droid/data/BlockRule.kt`); you can delete it or
add your own rules. The rules are rendered into the tun2socks config
`filesDir/tun2socks.yaml`, whose `misc` section on a fresh install reads:

```yaml
misc:
  tcp-buffer-size: 131072
  block-udp: '443'
```

A TCP rule adds a `block-tcp` line, for example `block-tcp: '25,465'` for two
single ports; ports and ranges are comma separated and sorted by start port.

The patched tunnel applies them before any session is created: a blocked TCP port
is answered with a reset (so the client fails over immediately) and a blocked UDP
port is dropped. Nothing of a blocked flow reaches the core; the check is a bit
lookup on the destination port in the accept and receive hooks, not a filter list.
Changes apply on the next connection. With no rules the `misc` section carries no
`block-*` line. Port 53 is left out while DNS is answered in the tunnel: a UDP rule
that covers it is split around it, so blocking `1-1024` still blocks everything
except 53.

## Split tunnelling and local network

- **Split tunnelling** (Settings → Network) takes a list of packages that bypass
  the VPN: their traffic is routed by Android outside the TUN and leaves over the
  underlying network, unproxied.
- **Bypass local network** (Android 13+) keeps `10.0.0.0/8`, `172.16.0.0/12`,
  `192.168.0.0/16`, `169.254.0.0/16`, `100.64.0.0/10` and `224.0.0.0/4`, plus
  `fc00::/7`, `fe80::/10` and `ff00::/8` outside the VPN, so NAS boxes, printers,
  router admin pages and casting discovery (mDNS/SSDP) keep working
  (`app/src/main/java/us/leaf3stones/hy2droid/data/LanRanges.kt`).
- On Android 17+ the app also declares `ACCESS_LOCAL_NETWORK`, asked once on the
  first connect, for nodes hosted on the home LAN (see
  [Security notes](#security-notes)).

## Languages

The app is available in **seven languages**: English, Arabic, Spanish, French,
Russian and Simplified Chinese — the six official languages of the United
Nations — plus Traditional Chinese. Android's per-app language picker
(`Settings → Apps → Hy2droid → Language`) is wired up through
`app/src/main/res/xml/locales_config.xml`:

| Locale | Resource folder |
| --- | --- |
| English | `app/src/main/res/values/` (default) |
| Arabic (RTL) | `app/src/main/res/values-ar/` |
| Spanish | `app/src/main/res/values-es/` |
| French | `app/src/main/res/values-fr/` |
| Russian | `app/src/main/res/values-ru/` |
| Chinese (Simplified) | `app/src/main/res/values-zh-rCN/` |
| Chinese (Traditional) | `app/src/main/res/values-zh-rTW/` |

All user-visible text lives in `app/src/main/res/values/strings.xml`; adding a
language means copying that file into a new `values-<qualifier>/` folder under
`app/src/main/res/` and translating the values (keep the `%1$s` / `%1$d`
placeholders intact, escape apostrophes as
`\'`). `app_name` is not translated (`translatable="false"`). The layouts only use
direction-aware padding/alignment, so RTL (Arabic) mirrors correctly.

## How it works

```
UI (Compose / Material 3)
  └─ MainViewModel ── renders config ──┐
                                       ▼
                     Hysteria2VpnService (VpnService)
                        ├─ VpnService.Builder → TUN fd
                        │    IPv4 10.0.88.88/16, IPv6 fc00::1/64,
                        │    MTU from Settings (default 1500)
                        ├─ exec libhysteria.so -c filesDir/hysteria.yaml
                        │    → 127.0.0.1:1080 (SOCKS5) + :1081 (HTTP)
                        └─ libtun2socks.so (JNI): TUN fd → SOCKS5
```

The core runs as a child process exposing a local SOCKS5 listener;
`libtun2socks.so` (hev-socks5-tunnel plus the patches in `native/tun2socks/`)
bridges the TUN device to it. The service monitors the child process and tears the
tunnel down if it exits.

The app's own UID is excluded from the tunnel
(`VpnService.Builder.addDisallowedApplication(packageName)`), so the core child
process — same UID — reaches the server over the underlying network and resolves
the profile's hostname with the system resolver. If that resolver is broken the
connect fails; there is deliberately no fallback (no hardcoded resolver, no DoH
retry) on that path. DNS *inside* the tunnel is separate: the tun always gets a
resolver (see [Features in detail](#features-in-detail)), and in the default mode
that resolver is the TUN's own `mapdns` address, answered by the engine, which
forwards the name over SOCKS5.

A network handover cannot be fixed by re-pinning the TUN: Hysteria 2 has no rebind
API, so the service watches the set of validated non-VPN transports and restarts
the core when it changes (Wi-Fi ↔ mobile, airplane mode). The restart is
serialised with connect/disconnect, the old process is killed before the new one
binds the SOCKS port, and a core that dies afterwards is still reported as an
error.

`libtun2socks.so` binds its JNI symbols to the **exact** class
`us.leaf3stones.hy2droid.proxy.Hysteria2VpnService` and to the signatures of
`startTun2socks` / `stopTun2socks` / `getTun2socksStats`. Neither the class name
nor those signatures may change without rebuilding the `.so`, and ProGuard/R8 is
configured to keep them. The prebuilt `.so` files are committed, so building the
app needs no NDK.

Unlike the Hysteria core — downloaded at build time and verified against the
SHA-256 values in `core/core.properties` — these four binaries are checked into
git and carry no published hash of their own. They are reproducible only
approximately: `scripts/tun2socks.sh build` rebuilds them from the pinned
upstream tag plus the two patches listed in
[`native/tun2socks/README.md`](native/tun2socks/README.md), but the toolchain
version is not pinned, so a rebuild is not byte-identical. Treat the committed
files as the source of truth for a release and review the patches there.

## Repository layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/us/leaf3stones/hy2droid/ui` | Compose UI (home, profile editor, settings, split tunnel, blocked traffic, QR scanner) |
| `app/src/main/java/us/leaf3stones/hy2droid/data` | `Profile`, `ProfileStore`, `SettingsStore`, `BlockRule`, `HysteriaConfigBuilder`, `HysteriaUri`, `ProfileOrdering`, `ProfileMerge`, `Redact`, `DiagnosticsExporter`, `ConfigFile`, `CoreVersion`, `LanRanges` |
| `app/src/main/java/us/leaf3stones/hy2droid/proxy` | `Hysteria2VpnService`, `VpnTileService`, `TunnelStatus` (`TunnelState.kt`), `Tun`, `Tun2socksConfig`, `DnsMode`, `NetworkSwitch`, `LatencyProbe`, `TunnelProbe`, `CoreLog` |
| `app/src/main/res` | `values/strings.xml` and the six translations, `xml/locales_config.xml`, the backup rules |
| `app/src/main/jniLibs` | `libtun2socks.so` (committed, see `native/tun2socks/`); `libhysteria.so` is downloaded at build time |
| `native/tun2socks` | Patches applied on top of hev-socks5-tunnel (blocked-port rules, JNI names) |
| `core/core.properties` | Pinned Hysteria core version + per-ABI checksums |
| `scripts/core.sh` | Fetch / verify / upgrade the core |
| `scripts/tun2socks.sh` | Rebuild `libtun2socks.so` from the pinned upstream tag + patches |
| `scripts/prune-runs.sh` | Delete all but the newest three runs of the workflow calling it |
| `.github/workflows` | `build.yml` (debug), `release.yml` (signed release), `native.yml` (rebuild `libtun2socks.so`), `pages.yml` (deploy `docs/`) |

## Building and tooling

Prerequisites: JDK 17 and an Android SDK with platform 37 (Android 17). The
Hysteria core is fetched automatically (see [Bundled core](#bundled-core)), and
`local.properties` (gitignored) must point `sdk.dir` at the SDK, so a fresh clone
builds with:

```bash
export JAVA_HOME=/path/to/jdk17
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew assembleRelease      # signed APK when a keystore is present
./gradlew lintDebug            # Android lint; errors fail the build

scripts/check-style.sh         # .editorconfig rules (LF, final newline, width,
                               # import order, no tabs/trailing spaces)
scripts/check-style.sh --fix   # rewrite the mechanical ones
```

## Bundled core

The Hysteria core binaries are **not committed to git**. They are downloaded at
build time from the upstream release (`hysteria-android-<arch>`), verified against
that release's `hashes.txt`, and installed into
`app/src/main/jniLibs/<abi>/libhysteria.so`. `core/core.properties` pins the
version plus the per-ABI SHA-256 checksums; the app reads the version back from
the binary itself (`libhysteria.so version`) on connect, so a swapped core is
reported truthfully.

`scripts/core.sh` manages it:

| Command | Description |
| --- | --- |
| `scripts/core.sh fetch` | Install the pinned core (no-op when already present); Gradle runs this via the `fetchCore` task on `preBuild` |
| `scripts/core.sh latest` | Resolve the newest upstream release tag |
| `scripts/core.sh upgrade [tag\|latest]` | Install a specific (default: newest) core |
| `scripts/core.sh version` | Pinned version + whether the binaries match |
| `scripts/core.sh verify` | Verify the binaries against the manifest, including 16 KB LOAD alignment on the 64-bit ABIs (run in CI) |
| `scripts/core.sh hashes` | Print the per-ABI checksums |

The debug workflow (`build.yml`) installs the newest upstream release on every run
(`core_tag` defaults to `latest`) and prints the resolved version in the run
summary; the release workflow ships the pinned core on a tag push. Pin a build with
the `core_tag` workflow input (e.g.
`app/v2.12.3`) or locally:

```bash
HY2_CORE_TAG=app/v2.12.3 ./gradlew assembleDebug   # fetch that tag now
scripts/core.sh upgrade app/v2.12.3                # same, then print the version
```

Both end in the script's `install_tag`, which rewrites `core/core.properties` with
the new tag and checksums — restore that file if the pin should not move.

Where GitHub's release CDN is slow, point the asset download at a mirror (the
checksums still come from upstream's `hashes.txt`):

```bash
HY2_GITHUB_MIRROR=https://gh-proxy.com/ scripts/core.sh upgrade app/v2.14.0
```

ABI mapping: `arm64-v8a` → `arm64`, `armeabi-v7a` → `armv7`, `x86_64` → `amd64`,
`x86` → `386`. A core bump needs no app changes because the service only execs the
binary with `-c <config>`; if a release changes the YAML schema,
`HysteriaConfigBuilder` is the only app-side touch point.

## Scripts

| Script | What it does |
| --- | --- |
| `scripts/core.sh` | Fetch / verify / upgrade the bundled Hysteria core (see [Bundled core](#bundled-core)) |
| `scripts/tun2socks.sh` | Rebuild, verify or inspect `libtun2socks.so` (see below) |
| `scripts/check-style.sh` | Enforce the `.editorconfig` rules; `--fix` rewrites the mechanical ones |
| `scripts/prune-runs.sh [keep]` | Delete all but the newest runs of the workflow that is running it (default `keep=3`) |

`scripts/tun2socks.sh` takes one of four subcommands:

| Command | Description |
| --- | --- |
| `scripts/tun2socks.sh build [abi…]` | Clone the pinned hev-socks5-tunnel tag (`HEV_TAG` in the script), apply `native/tun2socks/*.patch`, run `ndk-build`, install into `app/src/main/jniLibs/` |
| `scripts/tun2socks.sh verify` | Check the committed `.so` files, including 16 KB LOAD alignment on all four ABIs (run in CI) |
| `scripts/tun2socks.sh source` | Prepare the patched tree only and print its path |
| `scripts/tun2socks.sh info` | Pinned upstream version, patch list, and the alignment of whichever core is bundled |

Environment: `ANDROID_NDK_HOME` selects the NDK; otherwise the script takes the
first `android-ndk-*` with an `ndk-build` it finds under `<repo>/../../tools/`,
`$ANDROID_SDK_ROOT/ndk/*` or `$ANDROID_HOME/ndk/*` (both defaulting to
`$HOME/Android/Sdk`), `TUN2SOCKS_ABIS` narrows the ABI list (default: all four),
and `TUN2SOCKS_BUILD` keeps the patched tree at a given path instead of a temp
directory.

## Continuous integration and releases

| Workflow | Trigger | Output |
| --- | --- | --- |
| `.github/workflows/build.yml` | push to `main`, or manual (`core_tag`) | debug APK as a workflow artifact; gates: `scripts/check-style.sh`, `testDebugUnitTest`, `lintDebug` |
| `.github/workflows/release.yml` | pushing a `v*` tag, or manual with a version | signed release APKs, a signed AAB for Play, + a GitHub Release |
| `.github/workflows/native.yml` | manual | rebuilt `libtun2socks.so` for all four ABIs, as an artifact |
| `.github/workflows/pages.yml` | push to `main` touching `docs/**`, or manual | the landing page in `docs/`, deployed to GitHub Pages |

Both app workflows install and verify the bundled core before building and run
`testDebugUnitTest`; `build.yml` also runs `scripts/check-style.sh` and `lintDebug`,
while a tag build in `release.yml` ships the core **pinned in
`core/core.properties`** so rebuilding a tag yields the same binaries (only a
manual run with an explicit `core_tag` moves it). The debug workflow follows the
newest upstream core, which is the point of it. Signing uses a keystore from
repository secrets, verified with `apksigner`; the release publishes
`app-universal-release.apk` plus the four ABI splits to the GitHub Release, and
keeps the signed `app-release.aab` as a workflow artifact for the Play Console.

A signed release also runs `scripts/release-verify.sh`, which writes
`SHA256SUMS.txt` (in `sha256sum` format) next to the APKs and reads the signing
certificate of every one of them with `apksigner verify --print-certs`, failing
the job if two APKs disagree. The checksum file is published with the release and
its certificate digest becomes the last line of the release notes, so a download
can be checked without trusting the release page: see
[README > Verify your download](README.md#verify-your-download).

`build.yml`, `release.yml` and `native.yml` end with `scripts/prune-runs.sh 3`
(`pages.yml` has no such step): it deletes the runs of *that workflow*
older than the three newest (logs and artifacts included, which keeps
the Actions tab and the artifact quota small). A deleted release run does not
remove its GitHub Release. Those three workflows grant `actions: write` for it,
and the script stops after one pass if it cannot delete anything.

## Setting up signing

Add four repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore, base64-encoded: `base64 -w0 signing/production.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_PASSWORD` | key password |
| `ANDROID_KEY_ALIAS` | key alias |

Create the keystore once and back it up — losing it means installed apps can never
be updated under the same package name:

```bash
keytool -genkeypair -v -keystore signing/production.jks -alias signing \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then publish by pushing a tag (or from the Actions tab with a version input):

```bash
git tag v2.1.0 && git push origin v2.1.0     # versionName 2.1.0
```

`versionName` comes from the tag (or the `version_name` input) and `versionCode`
from the workflow run number, so every release is upgradeable. Without
`ANDROID_KEYSTORE_BASE64` the workflow still builds (validating R8/ProGuard against
the JNI bindings) but produces an unsigned APK, skips the signature check and does
not publish.

To sign locally, place the keystore at `signing/production.jks` (gitignored, or
point `HY2_KEYSTORE_FILE` at it) and export `HY2_KEYSTORE_PASSWORD`,
`HY2_KEY_PASSWORD`, `HY2_KEY_ALIAS`, then run `./gradlew assembleRelease`.

## Updating

Install the newer APK over the installed one. Every release is signed with the
same key, so Android accepts it as an update and the profiles and settings in the
app's private DataStore survive; there is no in-app updater, because there is no
store listing to push through and a background downloader would be one more thing
holding the `INTERNET` permission. Watch the repository or the Releases page.

The bundled core can move independently of the app: a release built from a tag
ships the version pinned in `core/core.properties`, a manual run with `core_tag`
takes another one — see [Bundled core](#bundled-core). A core bump needs no app
change unless upstream changes the YAML schema.

Restoring on a new phone is Android Auto Backup's job (see
[Security notes](#security-notes)).

### Uninstalling

Uninstall the app like any other (`adb uninstall us.leaf3stones.hy2droid`, or
long-press the icon → App info → Uninstall). That deletes the app's private
storage: every saved profile including its password, the settings and the
exported diagnostics. If Android's backup is on, a copy may still exist under
Settings → Google → Backup; restore it on a new phone and the old profiles come
back.

Uninstalling also removes the VPN entry Android registered for the app. The
Hysteria 2 server is untouched, and so is any `.conf` or QR code you exported.
Nothing else on the device is written to.

## Security notes

- **Where the secrets live**: `auth` and the obfs password are stored as-is in the
  profiles DataStore under the app's private directory
  (`/data/data/us.leaf3stones.hy2droid/files/datastore/hysteria_profiles.preferences_pb`,
  inside a directory with mode `0700`, owned by the app UID; settings live next to
  it in `settings.preferences_pb`). No other app can read them — that is enforced by
  the per-app UID, not by app code. The app writes nothing to shared storage,
  declares no `FileProvider`, and no exported component hands out data.
- **Screens and clipboard**: the share dialog and the profile editor set
  `FLAG_SECURE` while they are open, so the QR, the link and the passwords cannot
  be captured by screenshots, screen recordings or the recents thumbnail; copying
  the link marks the clip `EXTRA_IS_SENSITIVE` (Android 13+), which hides previews
  and shortens its life.
- **What this does not cover, on purpose**: a rooted device or `adb` on a
  debuggable build can read the private directory, and Android's own backup can
  carry the file (Auto Backup is end-to-end encrypted with the device lock on
  Android 9+). That is the price for a backup restore that keeps working on a new
  phone, and it is why there is no Keystore wrapper: the encryption bought little
  against those two paths and cost a restore that silently lost every password.
- The YAML the core reads (`filesDir/hysteria.yaml`, that is
  `/data/data/us.leaf3stones.hy2droid/files/hysteria.yaml`) is written for the
  session and deleted when the tunnel stops; it, the `.tmp` file an interrupted
  atomic write would leave behind, and the core log are all excluded from backups.
- Exported diagnostics mask `auth` / obfs `password` and drop core log lines that
  carry a share link. Nothing else in the app writes the secret anywhere (not the
  notification, not logcat, not files outside the private dir).
- Any app on the device can hand the app a `hysteria2://` link — that is the
  deep-link feature. An imported profile becomes the selected one (so the power
  button and the Quick Settings tile use it) but is never connected
  automatically: the user still has to press connect.
- The app declares `INTERNET` for the tunnel and the latency test; other
  permissions are `ACCESS_NETWORK_STATE` (handover detection), `CAMERA` (the QR
  scanner, only while it is open), `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` and
  `FOREGROUND_SERVICE_SPECIAL_USE` (the foreground service a VPN has to be), and
  `ACCESS_LOCAL_NETWORK` on Android 17+ (asked once on the first connect, for nodes
  hosted on the home LAN). No analytics, no crash reporting, no ads, no update
  check.

## Troubleshooting and diagnostics

- Settings → Diagnostics → *Test tunnel* runs a name lookup, a connect and a
  download through the running tunnel and reports which step failed — the quickest
  answer to "connected, but nothing loads".
- Settings → Diagnostics → *Export logs* writes a zip the user picks in the system
  file picker: `app-info.txt`, `logcat.txt`, `hysteria.log` (plus the rotated
  `hysteria.1.log`), and `hysteria.config.redacted.yaml`. Attach that to a bug
  report instead of pasting logcat.
- The core logs to logcat under the `hysteria` tag (lines containing a share URI or
  the obfs password are redacted). The same lines are appended to
  `filesDir/logs/hysteria.log` so they survive a reboot or an app crash.
- The service logs to the `Hysteria2VpnService` tag, including the bundled core
  version at every connect:
  `adb logcat -s Hysteria2VpnService | grep 'Hysteria core'`.
- If a newly released core breaks connectivity, pin the previous one via the
  `core_tag` input or `HY2_CORE_TAG` and open an issue.
- Report bugs at
  [noelbennettz/Hy2droid/issues](https://github.com/noelbennettz/Hy2droid/issues)
  with the *Export logs* zip attached — the config inside is redacted. Never paste
  a share link: it contains the password.

## FAQ

### Is Hy2droid free, and does it need Play Services or root?

It is free software under AGPL-3.0. No Play Services (the QR scanner is CameraX
plus ZXing, so it works offline on a de-Googled phone), no root, no account. The
tunnel is an ordinary `VpnService`, which is why Android asks for consent once.

### Which Android versions and phones are supported?

Android 11 (API 30) and newer, on `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`.
Tablets and boxes with a touchscreen or a mouse work too — nothing is phone-only.

### Does it support Hysteria 1, TUIC, VLESS or subscription URLs?

No. This is a client for **Hysteria 2** only. Profiles are added one link or one QR
code at a time; there is no subscription-URL importer — a subscription means a
remote server decides what your device connects to, which is a different product.

### Why is QUIC (UDP 443) blocked out of the box?

Hysteria 2 carries UDP through its own QUIC connection, so QUIC inside the tunnel
is QUIC-over-QUIC, which upstream does not accelerate and which behaves badly on a
lossy link. Blocking UDP 443 makes browsers and apps fall back to TCP/TLS, which
the core handles well. Remove the rule in *Settings → Blocked traffic* if you would
rather keep QUIC.

### Why does ping not work through the tunnel?

Hysteria carries TCP and UDP, not ICMP. *Answer ping locally* (off by default) makes
the engine reply to echo itself; it is off because a fake reply turns ping from a
diagnostic into decoration.

### Why is QUIC fast open on by default, and is 0-RTT risky?

It saves one round trip per connection, which is what makes many small connections
feel fast (580 → 230 ms to open one on a ~150 ms node). The cost, per upstream, is
that a dead destination is closed instead of returning an error. The per-profile
switch turns it off; it does not weaken TLS.

### Does the app trigger the 16 KB page-size compatibility warning on Android 15+?

No. Android 15 can run on devices with 16 KB memory pages, and a native library
must be linked with 16 KB LOAD alignment to be mapped there. Both libraries are:
`libtun2socks.so` on all four ABIs and the bundled core on `arm64-v8a` and
`x86_64`. The core's `armeabi-v7a` and `x86` builds stay at 4 KB, which is what
`scripts/core.sh verify` requires: only the 64-bit libraries are checked, because
those are the ones a 16 KB-page kernel loads.
`scripts/tun2socks.sh info` prints the alignment of whatever core is bundled.

### Connected, but pages do not load — where do I start?

Run *Settings → Diagnostics → Test tunnel*. If the name lookup fails, the DNS mode
is the suspect (a mapped address from *Answer DNS in the tunnel* only lives as long
as the session); if the connect fails, the status card names the reason (TLS,
password, obfs mismatch); if the download fails, turn *Answer DNS in the tunnel*
off once, then export the logs.

### Does the app collect anything?

No. No analytics, no crash reporting, no ads, no update check. Its traffic is the
tunnel to the server you configured, the system resolver, and latency probes that
go through that same server.

### How do I move my profiles to a new phone?

Android Auto Backup carries the app's private DataStore, secrets included, so a
restore from the same Google account keeps the profiles usable. Without a backup,
re-import each share link or QR code — there is no export-to-file button on
purpose, since it would put every password in shared storage.

### Why is there a permanent VPN notification?

Android requires a foreground-service notification for an active VPN. On Android 14
it can be swiped away; *Live traffic* is off by default because a throughput update
would immediately bring it back.

### Can I use it as an always-on VPN?

Yes. A system-initiated start connects normally instead of refusing, and
disconnecting while always-on is enabled explains that Android will start the tunnel
again. Lockdown mode behaves as Android defines it: traffic is blocked while the
tunnel is down.

## Credits

- [hysteria](https://github.com/apernet/hysteria) — protocol and reference
  implementation. [LICENSE](https://github.com/apernet/hysteria/blob/master/LICENSE.md)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) — TUN ↔ SOCKS5
  bridge, patched for blocked-port rules (see `native/tun2socks/`).
  [LICENSE](https://github.com/heiher/hev-socks5-tunnel/blob/main/LICENSE)

This application is provided for learning and personal use. Follow the laws of your
jurisdiction.

