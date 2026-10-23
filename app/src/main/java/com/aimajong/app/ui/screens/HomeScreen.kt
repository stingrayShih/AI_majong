package com.aimajong.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.ui.theme.*

@Composable
fun HomeScreen(
    onStartPhotoScan: () -> Unit,
    onStartManualSetup: () -> Unit,
    onViewHistory: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MahjongFeltGreen)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 功能按鈕 1: 拍照辨識
            MenuActionCard(
                title = "拍照辨識手牌",
                subtitle = "拍攝實體 16 張牌，AI 自動辨識入庫",
                icon = Icons.Default.CameraAlt,
                onClick = onStartPhotoScan
            )

            // 功能按鈕 2: 手動選牌
            MenuActionCard(
                title = "手動選取手牌",
                subtitle = "使用磁磚選擇器快速點選 16 張起手牌",
                icon = Icons.Default.Edit,
                onClick = onStartManualSetup
            )

            // 功能按鈕 3: 歷史紀錄
            MenuActionCard(
                title = "牌局歷史紀錄",
                subtitle = "回顧過往牌局走勢與決策機率",
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
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(36.dp)
            )
            Column {
                Text(
                    text = title,
                    color = TextLight,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}
