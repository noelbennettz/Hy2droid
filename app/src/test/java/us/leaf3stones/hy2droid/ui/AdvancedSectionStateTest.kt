package us.leaf3stones.hy2droid.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedSectionStateTest {

    @Test
    fun `a freshly opened settings screen starts collapsed`() {
        assertFalse(AdvancedSectionState.collapsed.isExpanded)
    }

    @Test
    fun `toggling opens and closes the group`() {
        val open = AdvancedSectionState.collapsed.toggled()
        assertTrue(open.isExpanded)
        assertFalse(open.toggled().isExpanded)
    }
}
