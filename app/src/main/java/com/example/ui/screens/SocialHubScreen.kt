package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.ArcadeViewModel
import com.example.ui.components.ArcadeEmptyCard
import com.example.ui.components.ArcadePillBadge
import com.example.ui.components.ProfileFaceAvatar
import com.example.ui.theme.*

@Composable
fun SocialHubScreen(
    viewModel: ArcadeViewModel,
    profile: UserProfile,
    players: List<UserProfile>,
    incomingRequests: List<FriendRequest>,
    outgoingRequests: List<FriendRequest>,
    chats: List<ChatThread>,
    communities: List<Community>,
    gallery: List<GalleryMedia>,
    blockedUids: Set<String>
) {
    val selectedChatId by viewModel.selectedChatId.collectAsState()
    val chatMessagesState by viewModel.chatMessagesState.collectAsState()
    val followedUids by viewModel.followedUserIds.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0=Friends, 1=Messages/Group Chat, 2=Communities, 3=Gallery/Upload
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreateCommunityDialog by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }

    val activeChat = remember(selectedChatId, chats) { chats.firstOrNull { it.id == selectedChatId } }

    if (activeChat != null) {
        BackHandler { viewModel.openChatThread(null) }
        ChatRoomView(
            chat = activeChat,
            messages = (chatMessagesState as? UiState.Success)?.data ?: emptyList(),
            currentUserId = profile.userId,
            onBack = { viewModel.openChatThread(null) },
            onSend = { text -> viewModel.sendChatMessage(activeChat, text) }
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
                    Text("Social & Media", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Friends, group chats, communities & media gallery", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                if (activeTab == 1) {
                    Button(onClick = { showCreateGroupDialog = true }) { Text("+ Group") }
                } else if (activeTab == 2) {
                    Button(onClick = { showCreateCommunityDialog = true }) { Text("+ Hub") }
                } else if (activeTab == 3) {
                    Button(onClick = { showUploadDialog = true }, modifier = Modifier.testTag("upload_media_button")) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Upload")
                    }
                }
            }

            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = ObsidianSurface,
                contentColor = NeonCyan,
                edgePadding = 16.dp
            ) {
                listOf("Friends", "Messages", "Communities", "Gallery").forEachIndexed { idx, label ->
                    Tab(selected = activeTab == idx, onClick = { activeTab = idx }, text = { Text(label) })
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        val pendingIncoming = incomingRequests.filter { it.status == FriendReqStatus.PENDING.name }
                        val acceptedFriends = (incomingRequests + outgoingRequests).filter { it.status == FriendReqStatus.ACCEPTED.name }
                        val friendUids = acceptedFriends.flatMap { listOf(it.fromUserId, it.toUserId) }.toSet() - profile.userId
                        val otherPlayers = players.filter { it.userId != profile.userId && it.userId !in blockedUids }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ArcadePillBadge("Followers: ${profile.followersCount}", NeonCyanContainer, NeonCyan)
                                ArcadePillBadge("Following: ${profile.followingCount}", ArcadeVioletContainer, ArcadeViolet)
                                ArcadePillBadge("Friends: ${friendUids.size}", EmeraldContainer, EmeraldPulse)
                            }
                        }

                        if (pendingIncoming.isNotEmpty()) {
                            item {
                                Text("Friend Requests (${pendingIncoming.size})", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                            }
                            items(pendingIncoming, key = { it.id }) { req ->
                                Surface(
                                    color = ObsidianSurface,
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${req.fromUserName} wants to be friends", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(onClick = { viewModel.respondFriendRequest(req, true) }) { Text("Accept") }
                                            TextButton(onClick = { viewModel.respondFriendRequest(req, false) }) { Text("Decline") }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("Discover Arcade Players", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }

                        if (otherPlayers.isEmpty()) {
                            item {
                                ArcadeEmptyCard(
                                    icon = Icons.Default.People,
                                    title = "No Other Players Online Yet",
                                    subtitle = "Invite friends to sign in with Google on Mobilive Arcade to add friends, follow, and start group chats!"
                                )
                            }
                        } else {
                            items(otherPlayers, key = { it.userId }) { player ->
                                val isFriend = player.userId in friendUids
                                val isFollowing = player.userId in followedUids
                                val reqSent = outgoingRequests.any { it.toUserId == player.userId && it.status == FriendReqStatus.PENDING.name }
                                Surface(
                                    color = ObsidianSurface,
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ProfileFaceAvatar(avatarId = player.avatarId, avatarUri = player.avatarUri, size = 44.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(player.displayName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                            Text("@${player.gamerTag} • LV ${player.level}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilledTonalButton(onClick = { viewModel.toggleFollow(player.userId) }) {
                                                Text(if (isFollowing) "Following" else "Follow")
                                            }
                                            if (isFriend) {
                                                IconButton(onClick = {
                                                    viewModel.createChat(player.displayName, false, listOf(player))
                                                    activeTab = 1
                                                }) {
                                                    Icon(Icons.Default.Chat, contentDescription = "Message", tint = NeonCyan)
                                                }
                                            } else if (player.allowFriendRequests) {
                                                Button(
                                                    onClick = { viewModel.sendFriendRequest(player) },
                                                    enabled = !reqSent
                                                ) {
                                                    Text(if (reqSent) "Sent" else "Add")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (chats.isEmpty()) {
                            item {
                                ArcadeEmptyCard(
                                    icon = Icons.Default.Forum,
                                    title = "No Messages or Group Chats",
                                    subtitle = "Start a Group Chat for your gaming squad or message a friend directly.",
                                    actionLabel = "New Group Chat",
                                    onAction = { showCreateGroupDialog = true }
                                )
                            }
                        } else {
                            items(chats, key = { it.id }) { chat ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openChatThread(chat.id) },
                                    color = ObsidianSurface,
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            if (chat.groupChat) Icons.Default.Groups else Icons.Default.Chat,
                                            contentDescription = null,
                                            tint = NeonCyan
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(chat.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                            Text(chat.lastMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                                        }
                                        ArcadePillBadge("${chat.memberUids.size}", ObsidianSurfaceElevated, TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (communities.isEmpty()) {
                            item {
                                ArcadeEmptyCard(
                                    icon = Icons.Default.Hub,
                                    title = "No Game Communities Yet",
                                    subtitle = "Launch a community hub for Minecraft, Cyber Strikers, or your favorite arcade title.",
                                    actionLabel = "Create Community",
                                    onAction = { showCreateCommunityDialog = true }
                                )
                            }
                        } else {
                            items(communities, key = { it.id }) { comm ->
                                val joined = profile.userId in comm.memberUids
                                Surface(
                                    color = ObsidianSurface,
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(comm.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                                Text("${comm.gameTag} • ${comm.memberCount} members", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                                            }
                                            Button(onClick = { viewModel.toggleJoinCommunity(comm) }) {
                                                Text(if (joined) "Joined" else "Join")
                                            }
                                        }
                                        Text(comm.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        if (gallery.isEmpty()) {
                            item {
                                ArcadeEmptyCard(
                                    icon = Icons.Default.PhotoLibrary,
                                    title = "Your Media Gallery is Empty",
                                    subtitle = "Upload gameplay videos, screenshots, and highlights using the Android Photo Picker.",
                                    actionLabel = "Upload Media",
                                    onAction = { showUploadDialog = true }
                                )
                            }
                        } else {
                            items(gallery, key = { it.id }) { item ->
                                Surface(
                                    color = ObsidianSurface,
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        AsyncImage(
                                            model = item.mediaUri,
                                            contentDescription = item.title,
                                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                ArcadePillBadge(item.category, ArcadeVioletContainer, ArcadeViolet)
                                                Text(item.gameTag, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deleteGalleryMedia(item.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete media", tint = LiveCrimson)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        var title by remember { mutableStateOf("Arcade Squad Chat") }
        val otherPlayers = players.filter { it.userId != profile.userId && it.userId !in blockedUids }
        var selectedIds by remember { mutableStateOf(setOf<String>()) }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create Group Chat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Group Title") })
                    if (otherPlayers.isNotEmpty()) {
                        Text("Select squad members:", style = MaterialTheme.typography.labelMedium)
                        otherPlayers.take(6).forEach { p ->
                            val checked = p.userId in selectedIds
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedIds = if (checked) selectedIds - p.userId else selectedIds + p.userId
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(p.displayName)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val chosen = otherPlayers.filter { it.userId in selectedIds }
                    viewModel.createChat(title, groupChat = true, members = chosen)
                    showCreateGroupDialog = false
                }) { Text("Start Chat") }
            },
            dismissButton = { TextButton(onClick = { showCreateGroupDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCreateCommunityDialog) {
        var name by remember { mutableStateOf("BlockCraft LAN Builders") }
        var gameTag by remember { mutableStateOf("Minecraft") }
        var desc by remember { mutableStateOf("Share LAN worlds, redstone builds & highlights.") }
        AlertDialog(
            onDismissRequest = { showCreateCommunityDialog = false },
            title = { Text("Create Game Community") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Community Name") })
                    OutlinedTextField(value = gameTag, onValueChange = { gameTag = it }, label = { Text("Game Tag") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createCommunity(name, gameTag, desc)
                    showCreateCommunityDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateCommunityDialog = false }) { Text("Cancel") } }
        )
    }

    if (showUploadDialog) {
        UploadGalleryDialog(
            onDismiss = { showUploadDialog = false },
            onUpload = { title, cat, gameTag, uri ->
                viewModel.addGalleryMedia(title, cat, gameTag, uri)
                showUploadDialog = false
            }
        )
    }
}

@Composable
private fun ChatRoomView(
    chat: ChatThread,
    messages: List<ChatMessage>,
    currentUserId: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().background(ObsidianNight)) {
        Surface(color = ObsidianSurface, tonalElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(chat.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("${chat.memberUids.size} members", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderId == currentUserId
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        color = if (isMe) NeonCyanContainer else ObsidianSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isMe) NeonCyan.copy(alpha = 0.4f) else ObsidianBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (!isMe) {
                                Text(msg.senderName, style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                            }
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(ObsidianSurface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type message...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(onClick = {
                if (input.isNotBlank()) {
                    onSend(input)
                    input = ""
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = NeonCyan)
            }
        }
    }
}

@Composable
private fun UploadGalleryDialog(
    onDismiss: () -> Unit,
    onUpload: (String, GalleryCategory, String, String) -> Unit
) {
    var title by remember { mutableStateOf("Epic Boss Highlight") }
    var category by remember { mutableStateOf(GalleryCategory.HIGHLIGHT) }
    var gameTag by remember { mutableStateOf("Minecraft") }
    var selectedUri by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Videos / Screenshots / Highlights") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GalleryCategory.entries.forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.name) })
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = gameTag, onValueChange = { gameTag = it }, label = { Text("Game") })
                Button(
                    onClick = {
                        val type = when (category) {
                            GalleryCategory.VIDEO -> ActivityResultContracts.PickVisualMedia.VideoOnly
                            GalleryCategory.SCREENSHOT -> ActivityResultContracts.PickVisualMedia.ImageOnly
                            GalleryCategory.HIGHLIGHT -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        }
                        picker.launch(PickVisualMediaRequest(type))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (selectedUri != null) "Media Selected" else "Pick from Photo Picker")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    onUpload(title, category, gameTag, uri)
                },
                enabled = selectedUri != null && title.isNotBlank()
            ) { Text("Save to Gallery") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
