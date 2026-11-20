# Security policy

Hy2droid is a VPN client: it moves every packet the device sends through a server
the user supplies, and it holds the passwords that open that server. A bug in it
can expose either. This file says what is in scope, how to report it, and what to
expect. [DOCS.md](DOCS.md#security-notes) describes how the app stores secrets and
what it deliberately does not protect against; the two files are meant to be read
together and must not contradict each other.

## Supported versions

| Version | Supported |
| --- | --- |
| The newest release on the [Releases](https://github.com/noelbennettz/Hy2droid/releases) page | Yes |
| `main` (the tip of the default branch) | Yes |
| Any older release | No — update first, then report if it still applies |

There are no long-term support branches and no backports: a fix ships in the next
release built from `main`.

## Reporting a vulnerability

Use GitHub's private reporting form: **Security → Report a vulnerability** on
<https://github.com/noelbennettz/Hy2droid>. It creates a private advisory that
only the maintainers can see, which is the place for technical detail, proof of
concept and affected versions.

If that button is unavailable, open a normal issue titled exactly
`Security contact request` with **no technical detail in it at all** — no stack
trace, no sample link, no server address, no log excerpt. Say only that you have
a report and would like a private channel; the maintainer will open one in the
advisory and you can send the details there.

There is no security email address. Please do not send reports to any address you
may find elsewhere in the project's history, and do not put a report in a public
issue, a discussion or a pull request.

No response time is promised. This is a volunteer project; reports are read when
someone is available, and fixes ship when they are ready.

## In scope

- The app itself: profiles, the profile editor, share-link and QR import
  (`hysteria2://` parsing), the profile store and the settings store.
- The `VpnService` and the TUN handling: routing, split tunnelling, the
  blocked-port rules, DNS handling, and the handover between networks.
- Certificate policy handling, in particular whether `insecure=1` /
  *Allow insecure certificate* can be set, kept or applied in a way the user did
  not choose, or can leak into a profile that did not ask for it.
- Where secrets end up: the YAML handed to the core, the diagnostics export, the
  notification, the logs, `FLAG_SECURE` on the screens that show secrets, and the
  clipboard marking.
- The build and release chain: the signing configuration and the published
  artefacts, including the checksums and certificate fingerprint documented in
  the README, and anything that would let a third party publish an update that
  installs over a genuine one.

## Out of scope

- **Bugs in the upstream Hysteria core.** `libhysteria.so` is an unmodified build
  of [apernet/hysteria](https://github.com/apernet/hysteria); protocol-level
  findings belong there, through its own security process
  (<https://github.com/apernet/hysteria/security>). Report it upstream; this
  project only picks up the fixed version.
- **Bugs in hev-socks5-tunnel**, the upstream of `libtun2socks.so` (patched
  copies of the patches live in `native/tun2socks/`): report to
  <https://github.com/heiher/hev-socks5-tunnel>. A bug in one of *our* patches is
  in scope, so send that one here.
- **Configurations the user chose on purpose.** A profile with
  *Allow insecure certificate* on accepts any certificate; a server that does not
  authenticate its clients is reachable by anyone who knows the password; a
  share link pasted from an untrusted source carries whatever it carries. None of
  that is a vulnerability in the app.
- **The server side.** The Hysteria 2 server, its configuration, its TLS
  certificate and its host are not part of this project.
- **Anything that needs an already-compromised device.** A rooted phone, `adb` on
  a debuggable build, a malicious app with device-admin rights, or a physical
  attacker who can unlock the phone: [DOCS.md](DOCS.md#security-notes) says
  plainly which of those the app does not defend against, and that is by design.
- Reports produced only by a scanner, with no explanation of what an attacker
  gains.
