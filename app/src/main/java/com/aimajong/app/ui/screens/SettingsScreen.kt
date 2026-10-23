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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.data.SettingsRepository
import com.aimajong.app.network.VllmApiClient
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
    val coroutineScope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(settingsRepo.serverBaseUrl) }
    var apiKey by remember { mutableStateOf(settingsRepo.apiKey) }
    var modelName by remember { mutableStateOf(settingsRepo.modelName) }
    var useMockFallback by remember { mutableStateOf(settingsRepo.useMockFallback) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

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
                    Text("💡 vLLM d3-flash 連線說明", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• 若在【Android 模擬器】中運行且 vLLM 架在你的電腦上，請填寫: http://10.0.2.2:8000\n" +
                                "• 若使用實體手機，請填寫電腦的區域網路 IP (如 http://192.168.x.x:8000)\n" +
                                "• 若架設在雲端主機，請填寫對應的公網網址。",
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
                    Text("vLLM 伺服器 Base URL：", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            connectionTestResult = null
                        },
                        placeholder = { Text("http://10.0.2.2:8000", color = Color.Gray) },
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
                            onClick = { baseUrl = "http://10.0.2.2:8000" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("模擬器 (10.0.2.2)", fontSize = 11.sp, color = TextLight)
                        }
                        OutlinedButton(
                            onClick = { baseUrl = "http://127.0.0.1:8000" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("本機 (127.0.0.1)", fontSize = 11.sp, color = TextLight)
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
                    Text("模型名稱 (Model Name)：", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("API Key (選填，若有驗證請填)：", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = { Text("Bearer Token 或空白", color = Color.Gray) },
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

            // 測試連線按鈕與結果
            Button(
                onClick = {
                    isTestingConnection = true
                    connectionTestResult = null
                    coroutineScope.launch {
                        val result = apiClient.testConnection(baseUrl, apiKey)
                        isTestingConnection = false
                        result.onSuccess { (msg, latency) ->
                            connectionTestResult = Pair(true, "$msg (延遲: ${latency}ms)")
                        }.onFailure { err ->
                            connectionTestResult = Pair(false, err.message ?: "連線失敗")
                        }
                    }
                },
                enabled = !isTestingConnection && baseUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MahjongGreenDark),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(color = AccentGold, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("連線測試中...", color = AccentGold)
                } else {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = AccentGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚡ 測試連線 (Ping Server)", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            }

            // 測試結果展示
            connectionTestResult?.let { (isSuccess, message) ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSuccess) Color(0xFF1B5E20) else Color(0xFFC62828),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 儲存設定按鈕
            Button(
                onClick = {
                    settingsRepo.serverBaseUrl = baseUrl
                    settingsRepo.apiKey = apiKey
                    settingsRepo.modelName = modelName
                    settingsRepo.useMockFallback = useMockFallback
                    Toast.makeText(context, "設定已保存！", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("💾 儲存並套用設定", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
