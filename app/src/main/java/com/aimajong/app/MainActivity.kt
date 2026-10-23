package com.aimajong.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
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
                var currentScreen by remember { mutableStateOf("home") }

                when (currentScreen) {
                    "home" -> HomeScreen(
                        onStartPhotoScan = { currentScreen = "photo_scan" },
                        onStartManualSetup = { currentScreen = "manual_setup" },
                        onViewHistory = { currentScreen = "history" }
                    )

                    "manual_setup" -> ManualSetupScreen(
                        onBack = { currentScreen = "home" },
                        onStartGameWithHand = { tiles, dealerSeat ->
                            gameViewModel.startNewGame(tiles, dealerSeat)
                            currentScreen = "game"
                        }
                    )

                    "photo_scan" -> PhotoScanScreen(
                        onBack = { currentScreen = "home" },
                        onPhotoRecognized = { tiles, dealerSeat ->
                            gameViewModel.startNewGame(tiles, dealerSeat)
                            currentScreen = "game"
                        }
                    )

                    "game" -> GameScreen(
                        viewModel = gameViewModel,
                        onBackToHome = { currentScreen = "home" }
                    )

                    "history" -> {
                        val state by gameViewModel.gameState.collectAsState()
                        HistoryScreen(
                            records = state.history,
                            onBack = { currentScreen = "home" }
                        )
                    }
                }
            }
        }
    }
}
