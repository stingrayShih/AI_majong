package com.aimajong.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.model.GameState
import com.aimajong.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    gameState: GameState,
    onResumeGame: () -> Unit,
    onStartPhotoScan: () -> Unit,
    onStartManualSetup: () -> Unit,
    onViewHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val hasActiveGame = gameState.handTiles.isNotEmpty() && !gameState.isGameOver

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🀄 AI 台灣麻將助手", color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定", tint = AccentGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MahjongGreenDark)
            )
        },
        containerColor = MahjongFeltGreen
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 【繼續當前對局】卡片
                if (hasActiveGame) {
                    Card(
                        onClick = onResumeGame,
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32)),
                        border = androidx.compose.foundation.BorderStroke(2.dp, AccentGold),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(18.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(38.dp)
                            )
                            Column {
                                Text(
                                    text = "▶ 繼續進行中的牌局",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "目前第 ${gameState.currentTurn} 巡，輪到【${gameState.currentTurnPlayer.title}】",
                                    color = AccentGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 功能 1: 手動選牌
                MenuActionCard(
                    title = if (hasActiveGame) "重新開新局 (手動選牌)" else "手動選取手牌開局",
                    subtitle = "自選莊家與起手手牌 (支援一鍵範例牌)",
                    icon = Icons.Default.Edit,
                    onClick = onStartManualSetup
                )

                // 功能 2: 拍照辨識
                MenuActionCard(
                    title = if (hasActiveGame) "重新開新局 (拍照辨識)" else "拍照辨識手牌開局",
                    subtitle = "拍攝實體 16/17 張手牌由 AI 辨識",
                    icon = Icons.Default.CameraAlt,
                    onClick = onStartPhotoScan
                )

                // 功能 3: 牌局歷史
                MenuActionCard(
                    title = "牌局歷史紀錄",
                    subtitle = "回顧過往牌局走勢與操作記錄",
                    icon = Icons.Default.History,
                    onClick = onViewHistory
                )

                // 功能 4: 伺服器設定
                MenuActionCard(
                    title = "AI 伺服器與模型設定",
                    subtitle = "配置 vLLM d3-flash URL、API Key 與連線測試",
                    icon = Icons.Default.Settings,
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
fun MenuActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = title,
                    color = TextLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
