package us.leaf3stones.hy2droid.data

/**
 * Rules for folding an imported profile (deep link, QR code, editor paste) into
 * the stored list. Pure Kotlin so the behaviour is unit-testable.
 *
 * Re-importing the same share link used to append a second, identical node — and
 * after a device migration, where the secrets are gone, it appended a *broken*
 * node next to the old one. Matching on the node identity instead updates the
 * existing entry.
 */
object ProfileMerge {

    /**
     * True when both profiles point at the same account on the same node.
     *
     * Host and port alone are not enough: a provider can run several accounts on
     * one server, and importing the second account's link must add a profile
     * instead of overwriting the first one's password. A blank auth on either
     * side still matches, so a link without a secret fills in (or keeps) the
     * stored one.
     */
    fun sameNode(existing: Profile, incoming: Profile): Boolean =
        existing.port == incoming.port &&
            Profile.normalizeServer(existing.server)
                .equals(Profile.normalizeServer(incoming.server), ignoreCase = true) &&
            (existing.auth.isBlank() || incoming.auth.isBlank() || existing.auth == incoming.auth)

    /**
     * Fold [incoming] into [existing]: the id (and therefore the position and the
     * selection) is kept, and a blank field in the link never erases a stored
     * value — a bare `hysteria2://@host:port` re-import must not wipe the password.
     */
    fun merge(existing: Profile, incoming: Profile): Profile = incoming.copy(
        id = existing.id,
        name = incoming.name.ifBlank { existing.name },
        auth = incoming.auth.ifBlank { existing.auth },
        sni = incoming.sni.ifBlank { existing.sni },
        obfsPassword = incoming.obfsPassword.ifBlank { existing.obfsPassword },
        // The switch, the rates and fast open are not in a share link: importing
        // one must not turn any of them off. `insecure` can only be turned on by
        // a link (the editor is where it is turned off again).
        insecure = incoming.insecure || existing.insecure,
        fastOpen = existing.fastOpen,
        brutal = incoming.brutal || existing.brutal,
        txMbps = if (incoming.txMbps > 0) incoming.txMbps else existing.txMbps,
        rxMbps = if (incoming.rxMbps > 0) incoming.rxMbps else existing.rxMbps,
    )

    /** The profile to store for [incoming] given the [existing] list. */
    fun resolve(existing: List<Profile>, incoming: Profile): Profile =
        existing.firstOrNull { sameNode(it, incoming) }
            ?.let { merge(it, incoming) }
            ?: incoming
}
