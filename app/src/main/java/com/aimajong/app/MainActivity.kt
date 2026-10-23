package com.aimajong.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.aimajong.app.model.PlayerSeat
import com.aimajong.app.ui.screens.*
import com.aimajong.app.ui.theme.AIMajongTheme
import com.aimajong.app.ui.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {

    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIMajongTheme {
                // 使用 rememberSaveable 確保旋轉螢幕、切換 App 返回後不丟失畫面狀態
                var currentScreen by rememberSaveable { mutableStateOf("home") }
                val gameState by gameViewModel.gameState.collectAsState()

                when (currentScreen) {
                    "home" -> HomeScreen(
                        gameState = gameState,
                        onResumeGame = { currentScreen = "game" },
                        onStartPhotoScan = { currentScreen = "photo_scan" },
                        onStartManualSetup = { currentScreen = "manual_setup" },
                        onViewHistory = { currentScreen = "history" },
                        onOpenSettings = { currentScreen = "settings" }
                    )

                    "manual_setup" -> ManualSetupScreen(
                        onBack = {
                            currentScreen = if (gameState.handTiles.isNotEmpty() && !gameState.isGameOver) "game" else "home"
                        },
                        onStartGameWithHand = { tiles, dealerSeat ->
                            gameViewModel.startNewGame(tiles, dealerSeat)
                            currentScreen = "game"
                        }
                    )

                    "photo_scan" -> PhotoScanScreen(
                        settingsRepo = gameViewModel.settingsRepo,
                        apiClient = gameViewModel.vllmApiClient,
                        onBack = {
                            currentScreen = if (gameState.handTiles.isNotEmpty() && !gameState.isGameOver) "game" else "home"
                        },
                        onPhotoRecognized = { tiles, dealerSeat ->
                            gameViewModel.startNewGame(tiles, dealerSeat)
                            currentScreen = "game"
                        }
                    )

                    "game" -> GameScreen(
                        viewModel = gameViewModel,
                        onBackToHome = { currentScreen = "home" },
                        onOpenSettings = { currentScreen = "settings" }
                    )

                    "history" -> {
                        HistoryScreen(
                            records = gameState.history,
                            onBack = {
                                currentScreen = if (gameState.handTiles.isNotEmpty() && !gameState.isGameOver) "game" else "home"
                            }
                        )
                    }

                    "settings" -> {
                        SettingsScreen(
                            settingsRepo = gameViewModel.settingsRepo,
                            apiClient = gameViewModel.vllmApiClient,
                            onBack = {
                                currentScreen = if (gameState.handTiles.isNotEmpty() && !gameState.isGameOver) "game" else "home"
                            }
                        )
                    }
                }
            }
        }
    }
}
