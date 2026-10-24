package com.aimajong.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aimajong.app.model.MahjongTile
import com.aimajong.app.model.TileSuit
import com.aimajong.app.ui.theme.AccentGold
import com.aimajong.app.ui.theme.CardBackground
import com.aimajong.app.ui.theme.MahjongGreenDark

@Composable
fun TilePickerDialog(
    title: String = "請選擇牌面",
    initialSuit: TileSuit = TileSuit.HONOR, // 預設優先開啟【字牌】頁面
    getRemainingCount: ((MahjongTile) -> Int)? = null,
    onDismissRequest: () -> Unit,
    onTileSelected: (MahjongTile) -> Unit
) {
    var selectedSuit by remember { mutableStateOf(initialSuit) }
    val availableSuits = remember { TileSuit.values().toList() }

    val tilesForSuit = remember(selectedSuit) {
        MahjongTile.getTilesBySuit(selectedSuit)
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBackground,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 花色分類切換 Tab (預設開啟字牌)
                TabRow(
                    selectedTabIndex = availableSuits.indexOf(selectedSuit).coerceAtLeast(0),
                    containerColor = MahjongGreenDark,
                    contentColor = AccentGold,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableSuits.forEach { suit ->
                        Tab(
                            selected = selectedSuit == suit,
                            onClick = { selectedSuit = suit },
                            text = {
                                Text(
                                    text = suit.displayName.replace("子", ""),
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedSuit == suit) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 牌面點選區
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tilesForSuit.forEach { tile ->
                        val remaining = getRemainingCount?.invoke(tile) ?: 4
                        val isAvailable = remaining > 0

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.then(
                                if (!isAvailable) Modifier.alpha(0.35f) else Modifier
                            )
                        ) {
                            MahjongTileView(
                                tile = tile,
                                width = 46.dp,
                                height = 66.dp,
                                onClick = if (isAvailable) {
                                    {
                                        onTileSelected(tile)
                                        onDismissRequest()
                                    }
                                } else null
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "剩 $remaining",
                                fontSize = 10.sp,
                                color = if (isAvailable) AccentGold else Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
