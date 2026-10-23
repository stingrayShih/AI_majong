package com.aimajong.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimajong.app.model.MahjongTile
import com.aimajong.app.model.TileSuit
import com.aimajong.app.ui.theme.*

@Composable
fun MahjongTileView(
    tile: MahjongTile,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    width: Dp = 44.dp,
    height: Dp = 60.dp,
    onClick: (() -> Unit)? = null
) {
    val textColor = when (tile.suit) {
        TileSuit.WAN -> TileWanRed
        TileSuit.TONG -> TileTongBlue
        TileSuit.TIAO -> TileTiaoGreen
        TileSuit.HONOR -> when (tile.id) {
            "zhong" -> TileWanRed
            "fa" -> TileTiaoGreen
            else -> TileHonorBlack
        }
        TileSuit.FLOWER -> TileFlowerPurple
    }

    val borderColor = if (isSelected) AccentGold else TileBorder
    val borderWidth = if (isSelected) 3.dp else 1.dp
    val elevation = if (isSelected) 6.dp else 2.dp

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .shadow(elevation, shape = RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(TileIvory)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 牌面文字
            if (tile.name.length >= 2) {
                Text(
                    text = tile.name.substring(0, 1),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (height < 50.dp) 12.sp else 15.sp,
                    lineHeight = if (height < 50.dp) 13.sp else 16.sp
                )
                Text(
                    text = tile.name.substring(1),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (height < 50.dp) 12.sp else 15.sp,
                    lineHeight = if (height < 50.dp) 13.sp else 16.sp
                )
            } else {
                Text(
                    text = tile.name,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (height < 50.dp) 14.sp else 18.sp
                )
            }
        }
    }
}
