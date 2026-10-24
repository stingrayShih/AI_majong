package com.aimajong.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    dealerSeat: PlayerSeat = PlayerSeat.SELF,
    onBack: () -> Unit,
    onStartGameWithHand: (List<MahjongTile>, PlayerSeat) -> Unit
) {
    val selectedTiles = remember { mutableStateListOf<MahjongTile>() }
    val isSelfDealer = dealerSeat == PlayerSeat.SELF
    val targetTileCount = if (isSelfDealer) 17 else 16

    // 起手手牌絕不能包含花牌，花牌不提供選取
    val availableSuits = remember { TileSuit.values().filter { it != TileSuit.FLOWER } }
    var activeSuit by remember { mutableStateOf(TileSuit.HONOR) } // 預設先開字牌
    val suitTiles = remember(activeSuit) { MahjongTile.getTilesBySuit(activeSuit) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("選取起手手牌 (${selectedTiles.size}/$targetTileCount)", color = TextLight, fontSize = 17.sp) },
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
                        Text("載入範例牌", color = AccentGold, fontWeight = FontWeight.Bold)
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 【最上方：已確認莊家資訊卡片】
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("👑 本局莊家：【${dealerSeat.title}】", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (isSelfDealer) "您當莊 (起手 17 張牌，先攻出牌)" else "您為閒家 (起手 16 張牌)",
                            color = TextLight,
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1B4D2E)
                    ) {
                        Text(
                            text = "需要 $targetTileCount 張",
                            color = AccentGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 目前選取的手牌預覽區
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "我的起手牌 (${selectedTiles.size}/$targetTileCount 張):",
                            color = AccentGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedTiles.isNotEmpty()) {
                            IconButton(onClick = { selectedTiles.clear() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "清空", tint = Color.LightGray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (selectedTiles.isEmpty()) {
                        Text(
                            "點選下方牌面加入（同一種牌最多 4 張，花牌禁止加入起手手牌）",
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

            // 牌面選擇區 (排除花牌)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    TabRow(
                        selectedTabIndex = availableSuits.indexOf(activeSuit).coerceAtLeast(0),
                        containerColor = MahjongGreenDark,
                        contentColor = AccentGold,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableSuits.forEach { suit ->
                            Tab(
                                selected = activeSuit == suit,
                                onClick = { activeSuit = suit },
                                text = { Text(suit.displayName.replace("子", ""), fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                onClick = { onStartGameWithHand(selectedTiles.toList(), dealerSeat) },
                enabled = selectedTiles.size == targetTileCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGold,
                    disabledContainerColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (selectedTiles.size == targetTileCount) {
                        "開始 AI 決策對局 (${if (isSelfDealer) "我當莊先攻" else "${dealerSeat.title}當莊先攻"}) 🚀"
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
