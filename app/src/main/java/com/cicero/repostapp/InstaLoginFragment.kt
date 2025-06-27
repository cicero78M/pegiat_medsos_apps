package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment

class InstaLoginFragment : Fragment(R.layout.fragment_insta_login) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val username = view.findViewById<EditText>(R.id.input_username)
        val password = view.findViewById<EditText>(R.id.input_password)
        view.findViewById<Button>(R.id.button_login_insta).setOnClickListener {
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()
            if (user.isNotBlank() && pass.isNotBlank()) {
                Toast.makeText(requireContext(), "Login: $user", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
