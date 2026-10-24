package com.aimajong.app.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * AI 模型 API 呼叫與回應偵錯報文紀錄
 */
data class ApiDebugLog(
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
    val title: String,                // 呼叫動作簡述，如 "出牌決策 (第3巡)", "連線認證測試", "拍照手牌辨識"
    val endpoint: String,             // 端點 URL，如 https://decision.vllm-sr.ai/v1/systemone
    val model: String,                // 使用模型，如 vllm-sr/d3, vllm-sr/d3-flash
    val httpCode: Int?,               // HTTP 回應碼，如 200, 401, 404
    val latencyMs: Long,              // 請求耗時 (毫秒)
    val requestJson: String,          // 發送的完整 Request JSON
    val responseJson: String,         // 接收到的原始 Response Body
    val isSuccess: Boolean,           // 是否成功
    val errorMessage: String? = null  // 錯誤描述 (若失敗)
)
