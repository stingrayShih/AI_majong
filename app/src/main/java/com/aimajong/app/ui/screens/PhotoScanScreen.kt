package com.aimajong.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.aimajong.app.ui.components.MahjongTileView
import com.aimajong.app.ui.components.TilePickerDialog
import com.aimajong.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScanScreen(
    settingsRepo: SettingsRepository,
    apiClient: VllmApiClient,
    onBack: () -> Unit,
    onPhotoRecognized: (List<MahjongTile>, PlayerSeat) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var selectedDealer by remember { mutableStateOf(PlayerSeat.SELF) }
    val targetCount = if (selectedDealer == PlayerSeat.SELF) 17 else 16

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
    var processingStatusText by remember { mutableStateOf("vLLM d3-flash 視覺辨識分析中...") }

    // 辨識結果手牌列表 (進入校對微調模式)
    var recognizedTiles by remember { mutableStateOf<List<MahjongTile>?>(null) }
    var showTilePickerForAdd by remember { mutableStateOf(false) }
    var editingTileIndex by remember { mutableStateOf<Int?>(null) }

    // 相機 ImageCapture 實例
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
                    analyzeBitmap(bitmap)
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
                        if (recognizedTiles == null) "拍照辨識起手手牌" else "校對與微調手牌",
                        color = TextLight,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (recognizedTiles != null) {
                            recognizedTiles = null // 返回相機重拍
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextLight)
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
                // ==================== 1. 相機拍攝 / 圖片選取 畫面 ====================
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 上方莊家選擇
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MahjongGreenDark),
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👑 開局莊家：", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (selectedDealer == PlayerSeat.SELF) "起手 17 張 (我開門)" else "起手 16 張",
                                    color = TextLight,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    PlayerSeat.SELF to "我 (莊)",
                                    PlayerSeat.XIA_JIA to "下家",
                                    PlayerSeat.DUI_JIA to "對家",
                                    PlayerSeat.SHANG_JIA to "上家"
                                ).forEach { (seat, label) ->
                                    val isSelected = selectedDealer == seat
                                    Button(
                                        onClick = { selectedDealer = seat },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) AccentGold else Color(0xFF1B4D2E),
                                            contentColor = if (isSelected) Color.Black else TextLight
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
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
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        try {
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.surfaceProvider = previewView.surfaceProvider
                                            }
                                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                imageCapture
                                            )
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
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("相簿選取", fontSize = 13.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

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
                                                    analyzeBitmap(rotatedBitmap)
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Toast.makeText(context, "拍照失敗: ${exception.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.3f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("拍攝辨識", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // 離線/快速測試按鈕
                                OutlinedButton(
                                    onClick = {
                                        recognizedTiles = generateSampleTiles(selectedDealer)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("快速填入", fontSize = 13.sp)
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
                                    Text("👑 莊家：${selectedDealer.title}", color = AccentGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                                onPhotoRecognized(currentTiles, selectedDealer)
                            },
                            enabled = isCountValid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGold,
                                disabledContainerColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "確認手牌並開始對局 (${selectedDealer.title}當莊)",
                                color = if (isCountValid) Color.Black else Color.LightGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { recognizedTiles = null },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
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
            allowFlower = false,
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
            allowFlower = false,
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