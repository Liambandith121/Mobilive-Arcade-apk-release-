package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.*
import com.example.ui.ArcadeViewModel
import com.example.ui.components.ArcadeEmptyCard
import com.example.ui.components.ArcadePillBadge
import com.example.ui.theme.*

@Composable
fun PlayArenaScreen(
    viewModel: ArcadeViewModel,
    profile: UserProfile,
    lobbies: List<MultiplayerLobby>,
    parties: List<PartyRoom>,
    tournaments: List<Tournament>,
    blockedUids: Set<String>
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val matchmakingActive by viewModel.matchmakingActive.collectAsState()
    val matchmakingGame by viewModel.matchmakingGame.collectAsState()
    val lastMatchLobbyId by viewModel.lastMatchFoundLobbyId.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0=LAN/Lobbies, 1=Party/Voice, 2=Matchmaking, 3=Tournaments
    var showHostWorldDialog by remember { mutableStateOf(false) }
    var showCreatePartyDialog by remember { mutableStateOf(false) }
    var showCreateTourneyDialog by remember { mutableStateOf(false) }
    var selectedGameForMatch by remember { mutableStateOf(ArcadeCatalog.gameTitles.first()) }

    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPerm = granted
    }

    val visibleLobbies = remember(lobbies, blockedUids) { lobbies.filter { it.hostId !in blockedUids } }
    val visibleParties = remember(parties, blockedUids) { parties.filter { it.leaderId !in blockedUids } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianNight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Play & LAN", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("Multiplayer lobbies, LAN worlds, voice party & cups", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Button(
                onClick = {
                    when (activeTab) {
                        0 -> showHostWorldDialog = true
                        1 -> showCreatePartyDialog = true
                        3 -> showCreateTourneyDialog = true
                        else -> showHostWorldDialog = true
                    }
                },
                modifier = Modifier.testTag("play_arena_primary_action"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPulse, contentColor = ObsidianNight)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    when (activeTab) {
                        1 -> "Party"
                        3 -> "Host Cup"
                        else -> "Host World"
                    }
                )
            }
        }

        ScrollableTabRow(
            selectedTabIndex = activeTab,
            containerColor = ObsidianSurface,
            contentColor = NeonCyan,
            edgePadding = 16.dp
        ) {
            listOf("LAN & Lobbies", "Party & Voice", "Matchmaking", "Tournaments").forEachIndexed { idx, label ->
                Tab(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    text = { Text(label) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (activeTab) {
                0 -> {
                    // Minecraft LAN Hosting & Joining Guide Card
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = ObsidianSurface,
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.55f))
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(136.dp)
                                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_lan_world_banner),
                                        contentDescription = "LAN World Portal",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(ObsidianNight.copy(alpha = 0.1f), ObsidianNight.copy(alpha = 0.88f))
                                                )
                                            )
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.align(Alignment.BottomStart),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ArcadePillBadge("LAN BRIDGE", EmeraldContainer, EmeraldPulse, Icons.Default.Lan)
                                            Text("Minecraft LAN World Guide", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        color = ObsidianSurfaceElevated,
                                        shape = MaterialTheme.shapes.medium,
                                        border = BorderStroke(1.dp, ObsidianBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                "Hosting: open your world, then turn on Multiplayer Game and Visible to LAN Players.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary
                                            )
                                            HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.5f))
                                            Text(
                                                "Joining: press Join here, then open Minecraft > Play > Worlds and tap the world.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = NeonCyan
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { showHostWorldDialog = true },
                                            modifier = Modifier.weight(1f).testTag("lan_host_world_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPulse, contentColor = ObsidianNight)
                                        ) {
                                            Text("Host LAN World")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.mojang.minecraftpe")
                                                if (launchIntent != null) {
                                                    context.startActivity(launchIntent)
                                                } else {
                                                    val fallback = Intent(Intent.ACTION_VIEW).apply {
                                                        data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.mojang.minecraftpe")
                                                    }
                                                    runCatching { context.startActivity(fallback) }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Open Minecraft")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (visibleLobbies.isEmpty()) {
                        item {
                            ArcadeEmptyCard(
                                icon = Icons.Default.Public,
                                title = "No Open Lobbies Yet",
                                subtitle = "Publish your Minecraft LAN world or arcade multiplayer lobby so friends can press Join here!",
                                actionLabel = "Publish World",
                                onAction = { showHostWorldDialog = true }
                            )
                        }
                    } else {
                        items(visibleLobbies, key = { it.id }) { lobby ->
                            val isJoined = profile.userId in lobby.memberUids
                            val isHost = lobby.hostId == profile.userId
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = ObsidianSurface,
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, if (isJoined) EmeraldPulse else ObsidianBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(lobby.worldName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                            Text(
                                                "${lobby.gameTitle} • ${lobby.gameMode} • Host: ${lobby.hostName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        ArcadePillBadge(
                                            "${lobby.currentPlayers}/${lobby.maxPlayers}",
                                            NeonCyanContainer,
                                            NeonCyan,
                                            Icons.Default.Group
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        ArcadePillBadge("Room ${lobby.roomCode}", ObsidianSurfaceElevated, TextPrimary)
                                        ArcadePillBadge("LAN :${lobby.lanPort}", EmeraldContainer, EmeraldPulse)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.toggleJoinLobby(lobby) },
                                            modifier = Modifier.weight(1f).testTag("join_lobby_${lobby.id}"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isJoined) ObsidianSurfaceElevated else NeonCyan,
                                                contentColor = if (isJoined) TextPrimary else ObsidianNight
                                            )
                                        ) {
                                            Text(if (isJoined) "Joined (Tap to Leave)" else "Join Here")
                                        }
                                        IconButton(onClick = {
                                            clipboard.setText(AnnotatedString("${lobby.worldName} | Code: ${lobby.roomCode} | LAN Port: ${lobby.lanPort}"))
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy room info", tint = NeonCyan)
                                        }
                                        if (isHost) {
                                            IconButton(onClick = { viewModel.deleteLobby(lobby.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Close lobby", tint = LiveCrimson)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        Surface(
                            color = ObsidianSurface,
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Party Voice Chat Mic", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text(
                                        if (hasAudioPerm) "Microphone permission active for party voice rooms"
                                        else "Grant microphone access to speak in Party Voice Chat",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Button(
                                    onClick = { audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                    enabled = !hasAudioPerm
                                ) {
                                    Text(if (hasAudioPerm) "Mic Ready" else "Enable Mic")
                                }
                            }
                        }
                    }

                    if (visibleParties.isEmpty()) {
                        item {
                            ArcadeEmptyCard(
                                icon = Icons.Default.HeadsetMic,
                                title = "No Open Voice Parties",
                                subtitle = "Create a Party room to squad up with voice chat before entering a match or LAN world.",
                                actionLabel = "Create Party",
                                onAction = { showCreatePartyDialog = true }
                            )
                        }
                    } else {
                        items(visibleParties, key = { it.id }) { party ->
                            val inParty = profile.userId in party.memberUids
                            val amMuted = profile.userId in party.mutedUids
                            Surface(
                                color = ObsidianSurface,
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, if (inParty) ArcadeViolet else ObsidianBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(party.partyName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                            Text("Leader: ${party.leaderName} • ${party.memberUids.size}/8 in Voice", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                        ArcadePillBadge("VOICE", ArcadeVioletContainer, ArcadeViolet, Icons.Default.GraphicEq)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        party.memberNames.forEach { name ->
                                            ArcadePillBadge(name, ObsidianSurfaceElevated, TextPrimary)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.toggleJoinParty(party) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(if (inParty) "Leave Party" else "Join Party")
                                        }
                                        if (inParty) {
                                            FilledTonalButton(
                                                onClick = {
                                                    if (!hasAudioPerm) audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    viewModel.togglePartyMute(party)
                                                },
                                                modifier = Modifier.testTag("party_mute_button_${party.id}")
                                            ) {
                                                Icon(
                                                    if (amMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                                    contentDescription = null,
                                                    tint = if (amMuted) LiveCrimson else EmeraldPulse
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(if (amMuted) "Muted" else "Speaking")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = ObsidianSurface,
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Quick Matchmaking", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Text("Select a game mode to queue for ranked or co-op lobby matching.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    ArcadeCatalog.gameTitles.take(3).forEach { game ->
                                        FilterChip(
                                            selected = selectedGameForMatch == game,
                                            onClick = { selectedGameForMatch = game },
                                            label = { Text(game) }
                                        )
                                    }
                                }
                                if (matchmakingActive) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(26.dp), color = NeonCyan)
                                        Text("Searching for $matchmakingGame players...", color = NeonCyan)
                                    }
                                    OutlinedButton(onClick = { viewModel.cancelMatchmaking() }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Cancel Queue")
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.startMatchmaking(selectedGameForMatch) },
                                        modifier = Modifier.fillMaxWidth().testTag("start_matchmaking_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianNight)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Find Match")
                                    }
                                }
                                if (lastMatchLobbyId != null) {
                                    ArcadePillBadge("Match Lobby Ready! Check LAN & Lobbies tab.", EmeraldContainer, EmeraldPulse, Icons.Default.CheckCircle)
                                }
                            }
                        }
                    }
                }

                3 -> {
                    if (tournaments.isEmpty()) {
                        item {
                            ArcadeEmptyCard(
                                icon = Icons.Default.EmojiEvents,
                                title = "No Active Tournaments",
                                subtitle = "Launch a bracket tournament with Gift Coin prizes for your community!",
                                actionLabel = "Host Tournament",
                                onAction = { showCreateTourneyDialog = true }
                            )
                        }
                    } else {
                        items(tournaments, key = { it.id }) { tourney ->
                            val registered = profile.userId in tourney.participantUids
                            Surface(
                                color = ObsidianSurface,
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, CoinGold.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tourney.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                            Text("${tourney.gameTitle} • ${tourney.participantUids.size}/${tourney.bracketSize} Players", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                        ArcadePillBadge("${tourney.prizeCoins} Coins", CoinGoldContainer, CoinGold, Icons.Default.EmojiEvents)
                                    }
                                    Button(
                                        onClick = { viewModel.toggleRegisterTournament(tourney) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (registered) ObsidianSurfaceElevated else ArcadeViolet
                                        )
                                    ) {
                                        Text(if (registered) "Registered (Withdraw)" else "Register for Bracket")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHostWorldDialog) {
        var worldName by remember { mutableStateOf("Emerald Skyblock LAN") }
        var gameTitle by remember { mutableStateOf("Minecraft") }
        var gameMode by remember { mutableStateOf("Survival") }
        var portText by remember { mutableStateOf("19132") }
        var visibility by remember { mutableStateOf("PUBLIC") }

        AlertDialog(
            onDismissRequest = { showHostWorldDialog = false },
            title = { Text("Host Multiplayer / LAN World") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Hosting: open your world, then turn on Multiplayer Game and Visible to LAN Players.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldPulse
                    )
                    OutlinedTextField(value = worldName, onValueChange = { worldName = it }, label = { Text("World Name") })
                    OutlinedTextField(value = gameTitle, onValueChange = { gameTitle = it }, label = { Text("Game") })
                    OutlinedTextField(value = gameMode, onValueChange = { gameMode = it }, label = { Text("Mode (Survival / Creative)") })
                    OutlinedTextField(value = portText, onValueChange = { portText = it }, label = { Text("LAN Port (e.g. 19132)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = visibility == "PUBLIC", onClick = { visibility = "PUBLIC" }, label = { Text("Public") })
                        FilterChip(selected = visibility == "FRIENDS", onClick = { visibility = "FRIENDS" }, label = { Text("Friends") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val port = portText.toLongOrNull() ?: 19132L
                    viewModel.hostLanWorld(worldName, gameTitle, gameMode, port, 8L, visibility)
                    showHostWorldDialog = false
                }) { Text("Publish World") }
            },
            dismissButton = { TextButton(onClick = { showHostWorldDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCreatePartyDialog) {
        var partyName by remember { mutableStateOf("Night Raid Squad") }
        AlertDialog(
            onDismissRequest = { showCreatePartyDialog = false },
            title = { Text("Create Voice Party") },
            text = {
                OutlinedTextField(value = partyName, onValueChange = { partyName = it }, label = { Text("Party Name") })
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createParty(partyName, openParty = true)
                    showCreatePartyDialog = false
                }) { Text("Launch Party") }
            },
            dismissButton = { TextButton(onClick = { showCreatePartyDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCreateTourneyDialog) {
        var title by remember { mutableStateOf("Neon Arcade Cup") }
        var game by remember { mutableStateOf("Cyber Strikers") }
        AlertDialog(
            onDismissRequest = { showCreateTourneyDialog = false },
            title = { Text("Host Tournament") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Tournament Title") })
                    OutlinedTextField(value = game, onValueChange = { game = it }, label = { Text("Game") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createTournament(title, game, prizeCoins = 2500L, bracketSize = 16L)
                    showCreateTourneyDialog = false
                }) { Text("Create Cup") }
            },
            dismissButton = { TextButton(onClick = { showCreateTourneyDialog = false }) { Text("Cancel") } }
        )
    }
}
