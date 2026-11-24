package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gamepad.model.ControllerLayout
import com.example.gamepad.ui.GamepadViewModel
import com.example.gamepad.ui.screens.ConnectionHubScreen
import com.example.gamepad.ui.screens.GamepadScreen
import com.example.gamepad.ui.screens.TvReceiverScreen
import com.example.ui.theme.PulsePadTheme

object AppRoutes {
    const val ROUTE_CONNECTION_HUB = "connection_hub"
    const val ROUTE_GAMEPAD = "gamepad"
    const val ROUTE_TV_RECEIVER = "tv_receiver"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            PulsePadTheme {
                val vm: GamepadViewModel = viewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.ROUTE_CONNECTION_HUB
                ) {
                    composable(AppRoutes.ROUTE_CONNECTION_HUB) {
                        ConnectionHubScreen(
                            state = uiState,
                            onScanClick = vm::refreshNetworkAndScan,
                            onStopScanClick = vm::stopScan,
                            onConnectTv = { tv -> vm.connectToTv(tv) },
                            onManualConnect = vm::connectManualIp,
                            onDisconnect = vm::disconnectGamepad,
                            onOpenGamepad = {
                                navController.navigate(AppRoutes.ROUTE_GAMEPAD) {
                                    launchSingleTop = true
                                }
                            },
                            onStartTvReceiver = {
                                vm.startTvReceiver()
                                navController.navigate(AppRoutes.ROUTE_TV_RECEIVER) {
                                    launchSingleTop = true
                                }
                            },
                            onStartLoopbackTest = {
                                vm.startLoopbackTest()
                                navController.navigate(AppRoutes.ROUTE_GAMEPAD) {
                                    launchSingleTop = true
                                }
                            },
                            onRemoveSavedTv = vm::removeSavedTv,
                            onLanguageChange = vm::setLanguage,
                            onPollingRateChange = vm::setPollingRate,
                            onHapticsChange = vm::setHapticsEnabled,
                            onLayoutChange = vm::setLayout,
                            onSymbolStyleChange = vm::setSymbolStyle,
                            onDeadzoneChange = vm::setDeadzone,
                            onAutoLandscapeChange = vm::setAutoLandscape
                        )
                    }

                    composable(AppRoutes.ROUTE_GAMEPAD) {
                        GamepadScreen(
                            state = uiState,
                            onBack = {
                                if (uiState.isLoopbackSession) {
                                    vm.disconnectGamepad()
                                }
                                navController.popBackStack()
                            },
                            onButtonsMaskChange = vm::updateButtonsMask,
                            onButtonPress = vm::setButtonPressed,
                            onLeftStickChange = vm::updateLeftStick,
                            onRightStickChange = vm::updateRightStick,
                            onTriggersChange = vm::updateTriggers,
                            onLayoutCycle = {
                                val next = when (uiState.settings.layout) {
                                    ControllerLayout.STANDARD -> ControllerLayout.ARCADE
                                    ControllerLayout.ARCADE -> ControllerLayout.RACING_GYRO
                                    ControllerLayout.RACING_GYRO -> ControllerLayout.STANDARD
                                }
                                vm.setLayout(next)
                            },
                            onToggleGyro = vm::setGyroEnabled
                        )
                    }

                    composable(AppRoutes.ROUTE_TV_RECEIVER) {
                        TvReceiverScreen(
                            state = uiState,
                            onStopAndBack = {
                                vm.stopTvReceiver()
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
