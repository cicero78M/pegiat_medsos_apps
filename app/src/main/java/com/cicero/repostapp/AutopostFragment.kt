package com.cicero.repostapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/** Simple placeholder fragment replacing the old Macro page. */
class AutopostFragment : Fragment() {

    companion object {
        fun newInstance(): AutopostFragment = AutopostFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_autopost, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val loginButton = view.findViewById<android.widget.Button>(R.id.button_instagram_login)
        val usernameInput = view.findViewById<android.widget.EditText>(R.id.input_instagram_username)
        val passwordInput = view.findViewById<android.widget.EditText>(R.id.input_instagram_password)

        loginButton.setOnClickListener {
            val user = usernameInput.text.toString().trim()
            val pass = passwordInput.text.toString().trim()
            if (user.isEmpty() || pass.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "Username dan password wajib diisi", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(requireContext(), "Login $user - fitur belum siap", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
