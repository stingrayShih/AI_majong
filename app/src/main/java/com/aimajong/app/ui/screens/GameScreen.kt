package com.aimajong.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.model.*
import com.aimajong.app.ui.components.MahjongTileView
import com.aimajong.app.ui.components.TilePickerDialog
import com.aimajong.app.ui.theme.*
import com.aimajong.app.ui.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onBackToHome: () -> Unit
) {
    val state by viewModel.gameState.collectAsState()
    val aiDecision by viewModel.aiDecision.collectAsState()
    val isLoadingDecision by viewModel.isLoadingDecision.collectAsState()

    var showTilePickerForDraw by remember { mutableStateOf(false) }
    var showTilePickerForOtherDiscard by remember { mutableStateOf(false) }
    var selectedOtherSeat by remember { mutableStateOf(PlayerSeat.SHANG_JIA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "對局中 - 第 ${state.currentTurn} 巡",
                        color = TextLight,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextLight)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.startNewGame() }) {
                        Text("重新開始", color = AccentGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MahjongGreenDark)
            )
        },
        containerColor = MahjongFeltGreen
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. 各家牌河概覽區
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("牌桌動態 (各家牌河)", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    PlayerRiverRow("上家", state.discards[PlayerSeat.SHANG_JIA] ?: emptyList())
                    PlayerRiverRow("對家", state.discards[PlayerSeat.DUI_JIA] ?: emptyList())
                    PlayerRiverRow("下家", state.discards[PlayerSeat.XIA_JIA] ?: emptyList())
                    PlayerRiverRow("我的打牌", state.discards[PlayerSeat.SELF] ?: emptyList())
                }
            }

            // 2. AI 決策卡片 (vLLM d3-flash 預測結果)
            Card(
                colors = CardDefaults.cardColors(containerColor = MahjongGreenDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "🤖 AI 決策建議",
                            color = AccentGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isLoadingDecision) {
                            CircularProgressIndicator(
                                color = AccentGold,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (aiDecision != null) {
                        Text(
                            text = aiDecision!!.recommendedAction,
                            color = TextLight,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = aiDecision!!.explanationNote,
                            color = TextMuted,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 機率條
                        aiDecision!!.optionsProbability.take(3).forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = option.actionText,
                                    color = TextLight,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(90.dp)
                                )
                                LinearProgressIndicator(
                                    progress = { option.probability },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = AccentGold,
                                    trackColor = SurfaceDark
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${(option.probability * 100).toInt()}%",
                                    color = AccentGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text(
                            "點選下方手牌打出，或點擊「摸牌」開始運算...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 3. 自己的手牌區域
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "我的手牌 (${state.concealedHandCount} 張)",
                        color = AccentGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "點選牌面即可打出",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 手牌橫向列表
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.handTiles.forEach { tile ->
                        MahjongTileView(
                            tile = tile,
                            width = 40.dp,
                            height = 58.dp,
                            onClick = {
                                viewModel.onDiscardTile(PlayerSeat.SELF, tile)
                            }
                        )
                    }
                }
            }

            // 4. 底部動作控制列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showTilePickerForDraw = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                ) {
                    Text("摸牌", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        selectedOtherSeat = PlayerSeat.SHANG_JIA
                        showTilePickerForOtherDiscard = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackground)
                ) {
                    Text("他家出牌", color = TextLight)
                }
            }
        }
    }

    // 彈窗: 摸牌選擇器
    if (showTilePickerForDraw) {
        TilePickerDialog(
            title = "選擇摸到的牌",
            onDismissRequest = { showTilePickerForDraw = false },
            onTileSelected = { tile ->
                viewModel.onDrawTile(tile)
            }
        )
    }

    // 彈窗: 他家出牌選擇器
    if (showTilePickerForOtherDiscard) {
        TilePickerDialog(
            title = "選擇他家打出的牌",
            onDismissRequest = { showTilePickerForOtherDiscard = false },
            onTileSelected = { tile ->
                viewModel.onDiscardTile(selectedOtherSeat, tile)
            }
        )
    }
}

@Composable
fun PlayerRiverRow(seatName: String, tiles: List<MahjongTile>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$seatName:",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.width(36.dp)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (tiles.isEmpty()) {
                Text("尚無打牌", color = Color.Gray, fontSize = 11.sp)
            } else {
                tiles.takeLast(10).forEach { tile ->
                    MahjongTileView(
                        tile = tile,
                        width = 24.dp,
                        height = 34.dp
                    )
                }
            }
        }
    }
}
