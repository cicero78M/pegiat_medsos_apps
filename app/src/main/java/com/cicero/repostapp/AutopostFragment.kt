package com.cicero.repostapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import java.io.File
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.exceptions.IGLoginException
import com.github.instagram4j.instagram4j.utils.IGChallengeUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AutopostFragment : Fragment() {

    companion object {
        fun newInstance(): AutopostFragment = AutopostFragment()
    }

    private var igClient: IGClient? = null

    private fun sessionFiles(): Pair<File, File> {
        val dir = requireContext().filesDir
        return Pair(File(dir, "igclient.ser"), File(dir, "cookie.ser"))
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

        val icon = view.findViewById<ImageView>(R.id.instagram_icon)
        val check = view.findViewById<ImageView>(R.id.check_mark)
        val start = view.findViewById<Button>(R.id.button_start)

        // attempt to load saved session
        lifecycleScope.launch(Dispatchers.IO) {
            loadSavedSession(icon, check)
        }

        icon.setOnClickListener { showLoginDialog(icon, check) }
        start.setOnClickListener {
            Toast.makeText(requireContext(), "Start pressed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoginDialog(icon: ImageView, check: ImageView) {
        val view = layoutInflater.inflate(R.layout.dialog_login, null)
        val userInput = view.findViewById<EditText>(R.id.edit_username)
        val passInput = view.findViewById<EditText>(R.id.edit_password)
        AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Login") { _, _ ->
                val user = userInput.text.toString().trim()
                val pass = passInput.text.toString().trim()
                if (user.isBlank() || pass.isBlank()) {
                    Toast.makeText(requireContext(), "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
                } else {
                    performLogin(user, pass, icon, check)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun performLogin(username: String, password: String, icon: ImageView, check: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val twoFactor = IGClient.Builder.LoginHandler { client, resp ->
                    val code = runBlocking { promptTwoFactorCode() }
                    if (code.isNullOrEmpty()) return@LoginHandler resp
                    IGChallengeUtils.resolveTwoFactor(client, resp) { code }
                }
                val checkpoint = IGClient.Builder.LoginHandler { client, resp ->
                    val code = runBlocking { promptCheckpointCode() }
                    if (code.isNullOrEmpty()) return@LoginHandler resp
                    IGChallengeUtils.resolveChallenge(client, resp) { code }
                }

                val client = IGClient.builder()
                    .username(username)
                    .password(password)
                    .onTwoFactor(twoFactor)
                    .onChallenge(checkpoint)
                    .login()

                igClient = client
                saveSession(client)
                val pic = client.selfProfile.profile_pic_url
                withContext(Dispatchers.Main) {
                    Glide.with(this@AutopostFragment).load(pic).into(icon)
                    check.visibility = View.VISIBLE
                }
            } catch (e: IGLoginException) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Login gagal", Toast.LENGTH_SHORT).show() }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Gagal terhubung", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private suspend fun promptTwoFactorCode(): String? = suspendCancellableCoroutine { cont ->
        requireActivity().runOnUiThread {
            val view = layoutInflater.inflate(R.layout.dialog_two_factor, null)
            val codeInput = view.findViewById<EditText>(R.id.edit_code)
            AlertDialog.Builder(requireContext())
                .setView(view)
                .setPositiveButton("Verify") { _, _ ->
                    cont.resume(codeInput.text.toString().trim())
                }
                .setNegativeButton("Batal") { _, _ -> cont.resume(null) }
                .setOnCancelListener { cont.resume(null) }
                .show()
        }
    }

    private suspend fun promptCheckpointCode(): String? = suspendCancellableCoroutine { cont ->
        requireActivity().runOnUiThread {
            val view = layoutInflater.inflate(R.layout.dialog_checkpoint, null)
            val codeInput = view.findViewById<EditText>(R.id.edit_checkpoint)
            AlertDialog.Builder(requireContext())
                .setView(view)
                .setPositiveButton("Submit") { _, _ ->
                    cont.resume(codeInput.text.toString().trim())
                }
                .setNegativeButton("Batal") { _, _ -> cont.resume(null) }
                .setOnCancelListener { cont.resume(null) }
                .show()
        }
    }

    private fun saveSession(client: IGClient) {
        try {
            val (clientFile, cookieFile) = sessionFiles()
            client.serialize(clientFile, cookieFile)
        } catch (_: Exception) {
        }
    }

    private suspend fun loadSavedSession(icon: ImageView, check: ImageView) {
        val (clientFile, cookieFile) = sessionFiles()
        if (!clientFile.exists() || !cookieFile.exists()) return
        try {
            val client = IGClient.deserialize(clientFile, cookieFile)
            // simple request to verify session
            client.actions().users().info(client.selfProfile.pk).join()
            igClient = client
            val pic = client.selfProfile.profile_pic_url
            withContext(Dispatchers.Main) {
                Glide.with(this@AutopostFragment).load(pic).into(icon)
                check.visibility = View.VISIBLE
            }
        } catch (_: Exception) {
            clientFile.delete()
            cookieFile.delete()
        }
    }
}
