package com.cicero.repostapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.cicero.repostapp.macro.MacroAccessibilityService

/**
 * Fragment hosting a ViewPager to manage saved macros.
 */
class MacroFragment : Fragment() {

    companion object {
        fun newInstance(userId: String?, token: String?): MacroFragment {
            val frag = MacroFragment()
            val args = Bundle()
            args.putString("userId", userId)
            args.putString("token", token)
            frag.arguments = args
            return frag
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_macro, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Placeholder UI using ViewPager for macro editing
        val viewPager = view.findViewById<ViewPager2>(R.id.macro_view_pager)
        viewPager.adapter = MacroPagerAdapter(this)
        view.findViewById<FloatingActionButton>(R.id.add_macro_action)
            .setOnClickListener {
                (viewPager.adapter as? MacroPagerAdapter)?.addAction()
            }

        view.findViewById<FloatingActionButton>(R.id.run_macro)
            .setOnClickListener {
                val intent = Intent(requireContext(), MacroAccessibilityService::class.java)
                intent.action = MacroAccessibilityService.ACTION_RUN
                requireContext().startService(intent)
            }
    }
}
