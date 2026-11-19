package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.*
import com.example.ui.ArcadeViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun LiveHubScreen(
    viewModel: ArcadeViewModel,
    profile: UserProfile,
    streams: List<LiveStream>,
    posts: List<SocialPost>,
    blockedUids: Set<String>
) {
    val context = LocalContext.current
    val selectedStreamId by viewModel.selectedStreamId.collectAsState()
    val streamMessagesState by viewModel.streamMessagesState.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0=Live, 1=Replays, 2=Shorts
    var showGoLiveDialog by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val visibleStreams = remember(streams, blockedUids) { streams.filter { it.hostId !in blockedUids } }
    val activeStream = remember(selectedStreamId, visibleStreams) {
        visibleStreams.firstOrNull { it.id == selectedStreamId }
    }

    if (activeStream != null) {
        BackHandler { viewModel.openStreamRoom(null) }
        StreamWatchRoom(
            stream = activeStream,
            messages = (streamMessagesState as? UiState.Success)?.data ?: emptyList(),
            profile = profile,
            onBack = { viewModel.openStreamRoom(null) },
            onLike = { viewModel.likeStream(activeStream.id) },
            onSendChat = { msg, gift -> viewModel.sendStreamMessage(activeStream, msg, gift) },
            onEndToReplay = { viewModel.endStreamToReplay(activeStream.id) },
            onDelete = { viewModel.deleteStream(activeStream.id) },
            onShare = {
                shareTextExternally(context, "Watch ${activeStream.hostName} on Mobilive Arcade", "${activeStream.title} (${activeStream.gameTitle})")
            },
            onReport = { reportTarget = activeStream.id to activeStream.hostName },
            onBlock = {
                viewModel.blockUser(activeStream.hostId, activeStream.hostName)
                viewModel.openStreamRoom(null)
            }
        )
    } else {
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
                    Text("Live & Replays", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Broadcast games, watch replays & browse Shorts", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Button(
                    onClick = { showGoLiveDialog = true },
                    modifier = Modifier.testTag("go_live_fab"),
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson, contentColor = TextPrimary)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Go Live")
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                listOf("Live Streams", "Replays", "Shorts").forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = activeTab == idx,
                        onClick = { activeTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(idx, 3)
                    ) { Text(label) }
                }
            }

            Spacer(Modifier.height(12.dp))

            val filteredStreams = when (activeTab) {
                0 -> visibleStreams.filter { it.status == StreamStatus.LIVE.name }
                1 -> visibleStreams.filter { it.status == StreamStatus.REPLAY.name }
                else -> emptyList()
            }
            val shorts = remember(posts, blockedUids) {
                posts.filter { it.kind == PostKind.SHORT.name && it.authorId !in blockedUids }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeTab == 2) {
                    if (shorts.isEmpty()) {
                        item {
                            ArcadeEmptyCard(
                                icon = Icons.Default.Movie,
                                title = "No Shorts Yet",
                                subtitle = "Create a Short from the Home feed to showcase vertical arcade clips!"
                            )
                        }
                    } else {
                        items(shorts, key = { it.id }) { shortPost ->
                            SocialPostCard(
                                post = shortPost,
                                currentUserId = profile.userId,
                                onLike = { viewModel.toggleLikePost(shortPost) },
                                onComment = { viewModel.openPostComments(shortPost.id) },
                                onShare = {
                                    viewModel.incrementShare(shortPost.id)
                                    shareTextExternally(context, "Mobilive Short", shortPost.caption)
                                },
                                onDelete = { viewModel.deletePost(shortPost.id) },
                                onReport = { reportTarget = shortPost.id to shortPost.authorName },
                                onBlock = { viewModel.blockUser(shortPost.authorId, shortPost.authorName) }
                            )
                        }
                    }
                } else if (filteredStreams.isEmpty()) {
                    item {
                        ArcadeEmptyCard(
                            icon = if (activeTab == 0) Icons.Default.LiveTv else Icons.Default.Replay,
                            title = if (activeTab == 0) "No Live Streams Right Now" else "No Stream Replays Yet",
                            subtitle = if (activeTab == 0) "Tap Go Live to launch a broadcast with real-time Stream Chat & Gift Coins!" else "End any Live Stream to archive it here as a Stream Replay.",
                            actionLabel = if (activeTab == 0) "Go Live Now" else null,
                            onAction = if (activeTab == 0) ({ showGoLiveDialog = true }) else null
                        )
                    }
                } else {
                    items(filteredStreams, key = { it.id }) { stream ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openStreamRoom(stream.id) },
                            color = ObsidianSurface,
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        ProfileFaceAvatar(avatarId = stream.hostAvatarId, size = 40.dp, showLiveRing = stream.status == StreamStatus.LIVE.name)
                                        Column {
                                            Text(stream.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                            Text("${stream.hostName} • ${stream.gameTitle}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                    }
                                    ArcadePillBadge(
                                        text = stream.status,
                                        containerColor = if (stream.status == StreamStatus.LIVE.name) LiveCrimsonContainer else NeonCyanContainer,
                                        contentColor = if (stream.status == StreamStatus.LIVE.name) LiveCrimson else NeonCyan
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (!profile.hideViewerCounts) {
                                        Text("${stream.viewerCount} viewers", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    }
                                    Text("${stream.likesCount} likes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    Text("${stream.giftCoinsTotal} gift coins", style = MaterialTheme.typography.labelMedium, color = CoinGold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGoLiveDialog) {
        GoLiveDialog(
            onDismiss = { showGoLiveDialog = false },
            onStartBroadcast = { title, game, lowLatency ->
                viewModel.goLive(title, game, lowLatency)
                showGoLiveDialog = false
            }
        )
    }

    reportTarget?.let { (targetId, targetName) ->
        ReportDialog(
            targetName = targetName,
            onDismiss = { reportTarget = null },
            onSubmit = { reason ->
                viewModel.submitReport(targetId, ReportTargetType.STREAM, reason)
                reportTarget = null
            }
        )
    }
}

@Composable
private fun StreamWatchRoom(
    stream: LiveStream,
    messages: List<StreamChatMessage>,
    profile: UserProfile,
    onBack: () -> Unit,
    onLike: () -> Unit,
    onSendChat: (String, Long) -> Unit,
    onEndToReplay: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    var chatInput by remember { mutableStateOf("") }
    var replayProgress by remember { mutableFloatStateOf(0.35f) }
    val isHost = stream.hostId == profile.userId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianNight)
    ) {
        // Stream Video Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.linearGradient(listOf(ObsidianSurfaceHighest, ArcadeVioletContainer, ObsidianSurface))
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("stream_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArcadePillBadge(
                        text = stream.status,
                        containerColor = if (stream.status == StreamStatus.LIVE.name) LiveCrimson else NeonCyan,
                        contentColor = ObsidianNight
                    )
                    if (!profile.hideViewerCounts) {
                        ArcadePillBadge("${stream.viewerCount} watching", ObsidianSurface.copy(alpha = 0.85f), TextPrimary)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stream.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("${stream.hostName} playing ${stream.gameTitle}", style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
                if (stream.status == StreamStatus.REPLAY.name) {
                    Slider(
                        value = replayProgress,
                        onValueChange = { replayProgress = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Action Bar: Like, Gift Coins, Share, Host End/Delete or Report/Block
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onLike, modifier = Modifier.testTag("stream_like_button")) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = LiveCrimson, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${stream.likesCount}")
            }
            FilledTonalButton(
                onClick = { onSendChat("Sent 25 Gift Coins!", 25L) },
                modifier = Modifier.testTag("stream_gift_button")
            ) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = CoinGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Gift 25")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share stream", tint = TextPrimary)
            }
            Spacer(Modifier.weight(1f))
            if (isHost) {
                if (stream.status == StreamStatus.LIVE.name) {
                    Button(
                        onClick = onEndToReplay,
                        colors = ButtonDefaults.buttonColors(containerColor = ArcadeViolet)
                    ) { Text("End & Save") }
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete replay", tint = LiveCrimson)
                    }
                }
            } else {
                IconButton(onClick = onReport) { Icon(Icons.Default.Flag, contentDescription = "Report", tint = TextSecondary) }
                IconButton(onClick = onBlock) { Icon(Icons.Default.Block, contentDescription = "Block", tint = LiveCrimson) }
            }
        }

        // Stream Chat List
        Text(
            "Stream Chat",
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Welcome to Stream Chat! Say hello or send Gift Coins.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val isGift = msg.kind == StreamMsgKind.GIFT.name
                    Surface(
                        color = if (isGift) CoinGoldContainer else ObsidianSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isGift) CoinGold else ObsidianBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(msg.authorName, style = MaterialTheme.typography.labelLarge, color = if (isGift) CoinGold else NeonCyan)
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
            }
        }

        // Chat Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                placeholder = { Text("Send message to stream chat...") },
                modifier = Modifier.weight(1f).testTag("stream_chat_input"),
                singleLine = true
            )
            IconButton(
                onClick = {
                    if (chatInput.isNotBlank()) {
                        onSendChat(chatInput, 0L)
                        chatInput = ""
                    }
                },
                modifier = Modifier.testTag("stream_chat_send")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = NeonCyan)
            }
        }
    }
}

@Composable
private fun GoLiveDialog(
    onDismiss: () -> Unit,
    onStartBroadcast: (String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("Ranked Arcade & LAN Stream") }
    var gameTitle by remember { mutableStateOf("Minecraft") }
    var lowLatency by remember { mutableStateOf(true) }
    var facecamEnabled by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        facecamEnabled = granted
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go Live Setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Stream Title") },
                    modifier = Modifier.fillMaxWidth().testTag("go_live_title_input")
                )
                OutlinedTextField(
                    value = gameTitle,
                    onValueChange = { gameTitle = it },
                    label = { Text("Game") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Low-Latency Mode", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = lowLatency, onCheckedChange = { lowLatency = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Facecam Overlay", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = facecamEnabled,
                        onCheckedChange = { want ->
                            if (want) cameraPermLauncher.launch(Manifest.permission.CAMERA)
                            else facecamEnabled = false
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onStartBroadcast(title, gameTitle, lowLatency) },
                modifier = Modifier.testTag("go_live_confirm_button"),
                colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
            ) { Text("Start Live Stream") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
