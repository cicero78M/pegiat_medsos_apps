package com.cicero.repostapp

import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import java.util.Locale

/** Utility functions for working with AccessibilityNodeInfo trees. */

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

/** Normalize string for comparison (lowercase, trim, remove diacritics). */
fun String.normalize(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .lowercase(Locale.ROOT)
    .trim()
    .replace(Regex("\\p{Mn}+"), "")
