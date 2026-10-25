package com.aimajong.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Size
import android.view.MotionEvent
import android.widget.Toast
import java.util.concurrent.TimeUnit
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aimajong.app.data.SettingsRepository
import com.aimajong.app.model.*
import com.aimajong.app.network.VllmApiClient
import com.aimajong.app.ui.components.DebugInspectorDialog
import com.aimajong.app.ui.components.DraggableCropOverlay
import com.aimajong.app.ui.components.MahjongTileView
import com.aimajong.app.ui.components.TilePickerDialog
import com.aimajong.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScanScreen(
    dealerSeat: PlayerSeat = PlayerSeat.SELF,
    settingsRepo: SettingsRepository,
    apiClient: VllmApiClient,
    onBack: () -> Unit,
    onPhotoRecognized: (List<MahjongTile>, PlayerSeat) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val isSelfDealer = (dealerSeat == PlayerSeat.SELF)
    val targetCount = if (isSelfDealer) 17 else 16

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "未取得相機權限，可改由相簿選取照片", Toast.LENGTH_SHORT).show()
        }
    }

    // 狀態變數
    var isProcessing by remember { mutableStateOf(false) }
    var processingStatusText by remember { mutableStateOf("vLLM ${settingsRepo.modelName} (27B) 視覺辨識分析中...") }

    // 拍下或選取的原始待裁切圖片
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // 手指拖動之手牌裁切框 (歸一化座標)
    var cropRectNorm by remember { mutableStateOf(Rect(0.04f, 0.22f, 0.96f, 0.48f)) }

    // 辨識結果手牌列表 (進入校對微調模式)
    var recognizedTiles by remember { mutableStateOf<List<MahjongTile>?>(null) }
    var showTilePickerForAdd by remember { mutableStateOf(false) }
    var editingTileIndex by remember { mutableStateOf<Int?>(null) }
    var showDebugDialog by remember { mutableStateOf(false) }

    // 系統返回鍵支援：與左上角返回箭頭行為 100% 同步，避免直接退出程式
    BackHandler {
        when {
            recognizedTiles != null -> recognizedTiles = null
            capturedBitmap != null -> capturedBitmap = null
            else -> onBack()
        }
    }

    // 高畫質相機 ImageCapture 實例：鎖定超高解析度與 3A (對焦/曝光) 收斂以獲得清晰牌面
    val imageCapture = remember {
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(3840, 2160),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                )
            )
            .build()

        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .build()
    }

    // 啟動圖片分析：符合用戶要求，先檢驗張數，若正確再依序由左至右單張提問
    fun analyzeBitmap(bitmap: Bitmap) {
        isProcessing = true
        coroutineScope.launch {
            processingStatusText = "第 1 階段：向模型檢驗手牌張數 (${targetCount}張)..."
            
            val countResult = apiClient.verifyTileCount(
                baseUrl = settingsRepo.serverBaseUrl,
                apiKey = settingsRepo.apiKey,
                modelName = settingsRepo.modelName,
                bitmap = bitmap,
                expectedCount = targetCount
            )

            if (countResult.isFailure && !settingsRepo.useMockFallback) {
                isProcessing = false
                val err = countResult.exceptionOrNull()?.message ?: "手牌張數不符合標準"
                Toast.makeText(context, "❌ $err", Toast.LENGTH_LONG).show()
                return@launch
            }

            // 張數檢驗通過，進入第 2 階段：由左至右逐張向模型提問
            val recognizedList = mutableListOf<MahjongTile>()
            for (pos in 1..targetCount) {
                processingStatusText = "第 2 階段：正在辨識第 $pos / $targetCount 張牌 (由左至右)..."
                val tileResult = apiClient.recognizeSingleTile(
                    baseUrl = settingsRepo.serverBaseUrl,
                    apiKey = settingsRepo.apiKey,
                    modelName = settingsRepo.modelName,
                    bitmap = bitmap,
                    position = pos,
                    total = targetCount
                )
                val tile = tileResult.getOrNull() ?: MahjongTile.findById("wan_1")!!
                recognizedList.add(tile)
            }

            isProcessing = false
            Toast.makeText(context, "✅ 逐張辨識完成！共 $targetCount 張，請在下方校對微調", Toast.LENGTH_SHORT).show()
            recognizedTiles = recognizedList
        }
    }

    // 相簿選圖 Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    capturedBitmap = bitmap
                } else {
                    Toast.makeText(context, "無法解析所選圖片", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "讀取圖片失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            recognizedTiles != null -> "校對與微調手牌"
                            capturedBitmap != null -> "👆 用手指拖動調整綠框"
                            else -> "拍照辨識起手手牌"
                        },
                        color = TextLight,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            recognizedTiles != null -> recognizedTiles = null
                            capturedBitmap != null -> capturedBitmap = null
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextLight)
                    }
                },
                actions = {
                    if (settingsRepo.isDebugMode) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentGold.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold.copy(alpha = 0.8f)),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable { showDebugDialog = true }
                        ) {
                            Text(
                                text = "🪲 報文",
                                color = AccentGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
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
        ) {
            if (recognizedTiles == null) {
                if (capturedBitmap != null) {
                    // ==================== 手指拖曳手牌裁切對齊畫面 ====================
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 頂部提示條
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MahjongGreenDark),
                            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("👑 莊家【${dealerSeat.title}】・手指拖動綠框對齊手牌", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("框內任意滑動可平移位置，四角黃色圓球可縮放", color = TextMuted, fontSize = 11.sp)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1B4D2E)
                                ) {
                                    Text(
                                        text = "共 $targetCount 張牌",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // 中間相片顯示與手指拖曳框 (精準 1:1 貼合圖片實際像素範圍，杜絕黑邊偏移)
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            val containerW = constraints.maxWidth.toFloat()
                            val containerH = constraints.maxHeight.toFloat()
                            val bmp = capturedBitmap!!
                            val bmpW = bmp.width.toFloat()
                            val bmpH = bmp.height.toFloat()

                            val scale = if (bmpW > 0f && bmpH > 0f) minOf(containerW / bmpW, containerH / bmpH) else 1f
                            val displayedWidthDp = with(LocalDensity.current) { (bmpW * scale).toDp() }
                            val displayedHeightDp = with(LocalDensity.current) { (bmpH * scale).toDp() }

                            Box(
                                modifier = Modifier
                                    .size(displayedWidthDp, displayedHeightDp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "待裁切手牌照片",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )

                                DraggableCropOverlay(
                                    cropRectNorm = cropRectNorm,
                                    onCropRectChanged = { cropRectNorm = it },
                                    tileCount = targetCount,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (isProcessing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.8f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        CircularProgressIndicator(color = AccentGold, strokeWidth = 4.dp)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            processingStatusText,
                                            color = TextLight,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // 底部控制區
                        Surface(
                            color = MahjongGreenDark,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 微調箭頭按鈕列 (輔助手動精準對齊)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("微調：", color = AccentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                    // 左移
                                    OutlinedButton(
                                        onClick = {
                                            val step = 0.02f
                                            val w = cropRectNorm.width
                                            val nL = (cropRectNorm.left - step).coerceAtLeast(0f)
                                            cropRectNorm = Rect(nL, cropRectNorm.top, nL + w, cropRectNorm.bottom)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF388E3C)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("⬅ 左", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }

                                    // 右移
                                    OutlinedButton(
                                        onClick = {
                                            val step = 0.02f
                                            val w = cropRectNorm.width
                                            val nL = (cropRectNorm.left + step).coerceAtMost(1f - w)
                                            cropRectNorm = Rect(nL, cropRectNorm.top, nL + w, cropRectNorm.bottom)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF388E3C)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("右 ➡", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }

                                    // 上移
                                    OutlinedButton(
                                        onClick = {
                                            val step = 0.02f
                                            val h = cropRectNorm.height
                                            val nT = (cropRectNorm.top - step).coerceAtLeast(0f)
                                            cropRectNorm = Rect(cropRectNorm.left, nT, cropRectNorm.right, nT + h)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF388E3C)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("⬆ 上", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }

                                    // 下移
                                    OutlinedButton(
                                        onClick = {
                                            val step = 0.02f
                                            val h = cropRectNorm.height
                                            val nT = (cropRectNorm.top + step).coerceAtMost(1f - h)
                                            cropRectNorm = Rect(cropRectNorm.left, nT, cropRectNorm.right, nT + h)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF388E3C)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("下 ⬇", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }

                                    // 變寬
                                    OutlinedButton(
                                        onClick = {
                                            val nL = (cropRectNorm.left - 0.015f).coerceAtLeast(0f)
                                            val nR = (cropRectNorm.right + 0.015f).coerceAtMost(1f)
                                            cropRectNorm = Rect(nL, cropRectNorm.top, nR, cropRectNorm.bottom)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF388E3C)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("↔ 寬", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            cropRectNorm = Rect(0.04f, 0.22f, 0.96f, 0.48f)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("置中還原", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { capturedBitmap = null },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("重新拍照", fontSize = 12.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (settingsRepo.apiKey.isBlank()) {
                                            Toast.makeText(context, "⚠️ 尚未設定 API Key！請先至首頁「設定 (⚙️)」填入您的 API Key", Toast.LENGTH_LONG).show()
                                            return@Button
                                        }
                                        val bmp = capturedBitmap ?: return@Button
                                        isProcessing = true
                                        coroutineScope.launch {
                                            val bW = bmp.width
                                            val bH = bmp.height

                                            // 1. 根據手指拖曳的歸一化座標截取整排手牌
                                            val cropX = (cropRectNorm.left * bW).toInt().coerceIn(0, bW - 1)
                                            val cropY = (cropRectNorm.top * bH).toInt().coerceIn(0, bH - 1)
                                            val cropW = ((cropRectNorm.right - cropRectNorm.left) * bW).toInt().coerceIn(1, bW - cropX)
                                            val cropH = ((cropRectNorm.bottom - cropRectNorm.top) * bH).toInt().coerceIn(1, bH - cropY)

                                            val handRowBitmap = Bitmap.createBitmap(bmp, cropX, cropY, cropW, cropH)

                                            // 2. 切割為 targetCount (16 或 17) 個單張牌特寫
                                            val sliceW = cropW / targetCount
                                            val recognizedList = mutableListOf<MahjongTile>()

                                            for (pos in 1..targetCount) {
                                                processingStatusText = "正在以 ${settingsRepo.modelName} 辨識第 $pos / $targetCount 張牌 (單牌特寫)..."
                                                val sliceX = ((pos - 1) * sliceW).coerceIn(0, cropW - 1)
                                                val actualSliceW = if (pos == targetCount) (cropW - sliceX) else sliceW
                                                val singleTileBitmap = Bitmap.createBitmap(handRowBitmap, sliceX, 0, actualSliceW, cropH)

                                                val tileResult = apiClient.recognizeSingleTile(
                                                    baseUrl = settingsRepo.serverBaseUrl,
                                                    apiKey = settingsRepo.apiKey,
                                                    modelName = settingsRepo.modelName,
                                                    bitmap = singleTileBitmap,
                                                    position = pos,
                                                    total = targetCount
                                                )
                                                val tile = tileResult.getOrNull() ?: MahjongTile.findById("wan_1")!!
                                                recognizedList.add(tile)
                                            }

                                            isProcessing = false
                                            recognizedTiles = recognizedList
                                            capturedBitmap = null
                                            Toast.makeText(context, "✅ 單牌特寫辨識完成！共 $targetCount 張，請校對", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("✂️ 確認裁切，開始單牌 AI 辨識", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                } else {
                    // ==================== 1. 相機拍攝 / 圖片選取 畫面 ====================
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 上方已確認莊家資訊
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MahjongGreenDark),
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                    text = "$targetCount 張牌",
                                    color = AccentGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // 中間相機預覽取景框
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPermission) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FIT_CENTER
                                    }
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        try {
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.surfaceProvider = previewView.surfaceProvider
                                            }
                                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                            cameraProvider.unbindAll()
                                            val camera = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                imageCapture
                                            )

                                            // 支援點擊預覽畫面手動對焦 (Tap-to-Focus)
                                            previewView.setOnTouchListener { view, motionEvent ->
                                                if (motionEvent.action == MotionEvent.ACTION_UP) {
                                                    val factory = previewView.meteringPointFactory
                                                    val point = factory.createPoint(motionEvent.x, motionEvent.y)
                                                    val action = FocusMeteringAction.Builder(
                                                        point,
                                                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                                    ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                                    camera.cameraControl.startFocusAndMetering(action)
                                                    view.performClick()
                                                }
                                                true
                                            }
                                        } catch (e: Exception) {
                                            // 模擬器無相機或初始化錯誤
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AccentGold, modifier = Modifier.size(54.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("需要相機權限以拍照辨識麻將牌", color = TextLight, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = Color.Black)
                                ) {
                                    Text("允許相機權限", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 橫向引導框
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .border(2.dp, AccentGold, RoundedCornerShape(12.dp))
                                    .background(AccentGold.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "🀄 請將一整排手牌平整橫向排入此框內",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // AI 辨識中遮罩
                        if (isProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    CircularProgressIndicator(color = AccentGold, strokeWidth = 4.dp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        processingStatusText,
                                        color = TextLight,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // 底部控制區 (相簿 / 快門 / 測試牌)
                    Surface(
                        color = MahjongGreenDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 從相簿選取
                                OutlinedButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("相簿選取", fontSize = 13.sp)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // 快門按鈕
                                Button(
                                    onClick = {
                                        if (!hasCameraPermission) {
                                            permissionLauncher.launch(Manifest.permission.CAMERA)
                                            return@Button
                                        }
                                        imageCapture.takePicture(
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    val bitmap = image.toBitmap()
                                                    val rotationDegrees = image.imageInfo.rotationDegrees
                                                    val rotatedBitmap = if (rotationDegrees != 0) {
                                                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                                    } else {
                                                        bitmap
                                                    }
                                                    image.close()
                                                    capturedBitmap = rotatedBitmap
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Toast.makeText(context, "拍照失敗: ${exception.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.3f).heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("拍攝辨識", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // 離線/快速測試按鈕
                                OutlinedButton(
                                    onClick = {
                                        recognizedTiles = generateSampleTiles(dealerSeat)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("快速填入", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ==================== 2. 辨識結果校對與微調畫面 ====================
                val currentTiles = recognizedTiles ?: emptyList()
                val currentCount = currentTiles.size
                val isCountValid = currentCount == targetCount

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 提示卡片
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MahjongGreenDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("👑 莊家：${dealerSeat.title}", color = AccentGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Surface(
                                        color = if (isCountValid) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "$currentCount / $targetCount 張",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    if (isCountValid) "手牌張數正確！點擊單張牌可替換，或點擊下方按鈕確認開局。"
                                    else "張數不符合標準 (${targetCount}張)。點擊牌上的 ✕ 刪除多餘牌，或點擊下方「補充牌」補足。",
                                    color = if (isCountValid) AccentGold else Color(0xFFFFB74D),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("🀄 已識別手牌 (點擊更換牌面，或按右上 ✕ 刪除)：", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 牌面網格/列表
                        FlowRowLayout(
                            tiles = currentTiles,
                            onTileClick = { index ->
                                editingTileIndex = index
                            },
                            onTileRemove = { index ->
                                val list = currentTiles.toMutableList()
                                list.removeAt(index)
                                recognizedTiles = list
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 新增牌按鈕
                        OutlinedButton(
                            onClick = { showTilePickerForAdd = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AccentGold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("➕ 手動補充牌 (${currentCount}/$targetCount)", fontWeight = FontWeight.Bold)
                        }
                    }

                    // 底部確認與重拍按鈕
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onPhotoRecognized(currentTiles, dealerSeat)
                            },
                            enabled = isCountValid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGold,
                                disabledContainerColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "確認手牌並開始對局 (${dealerSeat.title}當莊)",
                                color = if (isCountValid) Color.Black else Color.LightGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        OutlinedButton(
                            onClick = { recognizedTiles = null },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TextLight)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🔄 重新拍攝辨識", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // 替換單張牌 Dialog
    editingTileIndex?.let { index ->
        val currentTile = recognizedTiles?.getOrNull(index)
        TilePickerDialog(
            title = "替換牌面 (目前: ${currentTile?.name ?: ""})",
            initialSuit = TileSuit.HONOR,
            getRemainingCount = { tile ->
                val used = recognizedTiles?.count { it.id == tile.id } ?: 0
                MahjongTile.getMaxCount(tile) - used
            },
            onDismissRequest = { editingTileIndex = null },
            onTileSelected = { newTile ->
                recognizedTiles?.let { list ->
                    val mutable = list.toMutableList()
                    mutable[index] = newTile
                    recognizedTiles = mutable
                }
                editingTileIndex = null
            }
        )
    }

    // 手動補充新牌 Dialog
    if (showTilePickerForAdd) {
        TilePickerDialog(
            title = "選擇要補充的手牌",
            initialSuit = TileSuit.HONOR,
            getRemainingCount = { tile ->
                val used = recognizedTiles?.count { it.id == tile.id } ?: 0
                MahjongTile.getMaxCount(tile) - used
            },
            onDismissRequest = { showTilePickerForAdd = false },
            onTileSelected = { newTile ->
                recognizedTiles?.let { list ->
                    recognizedTiles = list + newTile
                }
                showTilePickerForAdd = false
            }
        )
    }

    if (showDebugDialog) {
        val latestLog by apiClient.latestDebugLog.collectAsState()
        val logHistory by apiClient.debugLogHistory.collectAsState()
        DebugInspectorDialog(
            initialLog = latestLog,
            logHistory = logHistory,
            onDismiss = { showDebugDialog = false }
        )
    }
}

/**
 * 牌面微調流動式排版
 */
@Composable
private fun FlowRowLayout(
    tiles: List<MahjongTile>,
    onTileClick: (Int) -> Unit,
    onTileRemove: (Int) -> Unit
) {
    // 每行排 6 ~ 8 張
    val chunked = tiles.chunked(6)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chunked.forEachIndexed { chunkIndex, rowTiles ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowTiles.forEachIndexed { itemIndex, tile ->
                    val overallIndex = chunkIndex * 6 + itemIndex
                    Box(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTileClick(overallIndex) }
                        ) {
                            MahjongTileView(tile = tile, isSelected = false)
                        }

                        // 右上方刪除鈕
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(20.dp)
                                .background(Color(0xFFD32F2F), CircleShape)
                                .clickable { onTileRemove(overallIndex) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "刪除",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                // 補齊剩餘空格維持尺寸一致
                if (rowTiles.size < 6) {
                    repeat(6 - rowTiles.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 產生標準測試手牌
 */
private fun generateSampleTiles(dealerSeat: PlayerSeat): List<MahjongTile> {
    val sampleIds = if (dealerSeat == PlayerSeat.SELF) {
        listOf(
            "wan_1", "wan_2", "wan_3", "wan_4", "wan_5", "wan_6",
            "tong_1", "tong_2", "tong_3",
            "tiao_4", "tiao_5", "tiao_6",
            "east", "south", "zhong", "fa", "bai"
        )
    } else {
        listOf(
            "wan_1", "wan_2", "wan_3", "wan_4", "wan_5", "wan_6",
            "tong_1", "tong_2", "tong_3",
            "tiao_4", "tiao_5", "tiao_6",
            "east", "south", "zhong", "fa"
        )
    }
    return sampleIds.mapNotNull { MahjongTile.findById(it) }
}