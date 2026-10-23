package com.aimajong.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    val context = LocalContext.current
    val state by viewModel.gameState.collectAsState()
    val aiDecision by viewModel.aiDecision.collectAsState()
    val isLoadingDecision by viewModel.isLoadingDecision.collectAsState()

    var showTilePickerForDraw by remember { mutableStateOf(false) }
    var showOtherActionDialog by remember { mutableStateOf(false) }
    var showSelfActionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "第 ${state.currentTurn} 巡",
                            color = TextLight,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // 莊家標籤
                        val dealerName = if (state.dealerSeat == PlayerSeat.SELF) "我當莊" else "${state.dealerSeat.title}當莊"
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = TileWanRed
                        ) {
                            Text(
                                text = " 莊: $dealerName ",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextLight)
                    }
                },
                actions = {
                    // ↩ 復原上一步 (Undo)
                    IconButton(
                        onClick = {
                            val success = viewModel.undoLastAction()
                            if (success) {
                                Toast.makeText(context, "已復原上一步操作", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "無更早的操作可復原", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "復原",
                            tint = if (viewModel.canUndo) AccentGold else Color.Gray
                        )
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. 各家牌桌與吃碰槓明牌展示區
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "牌桌動態 (牌河與副露明牌)",
                        color = AccentGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    PlayerRiverAndMeldRow("上家", state.discards[PlayerSeat.SHANG_JIA] ?: emptyList(), state.otherMelds[PlayerSeat.SHANG_JIA] ?: emptyList())
                    PlayerRiverAndMeldRow("對家", state.discards[PlayerSeat.DUI_JIA] ?: emptyList(), state.otherMelds[PlayerSeat.DUI_JIA] ?: emptyList())
                    PlayerRiverAndMeldRow("下家", state.discards[PlayerSeat.XIA_JIA] ?: emptyList(), state.otherMelds[PlayerSeat.XIA_JIA] ?: emptyList())
                    PlayerRiverAndMeldRow("我的打牌", state.discards[PlayerSeat.SELF] ?: emptyList(), state.melds)
                }
            }

            // 2. AI 決策卡片
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

                    Spacer(modifier = Modifier.height(4.dp))

                    if (aiDecision != null) {
                        Text(
                            text = aiDecision!!.recommendedAction,
                            color = TextLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = aiDecision!!.explanationNote,
                            color = TextMuted,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        aiDecision!!.optionsProbability.take(3).forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                            ) {
                                Text(
                                    text = option.actionText,
                                    color = TextLight,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(110.dp)
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
                    }
                }
            }

            // 3. 自己的手牌區域 (含防呆鎖定狀態提示)
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
                        if (state.canSelfDiscard) "🟢 可打出一張牌" else "⏸ 請先摸牌或等待他家出牌",
                        color = if (state.canSelfDiscard) Color(0xFF81C784) else Color(0xFFFFB74D),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
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
                            modifier = if (!state.canSelfDiscard) Modifier.alpha(0.85f) else Modifier,
                            onClick = {
                                if (state.canSelfDiscard) {
                                    viewModel.onDiscardTile(PlayerSeat.SELF, tile)
                                } else {
                                    Toast.makeText(context, "目前尚未摸牌，不能連續出牌！請先點擊「摸牌」", Toast.LENGTH_SHORT).show()
                                }
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
                // 摸牌按鈕
                Button(
                    onClick = { showTilePickerForDraw = true },
                    enabled = state.canSelfDraw,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGold,
                        disabledContainerColor = Color.DarkGray
                    )
                ) {
                    Text(
                        "摸牌",
                        color = if (state.canSelfDraw) Color.Black else Color.LightGray,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 我吃/碰/槓按鈕
                Button(
                    onClick = { showSelfActionDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MahjongGreenDark)
                ) {
                    Text("我吃/碰/槓", color = TextLight, fontWeight = FontWeight.Bold)
                }

                // 記錄他家動作按鈕 (完整支援上家/對家/下家 打牌與吃碰槓)
                Button(
                    onClick = { showOtherActionDialog = true },
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackground)
                ) {
                    Text("記錄他家動作", color = TextLight)
                }
            }
        }
    }

    // 摸牌選擇器 (帶剩餘牌數檢查)
    if (showTilePickerForDraw) {
        TilePickerDialog(
            title = "選擇摸到的牌",
            getRemainingCount = { tile -> state.remainingCount(tile) },
            onDismissRequest = { showTilePickerForDraw = false },
            onTileSelected = { tile -> viewModel.onDrawTile(tile) }
        )
    }

    // 他家動作對話框 (選擇座位 + 動作類型)
    if (showOtherActionDialog) {
        OtherPlayerActionDialog(
            state = state,
            onDismiss = { showOtherActionDialog = false },
            onActionConfirmed = { seat, type, tile, meldTiles ->
                if (type == ActionType.DISCARD) {
                    viewModel.onDiscardTile(seat, tile)
                } else {
                    val meld = Meld(type = type, tiles = meldTiles, triggerTile = tile)
                    viewModel.onMeldAction(seat, meld)
                }
            }
        )
    }

    // 自己鳴牌對話框 (吃、碰、槓)
    if (showSelfActionDialog) {
        SelfMeldDialog(
            state = state,
            onDismiss = { showSelfActionDialog = false },
            onMeldConfirmed = { meld ->
                viewModel.onMeldAction(PlayerSeat.SELF, meld)
            }
        )
    }
}

/**
 * 牌河與副露明牌顯示列
 */
@Composable
fun PlayerRiverAndMeldRow(seatName: String, discards: List<MahjongTile>, melds: List<Meld>) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$seatName:",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(46.dp)
            )

            // 副露明牌展示 (吃碰槓)
            if (melds.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    melds.forEach { meld ->
                        Row(
                            modifier = Modifier
                                .border(1.dp, AccentGold, RoundedCornerShape(4.dp))
                                .padding(1.dp)
                        ) {
                            meld.tiles.forEach { tile ->
                                MahjongTileView(tile = tile, width = 18.dp, height = 26.dp)
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }

            // 牌河 (棄牌)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (discards.isEmpty() && melds.isEmpty()) {
                    Text("尚無打牌", color = Color.Gray, fontSize = 11.sp)
                } else {
                    discards.takeLast(8).forEach { tile ->
                        MahjongTileView(tile = tile, width = 22.dp, height = 32.dp)
                    }
                }
            }
        }
    }
}

/**
 * 記錄他家動作對話框 (支援選擇上家/對家/下家，以及打牌、吃、碰、槓)
 */
@Composable
fun OtherPlayerActionDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onActionConfirmed: (seat: PlayerSeat, type: ActionType, tile: MahjongTile, meldTiles: List<MahjongTile>) -> Unit
) {
    var selectedSeat by remember { mutableStateOf(PlayerSeat.SHANG_JIA) }
    var selectedActionType by remember { mutableStateOf(ActionType.DISCARD) }
    var showTilePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "記錄他家動作",
                    color = AccentGold,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 1. 選擇座位
                Text("選擇哪一家：", color = TextLight, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(PlayerSeat.SHANG_JIA, PlayerSeat.DUI_JIA, PlayerSeat.XIA_JIA).forEach { seat ->
                        FilterChip(
                            selected = selectedSeat == seat,
                            onClick = { selectedSeat = seat },
                            label = { Text(seat.title, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. 選擇動作類型
                Text("採取了什麼動作：", color = TextLight, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(ActionType.DISCARD, ActionType.PENG, ActionType.GANG_MING).forEach { type ->
                        FilterChip(
                            selected = selectedActionType == type,
                            onClick = { selectedActionType = type },
                            label = { Text(type.label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showTilePicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("選擇該動作涉及的牌面", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text("取消", color = TextMuted)
                }
            }
        }
    }

    if (showTilePicker) {
        TilePickerDialog(
            title = "選擇 ${selectedSeat.title} ${selectedActionType.label} 的牌面",
            getRemainingCount = { tile -> state.remainingCount(tile) },
            onDismissRequest = { showTilePicker = false },
            onTileSelected = { tile ->
                val meldTiles = when (selectedActionType) {
                    ActionType.PENG -> listOf(tile, tile, tile)
                    ActionType.GANG_MING, ActionType.GANG_AN -> listOf(tile, tile, tile, tile)
                    else -> listOf(tile)
                }
                onActionConfirmed(selectedSeat, selectedActionType, tile, meldTiles)
                showTilePicker = false
                onDismiss()
            }
        )
    }
}

/**
 * 自己鳴牌對話框 (吃、碰、槓)
 */
@Composable
fun SelfMeldDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onMeldConfirmed: (Meld) -> Unit
) {
    val lastDiscard = state.lastDiscard
    val targetTile = lastDiscard?.second

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("我吃 / 碰 / 槓", color = AccentGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(12.dp))

                if (targetTile == null) {
                    Text("目前牌桌上沒有最新打出的牌可吃碰槓", color = TextMuted, fontSize = 13.sp)
                } else {
                    Text(
                        "目標牌: ${lastDiscard.first.title} 打出的 ${targetTile.name}",
                        color = TextLight,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. 碰
                    val canPeng = state.canPeng(targetTile)
                    Button(
                        onClick = {
                            val meld = Meld(
                                type = ActionType.PENG,
                                tiles = listOf(targetTile, targetTile, targetTile),
                                triggerTile = targetTile,
                                targetSeat = lastDiscard.first
                            )
                            onMeldConfirmed(meld)
                            onDismiss()
                        },
                        enabled = canPeng,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(if (canPeng) "碰牌 (${targetTile.name})" else "無法碰 (手牌不足兩張)", color = Color.Black)
                    }

                    // 2. 吃 (僅上家)
                    val chiCombinations = if (lastDiscard.first == PlayerSeat.SHANG_JIA) {
                        state.getChiCombinations(targetTile)
                    } else emptyList()

                    chiCombinations.forEach { (t1, t2) ->
                        Button(
                            onClick = {
                                val fullCombo = listOf(t1, targetTile, t2).sorted()
                                val meld = Meld(
                                    type = ActionType.CHI,
                                    tiles = fullCombo,
                                    triggerTile = targetTile,
                                    targetSeat = lastDiscard.first
                                )
                                onMeldConfirmed(meld)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MahjongGreenDark),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("吃牌: [${t1.name}, ${targetTile.name}, ${t2.name}]", color = TextLight)
                        }
                    }

                    // 3. 槓
                    val canGang = state.canMingGang(targetTile)
                    if (canGang) {
                        Button(
                            onClick = {
                                val meld = Meld(
                                    type = ActionType.GANG_MING,
                                    tiles = listOf(targetTile, targetTile, targetTile, targetTile),
                                    triggerTile = targetTile,
                                    targetSeat = lastDiscard.first
                                )
                                onMeldConfirmed(meld)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("明槓 (${targetTile.name})", color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onDismiss) {
                    Text("取消", color = TextMuted)
                }
            }
        }
    }
}
