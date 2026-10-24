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
import androidx.compose.ui.platform.LocalDensity
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
    val density = LocalDensity.current
    // 關鍵修復：麻將牌文字需鎖定牌面比例，避免系統特大字體導致字體暴衝溢出牌面破壞排版
    val fontScale = density.fontScale.coerceAtLeast(1.0f)
    val doubleCharFontSize = (if (height < 50.dp) 11.sp else 14.sp) / fontScale
    val doubleCharLineHeight = (if (height < 50.dp) 12.sp else 15.sp) / fontScale
    val singleCharFontSize = (if (height < 50.dp) 13.sp else 17.sp) / fontScale

    val textColor = when (tile.suit) {
        TileSuit.WAN -> TileWanRed
        TileSuit.TONG -> TileTongBlue
        TileSuit.TIAO -> TileTiaoGreen
        TileSuit.HONOR -> when (tile.id) {
            "zhong" -> TileWanRed
            "fa" -> TileTiaoGreen
            else -> TileHonorBlack
        }
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
                    fontSize = doubleCharFontSize,
                    lineHeight = doubleCharLineHeight
                )
                Text(
                    text = tile.name.substring(1),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = doubleCharFontSize,
                    lineHeight = doubleCharLineHeight
                )
            } else {
                Text(
                    text = tile.name,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = singleCharFontSize
                )
            }
        }
    }
}
