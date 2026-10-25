package com.aimajong.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.data.db.GameRecordEntity
import com.aimajong.app.data.db.GameRecordRepository
import com.aimajong.app.model.*
import com.aimajong.app.ui.components.MahjongTileBackView
import com.aimajong.app.ui.components.MahjongTileView
import com.aimajong.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameReplayScreen(
    record: GameRecordEntity,
    recordRepo: GameRecordRepository,
    onBack: () -> Unit
) {
    // 攔截返回鍵
    BackHandler {
        onBack()
    }

    val initialHand = remember(record) { recordRepo.parseTiles(record.initialHandJson) }
    val dealerSeat = remember(record) {
        try { PlayerSeat.valueOf(record.dealerSeat) } catch (e: Exception) { PlayerSeat.SELF }
    }
    val actions = remember(record) { recordRepo.parseActions(record.actionsJson) }

    val snapshots = remember(record) {
        ReplayEngine.buildSnapshots(initialHand, dealerSeat, actions)
    }

    var currentStepIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedRiverTab by remember { mutableStateOf(PlayerSeat.SELF) }

    val timelineListState = rememberLazyListState()

    // 自動播放邏輯
    LaunchedEffect(isPlaying, currentStepIndex) {
        if (isPlaying) {
            if (currentStepIndex < snapshots.lastIndex) {
                delay(1200)
                currentStepIndex++
            } else {
                isPlaying = false
            }
        }
    }

    // 隨當前步數自動滾動歷程清單
    LaunchedEffect(currentStepIndex) {
        if (snapshots.isNotEmpty()) {
            timelineListState.animateScrollToItem((currentStepIndex - 2).coerceAtLeast(0))
        }
    }

    val currentSnapshot = if (snapshots.isNotEmpty()) {
        snapshots[currentStepIndex.coerceIn(0, snapshots.lastIndex)]
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("牌局逐巡覆盤", color = TextLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${record.summaryTitle} • 起莊: ${dealerSeat.title}",
                            color = AccentGold,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回歷史列表", tint = TextLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MahjongGreenDark)
            )
        },
        containerColor = MahjongFeltGreen
    ) { padding ->
        if (currentSnapshot == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("無法載入本局覆盤資料", color = TextMuted)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 1. 控制台與當前動作卡片
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MahjongGreenLight
                        ) {
                            Text(
                                text = " 步數: $currentStepIndex / ${snapshots.lastIndex} (第 ${currentSnapshot.turnNumber} 巡) ",
                                color = TextLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                        }

                        if (currentSnapshot.actionTile != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("動作牌: ", color = TextMuted, fontSize = 12.sp)
                                MahjongTileView(
                                    tile = currentSnapshot.actionTile,
                                    width = 28.dp,
                                    height = 38.dp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 當前動作描述文字
                    Text(
                        text = currentSnapshot.description,
                        color = if (currentSnapshot.actionType == ActionType.HU) TileWanRed else AccentGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 進度滑桿
                    Slider(
                        value = currentStepIndex.toFloat(),
                        onValueChange = {
                            isPlaying = false
                            currentStepIndex = it.toInt()
                        },
                        valueRange = 0f..snapshots.lastIndex.toFloat().coerceAtLeast(1f),
                        steps = (snapshots.size - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentGold,
                            activeTrackColor = AccentGold,
                            inactiveTrackColor = SurfaceDark
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 步進控制按鈕列
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isPlaying = false
                                currentStepIndex = 0
                            },
                            enabled = currentStepIndex > 0
                        ) {
                            Icon(Icons.Default.FirstPage, contentDescription = "開局起手", tint = if (currentStepIndex > 0) AccentGold else Color.Gray)
                        }

                        IconButton(
                            onClick = {
                                isPlaying = false
                                if (currentStepIndex > 0) currentStepIndex--
                            },
                            enabled = currentStepIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一步", tint = if (currentStepIndex > 0) AccentGold else Color.Gray)
                        }

                        // 播放/暫停按鈕
                        FilledIconButton(
                            onClick = {
                                if (currentStepIndex >= snapshots.lastIndex) {
                                    currentStepIndex = 0
                                }
                                isPlaying = !isPlaying
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = AccentGold)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暫停" else "自動播放",
                                tint = MahjongGreenDark
                            )
                        }

                        IconButton(
                            onClick = {
                                isPlaying = false
                                if (currentStepIndex < snapshots.lastIndex) currentStepIndex++
                            },
                            enabled = currentStepIndex < snapshots.lastIndex
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一步", tint = if (currentStepIndex < snapshots.lastIndex) AccentGold else Color.Gray)
                        }

                        IconButton(
                            onClick = {
                                isPlaying = false
                                currentStepIndex = snapshots.lastIndex
                            },
                            enabled = currentStepIndex < snapshots.lastIndex
                        ) {
                            Icon(Icons.AutoMirrored.Filled.LastPage, contentDescription = "牌局末步", tint = if (currentStepIndex < snapshots.lastIndex) AccentGold else Color.Gray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. 當前步數自家手牌區
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
                            "🀄 自家手牌 (共 ${currentSnapshot.selfHand.size} 張)",
                            color = TextLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentSnapshot.selfFlowers.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("花牌: ", color = TextMuted, fontSize = 11.sp)
                                currentSnapshot.selfFlowers.forEach { f ->
                                    Text(f.name, color = TileTiaoGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 副露
                        currentSnapshot.selfMelds.forEach { meld ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceDark,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    meld.tiles.forEach { tile ->
                                        MahjongTileView(tile = tile, width = 30.dp, height = 42.dp)
                                    }
                                }
                            }
                        }

                        // 暗牌手牌
                        currentSnapshot.selfHand.forEach { tile ->
                            MahjongTileView(tile = tile, width = 34.dp, height = 48.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. 四家牌河檢視標籤
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
                        Text("🌊 牌河情況", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        // 選擇切換四家牌河
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                PlayerSeat.SELF to "自家",
                                PlayerSeat.XIA_JIA to "下家",
                                PlayerSeat.DUI_JIA to "對家",
                                PlayerSeat.SHANG_JIA to "上家"
                            ).forEach { (seat, label) ->
                                val count = currentSnapshot.discards[seat]?.size ?: 0
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (selectedRiverTab == seat) AccentGold else SurfaceDark,
                                    modifier = Modifier.clickable { selectedRiverTab = seat }
                                ) {
                                    Text(
                                        text = "$label($count)",
                                        color = if (selectedRiverTab == seat) MahjongGreenDark else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val discardsOfSeat = currentSnapshot.discards[selectedRiverTab] ?: emptyList()
                    val meldsOfSeat = if (selectedRiverTab == PlayerSeat.SELF) {
                        currentSnapshot.selfMelds
                    } else {
                        currentSnapshot.otherMelds[selectedRiverTab] ?: emptyList()
                    }

                    if (meldsOfSeat.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp).horizontalScroll(rememberScrollState())
                        ) {
                            Text("副露: ", color = TextMuted, fontSize = 11.sp)
                            meldsOfSeat.forEach { meld ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SurfaceDark,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Row(modifier = Modifier.padding(2.dp)) {
                                        if (meld.type == ActionType.GANG_AN && selectedRiverTab != PlayerSeat.SELF) {
                                            repeat(4) {
                                                MahjongTileBackView(width = 24.dp, height = 34.dp)
                                                Spacer(modifier = Modifier.width(1.dp))
                                            }
                                        } else {
                                            meld.tiles.forEach { t ->
                                                MahjongTileView(tile = t, width = 24.dp, height = 34.dp)
                                                Spacer(modifier = Modifier.width(1.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (discardsOfSeat.isEmpty()) {
                        Text("尚未有打出的捨牌", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            discardsOfSeat.forEachIndexed { idx, tile ->
                                MahjongTileView(
                                    tile = tile,
                                    width = 28.dp,
                                    height = 40.dp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. 完整歷程清單 (Timeline)
            Text("📜 對局完整歷程 (點擊任一步驟即可跳轉):", color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                state = timelineListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(snapshots) { index, snap ->
                    val isCurrent = index == currentStepIndex
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCurrent) MahjongGreenLight else CardBackground,
                        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, AccentGold) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isPlaying = false
                                currentStepIndex = index
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (index == 0) "起手" else "#$index (第${snap.turnNumber}巡)",
                                    color = if (isCurrent) AccentGold else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(75.dp)
                                )
                                Text(
                                    text = snap.description,
                                    color = if (isCurrent) TextLight else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            if (snap.actionTile != null) {
                                MahjongTileView(
                                    tile = snap.actionTile,
                                    width = 20.dp,
                                    height = 28.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
