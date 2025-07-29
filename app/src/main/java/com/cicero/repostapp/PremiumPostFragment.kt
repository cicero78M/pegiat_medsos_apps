package com.cicero.repostapp

import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import android.widget.TextView

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
                when (options[which]) {
                    "Instagram" -> shareViaInstagram(post)
                    "Facebook" -> sharePost(post, "com.facebook.katana")
                    "Twitter" -> sharePost(post, "com.twitter.android")
                    "TikTok" -> sharePost(post, "com.zhiliaoapp.musically")
                    "YouTube" -> sharePost(post, "com.google.android.youtube")
                    else -> sharePost(post)
                }
            }
            .show()
    }

    private fun shareViaInstagram(post: InstaPost) {
        val view = layoutInflater.inflate(R.layout.dialog_progress, null)
        val progressText = view.findViewById<TextView>(R.id.text_progress)
        progressText.text = "Menyiapkan..."
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            progressText.text = "Memuat sesi..."
            val client = withContext(Dispatchers.IO) {
                InstagramShareHelper.loadClient(requireContext())
            }
            if (client == null) {
                dialog.dismiss()
                Toast.makeText(requireContext(), "Autopost Instagram belum login", Toast.LENGTH_SHORT).show()
                return@launch
            }
            progressText.text = "Mengunggah..."
            val link = withContext(Dispatchers.IO) {
                InstagramShareHelper.uploadPost(requireContext(), client, post)
            }
            dialog.dismiss()
            if (link != null) {
                Toast.makeText(requireContext(), "Berhasil upload", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Gagal upload ke Instagram", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
