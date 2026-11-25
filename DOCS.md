# Hy2droid documentation

> This tree documents the current `main`. A release keeps the documentation it
> shipped with at its own tag (`git show <tag>:DOCS.md`).

The README is the short version. This file has the details: every feature and
setting, the profile fields, blocked-port rules, split tunnelling, how the tunnel
is built, the repository layout, build and core scripts, CI jobs, the security
model, diagnostics and the FAQ. The landing page,
<https://noelbennettz.github.io/Hy2droid/>, lives in the
[`noelbennettz.github.io`](https://github.com/noelbennettz/noelbennettz.github.io)
repository.

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
- [Instrumentation tests](#instrumentation-tests)
- [Bundled core](#bundled-core)
- [Scripts](#scripts)
- [Continuous integration and releases](#continuous-integration-and-releases)
- [Licences and the SBOM](#licences-and-the-sbom)
- [Setting up signing](#setting-up-signing)
- [Updating](#updating)
- [Security notes](#security-notes)
- [Troubleshooting and diagnostics](#troubleshooting-and-diagnostics)
- [FAQ](#faq)
- [Credits](#credits)

## Features in detail

- Full-tunnel `VpnService` backed by Hysteria 2, dual-stack (IPv4 + IPv6).
- Multiple saved profiles. The `+` button offers the three ways one arrives:
  *Import from link* reads a `hysteria2://` (or `hy2://`) link from the
  clipboard and puts the profile straight into the list, *Scan QR code* opens the
  offline camera scanner (no Play Services needed), and *Enter manually* opens
  the editor. Tapping a profile while connected switches to it: the service tears
  the old tunnel down and rebuilds it, so no manual disconnect is needed.
  Re-importing the same account updates that profile in place; a different
  password on the same host and port is a separate account and becomes a new
  profile.
- Share a profile as a QR code.
- Salamander obfuscation, custom SNI, optional "allow insecure certificate".
- Brutal congestion control as an explicit switch with an upload/download hint.
  It is off by default because Brutal paces at a fixed rate and does not back off
  on loss; BBR adapts by itself.
- `fastOpen` is **on** by default. The client accepts each new connection without
  waiting for the server to confirm the destination, which saves one round trip.
  That is invisible in a bulk download, but it is what makes browsing many small
  connections feel fast: on a ~150 ms node, opening a connection went from 580 to
  230 ms, and time to first byte from 1.27 to 0.76 s. The trade-off, per upstream,
  is that a dead destination is closed instead of returning an error; the
  per-profile switch turns it off.
- `lazy` is always on. It defers only the *first* connection until the app asks for
  one (upstream: "the client will not connect until the first call to TCP() or
  UDP()"); after that the session stays open, with a 20 s QUIC heartbeat.
- **16 KB page size.** `libtun2socks.so` is 16 KB `LOAD`-aligned on all four ABIs,
  including `armeabi-v7a` and `x86`. 32-bit devices run 4 KB kernels, so the flag
  costs nothing there and keeps one build rule instead of two. The bundled Hysteria
  core is 16 KB-aligned on the 64-bit ABIs only and stays at 4 KB on the 32-bit
  ones, which is what those devices expect. `scripts/core.sh verify` fails if the
  core regresses on `arm64-v8a` or `x86_64`; `scripts/tun2socks.sh verify` fails if
  `libtun2socks.so` regresses on any ABI. Android 15+ on a 16 KB-page device
  therefore loads the 64-bit libraries without a compatibility warning.
- **Failures are named**, not just reported: the status card turns the core's own
  log line into a short explanation (name does not resolve, TLS failed, password
  rejected, nothing came back, something else answered, obfuscation mismatch),
  while the raw line stays in logcat and the diagnostics export. The explanation
  goes away when the core reports a successful connection again (or when the
  tunnel is restarted after a network handover), so a timeout from an outage
  does not sit under a recovered tunnel.
- **Answer ping locally** (Settings → Network → Advanced, off by default): Hysteria carries
  TCP and UDP only, so ICMP cannot cross it; this makes the engine answer echo
  itself. It is off by default because a fake reply makes ping meaningless.
- DNS has three modes (`DnsMode` in
  `app/src/main/java/us/leaf3stones/hy2droid/proxy/DnsMode.kt`), chosen by two
  controls in Settings → Network → Advanced.
  - **Answer DNS in the tunnel** (on by default): the VPN pushes one resolver
    (`192.0.2.53`). The engine intercepts queries sent to it, answers A queries
    instantly with an address from `198.18.0.0/15`, and forwards the *name* over
    SOCKS5 so the server resolves it. This removes a round trip per name, the
    largest DNS cost on a high-latency tunnel. The trade-offs are real: a mapped
    address only means something while the app is connected, so a client that
    resolves early and connects much later can fail, and IPv6-only destinations
    do not resolve in this mode.
  - **Forwarded DNS** (switch off): the tun gets real resolvers — Quad9
    (`9.9.9.9` / `2620:fe::fe`) with Cloudflare (`1.1.1.1` /
    `2606:4700:4700::1111`) as the backup. Android has no "no DNS means use the
    tunnel" mode: with an empty list it resolves through the underlying network,
    so the queries leave over Wi-Fi and never reach the server.
  - **Custom DNS server** (off by default) overrides the switch: the primary and
    optional secondary replace that list, and a list that ends up empty falls back
    to Quad9/Cloudflare.
  - With the system Private DNS setting on, names leave the tunnel past the
    in-tunnel cache and the switch has no effect; Settings shows a hint under the
    switch in that case. A local resolver on port 53 is not an option because
    Android reserves privileged ports and `bind()` fails with `EACCES`.
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
- **Disable path MTU discovery** (Settings → Network → Advanced, off by default): emits
  `quic.disablePathMTUDiscovery: true`, the standard workaround for networks that
  silently drop large QUIC packets (MTU black holes), where connections otherwise
  stall.
- **Tunnel MTU** (Settings → Network, default 1500, range 1280–1500): the same
  value goes into the TUN interface and the tun2socks config, so the two cannot
  drift apart. 1500 is the largest standard frame and the TUN only feeds the local
  tunnel engine — the payload leaves as the core's own QUIC, which does its own
  path MTU discovery — so there is no outer header to reserve room for. Lower it
  only on networks that stall on full-size packets.
- **TCP buffer per connection** (Settings → Network → Advanced, 64 / 128 / 256 KB, default
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
  Network → Advanced, off by default) measures the nodes that have no result yet
  and then lists the fastest one first — it never switches the node for you, and
  the numbers survive a restart (they expire after 24 h, so the list is never
  sorted by yesterday's numbers).

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
| Network | *Split tunneling*, *Blocked traffic*, *Bypass local network* (off, Android 13+), *Tunnel MTU* (default 1500, 1280–1500) |
| Advanced (collapsed) | *TCP buffer per connection* (64 / 128 / 256 KB, default 128), *Disable path MTU discovery* (off), *Answer ping locally* (off), *Answer DNS in the tunnel* (on), *Custom DNS server* (off; primary plus optional secondary), *Test latency on launch* (off) |
| Diagnostics | *Test tunnel* (measures name lookup, connect and download through the running tunnel), *Export logs* (the zip described in [Troubleshooting](#troubleshooting-and-diagnostics)) |
| About | Opens the About screen: app version, bundled core version read from the binary, repository / issues / licence links. *Open source licenses* (next to it) lists every dependency the APK contains with its version and licence, generated at build time by Licensee, plus the Hysteria core and `libtun2socks.so` with their full licence texts. Nothing on that screen is loaded from the network |

The *Advanced* group is collapsed when the screen opens; expanding it is a property
of the screen, not a stored preference, so it is remembered across a rotation and
forgotten when the screen closes. The preference keys, their defaults and their
behaviour are unchanged by that layout: only *Appearance* and *Network* are visible
without opening it.

Everything here is stored in the app's private DataStore and survives a restart; the
app sends none of it anywhere. Android Auto Backup can include the DataStore in a
device backup (see [Security notes](#security-notes)). The order above is the order
on screen. Earlier releases had the advanced items inline in *Network*; the
documentation at those tags describes the older layout.

## Profile fields

- **Name** — the label on the profile card; a link without a fragment uses the
  host as the name
- **Server (host)** — domain or IP, plus a separate **Port** field (default `443`,
  also used when a share link carries no port)
- **Auth / password** — the Hysteria 2 password
- **SNI (optional)** — TLS server name; blank means "derive from the server host".
  Needed when the server is addressed by IP but the certificate is issued to a
  hostname
- **Obfuscation password (optional)** — Salamander password; blank disables obfs.
  Salamander is the only obfs type the app writes, so a share link that asks for
  another type (`obfs=gecko`), certificate pinning (`pinSHA256`) or ECH is
  rejected on import instead of being connected without the part it promised.
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

`scripts/check-locales.sh` compares the **complete key set** — strings, plurals
and string arrays — of every `values-*/strings.xml` with the default file and
fails on a missing key, an extra key (usually a rename that only touched one
side), a duplicate, or an element type that differs. `build.yml` runs it on every
pull request, next to `check-style.sh`. Lint's `MissingTranslation` stays disabled:
it only reports a string it sees used in code, while this compares the files.

## How it works

```
UI (Compose / Material 3)
  └─ MainViewModel ── renders config ──┐
                                       ▼
                     Hysteria2VpnService (VpnService)
                        ├─ VpnService.Builder → TUN fd
                        │    IPv4 198.51.100.1/24, IPv6 fd00:6879:6472:6f69::1/64,
                        │    MTU from Settings (default 1500)
                        ├─ exec libhysteria.so -c filesDir/hysteria.yaml
                        │    → 127.0.0.1:1080 (SOCKS5) + :1081 (HTTP)
                        └─ libtun2socks.so (JNI): TUN fd → SOCKS5
```

The core runs as a child process with a local SOCKS5 listener.
`libtun2socks.so` (hev-socks5-tunnel plus the patches in `native/tun2socks/`)
bridges the TUN device to that listener. The service watches the child process and
tears the tunnel down if it exits.

The app's own UID is excluded from the tunnel
(`VpnService.Builder.addDisallowedApplication(packageName)`). The core runs with
the same UID, so it reaches the server over the underlying network and resolves
the profile's hostname with the system resolver. If that resolver is broken the
connect fails: there is deliberately no fallback (no hardcoded resolver, no DoH
retry) on that path. DNS *inside* the tunnel is separate; the tun always gets a
resolver (see [Features in detail](#features-in-detail)). In the default mode that
resolver is the TUN's own `mapdns` address, answered by the engine, which forwards
the name over SOCKS5.

A network handover cannot be fixed by re-pinning the TUN: Hysteria 2 has no rebind
API. The service therefore watches the set of validated non-VPN transports and
restarts the core when it changes (Wi-Fi ↔ mobile, airplane mode). The restart is
serialised with connect/disconnect, and the old process is killed before the new
one binds the SOCKS port. A core that dies afterwards is still reported as an
error.

`libtun2socks.so` binds its JNI symbols to the **exact** class
`us.leaf3stones.hy2droid.proxy.Hysteria2VpnService` and to the signatures of
`startTun2socks` / `stopTun2socks` / `getTun2socksStats`. Neither the class name
nor those signatures may change without rebuilding the `.so`, and ProGuard/R8 is
configured to keep them. The prebuilt `.so` files are committed, so building the
app needs no NDK.

The Hysteria core is downloaded at build time and verified against the SHA-256
values in `core/core.properties`. The four `libtun2socks.so` files are different:
they are checked into git and pinned by `native/tun2socks/SHA256SUMS`, which
`scripts/tun2socks.sh verify` checks on every pull request. `scripts/tun2socks.sh build`
is byte-reproducible with the same NDK (`-ffile-prefix-map` removes the build path;
see
[`native/tun2socks/README.md`](native/tun2socks/README.md#reproducibility)). The
remaining limit is the toolchain: a different NDK revision produces different
code, so a byte-identical rebuild needs NDK r25c (25.2.9519653). The committed
files are what a release ships; review the patches there.

## Repository layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/us/leaf3stones/hy2droid/ui` | Compose UI (home, profile editor, settings, split tunnel, blocked traffic, QR scanner) |
| `app/src/main/java/us/leaf3stones/hy2droid/data` | `Profile`, `ProfileStore`, `SettingsStore`, `BlockRule`, `HysteriaConfigBuilder`, `HysteriaUri`, `ProfileOrdering`, `ProfileMerge`, `Redact`, `DiagnosticsExporter`, `ConfigFile`, `CoreVersion`, `LanRanges` |
| `app/src/main/java/us/leaf3stones/hy2droid/proxy` | `Hysteria2VpnService`, `VpnTileService`, `TunnelStatus` (`TunnelState.kt`), `Tun`, `Tun2socksConfig`, `DnsMode`, `NetworkSwitch`, `LatencyProbe`, `TunnelProbe`, `CoreLog` |
| `app/src/main/res` | `values/strings.xml` and the six translations, `xml/locales_config.xml`, the backup rules |
| `app/src/main/jniLibs` | `libtun2socks.so` (committed, see `native/tun2socks/`); `libhysteria.so` is downloaded at build time |
| `native/tun2socks` | Patches applied on top of hev-socks5-tunnel (blocked-port rules, JNI names), plus `SHA256SUMS` pinning the four committed libraries |
| `native/LICENSES.tsv` | One row per bundled native component: version source, licence asset, upstream, SPDX id |
| `core/core.properties` | Pinned Hysteria core version + per-ABI checksums |
| `scripts/core.sh` | Fetch / verify / upgrade the core |
| `scripts/tun2socks.sh` | Rebuild `libtun2socks.so` from the pinned upstream tag + patches |
| `scripts/prune-runs.sh` | Delete all but the newest three runs of the workflow calling it |
| `.github/workflows` | `build.yml` (debug + unit tests + lint), `connected.yml` (instrumentation tests on an emulator), `release.yml` (signed release), `native.yml` (rebuild `libtun2socks.so`) |
| `.github/ISSUE_TEMPLATE` | Issue forms: `bug_report.yml` (version, device, Test tunnel result, exported logs), `feature_request.yml` (scope checkboxes), and `config.yml` (contact links; blank issues are off) |
| `app/src/androidTest` | Instrumentation tests: settings screen, licences data, share-link parsing |

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

## Instrumentation tests

`app/src/androidTest` holds the tests that need an Android runtime instead of a
JVM: the settings screen (the Advanced group starts collapsed, opens and closes
on a tap, and the dynamic-colour switch has to reach the DataStore), the licences
screen's packaged data, and share-link parsing through the device's own
`java.net` implementation.

```bash
./gradlew connectedDebugAndroidTest   # needs a device or an emulator (API 30+)
```

CI runs them in `.github/workflows/connected.yml` on API 30 for every pull
request, and on API 30 + API 36 on `main` (see
[Continuous integration and releases](#continuous-integration-and-releases)).

**API 36, not 37.** `targetSdk` is 37, but Google publishes no
`system-images;android-37*` image and no `platforms;android-37` package (the
platform is `platforms;android-37.0`), so `android-emulator-runner` — which
installs `platforms;android-<api-level>` before booting — cannot start an API 37
emulator; it fails in its own setup step, before any test runs. 36 is the newest
emulator image that exists, so it is what runs; API 37 behaviour (edge-to-edge,
orientation/resizability rules, local network permission) is checked on a
physical device running Android 17.

**Real VPN testing stays a physical-device job.** These tests render screens and
write settings; none of them establish a tunnel, because that needs a reachable
Hysteria 2 server, the system VPN consent dialog and real packet forwarding.
Before a release, check by hand on a device: connect and disconnect, switch
between two profiles while connected, the Quick Settings tile, the split-tunnel
and blocked-traffic screens against a live tunnel, and *Settings → Diagnostics →
Test tunnel*. The emulator jobs do not cover any of that.

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
| `scripts/core.sh fetch` | Install the pinned core (no-op when already present); Gradle runs `fetch --pinned` via the `fetchCore` task on `preBuild`, then `verify` |
| `scripts/core.sh fetch --pinned` | Same, but ignore `HY2_CORE_TAG`: only the version in `core/core.properties` is accepted (what CI, the release workflow and Gradle use) |
| `scripts/core.sh latest` | Resolve the newest upstream release tag |
| `scripts/core.sh upgrade [tag\|latest]` | Install a specific (default: newest) core and rewrite `core/core.properties` |
| `scripts/core.sh version` | Pinned version + whether the binaries match |
| `scripts/core.sh verify` | Verify the binaries against the manifest, including 16 KB LOAD alignment on the 64-bit ABIs (run in CI) |
| `scripts/core.sh hashes` | Print the per-ABI checksums |

Every workflow installs the core the same way, `scripts/core.sh fetch --pinned`,
and then runs `scripts/core.sh verify`. Nothing in `build.yml` or `release.yml`
can move the pin: there is no `latest` option and no `core_tag` input any more,
because a build that fetched the newest upstream release would not be
reproducible and could bundle a core nobody reviewed. The debug APK and the
released APK therefore contain the same core as the commit they were built from.

Moving the pin is a normal change to `core/core.properties` (version plus the
four SHA-256 values), produced by:

```bash
scripts/core.sh upgrade app/v2.15.0     # downloads, verifies against upstream
                                        # hashes.txt, rewrites core.properties
git diff core/core.properties           # review the version + hashes
```

For a one-off local experiment without touching the pin, `HY2_CORE_TAG` still
works with a plain `fetch`/`assembleDebug`, but `fetch --pinned` (Gradle's
`fetchCore`), `verify` and CI ignore it, so an experiment cannot leak into a
build. `core/core.properties` is what the app ships.

### Building offline, or through a mirror

The core binaries are not committed, so a machine that has never built the
project needs the four `libhysteria.so` files once. After that, `fetch` is a
no-op as long as they match `core/core.properties`:

- **Second build, no network**: nothing is downloaded. `fetch --pinned` sees the
  binaries, matches the checksums, and returns; Gradle dependencies come from the
  Gradle cache (`--offline` works once they are cached).
- **Fresh checkout, no network**: copy
  `app/src/main/jniLibs/<abi>/libhysteria.so` from another build (or from a
  released APK's `lib/<abi>/`), put them in place, and run
  `scripts/core.sh verify`. If the four hashes match `core/core.properties`,
  Gradle accepts them without touching the network; if they do not, the build
  fails instead of shipping a different core.
- **Restricted network**: `HY2_GITHUB_MIRROR=<prefix>` repoints the asset URLs
  (the checksums still come from upstream's `hashes.txt`), and `GITHUB_TOKEN`
  lifts the API rate limit when resolving `latest`. Neither is used by CI, which
  downloads directly from upstream with the pinned tag:

  ```bash
  HY2_GITHUB_MIRROR=https://gh-proxy.com/ scripts/core.sh upgrade app/v2.14.0
  ```
- Nothing else in the build downloads binaries: `libtun2socks.so` is committed
  (with its own `native/tun2socks/SHA256SUMS`), and the rest is Maven
  dependencies.

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
| `scripts/check-locales.sh` | Fail when the `values-*/strings.xml` key sets differ from `values/strings.xml` (missing, extra, duplicate, or a changed element type) |
| `scripts/check-docs.sh` | Fail when a Markdown link, a script, a workflow or the DOCS contents list disagrees with the repository |
| `scripts/check-dco.sh <base> <head>` | Require a `Signed-off-by:` trailer matching the author on every non-merge commit; `dco.yml` runs it on pull requests |
| `scripts/check-native-licences.sh` | Verify `native/LICENSES.tsv` against the licence assets the app shows, `NOTICE`, and the pinned versions |
| `scripts/sbom.sh <apk>` | Write the SPDX 2.3 SBOM for a built APK (syft + Licensee's report); deterministic for a commit |
| `scripts/version.sh` | The single source of `versionName`/`versionCode`; `app/build.gradle` calls it |
| `scripts/check-version-consistency.sh` | Fail when README/DOCS pin the tree to a release, a versioned link names no tag, or the core badge drifts from `core/core.properties` |
| `scripts/check-release-version.sh` | Fail when `version.sh release` accepts a suffixed version, or `release.yml` stops validating before the build |
| `scripts/prune-runs.sh [keep]` | Delete all but the newest runs of the workflow that is running it (default `keep=3`) |

`scripts/tun2socks.sh` takes one of five subcommands:

| Command | Description |
| --- | --- |
| `scripts/tun2socks.sh build [abi…]` | Clone the pinned hev-socks5-tunnel tag (`HEV_TAG` in the script), apply `native/tun2socks/*.patch`, run `ndk-build`, install into `app/src/main/jniLibs/` |
| `scripts/tun2socks.sh verify` | Check the committed `.so` files against `native/tun2socks/SHA256SUMS`, then check the JNI entry points and 16 KB LOAD alignment on all four ABIs (run in CI) |
| `scripts/tun2socks.sh sha256` | Print the manifest for the four current libraries; redirect it to refresh the checked-in file |
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
| `.github/workflows/build.yml` | push to `main`, pull request, or manual | debug APK as a workflow artifact; gates: `scripts/check-style.sh`, `scripts/check-locales.sh`, `scripts/check-version-consistency.sh`, `scripts/check-release-version.sh`, `scripts/check-docs.sh`, `testDebugUnitTest`, `lintDebug`; a second job builds the release variant + AAB unsigned |
| `.github/workflows/connected.yml` | pull request (API 30), push to `main` / manual (API 30 and 36) | instrumentation-test report; gate: `connectedDebugAndroidTest` on an emulator |
| `.github/workflows/release.yml` | pushing a `v*` tag, or manual with a version | signed release APKs, a signed AAB for Play, `SHA256SUMS.txt`, `BUILD-INFO.txt`, a build attestation, and a GitHub Release |
| `.github/workflows/native.yml` | manual | rebuilt `libtun2socks.so` for all four ABIs, as an artifact |
| `.github/workflows/dco.yml` | pull request | `scripts/check-dco.sh` over the pull request's commits; plain git, no GitHub App |

Every `uses:` in these workflows is pinned to a full commit SHA with the original
tag in a trailing comment, so a moved tag cannot change what a build runs.
`.github/dependabot.yml` proposes the weekly bump of those pins and of the Gradle
dependencies in `gradle/libs.versions.toml`.

Both app workflows install and verify the bundled core before building and run
`testDebugUnitTest`; `build.yml` also runs `scripts/check-style.sh` and `lintDebug`.
Both use `scripts/core.sh fetch --pinned` followed by `scripts/core.sh verify`, so
the core is always the one pinned in `core/core.properties`: a debug APK and a
released APK built from the same commit contain the same core, and no workflow
input can substitute another. Signing uses a keystore from
repository secrets, verified with `apksigner`; the release publishes
`app-universal-release.apk` plus the four ABI splits to the GitHub Release, and
keeps the signed `app-release.aab` as a workflow artifact for the Play Console.

`build.yml` also runs `./gradlew licenseeRelease`, which fails the build when a
dependency is not under one of the licences allowed in `app/build.gradle`
(Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC); the same task writes the
report the in-app licences screen shows.

### What runs where, and what it costs

`build.yml` has three jobs. `changes` reads the pull request's file list and says
whether anything other than prose changed (anything outside
`README.md`, `DOCS.md`, `CONTRIBUTING.md`, `SECURITY.md`, `LICENSE`, `NOTICE` and
`screenshots/`). `debug-apk` runs on every pull request: style, locale parity,
docs/version consistency, licences, the core and `libtun2socks.so` checks,
`testDebugUnitTest`, `assembleDebug`, `lintDebug`. `unsigned-release` runs
`assembleRelease` and `bundleRelease` — the R8/shrinking/bundle path that a tag
uses — and skips itself for a documentation-only pull request. On `main` and on
manual runs it always runs.

Both Gradle jobs use `actions/setup-gradle`, which persists `~/.gradle/caches` and
the build cache (`org.gradle.caching=true` in `gradle.properties`) between runs,
so a warm run resolves no dependencies and recompiles only what changed.

The **configuration cache is deliberately off**. `versionName`/`versionCode` are
produced by `scripts/version.sh` (`git describe`) inside the build, and Gradle
cannot see the git repository as an input: a restored configuration-cache entry
would keep the version of whichever commit stored it, including a new tag's
`versionCode`. A few seconds per run is not worth risking a release that
reports the previous version.

A signed release also runs `scripts/release-verify.sh`, which writes
`SHA256SUMS.txt` (in `sha256sum` format) next to the APKs and reads the signing
certificate of every one of them with `apksigner verify --print-certs`, failing
the job if two APKs disagree. The checksum file is published with the release and
its certificate digest becomes the last line of the release notes, so a download
can be checked without trusting the release page: see
[README > Verify your download](README.md#verify-your-download).

Every workflow includes a final cleanup step that runs `scripts/prune-runs.sh 3`.
It keeps only the newest three runs of *that workflow* and deletes the rest,
including their logs and artifacts, which keeps the Actions tab and the artifact
quota small. Deleting a release run does not remove its GitHub Release. All five
workflows grant `actions: write`; on a run whose token cannot delete (for example
a pull request from a fork), the script stops after one pass instead of failing
the job.

## Licences and the SBOM

Three records describe what ships, and each has a CI check:

| Question | Answer | Gate |
| --- | --- | --- |
| Which Maven artefacts does the release link, and under what licence? | `./gradlew licenseeRelease` (plugin allowlist in `app/build.gradle`) and the same report as an app asset — the *Open source licenses* screen | `build.yml` fails on a disallowed licence |
| Which native components, which version, which licence text? | `native/LICENSES.tsv` | `scripts/check-native-licences.sh` in `build.yml` |
| What exactly is inside the built APK? | `hy2droid-<version>.spdx.json`, SPDX 2.3 | generated in `release.yml`, attached to the release |

`native/LICENSES.tsv` is one row per bundled binary: the component, where its
version is pinned (`core/core.properties` for the Go core, `scripts/tun2socks.sh`
for the tunnel engine), the licence asset the app displays, the upstream
repository and the SPDX licence id. `scripts/check-native-licences.sh` fails when
the asset is missing or empty, when its text does not appear verbatim in `NOTICE`,
when `ThirdPartyLicenses.kt` no longer references it, or when the version in the
manifest disagrees with the pin. The texts themselves are the upstream files,
committed unchanged — this repository does not write or paraphrase licence text.

`scripts/sbom.sh <apk>` builds the SBOM from two real inputs:

- **syft** scans the unpacked universal APK. It reads the **Go build info** of
  `libhysteria.so`, so the Hysteria core and its whole module graph appear with
  versions syft read from the binary. The three other ABI copies of that library
  are excluded: they are the same code and would list every module four times.
- **Licensee's report** supplies the Maven artefacts (Compose, AndroidX, …).
  syft cannot see them: by the time the APK is packaged they are DEX, and the JARs
  are gone. Their SPDX licence ids come from the same report the in-app screen
  shows, so the SBOM and the app cannot disagree.

The result is deterministic for a commit: the document namespace is derived from
the version and commit, `created` is the commit date rather than the clock, the
temporary extraction path is normalised out of every SPDX id, and packages and
relationships are sorted. Two runs over the same commit produced byte-identical
files. The tool is pinned: `release.yml`
downloads `syft 1.33.0`, verifies it against the release's `checksums.txt`, and
records `Tool: syft-1.33.0` in the document.

What the SBOM does **not** contain: it does not list
every file in the APK (`filesAnalyzed: false` for the Maven packages), it does not
resolve licences for the Go modules (syft reports `NOASSERTION` where the module
does not declare one), and it says nothing about code that was shrunk away by R8.
It is an inventory of what was linked, not a legal opinion.


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
is derived from it, so every release is upgradeable. Without
`ANDROID_KEYSTORE_BASE64` the workflow still builds (validating R8/ProGuard against
the JNI bindings) but produces an unsigned APK, skips the signature check and does
not publish.

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
is derived from it, so every release is upgradeable. Without
`ANDROID_KEYSTORE_BASE64` the workflow still builds (validating R8/ProGuard against
the JNI bindings) but produces an unsigned APK, skips the signature check and does
not publish.

### Release procedure

`versionName` and `versionCode` have one source: `scripts/version.sh`, which
`app/build.gradle` calls. Nothing reads the clock, the workflow run number or a
hand-maintained constant.

| Input | `versionName` | `versionCode` |
| --- | --- | --- |
| `v1.2.3` tag on `HEAD` | `1.2.3` | `1002003` |
| untagged commit, 11 commits after `v0.9.0` | `0.9.0-dev.11+g1f5e2fa` | `9000` |
| no tags (exported tarball, or a shallow CI clone) | `0.0.0-dev` | `1` |
| `workflow_dispatch` with `version_name: 2.1.0` | `2.1.0` | `2001000` |
| `v1.2.3-rc1` tag, or any suffixed release input | rejected by `release.yml` | — |

The code is `major*1_000_000 + minor*1_000 + patch`, a pure function of the name,
so rebuilding the same commit or re-running the release workflow yields the same
code. It used to be the workflow run number, which changed on every run and could
not be reproduced; nothing has been published to Play, so there is no installed
version whose code we would have to stay ahead of. The largest value Play accepts
is 2 100 000 000; the script rejects any name whose derived code exceeds it,
and still limits minor/patch to 999 so two names cannot alias the same code —
`scripts/version.sh` fails loudly outside that. A checkout with no tags at all
derives `0`, which Android rejects as a `versionCode`, so it is clamped to `1`;
those builds are never published.

A published version must be **exactly `<major>.<minor>.<patch>` with no suffix**.
`v1.0.0-rc1` and `v1.0.0` both derive `1000000`, so allowing a pre-release tag
would mean the final release is refused by Play as a duplicate. `release.yml`
runs `scripts/version.sh release` on the tag (or the `version_name` input) before
it builds anything and fails the run otherwise. `-dev` names such as
`0.9.0-dev.11+g1f5e2fa` stay valid for local and CI builds, which are never
published; `scripts/check-release-version.sh` asserts both halves of that rule.

A release is then four steps:

1. Update the documentation in the same commit as the version, and keep it
   version-neutral: the header describes `main`, not the release being cut,
   because the tag itself keeps the documentation that shipped with it.
2. Run `scripts/check-version-consistency.sh`; it fails when `README.md` or
   `DOCS.md` pins the tree to a release, when a versioned link names a tag that
   does not exist, or when the core badge drifts from `core/core.properties`.
   `build.yml` runs the same script on every pull request and push.
3. Push the commit to `main`, wait for the checks, then `git tag vX.Y.Z` and
   `git push origin vX.Y.Z`. The tag is the version — there is no second place to
   type it in.
4. `release.yml` builds, signs, writes `SHA256SUMS.txt` and `BUILD-INFO.txt`,
   attests the artefacts, publishes the GitHub Release and uploads the AAB
   artifact. Getting that AAB onto a Play track is a manual step, not something CI
   does — the whole checklist is in [PLAY_RELEASE.md](PLAY_RELEASE.md).

### Reproducibility and provenance

What is deterministic, and how you can check it:

| Thing | Rule | Check |
| --- | --- | --- |
| `versionName`/`versionCode` | `git describe` (or the release input); code = `major*1e6 + minor*1e3 + patch` | `scripts/version.sh print` |
| Hysteria core | version + four SHA-256 in `core/core.properties`, verified after every fetch | `scripts/core.sh verify` |
| `libtun2socks.so` | `native/tun2socks/SHA256SUMS`, and a rebuild is byte-identical with NDK r25c | `scripts/tun2socks.sh verify` |
| `SHA256SUMS.txt` | sorted by file name, no clock | `sha256sum -c` |
| `BUILD-INFO.txt` | commit, versions, core, tun2socks hashes, certificate; sorted, no clock | attached to the release |
| Artefacts → workflow | Sigstore attestation from the release workflow's OIDC identity | `gh attestation verify <file> --repo noelbennettz/Hy2droid` |

What is **not** deterministic:

- **The APK/AAB bytes.** AGP writes ZIP entries with the build's file
  modifications times and does not honour `SOURCE_DATE_EPOCH`, so two builds of
  the same commit differ even before signing. There is no reproducible-APK claim
  here; the claim is that the inputs are pinned (core, tun2socks, dependency
  versions from `gradle/libs.versions.toml`), that the pinned binaries are
  hash-checked, and that the artefacts are attested to the commit and workflow
  that produced them.
- **`versionCode` monotonicity across tracks** is a Play Console fact, not a build
  fact: the code comes from the version name, so a rebuild of the same tag is the
  same code (Play refuses that upload as a duplicate, which is the intended
  outcome).
- **Signing time.** `apksigner` is not given a timestamp source. The v2/v3
  signature block carries no signing time, so the signature adds nothing that
  varies; the ZIP timestamps still do.

CI never reads the clock for anything that lands in an artefact, and never reads
`github.run_number` for a version. The one remaining clock dependency is the ZIP
timestamps above.

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
ships the version pinned in `core/core.properties`, and that pin is the only way
to change it — see [Bundled core](#bundled-core). A core bump needs no app
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
  and shortens its life. The clipboard is only read when *Import from link* is
  tapped, and the text it returns stays in the app: nothing is logged or sent.
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
- If a newly released core breaks connectivity, pin the previous one by moving
  `core/core.properties` back (`scripts/core.sh upgrade <previous tag>`) and open
  an issue. `HY2_CORE_TAG` is a local-only experiment: Gradle's `fetchCore` and CI
  use `fetch --pinned` and ignore it.
- Report bugs at
  [noelbennettz/Hy2droid/issues](https://github.com/noelbennettz/Hy2droid/issues)
  — the *Bug report* form asks for the version, device, Test tunnel result and the
  *Export logs* zip, whose config is redacted. Never paste a share link: it
  contains the password.

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
those are the ones a 16 KB-page kernel loads. `libtun2socks.so` is 16 KB aligned
even on the 32-bit ABIs — an unused alignment on 4 KB kernels, not a bug, and it
means one build rule instead of two.
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

