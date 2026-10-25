package com.aimajong.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.data.db.GameRecordEntity
import com.aimajong.app.model.PlayerSeat
import com.aimajong.app.ui.components.MahjongTileView
import com.aimajong.app.ui.theme.*
import com.aimajong.app.ui.viewmodel.GameViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allRecords by viewModel.allGameRecords.collectAsState(initial = emptyList())
    var reviewingRecord by remember { mutableStateOf<GameRecordEntity?>(null) }
    var recordToDelete by remember { mutableStateOf<GameRecordEntity?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // 系統返回鍵處理
    BackHandler {
        if (reviewingRecord != null) {
            reviewingRecord = null
        } else {
            onBack()
        }
    }

    // 若使用者點擊了某局的「逐巡覆盤」，直接切入覆盤畫面
    if (reviewingRecord != null) {
        GameReplayScreen(
            record = reviewingRecord!!,
            recordRepo = viewModel.recordRepo,
            onBack = { reviewingRecord = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("牌局歷史與覆盤", color = TextLight, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextLight)
                    }
                },
                actions = {
                    if (allRecords.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空所有歷史", tint = TileWanRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MahjongGreenDark)
            )
        },
        containerColor = MahjongFeltGreen
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (allRecords.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("尚未有完成的歷史牌局", color = TextMuted, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("牌局結束（胡牌或海底流局）後，記錄將自動保存在 SQLite 本地資料庫", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allRecords, key = { it.gameId }) { record ->
                        GameRecordCard(
                            record = record,
                            viewModel = viewModel,
                            onReview = { reviewingRecord = record },
                            onDelete = { recordToDelete = record }
                        )
                    }
                }
            }
        }
    }

    // 單筆刪除確認對話框
    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("刪除牌局紀錄") },
            text = { Text("確定要刪除這筆牌局紀錄（${recordToDelete!!.summaryTitle}）嗎？此動作無法復原。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGameRecord(recordToDelete!!.gameId)
                        recordToDelete = null
                        Toast.makeText(context, "已刪除紀錄", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("刪除", color = TileWanRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 清空全部確認對話框
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空所有牌局紀錄") },
            text = { Text("確定要清空資料庫內全部歷史牌局嗎？清空後將無法復原。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllGameRecords()
                        showClearAllDialog = false
                        Toast.makeText(context, "已清空所有歷史紀錄", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("確認清空", color = TileWanRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun GameRecordCard(
    record: GameRecordEntity,
    viewModel: GameViewModel,
    onReview: () -> Unit,
    onDelete: () -> Unit
) {
    val winningTile = remember(record) { viewModel.recordRepo.parseSingleTile(record.winningTileJson) }
    val formattedDate = remember(record.endTime) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        sdf.format(Date(record.endTime))
    }

    val (tagColor, tagText) = when {
        record.isDrawGame -> Pair(Color(0xFF8E8E93), "海底流局")
        record.winnerSeat == PlayerSeat.SELF.name && record.isZimo -> Pair(TileTiaoGreen, "自家自摸")
        record.winnerSeat == PlayerSeat.SELF.name -> Pair(AccentGold, "抓放槍勝")
        record.loserSeat == PlayerSeat.SELF.name -> Pair(TileWanRed, "自家放槍")
        else -> Pair(SurfaceDark, "他家胡牌")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 第一列：勝負標籤 + 日期時間 + 刪除按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = tagColor
                    ) {
                        Text(
                            text = " $tagText ",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedDate,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "刪除此紀錄",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 第二列：摘要標題與詳情
            Text(
                text = record.summaryTitle,
                color = TextLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = record.summaryDetail,
                color = TextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 第三列：胡牌牌面預覽 + 逐巡覆盤按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (winningTile != null && !record.isDrawGame) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("胡牌: ", color = TextMuted, fontSize = 12.sp)
                        MahjongTileView(
                            tile = winningTile,
                            width = 24.dp,
                            height = 34.dp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = onReview,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "逐巡覆盤",
                        tint = MahjongGreenDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "逐巡覆盤",
                        color = MahjongGreenDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
