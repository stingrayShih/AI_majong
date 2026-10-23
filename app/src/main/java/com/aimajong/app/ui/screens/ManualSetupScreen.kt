package com.aimajong.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.model.MahjongTile
import com.aimajong.app.model.PlayerSeat
import com.aimajong.app.model.TileSuit
import com.aimajong.app.ui.components.MahjongTileView
import com.aimajong.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSetupScreen(
    onBack: () -> Unit,
    onStartGameWithHand: (List<MahjongTile>, PlayerSeat) -> Unit
) {
    val selectedTiles = remember { mutableStateListOf<MahjongTile>() }
    var selectedDealer by remember { mutableStateOf(PlayerSeat.SELF) }
    val isSelfDealer = selectedDealer == PlayerSeat.SELF
    val targetTileCount = if (isSelfDealer) 17 else 16

    var activeSuit by remember { mutableStateOf(TileSuit.WAN) }
    val suitTiles = remember(activeSuit) { MahjongTile.getTilesBySuit(activeSuit) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("選取起手手牌 (${selectedTiles.size}/$targetTileCount)", color = TextLight, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextLight)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        selectedTiles.clear()
                        val demoIds = if (isSelfDealer) {
                            listOf(
                                "wan_1", "wan_2", "wan_3", "wan_4", "wan_5", "wan_6",
                                "tong_2", "tong_2", "tong_2",
                                "tiao_7", "tiao_8", "tiao_9",
                                "east", "zhong", "zhong", "fa", "bai"
                            )
                        } else {
                            listOf(
                                "wan_1", "wan_2", "wan_3", "wan_4", "wan_5", "wan_6",
                                "tong_2", "tong_2", "tong_2",
                                "tiao_7", "tiao_8", "tiao_9",
                                "east", "zhong", "zhong", "fa"
                            )
                        }
                        demoIds.mapNotNull { MahjongTile.findById(it) }.forEach {
                            selectedTiles.add(it)
                        }
                    }) {
                        Text("載入範例牌", color = AccentGold)
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
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 誰當莊家選擇區 (四家皆可選)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("開局誰當莊家 (首輪由莊家先出牌)：", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlayerSeat.values().forEach { seat ->
                            FilterChip(
                                selected = selectedDealer == seat,
                                onClick = {
                                    selectedDealer = seat
                                    if (seat != PlayerSeat.SELF && selectedTiles.size > 16) {
                                        selectedTiles.removeAt(selectedTiles.lastIndex)
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (seat == PlayerSeat.SELF) "我當莊(17張)" else "${seat.title}當莊",
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 目前選取的手牌預覽區
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "我的起手牌 (${selectedTiles.size}/$targetTileCount):",
                            color = AccentGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedTiles.isNotEmpty()) {
                            IconButton(onClick = { selectedTiles.clear() }) {
                                Icon(Icons.Default.Delete, contentDescription = "清空", tint = Color.LightGray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (selectedTiles.isEmpty()) {
                        Text(
                            "請在下方點選牌面加入，已啟動全場 4 張上限防呆",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedTiles.sorted().forEach { tile ->
                                MahjongTileView(
                                    tile = tile,
                                    width = 38.dp,
                                    height = 54.dp,
                                    onClick = { selectedTiles.remove(tile) }
                                )
                            }
                        }
                    }
                }
            }

            // 牌面選擇區
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    TabRow(
                        selectedTabIndex = activeSuit.ordinal,
                        containerColor = MahjongGreenDark,
                        contentColor = AccentGold,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TileSuit.values().forEach { suit ->
                            Tab(
                                selected = activeSuit == suit,
                                onClick = { activeSuit = suit },
                                text = { Text(suit.displayName.replace("子", ""), fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suitTiles.forEach { tile ->
                            val currentChosen = selectedTiles.count { it.id == tile.id }
                            val maxAllowed = MahjongTile.getMaxCount(tile)
                            val remaining = maxAllowed - currentChosen
                            val canAdd = remaining > 0 && selectedTiles.size < targetTileCount

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.then(if (!canAdd) Modifier.alpha(0.35f) else Modifier)
                            ) {
                                MahjongTileView(
                                    tile = tile,
                                    width = 44.dp,
                                    height = 62.dp,
                                    onClick = if (canAdd) {
                                        { selectedTiles.add(tile) }
                                    } else null
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "剩 $remaining",
                                    fontSize = 10.sp,
                                    color = if (canAdd) AccentGold else Color.Red
                                )
                            }
                        }
                    }
                }
            }

            // 開始對局按鈕
            Button(
                onClick = { onStartGameWithHand(selectedTiles.toList(), selectedDealer) },
                enabled = selectedTiles.size == targetTileCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGold,
                    disabledContainerColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (selectedTiles.size == targetTileCount) {
                        "開始 AI 決策對局 (${if (isSelfDealer) "我當莊先攻" else "${selectedDealer.title}當莊先攻"}) 🚀"
                    } else {
                        "請選滿 $targetTileCount 張起手手牌 (${selectedTiles.size}/$targetTileCount)"
                    },
                    color = if (selectedTiles.size == targetTileCount) Color.Black else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
