package com.aimajong.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
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

@Composable
fun HomeScreen(
    gameState: GameState,
    onResumeGame: () -> Unit,
    onStartPhotoScan: () -> Unit,
    onStartManualSetup: () -> Unit,
    onViewHistory: () -> Unit
) {
    // 檢查是否有正在進行中且未結束的對局
    val hasActiveGame = gameState.handTiles.isNotEmpty() && !gameState.isGameOver

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MahjongFeltGreen)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // App 標題
            Text(
                text = "🀄 台灣 16 張麻將",
                color = AccentGold,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "AI 實戰決策輔助 (vLLM d3-flash)",
                color = TextLight,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 【繼續當前對局】卡片：若有進行中對局，顯示在最頂部，超大、超明顯
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
                            modifier = Modifier.size(40.dp)
                        )
                        Column {
                            Text(
                                text = "▶ 繼續進行中的牌局",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "目前進度：第 ${gameState.currentTurn} 巡，輪到【${gameState.currentTurnPlayer.title}】",
                                color = AccentGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 功能按鈕 1: 手動選牌新對局
            MenuActionCard(
                title = if (hasActiveGame) "重新開新局 (手動選牌)" else "手動選取手牌開局",
                subtitle = "使用磁磚選擇器設定莊家與起手手牌",
                icon = Icons.Default.Edit,
                onClick = onStartManualSetup
            )

            // 功能按鈕 2: 拍照辨識新對局
            MenuActionCard(
                title = if (hasActiveGame) "重新開新局 (拍照辨識)" else "拍照辨識手牌開局",
                subtitle = "拍攝實體手牌，AI 自動辨識入庫",
                icon = Icons.Default.CameraAlt,
                onClick = onStartPhotoScan
            )

            // 功能按鈕 3: 歷史紀錄
            MenuActionCard(
                title = "牌局歷史紀錄",
                subtitle = "回顧本局及過往走勢與決策過程",
                icon = Icons.Default.History,
                onClick = onViewHistory
            )
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
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = title,
                    color = TextLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
