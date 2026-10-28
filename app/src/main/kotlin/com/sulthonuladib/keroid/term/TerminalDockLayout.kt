package com.sulthonuladib.keroid.term

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

/**
 * Measures the floating dock before the canvas, without a composition/layout
 * feedback loop. The clearance provider is read by the renderer's Draw and
 * pointer handlers, and when a settled geometry request reserves rows; it is
 * never read in composition, so a dock fold can neither loop layout nor move
 * the canvas.
 */
@Composable
fun TerminalDockLayout(
    modifier: Modifier = Modifier,
    dock: @Composable () -> Unit,
    content: @Composable (bottomOcclusion: () -> Int) -> Unit,
) {
    val clearance = remember { mutableIntStateOf(0) }
    Layout(modifier = modifier, content = {
        Box { content { clearance.intValue } }
        Box { dock() }
    }) { children, constraints ->
        val dockPlaceable = children[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        clearance.intValue = dockPlaceable.height
        val canvas = children[0].measure(constraints)
        layout(canvas.width, canvas.height) {
            canvas.place(0, 0)
            dockPlaceable.place(canvas.width - dockPlaceable.width, canvas.height - dockPlaceable.height)
        }
    }
}
