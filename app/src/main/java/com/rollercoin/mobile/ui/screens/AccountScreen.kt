package com.rollercoin.mobile.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rollercoin.mobile.AuthUiState
import com.rollercoin.mobile.CaptchaChallenge
import com.rollercoin.mobile.CaptchaPoint
import com.rollercoin.mobile.ui.components.SectionHeader
import com.rollercoin.mobile.ui.theme.*
import kotlin.math.roundToInt

private enum class LoginTab {
    EMAIL_OTP,
    DIRECT_TOKEN
}

@Composable
fun AccountScreen(
    authState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onUserAgentChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onManualTokenChange: (String) -> Unit,
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
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLoginTab by remember { mutableStateOf(LoginTab.EMAIL_OTP) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Akun & Autentikasi",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = RcTextPrimary
                )
                Text(
                    text = "Kelola sesi RollerCoin, verifikasi CAPTCHA, dan kredensial",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RcTextSecondary
                )
            }
        }

        // Active session status banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (authState.isLoggedIn) RcGreen else RcSurfaceBorder
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (authState.isLoggedIn) RcGreen.copy(alpha = 0.2f) else RcTextMuted.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (authState.isLoggedIn) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (authState.isLoggedIn) RcGreen else RcTextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (authState.isLoggedIn) "Sesi Tersambung" else "Belum Login",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcTextPrimary
                            )
                            if (authState.isLoggedIn && authState.userId.isNotBlank()) {
                                Text(
                                    text = "UID: ${authState.userId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = RcCyan
                                )
                            }
                        }
                    }

                    if (authState.isLoggedIn) {
                        OutlinedButton(
                            onClick = onLogout,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RcRed),
                            border = BorderStroke(1.dp, RcRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Keluar")
                        }
                    }
                }
            }
        }

        // Notification / Feedback banner
        if (authState.message != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = RcGreen.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = authState.message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = RcGreenLight
                    )
                }
            }
        }

        if (authState.error != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = RcRed.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = authState.error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = RcRed
                    )
                }
            }
        }

        // Login Methods Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RcSurfaceBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Method Selector Tabs
                    TabRow(
                        selectedTabIndex = selectedLoginTab.ordinal,
                        containerColor = RcSurfaceElevated,
                        contentColor = RcAmber,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    ) {
                        Tab(
                            selected = selectedLoginTab == LoginTab.EMAIL_OTP,
                            onClick = { selectedLoginTab = LoginTab.EMAIL_OTP },
                            text = { Text("Email + OTP", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedLoginTab == LoginTab.DIRECT_TOKEN,
                            onClick = { selectedLoginTab = LoginTab.DIRECT_TOKEN },
                            text = { Text("Direct Token", fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedLoginTab == LoginTab.EMAIL_OTP) {
                        // Email field
                        OutlinedTextField(
                            value = authState.email,
                            onValueChange = onEmailChange,
                            label = { Text("Email RollerCoin") },
                            placeholder = { Text("email@contoh.com") },
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // CAPTCHA Challenge Box (Interactive touch-target & challenge icons)
                        val challenge = authState.captchaChallenge
                        if (challenge != null && challenge.image != null) {
                            InteractiveCaptchaCard(
                                challenge = challenge,
                                pointsList = authState.captchaPointList,
                                manualPoints = authState.captchaPoints,
                                onAddPoint = onAddCaptchaPoint,
                                onRemoveLastPoint = onRemoveLastCaptchaPoint,
                                onClearPoints = onClearCaptchaPoints,
                                onAutoDetect = onAutoDetectCaptchaPoints,
                                onManualPointsChange = onCaptchaPointsChange,
                                onValidate = onValidateCaptcha,
                                onReload = onPrepareCaptcha,
                                isBusy = authState.isBusy,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // CAPTCHA and OTP Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                Text(if (challenge == null) "Siapkan CAPTCHA" else "Ganti CAPTCHA")
                            }

                            Button(
                                onClick = onRequestOtp,
                                enabled = !authState.isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = RcAmber, contentColor = RcDarkBackground),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("request_otp_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kirim OTP", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = RcSurfaceBorder)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = authState.otpCode,
                            onValueChange = onOtpChange,
                            label = { Text("Kode OTP dari Email") },
                            placeholder = { Text("123456") },
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

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onValidateOtp,
                            enabled = !authState.isBusy && authState.otpCode.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = RcGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("validate_otp_button")
                        ) {
                            if (authState.isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            } else {
                                Text("Konfirmasi & Masuk", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Direct Token flow
                        Text(
                            text = "Tempel Bearer token langsung dari inspect browser RollerCoin:",
                            style = MaterialTheme.typography.bodySmall,
                            color = RcTextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = authState.manualToken,
                            onValueChange = onManualTokenChange,
                            label = { Text("Bearer Access Token") },
                            placeholder = { Text("eyJhbGciOi...") },
                            maxLines = 4,
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

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onDirectTokenLogin,
                            enabled = !authState.isBusy && authState.manualToken.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = RcAmber, contentColor = RcDarkBackground),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("direct_token_login_button")
                        ) {
                            Text("Sambungkan Token", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Advanced User-Agent Configuration
        item {
            SectionHeader(title = "Header User-Agent", icon = Icons.Default.Settings)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RcSurfaceBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = authState.userAgent,
                        onValueChange = onUserAgentChange,
                        label = { Text("Custom User-Agent") },
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp),
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

/**
 * Interactive CAPTCHA challenge component:
 * 1. Shows challenge icons to pick in order.
 * 2. Provides touch canvas where tapping automatically places target pins & records coordinates.
 * 3. Supports undo, reset, and manual adjustments.
 */
@Composable
private fun InteractiveCaptchaCard(
    challenge: CaptchaChallenge,
    pointsList: List<CaptchaPoint>,
    manualPoints: String,
    onAddPoint: (Int, Int, Float, Float) -> Unit,
    onRemoveLastPoint: () -> Unit,
    onClearPoints: () -> Unit,
    onAutoDetect: () -> Unit,
    onManualPointsChange: (String) -> Unit,
    onValidate: () -> Unit,
    onReload: () -> Unit,
    isBusy: Boolean,
    modifier: Modifier = Modifier
) {
    var showManualInput by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.5.dp, RcAmber.copy(alpha = 0.6f))
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
                            imageVector = Icons.Default.AdsClick,
                            contentDescription = null,
                            tint = RcAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tantangan CAPTCHA",
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

            // 1. Target Icons / Instruction Banner
            val targetImg = challenge.targetImage
            val targetIcons = challenge.targetIcons
            val instruction = challenge.instruction

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
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.5.dp, RcAmber, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = targetBitmap,
                                    contentDescription = "Ikon Target CAPTCHA (thumb_img)",
                                    modifier = Modifier.size(54.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = RcAmber.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = "TARGET IKON (THUMB_IMG)",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = RcAmber
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Pilih / ketuk simbol ikon di samping pada kanvas gambar berikut",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RcTextPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            } else if (targetIcons.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = BorderStroke(1.dp, RcCyan.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Urutan Ikon Target (Pilih Sesuai Nomor):",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = RcCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(targetIcons) { index, iconBytes ->
                                val iconBitmap = remember(iconBytes) {
                                    BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)?.asImageBitmap()
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(RcSurfaceElevated)
                                            .border(1.dp, RcAmber, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (iconBitmap != null) {
                                            Image(
                                                bitmap = iconBitmap,
                                                contentDescription = "Target #${index + 1}",
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(2.dp)
                                                .background(RcAmber, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                    if (index < targetIcons.size - 1) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = RcCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RcCyan.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, RcCyan.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = RcCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = instruction ?: "Ketuk ikon pada gambar sesuai urutan petunjuk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RcTextPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 2. Interactive Touch Canvas
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

                        // Challenge Image
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Gambar CAPTCHA RollerCoin",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        // Render Target Pin Reticles
                        pointsList.forEachIndexed { index, pt ->
                            val pinX = containerWidth * pt.ratioX
                            val pinY = containerHeight * pt.ratioY

                            Box(
                                modifier = Modifier
                                    .offset(x = pinX - 16.dp, y = pinY - 16.dp)
                                    .size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Outer pulse ring
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(RcAmber.copy(alpha = 0.35f))
                                        .border(1.5.dp, RcAmber, CircleShape)
                                )
                                // Inner solid badge
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

                        // Helper guide if empty
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
                                        text = "Sentuh gambar untuk memilih titik target #1",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Target Management Action Buttons
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
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
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
                        text = if (showManualInput) "Tutup Teks" else "Edit Angka",
                        fontSize = 11.sp,
                        color = RcCyan
                    )
                }
            }

            // Coordinates readout / manual textfield toggle
            AnimatedVisibility(visible = showManualInput) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = manualPoints,
                        onValueChange = onManualPointsChange,
                        label = { Text("Nilai Koordinat (x,y,...)") },
                        placeholder = { Text("Contoh: 120,45,210,88") },
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

            if (pointsList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Koordinat: $manualPoints",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = RcAmber
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Validate CAPTCHA Button
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
                    Text("Validasi CAPTCHA Sekarang", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
