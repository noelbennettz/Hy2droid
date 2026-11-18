package us.leaf3stones.hy2droid.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule behind refusing to delete a profile: only the entry the tunnel was
 * built for (or is being built for) is protected, and only while that tunnel is
 * running or moving between states.
 */
class ProfileInUseTest {

    @Test
    fun `the connected profile is in use`() {
        assertTrue(isProfileInUse("a", "a", TunnelState.Connected))
    }

    @Test
    fun `the profile being connected is already in use`() {
        assertTrue(isProfileInUse("a", "a", TunnelState.Connecting))
    }

    @Test
    fun `the profile being torn down is still in use`() {
        assertTrue(isProfileInUse("a", "a", TunnelState.Disconnecting))
    }

    @Test
    fun `an idle tunnel protects nothing`() {
        assertFalse(isProfileInUse("a", "a", TunnelState.Idle))
    }

    @Test
    fun `a failed tunnel protects nothing`() {
        assertFalse(isProfileInUse("a", "a", TunnelState.Error("boom")))
    }

    @Test
    fun `another profile is not protected by the running tunnel`() {
        assertFalse(isProfileInUse("b", "a", TunnelState.Connected))
    }

    @Test
    fun `no active profile protects nothing`() {
        assertFalse(isProfileInUse("a", null, TunnelState.Connected))
    }
}
