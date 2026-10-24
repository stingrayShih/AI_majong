package com.aimajong.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
 * 支援：
 * 1. 手指按住框內任意位置 ➔ 平移拖動框體位置
 * 2. 手指按住四角圓形把手 ➔ 同時縮放長寬
 * 3. 手指按住四條邊線把手 ➔ 單邊縮放長寬
 * 4. 框內即時等距分割為 tileCount (16 或 17) 等份，並標記 #1 ~ #16 序號
 */
@Composable
fun DraggableCropOverlay(
    cropRectNorm: Rect, // 歸一化座標 0.0f .. 1.0f
    onCropRectChanged: (Rect) -> Unit,
    tileCount: Int = 16,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleHitRadiusPx = with(density) { 36.dp.toPx() }
    val handleVisualRadiusPx = with(density) { 9.dp.toPx() }
    val borderWidthPx = with(density) { 2.5.dp.toPx() }
    val textSizePx = with(density) { 11.dp.toPx() }

    var currentHandle by remember { mutableStateOf(CropTouchHandle.NONE) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()

        if (viewW <= 0f || viewH <= 0f) return@BoxWithConstraints

        val pixelLeft = cropRectNorm.left * viewW
        val pixelTop = cropRectNorm.top * viewH
        val pixelRight = cropRectNorm.right * viewW
        val pixelBottom = cropRectNorm.bottom * viewH

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

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cropRectNorm, viewW, viewH) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val x = startOffset.x
                            val y = startOffset.y

                            // 1. 判定四角把手 (優先權最高)
                            currentHandle = when {
                                hypot(x - pixelLeft, y - pixelTop) <= handleHitRadiusPx -> CropTouchHandle.TOP_LEFT
                                hypot(x - pixelRight, y - pixelTop) <= handleHitRadiusPx -> CropTouchHandle.TOP_RIGHT
                                hypot(x - pixelLeft, y - pixelBottom) <= handleHitRadiusPx -> CropTouchHandle.BOTTOM_LEFT
                                hypot(x - pixelRight, y - pixelBottom) <= handleHitRadiusPx -> CropTouchHandle.BOTTOM_RIGHT
                                // 2. 判定四條邊緣
                                abs(y - pixelTop) <= handleHitRadiusPx && x in (pixelLeft - 10f)..(pixelRight + 10f) -> CropTouchHandle.TOP_EDGE
                                abs(y - pixelBottom) <= handleHitRadiusPx && x in (pixelLeft - 10f)..(pixelRight + 10f) -> CropTouchHandle.BOTTOM_EDGE
                                abs(x - pixelLeft) <= handleHitRadiusPx && y in (pixelTop - 10f)..(pixelBottom + 10f) -> CropTouchHandle.LEFT_EDGE
                                abs(x - pixelRight) <= handleHitRadiusPx && y in (pixelTop - 10f)..(pixelBottom + 10f) -> CropTouchHandle.RIGHT_EDGE
                                // 3. 判定框內平移
                                x in pixelLeft..pixelRight && y in pixelTop..pixelBottom -> CropTouchHandle.BODY
                                else -> CropTouchHandle.NONE
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (currentHandle == CropTouchHandle.NONE) return@detectDragGestures

                            val ndx = dragAmount.x / viewW
                            val ndy = dragAmount.y / viewH

                            val minW = 0.20f // 最小寬度 20%
                            val minH = 0.05f // 最小高度 5%

                            when (currentHandle) {
                                CropTouchHandle.BODY -> {
                                    val w = cropRectNorm.width
                                    val h = cropRectNorm.height
                                    val newLeft = (cropRectNorm.left + ndx).coerceIn(0f, 1f - w)
                                    val newTop = (cropRectNorm.top + ndy).coerceIn(0f, 1f - h)
                                    onCropRectChanged(Rect(newLeft, newTop, newLeft + w, newTop + h))
                                }
                                CropTouchHandle.TOP_LEFT -> {
                                    val newLeft = (cropRectNorm.left + ndx).coerceIn(0f, cropRectNorm.right - minW)
                                    val newTop = (cropRectNorm.top + ndy).coerceIn(0f, cropRectNorm.bottom - minH)
                                    onCropRectChanged(Rect(newLeft, newTop, cropRectNorm.right, cropRectNorm.bottom))
                                }
                                CropTouchHandle.TOP_RIGHT -> {
                                    val newRight = (cropRectNorm.right + ndx).coerceIn(cropRectNorm.left + minW, 1f)
                                    val newTop = (cropRectNorm.top + ndy).coerceIn(0f, cropRectNorm.bottom - minH)
                                    onCropRectChanged(Rect(cropRectNorm.left, newTop, newRight, cropRectNorm.bottom))
                                }
                                CropTouchHandle.BOTTOM_LEFT -> {
                                    val newLeft = (cropRectNorm.left + ndx).coerceIn(0f, cropRectNorm.right - minW)
                                    val newBottom = (cropRectNorm.bottom + ndy).coerceIn(cropRectNorm.top + minH, 1f)
                                    onCropRectChanged(Rect(newLeft, cropRectNorm.top, cropRectNorm.right, newBottom))
                                }
                                CropTouchHandle.BOTTOM_RIGHT -> {
                                    val newRight = (cropRectNorm.right + ndx).coerceIn(cropRectNorm.left + minW, 1f)
                                    val newBottom = (cropRectNorm.bottom + ndy).coerceIn(cropRectNorm.top + minH, 1f)
                                    onCropRectChanged(Rect(cropRectNorm.left, cropRectNorm.top, newRight, newBottom))
                                }
                                CropTouchHandle.TOP_EDGE -> {
                                    val newTop = (cropRectNorm.top + ndy).coerceIn(0f, cropRectNorm.bottom - minH)
                                    onCropRectChanged(Rect(cropRectNorm.left, newTop, cropRectNorm.right, cropRectNorm.bottom))
                                }
                                CropTouchHandle.BOTTOM_EDGE -> {
                                    val newBottom = (cropRectNorm.bottom + ndy).coerceIn(cropRectNorm.top + minH, 1f)
                                    onCropRectChanged(Rect(cropRectNorm.left, cropRectNorm.top, cropRectNorm.right, newBottom))
                                }
                                CropTouchHandle.LEFT_EDGE -> {
                                    val newLeft = (cropRectNorm.left + ndx).coerceIn(0f, cropRectNorm.right - minW)
                                    onCropRectChanged(Rect(newLeft, cropRectNorm.top, cropRectNorm.right, cropRectNorm.bottom))
                                }
                                CropTouchHandle.RIGHT_EDGE -> {
                                    val newRight = (cropRectNorm.right + ndx).coerceIn(cropRectNorm.left + minW, 1f)
                                    onCropRectChanged(Rect(cropRectNorm.left, cropRectNorm.top, newRight, cropRectNorm.bottom))
                                }
                                CropTouchHandle.NONE -> {}
                            }
                        },
                        onDragEnd = { currentHandle = CropTouchHandle.NONE },
                        onDragCancel = { currentHandle = CropTouchHandle.NONE }
                    )
                }
        ) {
            val pL = pixelLeft
            val pT = pixelTop
            val pR = pixelRight
            val pB = pixelBottom
            val boxW = pR - pL
            val boxH = pB - pT

            // 1. 裁切框外部陰影遮罩 (四塊半透明黑底)
            val dimColor = Color(0x99000000)
            drawRect(dimColor, Offset(0f, 0f), Size(viewW, pT))                      // 上
            drawRect(dimColor, Offset(0f, pB), Size(viewW, viewH - pB))              // 下
            drawRect(dimColor, Offset(0f, pT), Size(pL, boxH))                       // 左
            drawRect(dimColor, Offset(pR, pT), Size(viewW - pR, boxH))               // 右

            // 2. 主手牌裁切外框 (亮綠色 + 陰影效果)
            val frameColor = Color(0xFF4CAF50)
            drawRect(
                color = frameColor,
                topLeft = Offset(pL, pT),
                size = Size(boxW, boxH),
                style = Stroke(width = borderWidthPx)
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

                // 4. 繪製單牌序號標籤 (#1, #2 ... #16)
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

            // 5. 繪製四角抓取把手 (實心亮金色/白邊圓形)
            val handleFill = Color(0xFFFFD54F)
            val handleStroke = Color.White

            listOf(
                Offset(pL, pT),
                Offset(pR, pT),
                Offset(pL, pB),
                Offset(pR, pB)
            ).forEach { pt ->
                drawCircle(color = handleFill, radius = handleVisualRadiusPx, center = pt)
                drawCircle(color = handleStroke, radius = handleVisualRadiusPx, center = pt, style = Stroke(2f))
            }

            // 6. 繪製上下左右邊緣拉條把手 (小圓柱指示)
            val edgeHandleColor = Color(0xFF81C784)
            val barW = with(density) { 24.dp.toPx() }
            val barH = with(density) { 5.dp.toPx() }

            // 頂部中間拉條
            drawRoundRect(
                color = edgeHandleColor,
                topLeft = Offset((pL + pR) / 2f - barW / 2f, pT - barH / 2f),
                size = Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH, barH)
            )
            // 底部中間拉條
            drawRoundRect(
                color = edgeHandleColor,
                topLeft = Offset((pL + pR) / 2f - barW / 2f, pB - barH / 2f),
                size = Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH, barH)
            )
        }
    }
}
