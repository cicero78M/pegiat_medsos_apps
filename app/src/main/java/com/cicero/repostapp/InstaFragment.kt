package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import org.brunocvcunha.instagram4j.Instagram4j

class InstaFragment : Fragment(R.layout.fragment_insta) {

    private fun saveSession(insta: Instagram4j) {
        try {
            val file = File(requireContext().filesDir, "insta_session.ser")
            ObjectOutputStream(FileOutputStream(file)).use { out ->
                out.writeObject(insta)
            }
        } catch (_: Exception) {
        }
    }

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

                        val result = insta.login()
                        when {
                            result.two_factor_info != null -> handleTwoFactor(insta, result)
                            result.challenge != null -> showCheckpoint(result.challenge.api_path)
                            else -> withContext(Dispatchers.Main) {
                                saveSession(insta)
                                Toast.makeText(requireContext(), "Login berhasil", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: java.io.IOException) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
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

    private suspend fun handleTwoFactor(insta: Instagram4j, result: org.brunocvcunha.instagram4j.requests.payload.InstagramLoginResult) {
        withContext(Dispatchers.Main) {
            val input = EditText(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle("Two Factor Authentication")
                .setMessage("Masukkan kode verifikasi")
                .setView(input)
                .setPositiveButton("Verifikasi") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            insta.finishTwoFactorLogin(input.text.toString(), result.two_factor_info.two_factor_identifier)
                            withContext(Dispatchers.Main) {
                                saveSession(insta)
                                Toast.makeText(requireContext(), "Login berhasil", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Kode verifikasi salah", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private suspend fun showCheckpoint(url: String?) {
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "Login membutuhkan checkpoint", Toast.LENGTH_LONG).show()
        }
    }
}
