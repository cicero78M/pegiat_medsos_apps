package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brunocvcunha.instagram4j.Instagram4j

class InstaFragment : Fragment(R.layout.fragment_insta) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val usernameInput = view.findViewById<EditText>(R.id.input_username)
        val passwordInput = view.findViewById<EditText>(R.id.input_password)
        val button = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_submit)
        button.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val insta = Instagram4j.builder()
                            .username(username)
                            .password(password)
                            .build()
                        insta.setup()
                        insta.login()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Login berhasil", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Login gagal", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
