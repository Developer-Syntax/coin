package com.rollercoin.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.rollercoin.mobile.ui.components.ActiveGameOverlayBanner
import com.rollercoin.mobile.ui.screens.AccountScreen
import com.rollercoin.mobile.ui.screens.BotRunnerScreen
import com.rollercoin.mobile.ui.screens.DashboardScreen
import com.rollercoin.mobile.ui.theme.RcAmber
import com.rollercoin.mobile.ui.theme.RcDarkBackground
import com.rollercoin.mobile.ui.theme.RcSurface
import com.rollercoin.mobile.ui.theme.RcSurfaceBorder
import com.rollercoin.mobile.ui.theme.RcTextMuted
import com.rollercoin.mobile.ui.theme.RollerCoinTheme

enum class Screen(val label: String, val icon: ImageVector, val tag: String) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard, "nav_dashboard"),
    BOT("Auto-Bot", Icons.Default.SmartToy, "nav_bot"),
    ACCOUNT("Akun", Icons.Default.AccountCircle, "nav_account"),
}

class MainActivity : ComponentActivity() {
    private val viewModel: RollerCoinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            RollerCoinTheme {
                val authState by viewModel.authUiState.collectAsStateWithLifecycle()
                val dashboardState by viewModel.dashboardUiState.collectAsStateWithLifecycle()
                val activeGameState by viewModel.activeGameUiState.collectAsStateWithLifecycle()
                val botState by viewModel.botUiState.collectAsStateWithLifecycle()

                var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

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
                                onRefresh = { viewModel.refreshDashboard() },
                                onNavigateToBot = { currentScreen = Screen.BOT }
                            )

                            Screen.BOT -> BotRunnerScreen(
                                botState = botState,
                                isLoggedIn = authState.isLoggedIn,
                                onStartBot = { viewModel.startAutoBot() },
                                onStopBot = { viewModel.stopAutoBot() },
                                onSetDelay = { viewModel.setBotDelay(it) },
                                onClearLogs = { viewModel.clearLogs() }
                            )

                            Screen.ACCOUNT -> AccountScreen(
                                authState = authState,
                                onEmailChange = { viewModel.updateEmail(it) },
                                onUserAgentChange = { viewModel.updateUserAgent(it) },
                                onOtpChange = { viewModel.updateOtpCode(it) },
                                onManualTokenChange = { viewModel.updateManualToken(it) },
                                onCaptchaPointsChange = { viewModel.updateCaptchaPoints(it) },
                                onAddCaptchaPoint = { x, y, rx, ry -> viewModel.addCaptchaPoint(x, y, rx, ry) },
                                onRemoveLastCaptchaPoint = { viewModel.removeLastCaptchaPoint() },
                                onClearCaptchaPoints = { viewModel.clearCaptchaPoints() },
                                onAutoDetectCaptchaPoints = { viewModel.autoDetectCaptchaPoints() },
                                onPrepareCaptcha = { viewModel.prepareCaptcha() },
                                onValidateCaptcha = { viewModel.validateCaptcha() },
                                onRequestOtp = { viewModel.requestOtp() },
                                onValidateOtp = { viewModel.validateOtp() },
                                onDirectTokenLogin = { viewModel.directTokenLogin() },
                                onLogout = { viewModel.logout() }
                            )
                        }
                    }
                }
            }
        }
    }
}
