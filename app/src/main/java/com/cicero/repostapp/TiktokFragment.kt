package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButton

class TiktokFragment : Fragment(R.layout.fragment_tiktok) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val loginContainer: View = view.findViewById(R.id.login_container)
        val profileContainer: View = view.findViewById(R.id.profile_container)
        val statusView: TextView = view.findViewById(R.id.text_tiktok_status)
        val loginButton: MaterialButton = view.findViewById(R.id.button_tiktok_login)
        val logoutButton: MaterialButton = view.findViewById(R.id.button_tiktok_logout)

        val launcher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                statusView.text = getString(R.string.login)
                profileContainer.visibility = View.VISIBLE
                loginContainer.visibility = View.GONE
            }
        }

        loginButton.setOnClickListener {
            launcher.launch(android.content.Intent(requireContext(), TiktokLoginActivity::class.java))
        }

        logoutButton.setOnClickListener {
            TiktokSessionManager.clear(requireContext())
            loginContainer.visibility = View.VISIBLE
            profileContainer.visibility = View.GONE
            statusView.text = getString(R.string.not_logged_in)
        }

        if (TiktokSessionManager.loadCookies(requireContext()) != null) {
            displayProfile(loginContainer, profileContainer)
        }
    }

    private fun displayProfile(loginContainer: View, profileContainer: View) {
        loginContainer.visibility = View.GONE
        profileContainer.visibility = View.VISIBLE
    }
}
