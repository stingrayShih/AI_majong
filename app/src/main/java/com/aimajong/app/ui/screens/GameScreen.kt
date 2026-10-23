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
    var showTilePickerForOtherTurn by remember { mutableStateOf(false) }
    var showOtherMeldDialog by remember { mutableStateOf(false) }
    var showSelfMeldDialog by remember { mutableStateOf(false) }
    var showAnGangDialog by remember { mutableStateOf(false) }
    var showHuDialog by remember { mutableStateOf(false) }

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
                        val dealerName = if (state.dealerSeat == PlayerSeat.SELF) "我當莊" else "${state.dealerSeat.title}當莊"
                        Surface(shape = RoundedCornerShape(4.dp), color = TileWanRed) {
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
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 頂部順位狀態條
            TurnStatusBanner(state = state)

            // 1. 各家牌桌與吃碰槓明牌展示區
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "牌桌動態 (各家牌河與副露明牌)",
                        color = AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    PlayerRiverAndMeldRow("上家", state.discards[PlayerSeat.SHANG_JIA] ?: emptyList(), state.otherMelds[PlayerSeat.SHANG_JIA] ?: emptyList(), isCurrent = state.currentTurnPlayer == PlayerSeat.SHANG_JIA)
                    PlayerRiverAndMeldRow("對家", state.discards[PlayerSeat.DUI_JIA] ?: emptyList(), state.otherMelds[PlayerSeat.DUI_JIA] ?: emptyList(), isCurrent = state.currentTurnPlayer == PlayerSeat.DUI_JIA)
                    PlayerRiverAndMeldRow("下家", state.discards[PlayerSeat.XIA_JIA] ?: emptyList(), state.otherMelds[PlayerSeat.XIA_JIA] ?: emptyList(), isCurrent = state.currentTurnPlayer == PlayerSeat.XIA_JIA)
                    PlayerRiverAndMeldRow("我的打牌", state.discards[PlayerSeat.SELF] ?: emptyList(), state.melds, isCurrent = state.currentTurnPlayer == PlayerSeat.SELF)
                }
            }

            // 2. AI 決策卡片
            Card(
                colors = CardDefaults.cardColors(containerColor = MahjongGreenDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "🤖 AI 決策建議",
                            color = AccentGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isLoadingDecision) {
                            CircularProgressIndicator(color = AccentGold, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    if (aiDecision != null) {
                        Text(
                            text = aiDecision!!.recommendedAction,
                            color = TextLight,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = aiDecision!!.explanationNote,
                            color = TextMuted,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        aiDecision!!.optionsProbability.take(3).forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                            ) {
                                Text(
                                    text = option.actionText,
                                    color = TextLight,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(120.dp)
                                )
                                LinearProgressIndicator(
                                    progress = { option.probability },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = AccentGold,
                                    trackColor = SurfaceDark
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${(option.probability * 100).toInt()}%",
                                    color = AccentGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            state.isGameOver -> "牌局已結束"
                            state.canSelfDiscard -> "🟢 輪到你出牌 (點牌打出)"
                            state.canSelfDraw -> "🟡 輪到你摸牌"
                            state.isInCallResponsePhase -> "⚡ 他家出牌：可點擊鳴牌或按【過】"
                            else -> "🔴 等待 ${state.currentTurnPlayer.title} 行動"
                        },
                        color = if (state.canSelfDiscard) Color(0xFF81C784) else AccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 手牌橫向列表
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.handTiles.forEach { tile ->
                        MahjongTileView(
                            tile = tile,
                            width = 38.dp,
                            height = 54.dp,
                            modifier = if (!state.canSelfDiscard) Modifier.alpha(0.7f) else Modifier,
                            onClick = {
                                if (state.isGameOver) {
                                    Toast.makeText(context, "牌局已結束！", Toast.LENGTH_SHORT).show()
                                } else if (state.canSelfDiscard) {
                                    viewModel.onDiscardTile(PlayerSeat.SELF, tile)
                                } else {
                                    Toast.makeText(context, "非出牌回合或尚未摸牌！", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // 4. 底部動作控制面板 (依當前狀態精準呈現對應按鈕)
            DynamicActionBar(
                state = state,
                onDrawClicked = { showTilePickerForDraw = true },
                onSelfMeldClicked = { showSelfMeldDialog = true },
                onAnGangClicked = { showAnGangDialog = true },
                onOtherTurnClicked = { showTilePickerForOtherTurn = true },
                onOtherMeldClicked = { showOtherMeldDialog = true },
                onPassCallClicked = { viewModel.onPassCall() },
                onHuClicked = { showHuDialog = true }
            )
        }
    }

    // 彈窗: 摸牌選擇器
    if (showTilePickerForDraw) {
        TilePickerDialog(
            title = "選擇摸到的牌",
            getRemainingCount = { tile -> state.remainingCount(tile) },
            onDismissRequest = { showTilePickerForDraw = false },
            onTileSelected = { tile -> viewModel.onDrawTile(tile) }
        )
    }

    // 彈窗: 記錄當前輪到的他家出牌
    if (showTilePickerForOtherTurn) {
        val currentSeat = state.currentTurnPlayer
        TilePickerDialog(
            title = "選擇 ${currentSeat.title} 打出的牌",
            getRemainingCount = { tile -> state.remainingCount(tile) },
            onDismissRequest = { showTilePickerForOtherTurn = false },
            onTileSelected = { tile -> viewModel.onOtherTurnAction(currentSeat, tile) }
        )
    }

    // 彈窗: 我吃/碰/槓
    if (showSelfMeldDialog) {
        SelfMeldDialog(
            state = state,
            onDismiss = { showSelfMeldDialog = false },
            onMeldConfirmed = { meld -> viewModel.onMeldAction(PlayerSeat.SELF, meld) }
        )
    }

    // 彈窗: 暗槓選擇
    if (showAnGangDialog) {
        AnGangDialog(
            anGangTiles = state.getAnGangOptions(),
            onDismiss = { showAnGangDialog = false },
            onTileSelected = { tile ->
                viewModel.onAnGang(tile)
                showAnGangDialog = false
            }
        )
    }

    // 彈窗: 他家鳴牌 (吃碰槓)
    if (showOtherMeldDialog) {
        OtherPlayerMeldDialog(
            state = state,
            onDismiss = { showOtherMeldDialog = false },
            onMeldConfirmed = { seat, meld ->
                viewModel.onMeldAction(seat, meld)
                showOtherMeldDialog = false
            }
        )
    }

    // 彈窗: 胡牌記錄
    if (showHuDialog) {
        HuDialog(
            state = state,
            onDismiss = { showHuDialog = false },
            onHuConfirmed = { winner, loser ->
                viewModel.onDeclareHu(winner, loser)
                showHuDialog = false
            }
        )
    }
}

/**
 * 頂部順位狀態條
 */
@Composable
fun TurnStatusBanner(state: GameState) {
    val (bgColor, textColor, message) = when {
        state.isGameOver -> Triple(TileWanRed, Color.White, "🏆 牌局已結束 (按上方復原或重開局)")
        state.isInCallResponsePhase -> {
            val disc = state.lastDiscard
            Triple(Color(0xFFE65100), Color.White, "⚡ ${disc?.first?.title} 打出 [${disc?.second?.name}] — 等待鳴牌判定")
        }
        state.currentTurnPlayer == PlayerSeat.SELF -> {
            if (state.phase == TurnPhase.AWAITING_DRAW) {
                Triple(AccentGoldDark, Color.Black, "🟢 輪到你摸牌，請點擊下方【摸牌】")
            } else {
                Triple(Color(0xFF2E7D32), Color.White, "🟢 輪到你出牌，請直接點選手牌打出")
            }
        }
        else -> Triple(CardBackground, TextLight, "🔴 目前輪到【${state.currentTurnPlayer.title}】行動")
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    ) {
        Text(
            text = message,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * 依據遊戲階段動態呈現的動作控制列
 */
@Composable
fun DynamicActionBar(
    state: GameState,
    onDrawClicked: () -> Unit,
    onSelfMeldClicked: () -> Unit,
    onAnGangClicked: () -> Unit,
    onOtherTurnClicked: () -> Unit,
    onOtherMeldClicked: () -> Unit,
    onPassCallClicked: () -> Unit,
    onHuClicked: () -> Unit
) {
    if (state.isGameOver) {
        // 牌局已結束：鎖定所有操作
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("牌局已鎖定 (若按錯可點右上角 ↩ 復原)", color = TextMuted, fontSize = 13.sp)
            }
        }
        return
    }

    when {
        // 1. 他家出牌後的【鳴牌判定階段】(AWAITING_CALL_RESPONSE)
        state.isInCallResponsePhase -> {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 我吃/碰/槓
                    Button(
                        onClick = onSelfMeldClicked,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                    ) {
                        Text("我 吃/碰/槓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // 他家吃碰槓 (順位跳轉)
                    Button(
                        onClick = onOtherMeldClicked,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackground)
                    ) {
                        Text("他家吃碰槓", color = TextLight, fontSize = 13.sp)
                    }

                    // 胡牌
                    Button(
                        onClick = onHuClicked,
                        modifier = Modifier.weight(0.9f),
                        colors = ButtonDefaults.buttonColors(containerColor = TileWanRed)
                    ) {
                        Text("胡牌", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // 過 (無人鳴牌，順位流向下一家)
                Button(
                    onClick = onPassCallClicked,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MahjongGreenDark)
                ) {
                    Text("過 (無人鳴牌，換下家摸牌)", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 2. 輪到自己摸牌 (AWAITING_DRAW)
        state.canSelfDraw -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDrawClicked,
                    modifier = Modifier.weight(2f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                ) {
                    Text("摸牌", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Button(
                    onClick = onHuClicked,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TileWanRed)
                ) {
                    Text("自摸胡牌", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 3. 輪到自己出牌 (AWAITING_DISCARD)
        state.canSelfDiscard -> {
            val anGangList = state.getAnGangOptions()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (anGangList.isNotEmpty()) {
                    Button(
                        onClick = onAnGangClicked,
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                    ) {
                        Text("暗槓 (${anGangList.first().name})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = onHuClicked,
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TileWanRed)
                ) {
                    Text("宣告自摸", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 4. 輪到他家摸打 (當前不是自己)
        else -> {
            val currentSeat = state.currentTurnPlayer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOtherTurnClicked,
                    modifier = Modifier.weight(2f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                ) {
                    Text("記錄【${currentSeat.title}】摸打出牌", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Button(
                    onClick = onHuClicked,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TileWanRed)
                ) {
                    Text("${currentSeat.title}自摸", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * 牌河與副露明牌顯示列
 */
@Composable
fun PlayerRiverAndMeldRow(seatName: String, discards: List<MahjongTile>, melds: List<Meld>, isCurrent: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(if (isCurrent) Modifier.background(MahjongGreenDark.copy(alpha = 0.5f), RoundedCornerShape(4.dp)) else Modifier)
    ) {
        Text(
            text = if (isCurrent) "▶$seatName:" else "$seatName:",
            color = if (isCurrent) AccentGold else TextMuted,
            fontSize = 11.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(48.dp)
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
                            .border(1.dp, if (meld.type == ActionType.GANG_AN) Color.Magenta else AccentGold, RoundedCornerShape(3.dp))
                            .padding(1.dp)
                    ) {
                        meld.tiles.forEach { tile ->
                            MahjongTileView(tile = tile, width = 16.dp, height = 24.dp)
                        }
                    }
                    Spacer(modifier = Modifier.width(2.dp))
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
                Text("尚無打牌", color = Color.Gray, fontSize = 10.sp)
            } else {
                discards.takeLast(8).forEach { tile ->
                    MahjongTileView(tile = tile, width = 20.dp, height = 30.dp)
                }
            }
        }
    }
}

/**
 * 自己鳴牌對話框 (吃、碰、明槓) - 嚴格限制：吃牌只有上家可吃
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
                Text("我 吃 / 碰 / 槓", color = AccentGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(10.dp))

                if (targetTile == null) {
                    Text("目前沒有最新打出的牌", color = TextMuted, fontSize = 13.sp)
                } else {
                    Text(
                        "目標牌: ${lastDiscard.first.title} 打出的 ${targetTile.name}",
                        color = TextLight,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. 碰 (任一家皆可碰)
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Text(if (canPeng) "碰牌 (${targetTile.name})" else "無法碰 (手牌不足兩張)", color = Color.Black)
                    }

                    // 2. 吃牌 (嚴格限定：只有上家打出的牌才可以吃！)
                    val isFromShangJia = lastDiscard.first == PlayerSeat.SHANG_JIA
                    if (isFromShangJia) {
                        val chiCombinations = state.getChiCombinations(targetTile)
                        if (chiCombinations.isEmpty()) {
                            Text("無符合的吃牌順子搭子", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                        } else {
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
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Text("吃牌: [${t1.name}, ${targetTile.name}, ${t2.name}]", color = TextLight)
                                }
                            }
                        }
                    } else {
                        Text("⚠️ 僅有上家出牌才可吃牌 (${lastDiscard.first.title}出牌不可吃)", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }

                    // 3. 明槓 (任一家皆可槓)
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Text("明槓 (${targetTile.name})", color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
            }
        }
    }
}

/**
 * 暗槓對話框
 */
@Composable
fun AnGangDialog(
    anGangTiles: List<MahjongTile>,
    onDismiss: () -> Unit,
    onTileSelected: (MahjongTile) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = CardBackground, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("選擇暗槓牌面", color = AccentGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                anGangTiles.forEach { tile ->
                    Button(
                        onClick = { onTileSelected(tile) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("暗槓 4 張 [${tile.name}] (槓尾補牌)", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
            }
        }
    }
}

/**
 * 他家鳴牌對話框 (吃、碰、槓後順位跳轉)
 */
@Composable
fun OtherPlayerMeldDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onMeldConfirmed: (seat: PlayerSeat, meld: Meld) -> Unit
) {
    val lastDiscard = state.lastDiscard ?: return
    val targetTile = lastDiscard.second
    var selectedSeat by remember { mutableStateOf(lastDiscard.first.next()) }
    var selectedType by remember { mutableStateOf(ActionType.PENG) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = CardBackground, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("記錄他家吃碰槓 (順位將跳轉)", color = AccentGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("目標牌: ${lastDiscard.first.title} 打出的 ${targetTile.name}", color = TextLight, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(10.dp))

                Text("哪一家鳴牌：", color = TextLight, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(PlayerSeat.SHANG_JIA, PlayerSeat.DUI_JIA, PlayerSeat.XIA_JIA).forEach { seat ->
                        FilterChip(
                            selected = selectedSeat == seat,
                            onClick = { selectedSeat = seat },
                            label = { Text(seat.title, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("鳴牌類型：", color = TextLight, fontSize = 12.sp)
                val isShangJiaOfTarget = lastDiscard.first.next() == selectedSeat
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 吃牌僅目標打牌者的下家（相對於打牌者是上家打牌）可吃
                    if (isShangJiaOfTarget) {
                        FilterChip(
                            selected = selectedType == ActionType.CHI,
                            onClick = { selectedType = ActionType.CHI },
                            label = { Text("吃牌", fontSize = 11.sp) }
                        )
                    }
                    FilterChip(
                        selected = selectedType == ActionType.PENG,
                        onClick = { selectedType = ActionType.PENG },
                        label = { Text("碰牌", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedType == ActionType.GANG_MING,
                        onClick = { selectedType = ActionType.GANG_MING },
                        label = { Text("明槓", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val tiles = when (selectedType) {
                            ActionType.PENG -> listOf(targetTile, targetTile, targetTile)
                            ActionType.GANG_MING -> listOf(targetTile, targetTile, targetTile, targetTile)
                            else -> listOf(targetTile, targetTile, targetTile)
                        }
                        val meld = Meld(type = selectedType, tiles = tiles, triggerTile = targetTile, targetSeat = lastDiscard.first)
                        onMeldConfirmed(selectedSeat, meld)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("確認鳴牌並跳轉順位至【${selectedSeat.title}】", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
            }
        }
    }
}

/**
 * 宣告胡牌對話框
 */
@Composable
fun HuDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onHuConfirmed: (winner: PlayerSeat, loser: PlayerSeat?) -> Unit
) {
    var winner by remember { mutableStateOf(PlayerSeat.SELF) }
    var isZiMo by remember { mutableStateOf(state.lastDiscard == null) }
    var loser by remember { mutableStateOf(state.lastDiscard?.first ?: PlayerSeat.SHANG_JIA) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = CardBackground, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏆 宣告胡牌結束本局", color = AccentGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                Text("誰胡牌：", color = TextLight, fontSize = 13.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlayerSeat.values().forEach { seat ->
                        FilterChip(
                            selected = winner == seat,
                            onClick = { winner = seat },
                            label = { Text(if (seat == PlayerSeat.SELF) "我" else seat.title.replace("家", ""), fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isZiMo) "方式：自摸" else "方式：抓放槍 (榮和)", color = TextLight, fontSize = 13.sp)
                    Switch(checked = isZiMo, onCheckedChange = { isZiMo = it })
                }

                if (!isZiMo) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("誰放槍：", color = TextLight, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlayerSeat.values().filter { it != winner }.forEach { seat ->
                            FilterChip(
                                selected = loser == seat,
                                onClick = { loser = seat },
                                label = { Text(if (seat == PlayerSeat.SELF) "我" else seat.title.replace("家", ""), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onHuConfirmed(winner, if (isZiMo) null else loser) },
                    colors = ButtonDefaults.buttonColors(containerColor = TileWanRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("確認胡牌並鎖定本局", color = Color.White, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
            }
        }
    }
}
