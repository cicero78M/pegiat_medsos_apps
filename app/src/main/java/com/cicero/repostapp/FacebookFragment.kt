package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class FacebookFragment : Fragment(R.layout.fragment_facebook) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val loginButton: MaterialButton = view.findViewById(R.id.button_facebook_login)
        loginButton.setOnClickListener {
            Toast.makeText(requireContext(), "Login Facebook", Toast.LENGTH_SHORT).show()
        }
    }
}
