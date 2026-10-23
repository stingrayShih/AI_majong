package com.aimajong.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.model.MahjongTile
import com.aimajong.app.model.PlayerSeat
import com.aimajong.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScanScreen(
    onBack: () -> Unit,
    onPhotoRecognized: (List<MahjongTile>, PlayerSeat) -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    var selectedDealer by remember { mutableStateOf(PlayerSeat.SELF) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照辨識起手手牌", color = TextLight, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MahjongGreenDark)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 莊家設定
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("👑 開局誰當莊家：", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            PlayerSeat.SELF to "我",
                            PlayerSeat.XIA_JIA to "下家",
                            PlayerSeat.DUI_JIA to "對家",
                            PlayerSeat.SHANG_JIA to "上家"
                        ).forEach { (seat, label) ->
                            val isSelected = selectedDealer == seat
                            Button(
                                onClick = { selectedDealer = seat },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) AccentGold else MahjongGreenDark,
                                    contentColor = if (isSelected) Color.Black else TextLight
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (isSelected) "👑 $label" else label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 取景引導框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(2.dp, AccentGold, RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentGold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("vLLM d3-flash 視覺辨識分析中...", color = TextLight, fontSize = 13.sp)
                    }
                } else {
                    Text(
                        "相機預覽區\n(點擊下方快門以測試辨識流程)",
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            }

            // 拍照按鈕
            Button(
                onClick = {
                    isProcessing = true
                    val sampleTiles = if (selectedDealer == PlayerSeat.SELF) {
                        listOf(
                            "wan_1", "wan_2", "wan_3", "wan_4", "wan_5", "wan_6",
                            "tong_1", "tong_2", "tong_3",
                            "tiao_4", "tiao_5", "tiao_6",
                            "east", "south", "zhong", "fa", "bai"
                        )
                    } else {
                        listOf(
                            "wan_1", "wan_2", "wan_3", "wan_4", "wan_5", "wan_6",
                            "tong_1", "tong_2", "tong_3",
                            "tiao_4", "tiao_5", "tiao_6",
                            "east", "south", "zhong", "fa"
                        )
                    }.mapNotNull { MahjongTile.findById(it) }

                    onPhotoRecognized(sampleTiles, selectedDealer)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Camera, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("拍攝照片並開局 (${selectedDealer.title}當莊)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
