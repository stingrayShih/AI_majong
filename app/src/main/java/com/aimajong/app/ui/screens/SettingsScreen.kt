package com.aimajong.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.data.SettingsRepository
import com.aimajong.app.network.VllmApiClient
import com.aimajong.app.ui.components.DebugInspectorDialog
import com.aimajong.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    apiClient: VllmApiClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(settingsRepo.serverBaseUrl) }
    var apiKey by remember { mutableStateOf(settingsRepo.apiKey) }
    var modelName by remember { mutableStateOf(settingsRepo.modelName) }
    var useMockFallback by remember { mutableStateOf(settingsRepo.useMockFallback) }
    var isDebugMode by remember { mutableStateOf(settingsRepo.isDebugMode) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showDebugDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 伺服器與模型設定", color = TextLight, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MahjongGreenDark)
            )
        },
        containerColor = MahjongFeltGreen
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 說明卡片
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("💡 vLLM Semantic Router 官方 API 連線說明", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• 預設連線官方端點: https://decision.vllm-sr.ai\n" +
                                "• API Key 格式: sk-vsr-... (可於官網取得)\n" +
                                "• 無需自行架設伺服器，填入 API Key 即可使用 d3-flash 即時決策。\n" +
                                "• 如無 API Key，開啟下方「離線備援模式」可體驗本地規則引擎。",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // 伺服器網址
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("API Base URL：", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            connectionTestResult = null
                        },
                        placeholder = { Text("https://decision.vllm-sr.ai", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 快速預設按鈕
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { baseUrl = "https://decision.vllm-sr.ai" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("官方 (decision.vllm-sr.ai)", fontSize = 10.sp, color = TextLight)
                        }
                        OutlinedButton(
                            onClick = { 
                                baseUrl = "https://decision.vllm-sr.ai"
                                modelName = "d3-turbo"
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("重設回官方預設", fontSize = 11.sp, color = TextLight)
                        }
                    }
                }
            }

            // 模型名稱與 API Key
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("模型名稱 (Model Name)：", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("預設 27B 旗艦", color = AccentGold, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 快速選擇常用模型
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = modelName.contains("d3-turbo"),
                            onClick = { modelName = "d3-turbo" },
                            label = { Text("⚡ d3-turbo (27B 推薦)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGold,
                                selectedLabelColor = Color.Black
                            )
                        )
                        FilterChip(
                            selected = modelName.contains("d3-flash"),
                            onClick = { modelName = "d3-flash" },
                            label = { Text("d3-flash (9B 輕量)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGold,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("API Key：", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = { Text("sk-vsr-...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
                    )
                }
            }

            // 容錯設定
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("智慧本地模擬容錯 (Fallback)", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("當伺服器斷線或無法連線時，自動以本地規則引擎分析，保證 App 絕不崩潰中斷", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = useMockFallback,
                        onCheckedChange = { useMockFallback = it }
                    )
                }
            }

            // 偵錯模式設定 (Debug Mode)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🪲 決策偵錯與報文檢視模式", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("開啟後可在打牌決策與拍照辨識畫面隨時開啟「🪲 檢視報文」，查閱完整 Request/Response JSON 與延遲數據", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isDebugMode,
                        onCheckedChange = { isDebugMode = it }
                    )
                }
            }

            // 測試連線按鈕
            Button(
                onClick = {
                    keyboardController?.hide()
                    val targetUrl = baseUrl.trim()
                    if (targetUrl.isBlank()) {
                        Toast.makeText(context, "請先輸入 API Base URL！", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isTestingConnection = true
                    connectionTestResult = null
                    Toast.makeText(context, "⚡ 正在向伺服器發送測試請求，請稍候...", Toast.LENGTH_SHORT).show()

                    coroutineScope.launch {
                        val result = apiClient.testConnection(targetUrl, apiKey.trim(), modelName.trim())
                        isTestingConnection = false
                        result.onSuccess { (msg, latency) ->
                            connectionTestResult = Pair(true, msg)
                            showResultDialog = true
                            Toast.makeText(context, "✅ 驗證成功 (${latency}ms)", Toast.LENGTH_LONG).show()
                        }.onFailure { err ->
                            val errMsg = err.message ?: "無法建立連線"
                            connectionTestResult = Pair(false, errMsg)
                            showResultDialog = true
                            Toast.makeText(context, errMsg.lines().firstOrNull() ?: "連線失敗", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isTestingConnection,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGold,
                    disabledContainerColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("連線測試中 (請稍候)...", color = Color.Black, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚡ 測試連線 (Ping Server)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            // 測試結果展示卡片 (保留於下方)
            connectionTestResult?.let { (isSuccess, message) ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSuccess) Color(0xFF1B5E20) else Color(0xFFC62828),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showDebugDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("🪲 檢視本次測試報文 (Request/Response)", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }

            // 儲存設定按鈕
            Button(
                onClick = {
                    keyboardController?.hide()
                    settingsRepo.serverBaseUrl = baseUrl.trim()
                    settingsRepo.apiKey = apiKey.trim()
                    settingsRepo.modelName = modelName.trim()
                    settingsRepo.useMockFallback = useMockFallback
                    settingsRepo.isDebugMode = isDebugMode
                    Toast.makeText(context, "設定已保存並套用！", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("💾 儲存並套用設定", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    // 彈出式測試結果視窗 (保證不被鍵盤遮擋或滑動忽略)
    if (showResultDialog && connectionTestResult != null) {
        val (isSuccess, message) = connectionTestResult!!
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFEF5350),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isSuccess) "連線測試成功" else "連線測試失敗",
                        color = TextLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Text(
                    text = message,
                    color = TextLight,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResultDialog = false
                        showDebugDialog = true
                    }
                ) {
                    Text("🪲 檢視完整報文", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showResultDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                ) {
                    Text("確定", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CardBackground
        )
    }

    if (showDebugDialog) {
        val latestLog by apiClient.latestDebugLog.collectAsState()
        val logHistory by apiClient.debugLogHistory.collectAsState()
        DebugInspectorDialog(
            initialLog = latestLog,
            logHistory = logHistory,
            onDismiss = { showDebugDialog = false }
        )
    }
}
