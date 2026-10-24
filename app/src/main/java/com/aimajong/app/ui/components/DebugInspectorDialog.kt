package com.aimajong.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aimajong.app.model.ApiDebugLog
import com.aimajong.app.ui.theme.AccentGold
import com.aimajong.app.ui.theme.CardBackground

@Composable
fun DebugInspectorDialog(
    initialLog: ApiDebugLog?,
    logHistory: List<ApiDebugLog> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedLog by remember { mutableStateOf(initialLog ?: logHistory.firstOrNull()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Request, 1: Response, 2: History

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // 1. 標題與關閉按鈕
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🪲", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "AI 決策報文偵錯 (Debug)",
                            color = AccentGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "關閉", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val curLog = selectedLog
                if (curLog == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("尚無 API 呼叫紀錄", color = Color.Gray, fontSize = 14.sp)
                    }
                    return@Column
                }

                // 2. 狀態與摘要資訊條
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141A14)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(curLog.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val isHttpOk = curLog.httpCode in 200..299
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isHttpOk) Color(0xFF2E7D32) else Color(0xFFC62828)
                            ) {
                                Text(
                                    text = if (curLog.httpCode != null) "HTTP ${curLog.httpCode}" else "連線中斷",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("時間: ${curLog.timestamp} | 耗時: ${curLog.latencyMs}ms", color = Color.LightGray, fontSize = 11.sp)
                            Text("模型: ${curLog.model.takeLast(16)}", color = AccentGold, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. 分頁標籤 (Request / Response / 歷史)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF141A14),
                    contentColor = AccentGold
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("📤 Request", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("📥 Response", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    if (logHistory.size > 1) {
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("📜 歷史 (${logHistory.size})", fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. 內容區
                when (selectedTab) {
                    0 -> {
                        // Request JSON
                        JsonViewer(
                            content = curLog.requestJson,
                            label = "Request Payload (JSON)",
                            onCopy = {
                                copyToClipboard(context, "Request JSON", curLog.requestJson)
                            }
                        )
                    }
                    1 -> {
                        // Response JSON
                        Column(modifier = Modifier.weight(1f)) {
                            if (curLog.errorMessage != null && !curLog.isSuccess) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF4A1010),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                ) {
                                    Text(
                                        text = "⚠️ 錯誤說明: ${curLog.errorMessage}",
                                        color = Color(0xFFFF8A80),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            JsonViewer(
                                content = curLog.responseJson.ifBlank { "(無回應資料或連線異常)" },
                                label = "Response Body (JSON)",
                                onCopy = {
                                    copyToClipboard(context, "Response JSON", curLog.responseJson)
                                }
                            )
                        }
                    }
                    2 -> {
                        // 歷史呼叫列表
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            logHistory.forEach { item ->
                                val isSelected = item == selectedLog
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) AccentGold.copy(alpha = 0.2f) else Color(0xFF141A14),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, AccentGold) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLog = item
                                            selectedTab = 0
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("${item.timestamp} | ${item.latencyMs}ms", color = Color.Gray, fontSize = 10.sp)
                                        }
                                        Text(
                                            text = if (item.httpCode != null) "HTTP ${item.httpCode}" else "ERR",
                                            color = if (item.isSuccess) Color(0xFF81C784) else Color(0xFFE57373),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.JsonViewer(
    content: String,
    label: String,
    onCopy: () -> Unit
) {
    Column(modifier = Modifier.weight(1f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            OutlinedButton(
                onClick = onCopy,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentGold, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("複製 JSON", fontSize = 11.sp, color = AccentGold)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121412), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF2C352D), RoundedCornerShape(8.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = content,
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "已複製 $label 至剪貼簿！", Toast.LENGTH_SHORT).show()
}
