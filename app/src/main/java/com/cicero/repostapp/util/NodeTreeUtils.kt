package com.cicero.repostapp.util

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import java.text.Normalizer
import java.util.Locale

/** Recursively search node tree for text matches. */
fun findNodesByText(root: AccessibilityNodeInfo?, text: String, maxDepth: Int = Int.MAX_VALUE): List<AccessibilityNodeInfo> {
    if (root == null) return emptyList()
    val normalized = text.normalize()
    val result = mutableListOf<AccessibilityNodeInfo>()
    fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > maxDepth) return
        val nodeText = node.text?.toString() ?: node.contentDescription?.toString()
        if (nodeText?.normalize()?.contains(normalized) == true) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            traverse(node.getChild(i), depth + 1)
        }
    }
    traverse(root, 0)
    return result
}

/** Check that every text is present somewhere in the tree. */
fun containsAllTexts(root: AccessibilityNodeInfo?, texts: List<String>, maxDepth: Int = Int.MAX_VALUE): Boolean {
    return texts.all { findNodesByText(root, it, maxDepth).isNotEmpty() }
}

/** Perform safe click on visible, enabled node. */
fun safeClick(node: AccessibilityNodeInfo?): Boolean {
    if (node == null || !node.isVisibleToUser || !node.isEnabled) return false
    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
}

/** Traverse up the parent chain to find a clickable node. */
fun AccessibilityNodeInfo.traverseParentToFindClickable(): AccessibilityNodeInfo? {
    var current: AccessibilityNodeInfo? = this
    while (current != null && !current.isClickable) {
        current = current.parent
    }
    return current
}

/** Debug helper to log the node tree structure. */
fun logNodeTree(node: AccessibilityNodeInfo?, depth: Int = 0) {
    if (node == null) return
    val indent = "  ".repeat(depth)
    val text = node.text ?: node.contentDescription
    Log.d("NodeTree", "$indent${node.className} text=$text clickable=${node.isClickable}")
    for (i in 0 until node.childCount) {
        logNodeTree(node.getChild(i), depth + 1)
    }
}

/** Normalize string for comparison (lowercase, trim, remove diacritics). */
fun String.normalize(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .lowercase(Locale.ROOT)
    .trim()
    .replace(Regex("\\p{Mn}+"), "")
