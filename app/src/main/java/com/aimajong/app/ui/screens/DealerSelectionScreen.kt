package com.aimajong.app.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.model.PlayerSeat
import com.aimajong.app.ui.theme.*

/**
 * 獨立且不可跳過的開局選莊家頁面
 * 確保在進入拍照辨識或手動選牌前，先明確決定誰是莊家（決定手牌為 16 張或 17 張）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealerSelectionScreen(
    mode: String, // "photo" 或 "manual"
    initialDealer: PlayerSeat = PlayerSeat.SELF,
    onBack: () -> Unit,
    onDealerConfirmed: (PlayerSeat) -> Unit
) {
    var selectedDealer by remember { mutableStateOf(initialDealer) }
    val isSelfDealer = (selectedDealer == PlayerSeat.SELF)
    val targetTileCount = if (isSelfDealer) 17 else 16

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🀄 選擇開局莊家",
                        color = TextLight,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回首頁",
                            tint = AccentGold
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 說明卡片
            Card(
                colors = CardDefaults.cardColors(containerColor = MahjongGreenDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👑", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "第一步：確認本局誰當莊家",
                            color = AccentGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "台灣 16 張麻將規則：\n• 莊家起手為 17 張牌（開局直接出第一張牌）\n• 閒家起手為 16 張牌\n\n請點選下方座位選擇莊家，系統將鎖定對應手牌數量進行辨識與分析：",
                        color = TextLight.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            Text(
                text = "請選擇四家之一作為莊家：",
                color = AccentGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            // 四家座位選擇列表
            val seats = listOf(
                PlayerSeat.SELF to Triple("我 (本家)", "⭐ 您當莊家！起手 17 張牌，先攻出牌", "🀁"),
                PlayerSeat.XIA_JIA to Triple("下家", "您為閒家，起手 16 張牌", "🀂"),
                PlayerSeat.DUI_JIA to Triple("對家", "您為閒家，起手 16 張牌", "🀃"),
                PlayerSeat.SHANG_JIA to Triple("上家", "您為閒家，起手 16 張牌", "🀀")
            )

            seats.forEach { (seat, info) ->
                val isSelected = (selectedDealer == seat)
                Card(
                    onClick = { selectedDealer = seat },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF1B4D2E) else CardBackground
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) AccentGold else Color(0xFF2E5D3E)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(info.third, fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = info.first,
                                        color = if (isSelected) AccentGold else TextLight,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (seat == PlayerSeat.SELF) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFFB74D).copy(alpha = 0.25f)
                                        ) {
                                            Text(
                                                text = "玩家本人",
                                                color = Color(0xFFFFB74D),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = info.second,
                                    color = if (isSelected) Color(0xFFE0E0E0) else TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "已選中",
                                tint = AccentGold,
                                modifier = Modifier.size(24.dp).padding(start = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 設定預覽小條
            Card(
                colors = CardDefaults.cardColors(containerColor = MahjongGreenDark),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "當前選定莊家：【${selectedDealer.title}】",
                        color = AccentGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "您的手牌：$targetTileCount 張",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 核心確認按鈕 (不可跳過，支援大字體自適應高度)
            Button(
                onClick = { onDealerConfirmed(selectedDealer) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    text = "確認莊家，進入手牌${if (mode == "photo") "拍照辨識" else "手動選牌"} (${targetTileCount}張) ➜",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
