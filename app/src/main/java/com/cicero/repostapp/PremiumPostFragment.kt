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
}
