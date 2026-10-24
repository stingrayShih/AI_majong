package com.aimajong.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot

enum class CropTouchHandle {
    NONE,
    BODY,          // 拖曳整塊框體 (移動位置)
    TOP_LEFT,      // 左上角調整
    TOP_RIGHT,     // 右上角調整
    BOTTOM_LEFT,   // 左下角調整
    BOTTOM_RIGHT,  // 右下角調整
    TOP_EDGE,      // 頂邊調整高度
    BOTTOM_EDGE,   // 底邊調整高度
    LEFT_EDGE,     // 左邊調整寬度
    RIGHT_EDGE     // 右邊調整寬度
}

/**
 * 手指直接拖曳與縮放的手牌裁切對齊框組件
 * 極致順暢修復版：
 * 1. 使用 rememberUpdatedState 避免重組時 pointerInput 被中斷取消
 * 2. 框內任意觸控區域均判定為 BODY 平移移動，左右上下皆可順滑拖動
 * 3. 四個角落大半徑黃色圓球把手專門縮放長寬
 * 4. 框內即時等距分割為 tileCount (16 或 17) 等份，並標記 #1 ~ #16/17 序號
 */
@Composable
fun DraggableCropOverlay(
    cropRectNorm: Rect, // 歸一化座標 0.0f .. 1.0f
    onCropRectChanged: (Rect) -> Unit,
    tileCount: Int = 16,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cornerHitRadiusPx = with(density) { 44.dp.toPx() }
    val cornerVisualRadiusPx = with(density) { 11.dp.toPx() }
    val edgeHitMarginPx = with(density) { 24.dp.toPx() }
    val borderWidthPx = with(density) { 2.5.dp.toPx() }
    val textSizePx = with(density) { 11.dp.toPx() }

    // 關鍵：使用 rememberUpdatedState 保持最新狀態，避免 pointerInput 重複取消重建！
    val currentCropRect by rememberUpdatedState(cropRectNorm)
    val currentOnCropRectChanged by rememberUpdatedState(onCropRectChanged)

    var currentHandle by remember { mutableStateOf(CropTouchHandle.NONE) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()

        if (viewW <= 0f || viewH <= 0f) return@BoxWithConstraints

        val textPaint = remember(textSizePx) {
            Paint().apply {
                color = android.graphics.Color.WHITE
                this.textSize = textSizePx
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
            }
        }

        val centerHintPaint = remember(density) {
            Paint().apply {
                color = android.graphics.Color.argb(220, 255, 255, 255)
                this.textSize = with(density) { 12.dp.toPx() }
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewW, viewH) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val x = startOffset.x
                            val y = startOffset.y

                            val rect = currentCropRect
                            val pixelLeft = rect.left * viewW
                            val pixelTop = rect.top * viewH
                            val pixelRight = rect.right * viewW
                            val pixelBottom = rect.bottom * viewH

                            // 1. 優先判定四角圓形縮放把手
                            currentHandle = when {
                                hypot(x - pixelLeft, y - pixelTop) <= cornerHitRadiusPx -> CropTouchHandle.TOP_LEFT
                                hypot(x - pixelRight, y - pixelTop) <= cornerHitRadiusPx -> CropTouchHandle.TOP_RIGHT
                                hypot(x - pixelLeft, y - pixelBottom) <= cornerHitRadiusPx -> CropTouchHandle.BOTTOM_LEFT
                                hypot(x - pixelRight, y - pixelBottom) <= cornerHitRadiusPx -> CropTouchHandle.BOTTOM_RIGHT

                                // 2. 框內任意觸控點 ➔ 一律視為平移移動 (BODY)！保證手指隨意按進去就能任意拖曳！
                                x in pixelLeft..pixelRight && y in pixelTop..pixelBottom -> CropTouchHandle.BODY

                                // 3. 邊緣外圍微調
                                abs(y - pixelTop) <= edgeHitMarginPx && x in (pixelLeft - edgeHitMarginPx)..(pixelRight + edgeHitMarginPx) -> CropTouchHandle.TOP_EDGE
                                abs(y - pixelBottom) <= edgeHitMarginPx && x in (pixelLeft - edgeHitMarginPx)..(pixelRight + edgeHitMarginPx) -> CropTouchHandle.BOTTOM_EDGE
                                abs(x - pixelLeft) <= edgeHitMarginPx && y in (pixelTop - edgeHitMarginPx)..(pixelBottom + edgeHitMarginPx) -> CropTouchHandle.LEFT_EDGE
                                abs(x - pixelRight) <= edgeHitMarginPx && y in (pixelTop - edgeHitMarginPx)..(pixelBottom + edgeHitMarginPx) -> CropTouchHandle.RIGHT_EDGE

                                else -> CropTouchHandle.NONE
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (currentHandle == CropTouchHandle.NONE) return@detectDragGestures

                            val ndx = dragAmount.x / viewW
                            val ndy = dragAmount.y / viewH

                            val minW = 0.20f // 最小寬度 20%
                            val minH = 0.04f // 最小高度 4%
                            val rect = currentCropRect

                            when (currentHandle) {
                                CropTouchHandle.BODY -> {
                                    val w = rect.width
                                    val h = rect.height
                                    val newLeft = (rect.left + ndx).coerceIn(0f, 1f - w)
                                    val newTop = (rect.top + ndy).coerceIn(0f, 1f - h)
                                    currentOnCropRectChanged(Rect(newLeft, newTop, newLeft + w, newTop + h))
                                }
                                CropTouchHandle.TOP_LEFT -> {
                                    val newLeft = (rect.left + ndx).coerceIn(0f, rect.right - minW)
                                    val newTop = (rect.top + ndy).coerceIn(0f, rect.bottom - minH)
                                    currentOnCropRectChanged(Rect(newLeft, newTop, rect.right, rect.bottom))
                                }
                                CropTouchHandle.TOP_RIGHT -> {
                                    val newRight = (rect.right + ndx).coerceIn(rect.left + minW, 1f)
                                    val newTop = (rect.top + ndy).coerceIn(0f, rect.bottom - minH)
                                    currentOnCropRectChanged(Rect(rect.left, newTop, newRight, rect.bottom))
                                }
                                CropTouchHandle.BOTTOM_LEFT -> {
                                    val newLeft = (rect.left + ndx).coerceIn(0f, rect.right - minW)
                                    val newBottom = (rect.bottom + ndy).coerceIn(rect.top + minH, 1f)
                                    currentOnCropRectChanged(Rect(newLeft, rect.top, rect.right, newBottom))
                                }
                                CropTouchHandle.BOTTOM_RIGHT -> {
                                    val newRight = (rect.right + ndx).coerceIn(rect.left + minW, 1f)
                                    val newBottom = (rect.bottom + ndy).coerceIn(rect.top + minH, 1f)
                                    currentOnCropRectChanged(Rect(rect.left, rect.top, newRight, newBottom))
                                }
                                CropTouchHandle.TOP_EDGE -> {
                                    val newTop = (rect.top + ndy).coerceIn(0f, rect.bottom - minH)
                                    currentOnCropRectChanged(Rect(rect.left, newTop, rect.right, rect.bottom))
                                }
                                CropTouchHandle.BOTTOM_EDGE -> {
                                    val newBottom = (rect.bottom + ndy).coerceIn(rect.top + minH, 1f)
                                    currentOnCropRectChanged(Rect(rect.left, rect.top, rect.right, newBottom))
                                }
                                CropTouchHandle.LEFT_EDGE -> {
                                    val newLeft = (rect.left + ndx).coerceIn(0f, rect.right - minW)
                                    currentOnCropRectChanged(Rect(newLeft, rect.top, rect.right, rect.bottom))
                                }
                                CropTouchHandle.RIGHT_EDGE -> {
                                    val newRight = (rect.right + ndx).coerceIn(rect.left + minW, 1f)
                                    currentOnCropRectChanged(Rect(rect.left, rect.top, newRight, rect.bottom))
                                }
                                CropTouchHandle.NONE -> {}
                            }
                        },
                        onDragEnd = { currentHandle = CropTouchHandle.NONE },
                        onDragCancel = { currentHandle = CropTouchHandle.NONE }
                    )
                }
        ) {
            val pL = currentCropRect.left * viewW
            val pT = currentCropRect.top * viewH
            val pR = currentCropRect.right * viewW
            val pB = currentCropRect.bottom * viewH
            val boxW = pR - pL
            val boxH = pB - pT

            val isDragging = currentHandle != CropTouchHandle.NONE

            // 1. 裁切框外部陰影遮罩 (四塊半透明黑底)
            val dimColor = Color(0x99000000)
            drawRect(dimColor, Offset(0f, 0f), Size(viewW, pT))
            drawRect(dimColor, Offset(0f, pB), Size(viewW, viewH - pB))
            drawRect(dimColor, Offset(0f, pT), Size(pL, boxH))
            drawRect(dimColor, Offset(pR, pT), Size(viewW - pR, boxH))

            // 2. 主手牌裁切外框 (拖曳中變為高亮金色，靜態為鮮明亮綠色)
            val frameColor = if (isDragging) Color(0xFFFFD54F) else Color(0xFF4CAF50)
            drawRect(
                color = frameColor,
                topLeft = Offset(pL, pT),
                size = Size(boxW, boxH),
                style = Stroke(width = if (isDragging) borderWidthPx * 1.5f else borderWidthPx)
            )

            // 3. 繪製框內 tileCount (16 或 17) 條垂直等距切片分割線
            if (tileCount > 1 && boxW > 0f) {
                val sliceW = boxW / tileCount
                val dividerColor = Color(0xFF81C784).copy(alpha = 0.85f)

                for (i in 1 until tileCount) {
                    val lineX = pL + i * sliceW
                    drawLine(
                        color = dividerColor,
                        start = Offset(lineX, pT),
                        end = Offset(lineX, pB),
                        strokeWidth = 1.2f
                    )
                }

                // 4. 繪製單牌序號標籤 (1, 2 ... 16/17)
                for (i in 0 until tileCount) {
                    val centerX = pL + i * sliceW + (sliceW / 2f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${i + 1}",
                        centerX,
                        pT + textSizePx + 4f,
                        textPaint
                    )
                }
            }

            // 5. 提示標籤：中心微縮移動提示
            if (!isDragging && boxH > 40f) {
                drawContext.canvas.nativeCanvas.drawText(
                    "✣ 按住移動框框",
                    pL + boxW / 2f,
                    pB - 10f,
                    centerHintPaint
                )
            }

            // 6. 繪製四角抓取把手 (大黃色圓點 + 白色邊框，手掌極易點按縮放)
            val handleFill = if (isDragging) Color(0xFFFF9800) else Color(0xFFFFD54F)
            val handleStroke = Color.White

            listOf(
                Offset(pL, pT),
                Offset(pR, pT),
                Offset(pL, pB),
                Offset(pR, pB)
            ).forEach { pt ->
                drawCircle(color = handleFill, radius = cornerVisualRadiusPx, center = pt)
                drawCircle(color = handleStroke, radius = cornerVisualRadiusPx, center = pt, style = Stroke(2.5f))
            }

            // 7. 繪製邊緣微調拉條指示
            val edgeHandleColor = Color(0xFF81C784)
            val barW = with(density) { 28.dp.toPx() }
            val barH = with(density) { 5.dp.toPx() }

            // 頂部中間拉條
            drawRoundRect(
                color = edgeHandleColor,
                topLeft = Offset((pL + pR) / 2f - barW / 2f, pT - barH / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barH, barH)
            )
            // 底部中間拉條
            drawRoundRect(
                color = edgeHandleColor,
                topLeft = Offset((pL + pR) / 2f - barW / 2f, pB - barH / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barH, barH)
            )
        }
    }
}
