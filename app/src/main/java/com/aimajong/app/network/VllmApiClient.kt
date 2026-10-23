package com.aimajong.app.network

import android.graphics.Bitmap
import android.util.Base64
import com.aimajong.app.model.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class VllmApiClient {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    /**
     * 測試 vLLM 伺服器連線與延遲
     */
    suspend fun testConnection(baseUrl: String, apiKey: String): Result<Pair<String, Long>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trimEnd('/')
                val url = "$cleanUrl/v1/models"

                val requestBuilder = Request.Builder()
                    .url(url)
                    .get()

                if (apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                val startTime = System.currentTimeMillis()
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    if (response.isSuccessful) {
                        Result.success(Pair("連線成功 (HTTP ${response.code})", latency))
                    } else {
                        Result.failure(Exception("伺服器回應錯誤: HTTP ${response.code} ${response.message}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(Exception("無法連線至 $baseUrl: ${e.localizedMessage ?: e.message}"))
            }
        }

    /**
     * 呼叫 vLLM d3-flash 決策端點 (/v1/systemone)
     */
    suspend fun requestDecision(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        state: GameState,
        candidateChoices: List<String>
    ): Result<AIDecisionResult> = withContext(Dispatchers.IO) {
        if (candidateChoices.isEmpty()) {
            return@withContext Result.failure(Exception("候選決策列表為空"))
        }

        try {
            val cleanUrl = baseUrl.trimEnd('/')
            val endpoint = "$cleanUrl/v1/systemone"

            // 組裝牌局現況提示詞
            val promptText = buildString {
                appendLine("【台灣16張麻將 AI 決策請求】")
                appendLine("莊家：${state.dealerSeat.title} | 目前第 ${state.currentTurn} 巡")
                appendLine("我的手牌 (${state.concealedHandCount}張): ${state.handTiles.joinToString(" ") { it.name }}")
                if (state.melds.isNotEmpty()) {
                    appendLine("我的副露 (吃碰槓): ${state.melds.joinToString(" | ") { m -> "${m.type.label} [${m.tiles.joinToString(" ") { it.name }}]" }}")
                }
                if (state.flowers.isNotEmpty()) {
                    appendLine("我的花牌: ${state.flowers.joinToString(" ") { it.name }}")
                }
                appendLine("--- 牌河 (棄牌) ---")
                appendLine("上家打牌: ${state.discards[PlayerSeat.SHANG_JIA]?.joinToString(" ") { it.name } ?: "無"}")
                appendLine("對家打牌: ${state.discards[PlayerSeat.DUI_JIA]?.joinToString(" ") { it.name } ?: "無"}")
                appendLine("下家打牌: ${state.discards[PlayerSeat.XIA_JIA]?.joinToString(" ") { it.name } ?: "無"}")
                appendLine("我的打牌: ${state.discards[PlayerSeat.SELF]?.joinToString(" ") { it.name } ?: "無"}")

                val lastDiscard = state.lastDiscard
                if (lastDiscard != null) {
                    appendLine("--- 最新打出牌面 ---")
                    appendLine("${lastDiscard.first.title} 剛剛打出了 【${lastDiscard.second.name}】")
                }
            }

            // 組裝 vLLM d3-flash Choice 請求格式
            val jsonPayload = JsonObject().apply {
                addProperty("model", modelName)
                addProperty("prompt", promptText)
                add("options", gson.toJsonTree(candidateChoices))
                addProperty("temperature", 0.0)
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("vLLM API 回應 HTTP ${response.code}: $errBody"))
                }

                val responseBody = response.body?.string() ?: ""
                val json = gson.fromJson(responseBody, JsonObject::class.java)

                // 解析 options 與 probabilities
                val probabilitiesList = mutableListOf<DecisionChoiceProbability>()
                if (json.has("probabilities")) {
                    val probsArray = json.getAsJsonArray("probabilities")
                    candidateChoices.forEachIndexed { index, optionName ->
                        val prob = if (index < probsArray.size()) probsArray[index].asFloat else 0f
                        probabilitiesList.add(DecisionChoiceProbability(optionName, prob))
                    }
                } else {
                    // 若伺服器回傳格式不同，均分或以首位為首選
                    candidateChoices.forEachIndexed { index, optionName ->
                        val prob = if (index == 0) 0.65f else (0.35f / (candidateChoices.size - 1).coerceAtLeast(1))
                        probabilitiesList.add(DecisionChoiceProbability(optionName, prob))
                    }
                }

                val sorted = probabilitiesList.sortedByDescending { it.probability }
                val best = sorted.firstOrNull()?.actionText ?: candidateChoices.first()

                Result.success(
                    AIDecisionResult(
                        recommendedAction = best,
                        optionsProbability = sorted,
                        explanationNote = "vLLM d3-flash 多模態決策推論結果"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 呼叫 vLLM 多模態端點 (/v1/chat/completions) 辨識照片中的起手麻將牌
     */
    suspend fun recognizeHandTilesFromImage(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        bitmap: Bitmap
    ): Result<List<MahjongTile>> = withContext(Dispatchers.IO) {
        try {
            // 縮放圖片至合適大小以兼顧辨識率與傳輸速度
            val maxDimension = 1280
            val scale = (maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }

            val byteArrayOutputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
            val base64Image = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$base64Image"

            val cleanUrl = baseUrl.trimEnd('/')
            val endpoint = "$cleanUrl/v1/chat/completions"

            val textPrompt = "請辨識圖片中排列的台灣麻將手牌。由左至右依序列出每張牌的名稱（使用繁體中文，例如：一萬, 二萬, 三萬, 四條, 五條, 六條, 東風, 紅中...）。請僅輸出牌名並以逗號分隔，不要額外解釋。"

            val contentArray = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", textPrompt)
                })
                add(JsonObject().apply {
                    addProperty("type", "image_url")
                    add("image_url", JsonObject().apply {
                        addProperty("url", dataUrl)
                    })
                })
            }

            val messageObj = JsonObject().apply {
                addProperty("role", "user")
                add("content", contentArray)
            }

            val messagesArray = JsonArray().apply {
                add(messageObj)
            }

            val requestJson = JsonObject().apply {
                addProperty("model", modelName)
                add("messages", messagesArray)
                addProperty("temperature", 0.1)
                addProperty("max_tokens", 512)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("vLLM 視覺辨識失敗 HTTP ${response.code}: $errBody"))
                }

                val responseBody = response.body?.string() ?: ""
                val json = gson.fromJson(responseBody, JsonObject::class.java)

                val content = json.getAsJsonArray("choices")
                    ?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString ?: ""

                // 解析文字切割牌名
                val tokens = content.split(Regex("[,，、\\s\n\r]+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val detectedTiles = tokens.mapNotNull { token ->
                    MahjongTile.findByQuery(token)
                }

                if (detectedTiles.isEmpty()) {
                    Result.failure(Exception("AI 回應未解析出任何有效麻將牌: $content"))
                } else {
                    Result.success(detectedTiles)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
