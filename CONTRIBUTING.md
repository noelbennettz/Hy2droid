# Contributing to Hy2droid

Thanks for looking. This file covers the development environment, the sign-off
rule every commit has to follow, and what a change is expected to include. The
short version: build it locally, run the same checks CI runs, sign off your
commits, and keep the app free of Play Services, analytics, crash reporting and
ads.

## Development environment

- JDK 17 — Gradle refuses to start without it.
- Android SDK platform 37 (`compileSdk 37`, `targetSdk 37`); `apksigner` from
  build-tools is needed only if you touch the release scripts.
- No NDK: `libtun2socks.so` is committed and the Hysteria core is downloaded
  during the build.

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties   # gitignored
./gradlew assembleDebug                           # app/build/outputs/apk/debug/
```

`local.properties` is not optional on a fresh clone: without it Gradle cannot
find the SDK. The first build downloads the core pinned in
`core/core.properties` into `app/src/main/jniLibs/<abi>/libhysteria.so` and
verifies its checksum.

## Before you open a pull request

Run the same gates CI runs:

```bash
./scripts/check-style.sh                 # whitespace, import order, line width
./scripts/check-locales.sh               # identical key sets in every translation
./scripts/check-version-consistency.sh   # README/DOCS vs the newest pushed tag
./gradlew testDebugUnitTest lintDebug    # lint errors fail the build
```

`scripts/check-style.sh --fix` rewrites the mechanical violations (trailing
whitespace, missing or doubled final newline); import order and line width it
only reports. Also run `./gradlew licenseeRelease` if you add or bump a
dependency, and `./gradlew assembleRelease` if you touch R8 or the JNI bindings.
If a change touches a screen or anything that needs an Android runtime, run
`./gradlew connectedDebugAndroidTest` with a device or emulator attached (API 30+)
— CI runs it on every pull request, but a failure there costs a round trip.
Bumping the app version is not a Gradle change: `scripts/version.sh` derives it
from the git tag (see DOCS > Release procedure), and
`scripts/check-version-consistency.sh` fails a pull request whose documentation
still names an older release.

Keep a change small and explained: what it changes, why, and what you tested. If
it changes behaviour a user can see, update `DOCS.md` and both language versions
of the site — they live in the
[`noelbennettz.github.io`](https://github.com/noelbennettz/noelbennettz.github.io)
repository under `docs/Hy2droid/` (`index.html` and `zh.html`), so a site change
is a pull request against that repository, not this one. A change that affects an
existing release's behaviour is documented under `versions/<tag>/` on the project
site, as the README says.

## Commit sign-off (DCO)

Every commit in a pull request must carry a `Signed-off-by:` line, which is how
the [Developer Certificate of Origin 1.1](https://developercertificate.org/)
is asserted: you wrote the change, or you have the right to submit it under the
project's licence (AGPL-3.0). The full text is short and worth reading;
<https://github.com/apps/dco> documents the same rule, but no GitHub App is
involved here — `.github/workflows/dco.yml` checks it on every pull request.

Add the line with `git commit -s`, which appends it using your git identity:

```
Signed-off-by: Your Name <you@example.com>
```

Set that identity first:

```bash
git config user.name  "Your Name"
git config user.email "you@example.com"
```

The sign-off has to use the same address as the commit author, and a commit
whose author is a bot (`<name>[bot]`) is skipped by the check. To fix a missing
sign-off, amend or rebase (`git commit --amend -s`, `git rebase --signoff
origin/main`) and force-push your own branch. The check runs on the pull request,
so a failing run lists the commit that is missing it.

## What this project will not accept

- Google Play Services, Firebase, any analytics or crash-reporting SDK, ad SDKs,
  or other proprietary dependencies. The app promises none of those.
- Telemetry, "phone home" update checks, or anything that sends data anywhere
  except the user's own server.
- A change that weakens the certificate policy by default, or that stores a
  secret outside the app's private directory.
- Rewriting history of `main`, force-pushing a shared branch, or editing a
  published release.

## Reporting a vulnerability

Not here: see [SECURITY.md](SECURITY.md). Do not put technical detail about a
vulnerability in an issue or a pull request.
