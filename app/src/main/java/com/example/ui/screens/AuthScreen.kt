package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.ui.theme.*

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSignUpMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianNight)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_arcade_hero),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to ObsidianNight.copy(alpha = 0.25f),
                        0.32f to ObsidianNight.copy(alpha = 0.92f),
                        0.6f to ObsidianNight
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(64.dp))

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianSurfaceElevated)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mobilive_icon_fg),
                    contentDescription = "Mobilive Arcade Emblem",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Text(
                text = "MOBILIVE ARCADE",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Stream live gameplay, host Minecraft LAN worlds, squad up in voice parties, and earn daily coin buffs.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            // Login / Sign Up Mode Toggle
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isSignUpMode,
                    onClick = { isSignUpMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("auth_login_tab")
                ) {
                    Text("Login")
                }
                SegmentedButton(
                    selected = isSignUpMode,
                    onClick = { isSignUpMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("auth_signup_tab")
                ) {
                    Text("Sign Up")
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ObsidianSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = LiveCrimson)
                        Text("Go Live, Stream Chat & Replays", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Lan, contentDescription = null, tint = EmeraldPulse)
                        Text("Multiplayer Lobbies, LAN Worlds & Voice Chat", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = CoinGold)
                        Text("Level System, Buffs, Gift Coins & Tournaments", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = LiveCrimsonContainer,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, LiveCrimson.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                            errorMessage = "Sign-in was cancelled. Select a Google account to enter Mobilive Arcade."
                        }
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = ObsidianNight
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = ObsidianNight,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(Icons.Default.SportsEsports, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isSignUpMode) "Sign up with Google" else "Sign in with Google",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Text(
                text = "Your profile face, coins, communities, and friends sync automatically to your Google account.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
