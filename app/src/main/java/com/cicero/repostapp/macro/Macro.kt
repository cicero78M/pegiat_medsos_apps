package com.cicero.repostapp.macro

/** Simple container for a list of macro actions. */
data class Macro(
    val name: String,
    val actions: MutableList<MacroAction>
)
