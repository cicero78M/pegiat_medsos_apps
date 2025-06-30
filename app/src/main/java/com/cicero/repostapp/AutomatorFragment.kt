package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class AutomatorFragment : Fragment(R.layout.fragment_automator) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shellView = view.findViewById<TextView>(R.id.termux_shell_view)
        val startButton = view.findViewById<Button>(R.id.button_start_shell)

        startButton.setOnClickListener {
            shellView.append("\n$ ./start.sh")
        }
    }
}
