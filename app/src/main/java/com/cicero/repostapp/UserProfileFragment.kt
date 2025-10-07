package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cicero.repostapp.databinding.ActivityProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

class UserProfileFragment : Fragment(R.layout.activity_profile) {

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_TOKEN = "token"

        fun newInstance(userId: String?, token: String?): UserProfileFragment {
            val fragment = UserProfileFragment()
            fragment.arguments = bundleOf(ARG_USER_ID to userId, ARG_TOKEN to token)
            return fragment
        }
    }

    private var _binding: ActivityProfileBinding? = null
    private val bindingOrNull get() = _binding

    private val numberFormatter by lazy {
        NumberFormat.getIntegerInstance(Locale("id", "ID"))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ActivityProfileBinding.bind(view)
        updateProfileUI(ProfileData())
        val userId = arguments?.getString(ARG_USER_ID) ?: ""
        val token = arguments?.getString(ARG_TOKEN) ?: ""
        if (userId.isNotBlank() && token.isNotBlank()) {
            fetchProfile(userId, token)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun fetchProfile(userId: String, token: String) {
        val okHttpClient = OkHttpClient()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url("${BuildConfig.API_BASE_URL}/api/users/$userId")
                        .header("Authorization", "Bearer $token")
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        ProfileResponse(response.isSuccessful, body)
                    }
                }
            }

            val binding = bindingOrNull ?: return@launch

            result.onFailure {
                context?.let { ctx ->
                    Toast.makeText(ctx, ctx.getString(R.string.profile_error_connection), Toast.LENGTH_SHORT)
                        .show()
                }
            }.onSuccess { response ->
                if (!response.isSuccessful) {
                    context?.let { ctx ->
                        Toast.makeText(ctx, ctx.getString(R.string.profile_error_load), Toast.LENGTH_SHORT)
                            .show()
                    }
                    return@onSuccess
                }

                val data = parseProfileData(response.body, userId)
                if (data != null) {
                    storeProfileLocally(data)
                    updateProfileUI(data)
                    fetchStats(token, data.instagramUsername)
                } else {
                    updateProfileUI(ProfileData())
                }
            }
        }
    }

    private fun fetchStats(token: String, username: String) {
        if (username.isBlank()) {
            bindingOrNull?.cardStats?.isVisible = false
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                var (profileStats, _) = getStatsFromDb(token, username)
                if (profileStats == null) {
                    fetchAndStoreStats(token, username)
                    profileStats = getStatsFromDb(token, username).first
                }
                profileStats
            }

            val binding = bindingOrNull ?: return@launch
            binding.cardStats.isVisible = true
            binding.statPosts.text = formatCount(stats?.optInt("post_count") ?: 0)
            binding.statFollowers.text = formatCount(stats?.optInt("follower_count") ?: 0)
            binding.statFollowing.text = formatCount(stats?.optInt("following_count") ?: 0)

            val avatarUrl = stats?.optString("profile_pic_url").orEmpty()
            val fullAvatarUrl = if (avatarUrl.startsWith("http")) {
                avatarUrl
            } else {
                "${BuildConfig.API_BASE_URL}$avatarUrl"
            }

            Glide.with(this@UserProfileFragment)
                .load(fullAvatarUrl)
                .placeholder(R.drawable.profile_avatar_placeholder)
                .error(R.drawable.profile_avatar_placeholder)
                .circleCrop()
                .into(binding.imageAvatar)
        }
    }

    private fun updateProfileUI(data: ProfileData) {
        val binding = bindingOrNull ?: return
        val usernameDisplay = data.instagramUsername.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.profile_username_format, it) }
            .orEmpty()
        val displayName = listOf(data.rank, data.name)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        val tiktokHandle = formatHandle(data.tiktok)

        binding.textUsername.renderValue(usernameDisplay)
        binding.textName.renderValue(displayName)
        binding.textNrp.renderValue(data.nrp)
        binding.textClientId.renderValue(data.clientId)
        binding.textSatfung.renderValue(data.satfung)
        binding.textJabatan.renderValue(data.jabatan)
        binding.textTiktok.renderValue(tiktokHandle)

        binding.cardStats.isVisible = data.instagramUsername.isNotBlank()

        updateStatusIcon(data.status)
    }

    private fun updateStatusIcon(status: Boolean?) {
        val binding = bindingOrNull ?: return
        val (icon, description) = when (status) {
            true -> R.drawable.ic_status_true to getString(R.string.profile_status_active)
            false -> R.drawable.ic_status_false to getString(R.string.profile_status_inactive)
            null -> null to getString(R.string.profile_status_unknown)
        }

        binding.imageStatus.isVisible = icon != null
        if (icon != null) {
            binding.imageStatus.setImageResource(icon)
        }
        binding.imageStatus.contentDescription = description
    }

    private fun parseProfileData(body: String, fallbackUserId: String): ProfileData? {
        val rawJson = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val data = rawJson.optJSONObject("data") ?: rawJson
        if (data.length() == 0) return null

        val statusText = data.optString("status")
        val status = when {
            statusText.equals("true", true) -> true
            statusText.equals("false", true) -> false
            else -> null
        }

        return ProfileData(
            instagramUsername = data.optString("insta").orEmpty(),
            rank = data.optString("title").orEmpty(),
            name = data.optString("nama").orEmpty(),
            satfung = data.optString("divisi").orEmpty(),
            nrp = data.optString("user_id", fallbackUserId).orEmpty(),
            clientId = data.optString("client_id").orEmpty(),
            jabatan = data.optString("jabatan").orEmpty(),
            tiktok = data.optString("tiktok").orEmpty(),
            status = status
        )
    }

    private fun storeProfileLocally(data: ProfileData) {
        val context = context ?: return
        val authPrefs = SecurePreferences.getAuthPrefs(context)
        authPrefs.edit {
            putString("rank", data.rank)
            putString("name", data.name)
            putString("satfung", data.satfung)
        }
    }

    private fun TextView.renderValue(value: String) {
        val displayText = value.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.profile_data_not_available)
        text = displayText
    }

    private fun formatHandle(raw: String): String {
        val sanitized = raw.trim().removePrefix("@")
        return if (sanitized.isNotBlank()) "@${sanitized}" else ""
    }

    private fun formatCount(count: Int): String = numberFormatter.format(count)

    private data class ProfileResponse(
        val isSuccessful: Boolean,
        val body: String
    )

    private data class ProfileData(
        val instagramUsername: String = "",
        val rank: String = "",
        val name: String = "",
        val satfung: String = "",
        val nrp: String = "",
        val clientId: String = "",
        val jabatan: String = "",
        val tiktok: String = "",
        val status: Boolean? = null
    )

    private fun getStatsFromDb(token: String, username: String): Pair<JSONObject?, String?> {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/insta/profile?username=$username")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Pair(null, null)
                val body = resp.body?.string()
                val obj = JSONObject(body ?: "{}")
                Pair(obj.optJSONObject("data") ?: obj, body)
            }
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    private fun fetchAndStoreStats(token: String, username: String) {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/insta/rapid-profile?username=$username")
            .header("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

}
