# libtun2socks.so — hev-socks5-tunnel + blocked-port rules

`app/src/main/jniLibs/<abi>/libtun2socks.so` is the TUN ↔ SOCKS5 bridge the
`VpnService` uses. It is not built from source in this repository: it is
[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) (MIT, C) at the
version pinned in `scripts/tun2socks.sh`, plus the two patches in this
directory.

```
scripts/tun2socks.sh build          # rebuild all four ABIs into app/src/main/jniLibs
scripts/tun2socks.sh build arm64-v8a
scripts/tun2socks.sh info
```

The built `.so` files are committed, so a normal `./gradlew assembleDebug` (and
CI) never needs the NDK. You only need one when a patch or the pinned upstream
tag changes.

## The patches

**`0001-block-ports.patch`** — adds `misc.block-tcp` / `misc.block-udp` to the
YAML config (comma separated ports and ranges, e.g. `"25,465,8000-8100"`):

- a blocked **TCP** destination port is answered with `ERR_RST` in
  `tcp_accept_handler`, so the client sees a reset immediately and can fall back
  to another protocol instead of waiting for the proxy to time out;
- a blocked **UDP** destination port is dropped in `udp_recv_handler` before any
  session is created, so the datagram never reaches the Hysteria core.

Both hooks use `pcb->local_ip` / `pcb->local_port`, which lwIP fills with the
*original destination* of the intercepted packet (see
`hev-socks5-session-{tcp,udp}.c`).

Motivation: Hysteria 2 forwards UDP through its own QUIC connection and cannot
accelerate it, so QUIC (UDP/443) inside the tunnel is much slower than TCP on
lossy networks. Refusing UDP/443 makes browsers and apps use TCP/TLS instead.
The app exposes this in Settings → Blocked traffic.

**`0002-jni-contract.patch`** — renames the JNI entry points to the ones
`Hysteria2VpnService` declares (`startTun2socks`, `stopTun2socks`,
`getTun2socksStats`) and drops the unused `isRunning` binding.

## Mapped DNS (`mapdns`): what the app uses for DNS

> A resolver bound to port 53 inside the app is impossible on Android
> (`ip_unprivileged_port_start` is 1024, so `bind()` fails with EACCES), which is
> why DNS is answered here instead of in Kotlin.

2.19.3 already answers DNS locally when the YAML has a top-level `mapdns` block,
and it converts a mapped address back to a **name** before dialling
(`src/misc/hev-utils.c:hev_socks5_addr_from_lwip()` →
`hev_mapped_dns_lookup()` → `ATYP_DOMAIN`, on both the TCP and the UDP session
path). The app relies on this.

The answers are *fake* addresses out of a reserved pool (`network | index`, TTL
1 second). Two things follow, and Settings says so: an app that resolves a name
and connects much later can reach a pool address that no longer maps to
anything, and AAAA is answered with "no records", so IPv6-only destinations do
not resolve in this mode. The switch (Settings -> Network -> "Answer DNS in the
tunnel") turns the block off and falls back to forwarded resolvers.

The app renders the block from `proxy/Tun2socksConfig.kt` (`mapDns = true`) and
pushes `Tun.MAPPED_DNS` as the only resolver; port 53 is carved out of
`misc.block-udp` so a user rule cannot shadow it. All of that is configuration,
so `scripts/tun2socks.sh` still applies exactly the two patches above.

## 16 KB page size

`libtun2socks.so` is linked with `-Wl,-z,max-page-size=16384` on **all four**
ABIs, including the two 32-bit ones, and `scripts/tun2socks.sh verify` fails when
any LOAD segment is aligned below that. 32-bit *devices* only ever run 4 KB
kernels, so the flag costs nothing there; it just means a rebuild cannot
silently produce a library that the 64-bit 16 KB-page devices refuse to load.
Android 15+ maps libraries on 16 KB kernels, so this is the check that matters.

The bundled Hysteria core is an upstream prebuilt and is different: its
`arm64-v8a`/`x86_64` binaries are 16 KB aligned, its `armeabi-v7a`/`x86` ones
are still 4 KB. `scripts/core.sh verify` only enforces the rule on the 64-bit
ABIs, and `scripts/tun2socks.sh info` prints the alignment of whatever core is
bundled so a future bump is visible.

## Which binary is this? The SHA-256 manifest

`native/tun2socks/SHA256SUMS` pins the four committed libraries:

```
scripts/tun2socks.sh verify          # exits non-zero on a byte that moved
scripts/tun2socks.sh sha256          # print the manifest for the current files
```

`verify` also checks that the manifest has exactly one entry per ABI, so a
library cannot be added or dropped without the manifest following it. CI runs it
on every pull request (`build.yml`) and after every rebuild (`native.yml`).

## Reproducibility

The inputs are pinned: the upstream tag (`HEV_TAG` in `scripts/tun2socks.sh`,
`2.19.3`, whose commit `git clone --branch <tag>` resolves to), the two patches
in this directory, the NDK (r25c, the revision recorded in `.note.android.ident`
of each library), and the flags in `cmd_build`. Nothing reads the clock or the
network beyond that clone.

The one thing that used to move was the build path: the sources are compiled
with absolute file names, `__FILE__` strings end up in the merged string table,
and the link order of those strings depends on their content — so a build in
`/tmp/tmp.uV85bwFKVk` and one in `/tmp/tmp.Xi0Y0axBmp` had different bytes even
though they were the same code. `build` therefore passes

```
-ffile-prefix-map=<checkout>=/hev-socks5-tunnel
-fdebug-prefix-map=<checkout>=/hev-socks5-tunnel
```

Measured on 2026-11-20 with NDK 25.2.9519653, `arm64-v8a`, two builds in two
different directories: identical SHA-256 (`0d0ee296…9670c`, 343 936 bytes).
Before the prefix map the same pair differed in 16 330 of 344 256 bytes.

What is still not reproducible, and cannot be checked by CI:

- **The toolchain.** A different NDK revision produces different code. The
  version is recorded in `.note.android.ident` but `build` accepts whatever
  `ANDROID_NDK_HOME`/`sdkmanager` gives it; use 25.2.9519653 (r25c) for a
  byte-identical result. `native.yml` installs exactly that.
- **Stripping.** `ndk-build` installs the library as built; nothing re-strips or
  re-signs it afterwards, which is what keeps the hash stable. Do not run
  `llvm-strip` on these files.
- **The manifest is a pin, not a proof.** CI compares the committed files with
  the checked-in hashes, so a change is always visible and reviewable, but only
  `native.yml` (or a local run) actually rebuilds and can show that the hash is
  reachable from the pinned sources.

To reproduce and compare:

```bash
ANDROID_NDK_HOME=/path/to/android-ndk-r25c scripts/tun2socks.sh build
scripts/tun2socks.sh sha256 | diff native/tun2socks/SHA256SUMS -   # empty = identical
```

## Testing the rule parser without a device

`block-ports-test.c` exercises `hev_config_init_from_str` and the two getters on
the host (it needs no TUN):

```bash
HEV=$(scripts/tun2socks.sh source)
cd "$HEV" && make static
gcc -O1 -o /tmp/block-ports-test "$OLDPWD"/native/tun2socks/block-ports-test.c \
    bin/libhev-socks5-tunnel.a third-part/lwip/bin/liblwip.a \
    third-part/yaml/bin/libyaml.a third-part/hev-task-system/bin/libhev-task-system.a \
    -I src -I src/core/include -I third-part/yaml/include \
    -I third-part/lwip/src/include -I third-part/lwip/src/ports/include \
    -I third-part/hev-task-system/include -lpthread
/tmp/block-ports-test
```

## Constraints

- The library name (`libtun2socks.so`), the class (`us.leaf3stones.hy2droid.proxy.Hysteria2VpnService`)
  and the three native method signatures are a binary contract with the app.
  Change them together, and keep the R8 `-keep` rules in
  `app/proguard-rules.pro` in sync.
- The upstream library is built with NDK r25c (`ANDROID_NDK_HOME`), the same
  toolchain the current binaries came from; `ndk-build` output is installed over
  `app/src/main/jniLibs/<abi>/libtun2socks.so`.
- Bumping upstream: change `HEV_TAG` in `scripts/tun2socks.sh`, check that both
  patches still apply (`git apply` will say so), rebuild, and re-check the
  tunnel on a device.
