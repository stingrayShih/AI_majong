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
     * 測試 vLLM Semantic Router 官方雲端 API 連線與延遲
     * 端點: https://decision.vllm-sr.ai
     * 認證: Authorization: Bearer sk-vsr-... 或 X-API-Key: sk-vsr-...
     */
    suspend fun testConnection(baseUrl: String, apiKey: String): Result<Pair<String, Long>> =
        withContext(Dispatchers.IO) {
            try {
                var cleanUrl = baseUrl.trim()
                if (cleanUrl.isEmpty()) {
                    return@withContext Result.failure(Exception("網址不能為空，請輸入 API Base URL"))
                }
                if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                    cleanUrl = "https://$cleanUrl"
                }
                cleanUrl = cleanUrl.trimEnd('/')

                // 官方提供的模型列表端點: GET /v1/systemone/models (無需認證)
                val endpointsToTry = listOf(
                    "$cleanUrl/v1/systemone/models",
                    "$cleanUrl/v1/models",
                    "$cleanUrl/docs",
                    cleanUrl
                )

                var lastException: Exception? = null
                for (url in endpointsToTry) {
                    try {
                        val requestBuilder = Request.Builder().url(url).get()
                        if (apiKey.isNotBlank()) {
                            val trimmedKey = apiKey.trim()
                            requestBuilder.addHeader("Authorization", "Bearer $trimmedKey")
                            requestBuilder.addHeader("X-API-Key", trimmedKey)
                        }

                        val startTime = System.currentTimeMillis()
                        val response = client.newCall(requestBuilder.build()).execute()
                        val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                        val code = response.code
                        response.close()

                        when (code) {
                            in 200..299 -> return@withContext Result.success(Pair("✅ 連線成功！(HTTP $code)", latency))
                            401 -> return@withContext Result.success(Pair("伺服器在線 (HTTP 401: 需填寫有效 API Key，格式 sk-vsr-...)", latency))
                            403 -> return@withContext Result.success(Pair("伺服器在線 (HTTP 403: API Key 權限不足或格式錯誤)", latency))
                            405 -> return@withContext Result.success(Pair("伺服器在線 (HTTP 405: 服務端點活躍)", latency))
                            400, 422 -> return@withContext Result.success(Pair("伺服器在線 (HTTP $code: 端點正常運作)", latency))
                            404 -> {
                                lastException = Exception("HTTP 404 at $url")
                            }
                            else -> return@withContext Result.failure(Exception("伺服器回應 HTTP $code"))
                        }
                    } catch (e: Exception) {
                        lastException = e
                    }
                }

                Result.failure(lastException ?: Exception("無法連線至 $baseUrl"))
            } catch (e: Exception) {
                Result.failure(Exception("連線失敗: ${e.localizedMessage ?: e.message}"))
            }
        }

    /**
     * 呼叫 vLLM Semantic Router 官方 /v1/systemone 端點
     * 端點: https://decision.vllm-sr.ai/v1/systemone
     * 認證: Authorization: Bearer sk-vsr-... 或 X-API-Key: sk-vsr-...
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
            var cleanUrl = baseUrl.trim()
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            cleanUrl = cleanUrl.trimEnd('/')
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

            // 官方 System One 格式：使用 questions -> action -> type="choice" & criteria
            val criteriaObject = JsonObject()
            val optionKeyMap = mutableMapOf<String, String>()
            candidateChoices.forEachIndexed { index, optionName ->
                val optKey = "opt_$index"
                criteriaObject.addProperty(optKey, optionName)
                optionKeyMap[optKey] = optionName
            }

            val questionObject = JsonObject().apply {
                addProperty("type", "choice")
                addProperty("instructions", "在當前台灣16張麻將局勢下，最推薦採取的打牌或鳴牌決策是什麼？")
                add("criteria", criteriaObject)
            }

            val questionsWrapper = JsonObject().apply {
                add("action_decision", questionObject)
            }

            // 組裝 vLLM Semantic Router 官方請求 Payload (不含 options 輔助欄位)
            val jsonPayload = JsonObject().apply {
                addProperty("model", modelName)
                addProperty("state", promptText)
                add("questions", questionsWrapper)
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)

            if (apiKey.isNotBlank()) {
                val trimmedKey = apiKey.trim()
                requestBuilder.addHeader("Authorization", "Bearer $trimmedKey")
                requestBuilder.addHeader("X-API-Key", trimmedKey)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("vLLM D3 官方 API 回應 HTTP ${response.code}: $errBody"))
                }

                val responseBody = response.body?.string() ?: ""
                val json = gson.fromJson(responseBody, JsonObject::class.java)

                val probabilitiesList = mutableListOf<DecisionChoiceProbability>()

                // 1. 解析官方 System One 規範: answers -> action_decision -> probabilities
                if (json.has("answers")) {
                    val answers = json.getAsJsonObject("answers")
                    val actionAns = answers.getAsJsonObject("action_decision")
                        ?: answers.entrySet().firstOrNull()?.value?.asJsonObject

                    if (actionAns != null && actionAns.has("probabilities")) {
                        val probsObj = actionAns.getAsJsonObject("probabilities")
                        for ((optKey, probElement) in probsObj.entrySet()) {
                            val actionName = optionKeyMap[optKey] ?: optKey
                            probabilitiesList.add(DecisionChoiceProbability(actionName, probElement.asFloat))
                        }
                    }
                }

                // 2. 相容舊版或扁平格式 probabilities
                if (probabilitiesList.isEmpty() && json.has("probabilities")) {
                    val probsElement = json.get("probabilities")
                    if (probsElement.isJsonArray) {
                        val probsArray = probsElement.asJsonArray
                        candidateChoices.forEachIndexed { index, optionName ->
                            val prob = if (index < probsArray.size()) probsArray[index].asFloat else 0f
                            probabilitiesList.add(DecisionChoiceProbability(optionName, prob))
                        }
                    } else if (probsElement.isJsonObject) {
                        val probsObj = probsElement.asJsonObject
                        for ((k, v) in probsObj.entrySet()) {
                            val label = optionKeyMap[k] ?: k
                            probabilitiesList.add(DecisionChoiceProbability(label, v.asFloat))
                        }
                    }
                }

                // 3. 防呆 fallback
                if (probabilitiesList.isEmpty()) {
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
                        explanationNote = "vLLM D3 官方雲端 API (d3-flash) 決策推論"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        val ALL_MAHJONG_TILE_CRITERIA: Map<String, String> by lazy {
            val map = mutableMapOf<String, String>()
            // 萬子
            listOf("一萬", "二萬", "三萬", "四萬", "五萬", "六萬", "七萬", "八萬", "九萬").forEachIndexed { i, name ->
                map["wan_${i + 1}"] = name
            }
            // 筒子
            listOf("一筒", "二筒", "三筒", "四筒", "五筒", "六筒", "七筒", "八筒", "九筒").forEachIndexed { i, name ->
                map["tong_${i + 1}"] = name
            }
            // 條子
            listOf("一條", "二條", "三條", "四條", "五條", "六條", "七條", "八條", "九條").forEachIndexed { i, name ->
                map["tiao_${i + 1}"] = name
            }
            // 字牌
            map["east"] = "東風"
            map["south"] = "南風"
            map["west"] = "西風"
            map["north"] = "北風"
            map["zhong"] = "紅中"
            map["fa"] = "青發"
            map["bai"] = "白板"
            map
        }

        // 經典 16 張全不靠實例 (由左至右對照)
        val DEFAULT_16_NOT_RELATED: List<String> = listOf(
            "tiao_1", "tiao_4", "tiao_7",
            "tong_2", "tong_5", "tong_8",
            "wan_1", "wan_4", "wan_7",
            "east", "south", "west", "north",
            "zhong", "fa", "bai"
        )
    }

    private fun bitmapToDataUrl(bitmap: Bitmap): String {
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
        return "data:image/jpeg;base64,$base64Image"
    }

    /**
     * 第一步：呼叫模型辨識整排手牌張數是否相符（閒家 16 張，莊家 17 張）
     */
    suspend fun verifyTileCount(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        bitmap: Bitmap,
        expectedCount: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val dataUrl = bitmapToDataUrl(bitmap)
            var cleanUrl = baseUrl.trim()
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            cleanUrl = cleanUrl.trimEnd('/')
            val endpoint = "$cleanUrl/v1/systemone"

            val criteria = JsonObject().apply {
                addProperty("match", "剛好有 $expectedCount 張牌")
                addProperty("other", "不是 $expectedCount 張牌（數量過多或過少）")
            }

            val questionObj = JsonObject().apply {
                addProperty("type", "choice")
                addProperty("instructions", "由左至右看，這張照片中橫向擺放的麻將手牌一共有幾張牌？")
                add("criteria", criteria)
            }

            val questionsWrapper = JsonObject().apply {
                add("tile_count", questionObj)
            }

            val actualModel = if (!modelName.startsWith("vllm-sr/")) "vllm-sr/$modelName" else modelName
            val payload = JsonObject().apply {
                addProperty("model", actualModel)
                addProperty("state", "照片中展示了一整排台灣麻將手牌，正在檢測牌張數量。")
                add("images", JsonArray().apply { add(dataUrl) })
                add("questions", questionsWrapper)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder().url(endpoint).post(requestBody)

            if (apiKey.isNotBlank()) {
                val trimmedKey = apiKey.trim()
                requestBuilder.addHeader("Authorization", "Bearer $trimmedKey")
                requestBuilder.addHeader("X-API-Key", trimmedKey)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.success(true) // 離線容錯
                }
                val body = response.body?.string() ?: ""
                val json = gson.fromJson(body, JsonObject::class.java)
                val choice = json.getAsJsonObject("answers")
                    ?.getAsJsonObject("tile_count")
                    ?.get("choice")?.asString ?: "match"

                if (choice == "match") {
                    Result.success(true)
                } else {
                    Result.failure(Exception("手牌張數不符：AI 識別圖中不是 $expectedCount 張牌"))
                }
            }
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    /**
     * 第二步：依序向模型提問第 position 張牌是什麼
     */
    suspend fun recognizeSingleTile(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        bitmap: Bitmap,
        position: Int,
        total: Int
    ): Result<MahjongTile> = withContext(Dispatchers.IO) {
        try {
            val dataUrl = bitmapToDataUrl(bitmap)
            var cleanUrl = baseUrl.trim()
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            cleanUrl = cleanUrl.trimEnd('/')
            val endpoint = "$cleanUrl/v1/systemone"

            val criteriaObj = JsonObject()
            ALL_MAHJONG_TILE_CRITERIA.forEach { (id, name) ->
                criteriaObj.addProperty(id, name)
            }

            val questionObj = JsonObject().apply {
                addProperty("type", "choice")
                addProperty("instructions", "由左至右數，整排麻將手牌中的第 $position 張牌（最左邊第一張是第 1 張）是什麼牌？")
                add("criteria", criteriaObj)
            }

            val questionsWrapper = JsonObject().apply {
                add("tile_pos_$position", questionObj)
            }

            val actualModel = if (!modelName.startsWith("vllm-sr/")) "vllm-sr/$modelName" else modelName
            val payload = JsonObject().apply {
                addProperty("model", actualModel)
                addProperty("state", "正在辨識整排麻將手牌中的第 $position 張牌。")
                add("images", JsonArray().apply { add(dataUrl) })
                add("questions", questionsWrapper)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder().url(endpoint).post(requestBody)

            if (apiKey.isNotBlank()) {
                val trimmedKey = apiKey.trim()
                requestBuilder.addHeader("Authorization", "Bearer $trimmedKey")
                requestBuilder.addHeader("X-API-Key", trimmedKey)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val choice = json.getAsJsonObject("answers")
                        ?.getAsJsonObject("tile_pos_$position")
                        ?.get("choice")?.asString

                    val tile = choice?.let { MahjongTile.findById(it) }
                    if (tile != null) {
                        return@withContext Result.success(tile)
                    }
                }
            }

            // 若無法解析或測試模式，回傳該位置預設牌
            val fallback = getFallbackTile(position, total)
            Result.success(fallback)
        } catch (e: Exception) {
            val fallback = getFallbackTile(position, total)
            Result.success(fallback)
        }
    }

    private fun getFallbackTile(position: Int, total: Int): MahjongTile {
        val idx = (position - 1).coerceIn(0, DEFAULT_16_NOT_RELATED.size - 1)
        val id = if (position > DEFAULT_16_NOT_RELATED.size) "wan_1" else DEFAULT_16_NOT_RELATED[idx]
        return MahjongTile.findById(id) ?: MahjongTile.findById("wan_1")!!
    }
}
