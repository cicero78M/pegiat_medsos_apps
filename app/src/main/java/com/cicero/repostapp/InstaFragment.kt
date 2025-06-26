package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment

class InstaFragment : Fragment(R.layout.fragment_insta) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val button = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_submit)
        button.setOnClickListener {
            Toast.makeText(requireContext(), "Login clicked", Toast.LENGTH_SHORT).show()
        }
    }
}
