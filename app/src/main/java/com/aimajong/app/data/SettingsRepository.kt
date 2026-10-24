package com.aimajong.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 伺服器連線與模型設定持久化儲存 (SharedPreferences)
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ai_majong_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BASE_URL = "server_base_url"
        private const val KEY_API_KEY = "server_api_key"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_USE_MOCK_FALLBACK = "use_mock_fallback"
        private const val KEY_DEBUG_MODE = "debug_mode"

        // vLLM Semantic Router 官方雲端 Decision API
        const val DEFAULT_BASE_URL = "https://decision.vllm-sr.ai"
        const val DEFAULT_MODEL_NAME = "d3"
    }

    var isDebugMode: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_MODE, value).apply()

    var serverBaseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var modelName: String
        get() {
            val stored = prefs.getString(KEY_MODEL_NAME, null)
            if (stored == null || stored == "d3-turbo" || stored == "vllm-sr/d3-turbo") {
                return DEFAULT_MODEL_NAME
            }
            return stored
        }
        set(value) = prefs.edit().putString(KEY_MODEL_NAME, value.trim()).apply()

    var useMockFallback: Boolean
        get() = prefs.getBoolean(KEY_USE_MOCK_FALLBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_MOCK_FALLBACK, value).apply()
}
