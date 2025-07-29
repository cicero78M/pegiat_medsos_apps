package com.cicero.repostapp

import androidx.core.os.bundleOf

class PremiumPostFragment : DashboardFragment() {
    companion object {
        fun newInstance(userId: String?, token: String?): PremiumPostFragment {
            val frag = PremiumPostFragment()
            frag.arguments = bundleOf("userId" to userId, "token" to token)
            return frag
        }
    }

    override fun showShareDialog(post: InstaPost) {
        val options = mutableListOf("Instagram", "Facebook", "Twitter", "TikTok")
        if (post.isVideo) {
            options.add("YouTube")
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setItems(options.toTypedArray()) { _, which ->
                val pkg = when (options[which]) {
                    "Instagram" -> "com.instagram.android"
                    "Facebook" -> "com.facebook.katana"
                    "Twitter" -> "com.twitter.android"
                    "TikTok" -> "com.zhiliaoapp.musically"
                    "YouTube" -> "com.google.android.youtube"
                    else -> null
                }
                sharePost(post, pkg)
            }
            .show()
    }
}
