package us.leaf3stones.hy2droid.ui

import androidx.compose.runtime.saveable.listSaver

/**
 * Whether the Advanced group on the settings screen is open.
 *
 * Collapsed on first open, and remembered only through `rememberSaveable`: the
 * state belongs to the screen, not to the app's settings store. Nothing about
 * this class persists, so a fresh launch is always collapsed again.
 */
class AdvancedSectionState private constructor(val isExpanded: Boolean) {

    fun toggled(): AdvancedSectionState = AdvancedSectionState(!isExpanded)

    companion object {
        /** The state a newly opened settings screen starts from. */
        val collapsed: AdvancedSectionState = AdvancedSectionState(isExpanded = false)

        /** For `rememberSaveable(stateSaver = ...)`; survives rotation, not a restart. */
        val Saver = listSaver<AdvancedSectionState, Boolean>(
            save = { listOf(it.isExpanded) },
            restore = { AdvancedSectionState(it.first()) },
        )
    }
}
