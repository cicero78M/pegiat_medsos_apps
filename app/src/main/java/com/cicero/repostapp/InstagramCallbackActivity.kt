package com.cicero.repostapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstagramCallbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent.data?.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            finish()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = InstagramAuthManager.exchangeCode(this@InstagramCallbackActivity, code)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@InstagramCallbackActivity,
                    if (ok) "Instagram login sukses" else "Gagal login Instagram",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}
