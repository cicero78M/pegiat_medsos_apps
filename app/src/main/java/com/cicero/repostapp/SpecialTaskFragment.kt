package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fragment to display recap of special tasks from backend.
 */
class SpecialTaskFragment : Fragment(R.layout.fragment_special_task) {

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_TOKEN = "token"

        fun newInstance(userId: String?, token: String?): SpecialTaskFragment {
            val f = SpecialTaskFragment()
            f.arguments = bundleOf(ARG_USER_ID to userId, ARG_TOKEN to token)
            return f
        }
    }

    private lateinit var adapter: SpecialTaskAdapter
    private lateinit var progress: ProgressBar
    private lateinit var emptyView: TextView
    private var token: String = ""
    private var userId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SpecialTaskAdapter(mutableListOf())
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_special)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        progress = view.findViewById(R.id.progress_special)
        emptyView = view.findViewById(R.id.text_empty_special)
        token = arguments?.getString(ARG_TOKEN) ?: ""
        userId = arguments?.getString(ARG_USER_ID) ?: ""
        if (token.isNotBlank() && userId.isNotBlank()) {
            progress.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                fetchClientAndTasks()
            }
        }
    }

    private suspend fun fetchClientAndTasks() {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/users/$userId")
            .header("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                val clientId = if (resp.isSuccessful) {
                    try {
                        JSONObject(body ?: "{}")
                            .optJSONObject("data")
                            ?.optString("client_id") ?: ""
                    } catch (_: Exception) { "" }
                } else ""
                withContext(Dispatchers.Main) {
                    if (clientId.isNotBlank()) {
                        fetchTasks(clientId)
                    } else {
                        progress.visibility = View.GONE
                        Toast.makeText(requireContext(), "Gagal memuat data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchTasks(clientId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = "${BuildConfig.API_BASE_URL}/api/amplify-khusus/rekap?client_id=$clientId"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    withContext(Dispatchers.Main) {
                        emptyView.visibility = View.GONE
                        if (resp.isSuccessful) {
                            val arr = try {
                                JSONObject(body ?: "{}").optJSONArray("data") ?: JSONArray()
                            } catch (_: Exception) { JSONArray() }
                            val list = mutableListOf<SpecialTask>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val display = obj.optString("display_nama")
                                val count = obj.optInt("jumlah_link")
                                list.add(SpecialTask(display, count))
                            }
                            adapter.setData(list)
                            emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                            progress.visibility = View.GONE
                        } else {
                            progress.visibility = View.GONE
                            Toast.makeText(requireContext(), "Gagal mengambil data", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    Toast.makeText(requireContext(), "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
