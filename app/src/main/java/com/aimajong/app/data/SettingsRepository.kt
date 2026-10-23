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

        // vLLM D3 官方雲端 API 預設端點 (Verathos Decide 官方托管服務)
        const val DEFAULT_BASE_URL = "https://api.verathos.ai"
        const val DEFAULT_MODEL_NAME = "d3-flash"
    }

    var serverBaseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var modelName: String
        get() = prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL_NAME) ?: DEFAULT_MODEL_NAME
        set(value) = prefs.edit().putString(KEY_MODEL_NAME, value.trim()).apply()

    var useMockFallback: Boolean
        get() = prefs.getBoolean(KEY_USE_MOCK_FALLBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_MOCK_FALLBACK, value).apply()
}
