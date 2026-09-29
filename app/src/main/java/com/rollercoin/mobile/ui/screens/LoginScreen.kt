package com.rollercoin.mobile.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rollercoin.mobile.AuthUiState
import com.rollercoin.mobile.CaptchaChallenge
import com.rollercoin.mobile.CaptchaPoint
import com.rollercoin.mobile.R
import com.rollercoin.mobile.ui.theme.*
import kotlin.math.roundToInt

private enum class LoginMethod {
    EMAIL_OTP,
    DIRECT_TOKEN
}

@Composable
fun LoginScreen(
    authState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onUserAgentChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onManualTokenChange: (String) -> Unit,
    onManualRefreshTokenChange: (String) -> Unit,
    onCaptchaPointsChange: (String) -> Unit,
    onAddCaptchaPoint: (Int, Int, Float, Float) -> Unit,
    onRemoveLastCaptchaPoint: () -> Unit,
    onClearCaptchaPoints: () -> Unit,
    onAutoDetectCaptchaPoints: () -> Unit,
    onPrepareCaptcha: () -> Unit,
    onValidateCaptcha: () -> Unit,
    onRequestOtp: () -> Unit,
    onValidateOtp: () -> Unit,
    onDirectTokenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMethod by remember { mutableStateOf(LoginMethod.EMAIL_OTP) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    // Staggered screen entry animation
    var isLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isLoaded = true
    }

    val headerAlpha by animateFloatAsState(
        targetValue = if (isLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "headerAlpha"
    )
    val headerY by animateFloatAsState(
        targetValue = if (isLoaded) 0f else -30f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "headerY"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (isLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 650, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "cardAlpha"
    )
    val cardY by animateFloatAsState(
        targetValue = if (isLoaded) 0f else 35f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "cardY"
    )

    // Infinite Ambient & Hero Animations
    val infiniteTransition = rememberInfiniteTransition(label = "loginAmbient")

    // Ambient floating orbs
    val orbOffset1 by infiniteTransition.animateFloat(
        initialValue = -35f,
        targetValue = 35f,
        animationSpec = infiniteRepeatable(tween(5500, easing = EaseInOutQuad), RepeatMode.Reverse),
        label = "orb1"
    )
    val orbOffset2 by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(tween(6500, easing = EaseInOutQuad), RepeatMode.Reverse),
        label = "orb2"
    )
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(3800, easing = EaseInOutQuad), RepeatMode.Reverse),
        label = "ambientPulse"
    )

    // Hero Logo floating and breathing
    val logoFloatY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseInOutQuad), RepeatMode.Reverse),
        label = "logoFloat"
    )
    val logoPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logoPulse"
    )
    val haloRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart),
        label = "haloRotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RcDarkBackground)
    ) {
        // Dynamic Cyber Glow Mesh Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Top-left Amber glow orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RcAmber.copy(alpha = ambientPulse),
                        RcAmber.copy(alpha = ambientPulse * 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.25f + orbOffset1, height * 0.15f + orbOffset2),
                    radius = width * 0.55f
                ),
                radius = width * 0.55f,
                center = Offset(width * 0.25f + orbOffset1, height * 0.15f + orbOffset2)
            )

            // Bottom-right Cyan tech glow orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RcCyan.copy(alpha = ambientPulse * 0.85f),
                        RcCyan.copy(alpha = ambientPulse * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.8f + orbOffset2, height * 0.75f + orbOffset1),
                    radius = width * 0.65f
                ),
                radius = width * 0.65f,
                center = Offset(width * 0.8f + orbOffset2, height * 0.75f + orbOffset1)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Header & Animated Branding
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .graphicsLayer {
                            alpha = headerAlpha
                            translationY = headerY
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .offset(y = logoFloatY.dp)
                            .scale(logoPulseScale),
                        contentAlignment = Alignment.Center
                    ) {
                        // Rotating glowing neon halo behind the logo badge
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .rotate(haloRotation)
                                .clip(RoundedCornerShape(26.dp))
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            RcAmber,
                                            Color.Transparent,
                                            RcCyan,
                                            Color.Transparent,
                                            RcAmber
                                        )
                                    )
                                )
                        )

                        // Main Logo Badge Box
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                    )
                                )
                                .border(
                                    BorderStroke(
                                        2.dp,
                                        Brush.linearGradient(listOf(RcAmber, RcCyan))
                                    ),
                                    RoundedCornerShape(22.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_rollercoin_logo),
                                contentDescription = "RollerCoin Logo",
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ROLLERCOIN",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = RcAmber
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Mobile Companion & Mining Automation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RcTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Checking saved session banner with smooth animation
            item {
                AnimatedVisibility(
                    visible = authState.isCheckingSavedSession,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = RcSurfaceElevated),
                        border = BorderStroke(1.dp, RcCyan.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = RcCyan,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Memeriksa Sesi Lokal...",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RcTextPrimary
                                )
                                Text(
                                    text = "Memvalidasi token & mencoba login otomatis...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RcTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Info message banner with animation
            item {
                AnimatedVisibility(
                    visible = authState.message != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = RcGreen.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, RcGreen.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RcGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = authState.message.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = RcGreenLight
                            )
                        }
                    }
                }
            }

            // Error message banner with animation
            item {
                AnimatedVisibility(
                    visible = authState.error != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = RcRed.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, RcRed.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = RcRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = authState.error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = RcRed
                            )
                        }
                    }
                }
            }

            // Main Interactive Login Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = cardAlpha
                            translationY = cardY
                        },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = RcSurface),
                    border = BorderStroke(1.2.dp, RcSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Method Selector Tabs with Smooth Sliding Indicator
                        TabRow(
                            selectedTabIndex = selectedMethod.ordinal,
                            containerColor = RcSurfaceElevated,
                            contentColor = RcAmber,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedMethod.ordinal]),
                                    color = RcAmber,
                                    height = 3.dp
                                )
                            },
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            Tab(
                                selected = selectedMethod == LoginMethod.EMAIL_OTP,
                                onClick = { selectedMethod = LoginMethod.EMAIL_OTP },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Email & OTP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            )
                            Tab(
                                selected = selectedMethod == LoginMethod.DIRECT_TOKEN,
                                onClick = { selectedMethod = LoginMethod.DIRECT_TOKEN },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Direct Token", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Animated Content Transition between Login Methods
                        AnimatedContent(
                            targetState = selectedMethod,
                            transitionSpec = {
                                if (targetState == LoginMethod.DIRECT_TOKEN) {
                                    (slideInHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        initialOffsetX = { it / 3 }
                                    ) + fadeIn(tween(250))) togetherWith
                                        (slideOutHorizontally(
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                            targetOffsetX = { -it / 3 }
                                        ) + fadeOut(tween(200)))
                                } else {
                                    (slideInHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        initialOffsetX = { -it / 3 }
                                    ) + fadeIn(tween(250))) togetherWith
                                        (slideOutHorizontally(
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                            targetOffsetX = { it / 3 }
                                        ) + fadeOut(tween(200)))
                                }
                            },
                            label = "loginTabAnimatedContent"
                        ) { method ->
                            if (method == LoginMethod.EMAIL_OTP) {
                                Column {
                                    // Email field
                                    OutlinedTextField(
                                        value = authState.email,
                                        onValueChange = onEmailChange,
                                        label = { Text("Email RollerCoin") },
                                        placeholder = { Text("nama@email.com") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Email, contentDescription = null, tint = RcAmber)
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("email_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = RcSurfaceElevated,
                                            unfocusedContainerColor = RcSurfaceElevated,
                                            focusedBorderColor = RcAmber,
                                            unfocusedBorderColor = RcSurfaceBorder,
                                            focusedTextColor = RcTextPrimary,
                                            unfocusedTextColor = RcTextPrimary
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Interactive CAPTCHA challenge with animated expand
                                    val challenge = authState.captchaChallenge
                                    AnimatedVisibility(
                                        visible = challenge != null && challenge.image != null,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        if (challenge != null && challenge.image != null) {
                                            Column {
                                                InteractiveCaptchaSolver(
                                                    challenge = challenge,
                                                    pointsList = authState.captchaPointList,
                                                    manualPoints = authState.captchaPoints,
                                                    onAddPoint = onAddCaptchaPoint,
                                                    onRemoveLastPoint = onRemoveLastCaptchaPoint,
                                                    onClearPoints = onClearCaptchaPoints,
                                                    onAutoDetect = onAutoDetectCaptchaPoints,
                                                    onManualPointsChange = onCaptchaPointsChange,
                                                    onValidate = onValidateCaptcha,
                                                    isBusy = authState.isBusy,
                                                )
                                                Spacer(modifier = Modifier.height(14.dp))
                                            }
                                        }
                                    }

                                    // CAPTCHA and OTP Action buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onPrepareCaptcha,
                                            enabled = !authState.isBusy,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("check_captcha_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (challenge == null) "Siapkan CAPTCHA" else "Ganti CAPTCHA", fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = onRequestOtp,
                                            enabled = !authState.isBusy && authState.email.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = RcAmber,
                                                contentColor = RcDarkBackground
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("request_otp_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Kirim OTP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = RcSurfaceBorder)
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // OTP Code Input
                                    OutlinedTextField(
                                        value = authState.otpCode,
                                        onValueChange = onOtpChange,
                                        label = { Text("Kode OTP dari Email") },
                                        placeholder = { Text("Masukkan 6 digit angka") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Password, contentDescription = null, tint = RcGreen)
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("otp_code_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = RcSurfaceElevated,
                                            unfocusedContainerColor = RcSurfaceElevated,
                                            focusedBorderColor = RcGreen,
                                            unfocusedBorderColor = RcSurfaceBorder,
                                            focusedTextColor = RcTextPrimary,
                                            unfocusedTextColor = RcTextPrimary
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Submit OTP Button with Loading Animation
                                    Button(
                                        onClick = onValidateOtp,
                                        enabled = !authState.isBusy && authState.otpCode.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = RcGreen,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("validate_otp_button")
                                    ) {
                                        if (authState.isBusy) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                                        } else {
                                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Konfirmasi OTP & Masuk", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                    }
                                }
                            } else {
                                Column {
                                    // Direct Token flow
                                    Text(
                                        text = "Tempel token dari DevTools RollerCoin untuk login langsung:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RcTextSecondary
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = authState.manualToken,
                                        onValueChange = onManualTokenChange,
                                        label = { Text("Bearer Access Token (Wajib)") },
                                        placeholder = { Text("eyJhbGciOi...") },
                                        maxLines = 3,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("manual_token_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = RcSurfaceElevated,
                                            unfocusedContainerColor = RcSurfaceElevated,
                                            focusedBorderColor = RcAmber,
                                            unfocusedBorderColor = RcSurfaceBorder,
                                            focusedTextColor = RcTextPrimary,
                                            unfocusedTextColor = RcTextPrimary
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Refresh Token Input
                                    OutlinedTextField(
                                        value = authState.manualRefreshToken,
                                        onValueChange = onManualRefreshTokenChange,
                                        label = { Text("Refresh Token (Disarankan untuk Auto-Renew)") },
                                        placeholder = { Text("Refresh token dari inspect storage...") },
                                        maxLines = 2,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("manual_refresh_token_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = RcSurfaceElevated,
                                            unfocusedContainerColor = RcSurfaceElevated,
                                            focusedBorderColor = RcCyan,
                                            unfocusedBorderColor = RcSurfaceBorder,
                                            focusedTextColor = RcTextPrimary,
                                            unfocusedTextColor = RcTextPrimary
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "ℹ Token dan Refresh Token disimpan aman di lokal. Jika token kedaluwarsa, aplikasi akan memperbarui token baru secara otomatis.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RcCyan
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = onDirectTokenLogin,
                                        enabled = !authState.isBusy && authState.manualToken.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = RcAmber,
                                            contentColor = RcDarkBackground
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("direct_token_login_button")
                                    ) {
                                        if (authState.isBusy) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = RcDarkBackground, strokeWidth = 2.5.dp)
                                        } else {
                                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Sambungkan & Masuk", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Advanced Settings Toggle
                        TextButton(
                            onClick = { showAdvancedSettings = !showAdvancedSettings },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = RcTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showAdvancedSettings) "Sembunyikan Pengaturan User-Agent" else "Pengaturan Lanjutan (User-Agent)",
                                style = MaterialTheme.typography.labelSmall,
                                color = RcTextMuted
                            )
                        }

                        AnimatedVisibility(
                            visible = showAdvancedSettings,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value = authState.userAgent,
                                    onValueChange = onUserAgentChange,
                                    label = { Text("Custom User-Agent") },
                                    maxLines = 2,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = RcSurfaceElevated,
                                        unfocusedContainerColor = RcSurfaceElevated,
                                        focusedBorderColor = RcAmber,
                                        unfocusedBorderColor = RcSurfaceBorder,
                                        focusedTextColor = RcTextPrimary,
                                        unfocusedTextColor = RcTextPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Feature highlights footer with gentle fade-in
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = cardAlpha
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, RcSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(RcAmber)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Fitur RollerCoin Mobile:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Monitoring Hashpower & Mining Room Realtime\n• Auto-Play Bot 15 Mini-Games dengan smart cooldown\n• Enkripsi token & penyimpanan aman Android Keystore",
                            style = MaterialTheme.typography.bodySmall,
                            color = RcTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveCaptchaSolver(
    challenge: CaptchaChallenge,
    pointsList: List<CaptchaPoint>,
    manualPoints: String,
    onAddPoint: (Int, Int, Float, Float) -> Unit,
    onRemoveLastPoint: () -> Unit,
    onClearPoints: () -> Unit,
    onAutoDetect: () -> Unit,
    onManualPointsChange: (String) -> Unit,
    onValidate: () -> Unit,
    isBusy: Boolean,
    modifier: Modifier = Modifier
) {
    var showManualInput by remember { mutableStateOf(false) }

    // Pulsing radar animation for marked CAPTCHA pins
    val pinInfinite = rememberInfiniteTransition(label = "captchaPinRadar")
    val pinPulseScale by pinInfinite.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pinPulseScale"
    )
    val pinPulseAlpha by pinInfinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pinPulseAlpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
        border = BorderStroke(1.5.dp, RcAmber.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(RcAmber.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = RcAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verifikasi CAPTCHA",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = RcAmber
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (pointsList.isNotEmpty()) RcGreen.copy(alpha = 0.2f) else RcSurfaceElevated,
                    border = BorderStroke(1.dp, if (pointsList.isNotEmpty()) RcGreen else RcSurfaceBorder)
                ) {
                    Text(
                        text = "${pointsList.size} Titik Ditandai",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (pointsList.isNotEmpty()) RcGreen else RcTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Target banner
            val targetImg = challenge.targetImage
            if (targetImg != null) {
                val targetBitmap = remember(targetImg) {
                    BitmapFactory.decodeByteArray(targetImg, 0, targetImg.size)?.asImageBitmap()
                }
                if (targetBitmap != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.5.dp, RcAmber)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.5.dp, RcAmber, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = targetBitmap,
                                    contentDescription = "Target CAPTCHA",
                                    modifier = Modifier.size(50.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TARGET IKON",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RcAmber
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Ketuk ikon yang sama pada gambar kanvas di bawah",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RcTextPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Interactive Touch Canvas with animated reticle pins
            val captchaImg = challenge.image
            if (captchaImg != null) {
                val bitmap = remember(captchaImg) {
                    BitmapFactory.decodeByteArray(captchaImg, 0, captchaImg.size)
                }

                if (bitmap != null) {
                    val bitmapWidth = bitmap.width
                    val bitmapHeight = bitmap.height
                    val aspectRatio = if (bitmapHeight > 0) bitmapWidth.toFloat() / bitmapHeight.toFloat() else 1.5f

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(
                                width = 1.5.dp,
                                color = if (pointsList.isNotEmpty()) RcAmber else RcSurfaceBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .pointerInput(bitmap) {
                                detectTapGestures { offset ->
                                    val canvasW = size.width.toFloat()
                                    val canvasH = size.height.toFloat()
                                    if (canvasW > 0 && canvasH > 0) {
                                        val ratioX = (offset.x / canvasW).coerceIn(0f, 1f)
                                        val ratioY = (offset.y / canvasH).coerceIn(0f, 1f)
                                        val imgX = (ratioX * bitmapWidth).roundToInt().coerceIn(0, bitmapWidth)
                                        val imgY = (ratioY * bitmapHeight).roundToInt().coerceIn(0, bitmapHeight)
                                        onAddPoint(imgX, imgY, ratioX, ratioY)
                                    }
                                }
                            }
                    ) {
                        val containerWidth = maxWidth
                        val containerHeight = maxHeight

                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Gambar CAPTCHA",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        // Animated reticle pins with radar sonar pulse
                        pointsList.forEachIndexed { index, pt ->
                            val pinX = containerWidth * pt.ratioX
                            val pinY = containerHeight * pt.ratioY

                            Box(
                                modifier = Modifier
                                    .offset(x = pinX - 18.dp, y = pinY - 18.dp)
                                    .size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Expanding radar pulse wave
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .scale(pinPulseScale)
                                        .clip(CircleShape)
                                        .background(RcAmber.copy(alpha = pinPulseAlpha))
                                )

                                // Outer glow border
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(RcAmber.copy(alpha = 0.35f))
                                        .border(1.5.dp, RcAmber, CircleShape)
                                )

                                // Number badge
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(RcAmber),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        if (pointsList.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.Black.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, RcAmber.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = RcAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Sentuh gambar untuk memilih titik target",
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onAutoDetect,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RcCyan, contentColor = RcDarkBackground),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Detect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onRemoveLastPoint,
                        enabled = pointsList.isNotEmpty() && !isBusy,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Batal", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onClearPoints,
                        enabled = pointsList.isNotEmpty() && !isBusy,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontSize = 11.sp)
                    }
                }

                TextButton(
                    onClick = { showManualInput = !showManualInput },
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(
                        text = if (showManualInput) "Tutup" else "Edit Nilai",
                        fontSize = 11.sp,
                        color = RcCyan
                    )
                }
            }

            AnimatedVisibility(
                visible = showManualInput,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = manualPoints,
                        onValueChange = onManualPointsChange,
                        label = { Text("Koordinat Manual (x,y)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black,
                            focusedBorderColor = RcAmber,
                            unfocusedBorderColor = RcSurfaceBorder,
                            focusedTextColor = RcTextPrimary,
                            unfocusedTextColor = RcTextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onValidate,
                enabled = !isBusy && manualPoints.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RcCyan, contentColor = RcDarkBackground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("validate_captcha_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = RcDarkBackground)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Validasi CAPTCHA", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
