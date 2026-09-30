package com.rollercoin.mobile

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rollercoin.mobile.service.RollerCoinBotService
import com.rollercoin.mobile.ui.components.ActiveGameOverlayBanner
import com.rollercoin.mobile.ui.components.FloatingGameBubble
import com.rollercoin.mobile.ui.screens.AccountScreen
import com.rollercoin.mobile.ui.screens.BotRunnerScreen
import com.rollercoin.mobile.ui.screens.DashboardScreen
import com.rollercoin.mobile.ui.screens.LoginScreen
import com.rollercoin.mobile.ui.theme.RcAmber
import com.rollercoin.mobile.ui.theme.RcDarkBackground
import com.rollercoin.mobile.ui.theme.RcSurface
import com.rollercoin.mobile.ui.theme.RcSurfaceBorder
import com.rollercoin.mobile.ui.theme.RcTextMuted
import com.rollercoin.mobile.ui.theme.RollerCoinTheme

enum class Screen(val label: String, val icon: ImageVector, val tag: String) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard, "nav_dashboard"),
    BOT("Auto-Bot", Icons.Default.SmartToy, "nav_bot"),
    ACCOUNT("Profil", Icons.Default.AccountCircle, "nav_account"),
}

class MainActivity : ComponentActivity() {
    private val viewModel: RollerCoinViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        // Inform background service and ViewModel that the app is in the foreground
        RollerCoinBotService.setAppForeground(this, true)
        viewModel.setAppForeground(true)
    }

    override fun onStop() {
        super.onStop()
        // Inform background service and ViewModel that the app is minimized/in background
        RollerCoinBotService.setAppForeground(this, false)
        viewModel.setAppForeground(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            RollerCoinTheme {
                val authState by viewModel.authUiState.collectAsStateWithLifecycle()
                val dashboardState by viewModel.dashboardUiState.collectAsStateWithLifecycle()
                val activeGameState by viewModel.activeGameUiState.collectAsStateWithLifecycle()
                val botState by viewModel.botUiState.collectAsStateWithLifecycle()
                val isFloatingBubbleEnabled by viewModel.isFloatingBubbleEnabled.collectAsStateWithLifecycle()
                val isBackgroundServiceEnabled by viewModel.isBackgroundServiceEnabled.collectAsStateWithLifecycle()

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* notification permission result handled */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

                // Separate Login Screen from Dashboard & Bot screens completely
                if (!authState.isLoggedIn) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(RcDarkBackground)
                            .windowInsetsPadding(WindowInsets.systemBars)
                    ) {
                        LoginScreen(
                            authState = authState,
                            onEmailChange = { viewModel.updateEmail(it) },
                            onUserAgentChange = { viewModel.updateUserAgent(it) },
                            onOtpChange = { viewModel.updateOtpCode(it) },
                            onManualTokenChange = { viewModel.updateManualToken(it) },
                            onManualRefreshTokenChange = { viewModel.updateManualRefreshToken(it) },
                            onCaptchaPointsChange = { viewModel.updateCaptchaPoints(it) },
                            onAddCaptchaPoint = { x, y, rx, ry -> viewModel.addCaptchaPoint(x, y, rx, ry) },
                            onRemoveLastCaptchaPoint = { viewModel.removeLastCaptchaPoint() },
                            onClearCaptchaPoints = { viewModel.clearCaptchaPoints() },
                            onAutoDetectCaptchaPoints = { viewModel.autoDetectCaptchaPoints() },
                            onPrepareCaptcha = { viewModel.prepareCaptcha() },
                            onValidateCaptcha = { viewModel.validateCaptcha() },
                            onRequestOtp = { viewModel.requestOtp() },
                            onValidateOtp = { viewModel.validateOtp() },
                            onDirectTokenLogin = { viewModel.directTokenLogin() }
                        )
                    }
                } else {
                    BackHandler(enabled = currentScreen != Screen.DASHBOARD) {
                        currentScreen = Screen.DASHBOARD
                    }

                    Scaffold(
                        bottomBar = {
                            Column {
                                // Active Game floating banner
                                AnimatedVisibility(
                                    visible = activeGameState.activeGame != null,
                                    enter = slideInVertically(initialOffsetY = { it }),
                                    exit = slideOutVertically(targetOffsetY = { it })
                                ) {
                                    ActiveGameOverlayBanner(activeState = activeGameState)
                                }

                                NavigationBar(
                                    containerColor = RcSurface,
                                    contentColor = RcAmber,
                                    tonalElevation = 8.dp,
                                    windowInsets = WindowInsets.navigationBars
                                ) {
                                    Screen.values().forEach { screen ->
                                        val isSelected = currentScreen == screen
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentScreen = screen },
                                            icon = {
                                                Icon(
                                                    imageVector = screen.icon,
                                                    contentDescription = screen.label
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = screen.label,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = RcAmber,
                                                selectedTextColor = RcAmber,
                                                unselectedIconColor = RcTextMuted,
                                                unselectedTextColor = RcTextMuted,
                                                indicatorColor = RcAmber.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier.testTag(screen.tag)
                                        )
                                    }
                                }
                            }
                        },
                        contentWindowInsets = WindowInsets.statusBars
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(RcDarkBackground)
                                .padding(innerPadding)
                        ) {
                            when (currentScreen) {
                                Screen.DASHBOARD -> DashboardScreen(
                                    dashboardState = dashboardState,
                                    authState = authState,
                                    onRefresh = { viewModel.refreshDashboard() },
                                    onNavigateToBot = { currentScreen = Screen.BOT },
                                    onManualRefreshSession = { viewModel.manualRefreshSession() },
                                    onToggleBatterySaver = { viewModel.toggleBatterySaver(it) }
                                )

                                Screen.BOT -> BotRunnerScreen(
                                    botState = botState,
                                    isLoggedIn = authState.isLoggedIn,
                                    isFloatingBubbleEnabled = isFloatingBubbleEnabled,
                                    isBackgroundServiceEnabled = isBackgroundServiceEnabled,
                                    isBatterySaverEnabled = dashboardState.isBatterySaverEnabled,
                                    onStartBot = { viewModel.startAutoBot() },
                                    onStopBot = { viewModel.stopAutoBot() },
                                    onSetDelay = { viewModel.setBotDelay(it) },
                                    onToggleFloatingBubble = { viewModel.toggleFloatingBubble(it) },
                                    onToggleBackgroundService = { viewModel.toggleBackgroundService(it) },
                                    onToggleBatterySaver = { viewModel.toggleBatterySaver(it) },
                                    onClearLogs = { viewModel.clearLogs() }
                                )

                                Screen.ACCOUNT -> AccountScreen(
                                    authState = authState,
                                    dashboardState = dashboardState,
                                    onUserAgentChange = { viewModel.updateUserAgent(it) },
                                    onManualRefreshSession = { viewModel.manualRefreshSession() },
                                    onToggleBatterySaver = { viewModel.toggleBatterySaver(it) },
                                    onLogout = { viewModel.logout() }
                                )
                            }
                        }

                        // In-App Semi-Transparent Floating Ball on screen side as fallback ONLY when system overlay permission is not granted
                        val hasOverlayPermission = Settings.canDrawOverlays(this@MainActivity)
                        if (isFloatingBubbleEnabled && botState.isAutoRunning && !hasOverlayPermission) {
                            FloatingGameBubble(
                                botState = botState,
                                activeState = activeGameState,
                                onStopBot = { viewModel.stopAutoBot() },
                                onOpenBotTab = { currentScreen = Screen.BOT }
                            )
                        }
                    }
                }
            }
        }
    }
}
