package com.cicero.repostapp.macro

/** Simple sealed class representing an action for the macro accessibility service. */
sealed class MacroAction {
    data class Click(val x: Int, val y: Int) : MacroAction()
    data class SetText(val text: String) : MacroAction()
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int) : MacroAction()
}
