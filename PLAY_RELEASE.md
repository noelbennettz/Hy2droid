# Publishing to Google Play

CI builds and signs the bundle; a human does everything that touches the Play
Console. **No workflow calls the Play Developer API, uploads anything to a track,
or creates a release.** If this file and the Console disagree, the Console is
right — update this file.

No submission has been made yet: there is no Play listing, so the answers below
are what the code and manifest actually do, written down so the forms can be
filled in without guessing.

## What CI produces

`release.yml` runs on a `v*` tag (or manually with a version) and leaves:

| Artefact | Where | What it is |
| --- | --- | --- |
| `app-release.aab` | workflow artifact `hy2droid-release-aab` | the signed bundle Play accepts |
| `app-*-release.apk` | artifact `hy2droid-release-apk`, and the GitHub Release | sideloading builds, same signature |
| `SHA256SUMS.txt` | artifact `hy2droid-release-checksums`, and the GitHub Release | `sha256sum -c` file for the APKs |
| `BUILD-INFO.txt` | GitHub Release | commit, version/versionCode, core version, `libtun2socks.so` hashes, signing certificate |
| attestation | the repository's attestation log | `gh attestation verify <file> --repo noelbennettz/Hy2droid` |

Download the AAB from the run's *Artifacts* section (it is not attached to the
GitHub Release, because it is not a user-facing download), and verify it first:

```bash
gh attestation verify app-release.aab --repo noelbennettz/Hy2droid
```

The signing key is `key/Hy2droid/production.jks`; the same certificate signs every
APK and the bundle. Enrol it as the **upload key** in Play App Signing, or Play
rejects the bundle with a certificate mismatch. Losing that key while it is the
upload key means a Play support request; losing it while it is the app signing key
means a new package name. Keep a copy outside this machine.

## Before the first upload

1. **App created in the Console** with package id `us.leaf3stones.hy2droid` — it
   matches `applicationId` in `app/build.gradle` and cannot be changed later.
2. **Store listing**: title (≤30 chars), short description (≤80), full
   description (≤4000), app icon 512×512 PNG, feature graphic 1024×500, at least
   two phone screenshots. The screenshots in `screenshots/` are unmodified
   captures of the app; the icon is `app/src/main/res/mipmap-*/ic_launcher`.
3. **Privacy policy URL** — mandatory, a public page. It has to say what this app
   does with data, and the truthful answer is short:
   - profiles, settings and logs live only in the app's private storage;
   - nothing is sent to the developer: no analytics, crash reporting, account,
     advertising or third-party SDK with network access;
   - the camera is used only to decode a QR code inside the app, and the
     notifications are local;
   - traffic goes to the server the user configured, not through an intermediary;
   - uninstalling (or Android's *Clear storage*) deletes the local data, and
     Android Auto Backup may copy it to the user's own Google account.
   Write it in the project's own words; do not copy a template that claims
   collection the app does not do, or denies something it does.
4. **Data safety form** — the app collects and shares nothing:
   - *Does your app collect or share any of the required user data types?* **No**.
     No account, no identifiers, no location, no diagnostics, no purchase history.
   - *Is all user data encrypted in transit?* Not applicable — nothing is
     collected. Traffic to the user's own server is TLS from `VpnService`, but it
     is not data sent to the developer.
   - *Can users request deletion?* Not applicable. Local deletion is uninstall or
     clear storage; say so if the form asks.
   - The form must match the uploaded AAB: nothing was added that would change
     this answer, and the allowlist in `app/build.gradle` plus `NOTICE` are the
     list of what ships.
5. **Foreground service types** — the manifest declares
   `android:foregroundServiceType="specialUse"` on `Hysteria2VpnService` with
   `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "vpn"`, because the tunnel has
   to stay up while the screen is off. The Console asks for a description; use
   something like: *"A user-started VPN tunnel (VpnService) that keeps the
   connection to the server the user configured alive while the app is in the
   background or the screen is off. It stops when the user disconnects or the
   system revokes the VPN."* Play may also ask for a short screen recording of
   that flow; record it on a device, not from CI.
6. **VPN policy** — Play's VPN policy applies: the app must use `VpnService`, must
   not redirect or monetise traffic, and the listing must not imply anonymity. The
   first is structural; do not write "anonymous" in the description.
7. **Content rating**, **target audience**, **ads** ("no ads"), **news app**
   ("no"), **government app** ("no"), **financial features** ("none"), **health**
   ("none") — the honest answers for a network tool.
8. **App access** — the app has no login, but a reviewer needs a server to see it
   connect. In *App access* give the steps ("paste a `hysteria2://` link or scan
   the attached QR code; the sample server is …") and a link that works during
   the review. Without this, review usually fails as "not functional".
9. **Target API level** policy: `targetSdk` is 37, above the current requirement.

## Upload to the internal track

1. Console → *Testing* → *Internal testing* → *Create new release*.
2. Upload the AAB. Play re-signs it with the app signing key; the uploaded
   certificate only has to match the enrolled upload key.
3. Release notes: copy them from the GitHub Release for the same version
   (`gh release view v<version> --json body`), or write one line.
4. *Review release* → *Start rollout to Internal testing*.
5. Add testers (an email list or a Google Group) and install from the opt-in URL
   on a real device with a real server. **This is the step CI cannot cover**: no
   test in this repository starts a Hysteria 2 tunnel.
6. Only after that, promote the same bundle to closed/open/production. Production
   rollouts start at a small percentage; watch the crash-free rate before
   increasing it.

## Version codes

`versionCode` is a pure function of the version name
(`major*1_000_000 + minor*1_000 + patch`, see `scripts/version.sh`), so it only
grows when the version does. Play refuses a bundle whose code it has already seen,
and refuses anything lower than the newest one on a track. Do not reuse a tag:
build a new version instead.

## What is intentionally not automated

- No Play Developer API, no service account, no `r0adkll/upload-google-play`
  step. A token that can publish to production does not belong in a repository
  whose releases are built on a public runner.
- No `bundletool`-generated APK set: internal testing installs from the Play link.
- No screenshot generation and no store-listing text in this repository: the
  listing is edited in the Console, and the site in
  [`noelbennettz.github.io`](https://github.com/noelbennettz/noelbennettz.github.io)
  is separate from it.


