package us.leaf3stones.hy2droid.data

/** One dependency or bundled binary as the licences screen lists it. */
data class LicenseEntry(
    val name: String,
    val version: String,
    val license: String,
    /** Asset holding the full licence text, for the two native components. */
    val licenseTextAsset: String? = null,
)

/**
 * The data behind *Settings → About → Open source licenses*.
 *
 * Nothing here touches the network: the dependency list is generated at build
 * time from Licensee's report (`licenseeAssets` in `app/build.gradle`) and
 * shipped as an asset, and the two native components are described in code with
 * their licence texts in `assets/licenses/`.
 */
object ThirdPartyLicenses {

    /** Tab-separated `name<TAB>version<TAB>licence`, one dependency per line. */
    const val DEPENDENCIES_ASSET = "licenses/android-dependencies.txt"

    /** MIT text of the bundled Hysteria 2 core. */
    const val HYSTERIA_LICENSE_ASSET = "licenses/hysteria-LICENSE.txt"

    /** MIT text of hev-socks5-tunnel, the upstream of `libtun2socks.so`. */
    const val TUN2SOCKS_LICENSE_ASSET = "licenses/hev-socks5-tunnel-LICENSE.txt"

    /** Name shown for the Go core; its version comes from the binary itself. */
    const val HYSTERIA_NAME = "Hysteria 2 core"

    /** Name shown for the tunnel engine; the pin lives in scripts/tun2socks.sh. */
    const val TUN2SOCKS_NAME = "hev-socks5-tunnel"

    /**
     * Parses the generated report. A malformed line is dropped rather than
     * crashing the screen, so a truncated or hand-edited report shows fewer rows
     * instead of taking the app down.
     */
    fun parseDependencies(text: String): List<LicenseEntry> = text.lineSequence()
        .map(String::trimEnd)
        .filter(String::isNotEmpty)
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val name = parts[0].trim()
            val version = parts[1].trim()
            val license = parts[2].trim()
            if (name.isEmpty() || license.isEmpty()) null else LicenseEntry(name, version, license)
        }
        .toList()

    /**
     * The two native components that ship inside the APK and are not Maven
     * artefacts, so Licensee cannot see them. [hysteriaVersion] is what the
     * bundled binary reports; [tun2socksVersion] is the upstream tag the
     * committed `.so` files were built from.
     */
    fun nativeComponents(
        hysteriaVersion: String?,
        tun2socksVersion: String,
    ): List<LicenseEntry> = listOf(
        LicenseEntry(
            name = HYSTERIA_NAME,
            version = hysteriaVersion ?: "bundled",
            license = "MIT",
            licenseTextAsset = HYSTERIA_LICENSE_ASSET,
        ),
        LicenseEntry(
            name = TUN2SOCKS_NAME,
            version = tun2socksVersion,
            license = "MIT",
            licenseTextAsset = TUN2SOCKS_LICENSE_ASSET,
        ),
    )
}
