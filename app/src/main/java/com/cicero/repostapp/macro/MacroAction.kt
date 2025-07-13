package com.cicero.repostapp.macro

/** Simple sealed class representing an action for the macro accessibility service. */
sealed class MacroAction {
    data class Click(val x: Int, val y: Int) : MacroAction()
    data class SetText(val text: String) : MacroAction()
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int) : MacroAction()
    /**
     * Repost the given media URL to multiple platforms with the provided
     * caption. This high level action downloads the content then triggers
     * uploads to Instagram, X/Twitter, TikTok and YouTube.
     */
    data class Repost(val url: String, val caption: String) : MacroAction()
}
