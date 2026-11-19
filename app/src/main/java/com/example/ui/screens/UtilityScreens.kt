package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.signOutArcade
import com.example.data.*
import com.example.ui.ArcadeViewModel
import com.example.ui.components.ArcadeEmptyCard
import com.example.ui.components.ArcadePillBadge
import com.example.ui.components.ProfileFaceAvatar
import com.example.ui.theme.*

@Composable
fun SearchScreen(
    players: List<UserProfile>,
    streams: List<LiveStream>,
    lobbies: List<MultiplayerLobby>,
    communities: List<Community>,
    posts: List<SocialPost>,
    blockedUids: Set<String>,
    onBack: () -> Unit,
    onOpenStream: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    val matchedStreams = remember(q, streams, blockedUids) {
        streams.filter { it.hostId !in blockedUids && (q.isEmpty() || it.title.lowercase().contains(q) || it.gameTitle.lowercase().contains(q) || it.hostName.lowercase().contains(q)) }
    }
    val matchedLobbies = remember(q, lobbies, blockedUids) {
        lobbies.filter { it.hostId !in blockedUids && (q.isEmpty() || it.worldName.lowercase().contains(q) || it.gameTitle.lowercase().contains(q)) }
    }
    val matchedPlayers = remember(q, players, blockedUids) {
        players.filter { it.userId !in blockedUids && (q.isEmpty() || it.displayName.lowercase().contains(q) || it.gamerTag.lowercase().contains(q)) }
    }
    val matchedComms = remember(q, communities) {
        communities.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.gameTag.lowercase().contains(q) }
    }
    val matchedPosts = remember(q, posts, blockedUids) {
        posts.filter { it.authorId !in blockedUids && (q.isEmpty() || it.caption.lowercase().contains(q) || it.gameTag.lowercase().contains(q)) }
    }

    Column(modifier = Modifier.fillMaxSize().background(ObsidianNight)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search streams, LAN worlds, players, communities...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.weight(1f).testTag("search_query_input"),
                singleLine = true
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (matchedStreams.isNotEmpty()) {
                item { Text("Streams & Replays", style = MaterialTheme.typography.titleMedium, color = NeonCyan) }
                items(matchedStreams, key = { it.id }) { s ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenStream(s.id) },
                        color = ObsidianSurface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text("${s.hostName} • ${s.gameTitle}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            ArcadePillBadge(s.status, LiveCrimsonContainer, LiveCrimson)
                        }
                    }
                }
            }
            if (matchedLobbies.isNotEmpty()) {
                item { Text("Multiplayer & LAN Worlds", style = MaterialTheme.typography.titleMedium, color = EmeraldPulse) }
                items(matchedLobbies, key = { it.id }) { l ->
                    Surface(color = ObsidianSurface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, ObsidianBorder)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(l.worldName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text("${l.gameTitle} • Room ${l.roomCode}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            ArcadePillBadge("${l.currentPlayers}/${l.maxPlayers}", EmeraldContainer, EmeraldPulse)
                        }
                    }
                }
            }
            if (matchedPlayers.isNotEmpty()) {
                item { Text("Players", style = MaterialTheme.typography.titleMedium, color = ArcadeViolet) }
                items(matchedPlayers, key = { it.userId }) { p ->
                    Surface(color = ObsidianSurface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, ObsidianBorder)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProfileFaceAvatar(avatarId = p.avatarId, avatarUri = p.avatarUri, size = 36.dp)
                            Column {
                                Text(p.displayName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text("@${p.gamerTag} • LV ${p.level}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
            if (matchedComms.isNotEmpty()) {
                item { Text("Communities", style = MaterialTheme.typography.titleMedium, color = CoinGold) }
                items(matchedComms, key = { it.id }) { c ->
                    Surface(color = ObsidianSurface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, ObsidianBorder)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("${c.gameTag} • ${c.memberCount} members", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
            if (matchedPosts.isNotEmpty()) {
                item { Text("Posts & Shorts", style = MaterialTheme.typography.titleMedium, color = TextPrimary) }
                items(matchedPosts.take(8), key = { it.id }) { post ->
                    Surface(color = ObsidianSurface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, ObsidianBorder)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("${post.authorName} • ${post.kind}", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                            Text(post.caption, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    notifications: List<UserNotification>,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(ObsidianNight)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Notifications", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (notifications.isEmpty()) {
                item {
                    ArcadeEmptyCard(
                        icon = Icons.Default.NotificationsNone,
                        title = "All Caught Up",
                        subtitle = "Friend requests, party invites, gift alerts, and reward updates appear here."
                    )
                }
            } else {
                items(notifications, key = { it.id }) { notif ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { if (notif.unread) onMarkRead(notif.id) },
                        color = if (notif.unread) ObsidianSurfaceElevated else ObsidianSurface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, if (notif.unread) NeonCyan else ObsidianBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = if (notif.unread) NeonCyan else TextMuted)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(notif.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(notif.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            if (notif.unread) {
                                ArcadePillBadge("NEW", NeonCyanContainer, NeonCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPrivacyScreen(
    viewModel: ArcadeViewModel,
    profile: UserProfile,
    blockedUsers: List<BlockedUser>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var visibility by remember(profile.profileVisibility) {
        mutableStateOf(safeEnum(profile.profileVisibility, ProfileVisibility.PUBLIC))
    }
    var hideViews by remember(profile.hideViewerCounts) { mutableStateOf(profile.hideViewerCounts) }
    var allowFriends by remember(profile.allowFriendRequests) { mutableStateOf(profile.allowFriendRequests) }
    var allowParty by remember(profile.allowPartyInvites) { mutableStateOf(profile.allowPartyInvites) }
    var autoMuteVoice by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(ObsidianNight)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Settings & Privacy Controls", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Privacy Controls
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Privacy Controls", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                        Text("Profile Visibility", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProfileVisibility.entries.forEach { vis ->
                                FilterChip(
                                    selected = visibility == vis,
                                    onClick = {
                                        visibility = vis
                                        viewModel.updatePrivacy(vis, hideViews, allowFriends, allowParty)
                                    },
                                    label = { Text(vis.label) }
                                )
                            }
                        }

                        SettingSwitchRow(
                            title = "Hide Viewer Counts",
                            subtitle = "Hide live & profile viewer numbers",
                            checked = hideViews,
                            onCheckedChange = {
                                hideViews = it
                                viewModel.updatePrivacy(visibility, it, allowFriends, allowParty)
                            }
                        )
                        SettingSwitchRow(
                            title = "Allow Friend Requests",
                            subtitle = "Let players send you friend requests",
                            checked = allowFriends,
                            onCheckedChange = {
                                allowFriends = it
                                viewModel.updatePrivacy(visibility, hideViews, it, allowParty)
                            }
                        )
                        SettingSwitchRow(
                            title = "Allow Party Invites",
                            subtitle = "Allow squad voice room invitations",
                            checked = allowParty,
                            onCheckedChange = {
                                allowParty = it
                                viewModel.updatePrivacy(visibility, hideViews, allowFriends, it)
                            }
                        )

                        Button(
                            onClick = {
                                hideViews = true
                                viewModel.removeAllPeopleViews()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("remove_all_views_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ArcadeVioletContainer, contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Remove All People Views See")
                        }
                    }
                }
            }

            // More Settings
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("More Settings", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        SettingSwitchRow(
                            title = "Join Voice Parties Muted",
                            subtitle = "Start muted when entering new voice rooms",
                            checked = autoMuteVoice,
                            onCheckedChange = { autoMuteVoice = it }
                        )
                    }
                }
            }

            // Blocked Users & Moderation
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Blocked Users & Moderation", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        if (blockedUsers.isEmpty()) {
                            Text("No blocked users.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        } else {
                            blockedUsers.forEach { blocked ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(blocked.blockedUserName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    TextButton(onClick = { viewModel.unblockUser(blocked.blockedUserId) }) {
                                        Text("Unblock", color = NeonCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sign Out
            item {
                Button(
                    onClick = {
                        signOutArcade(credentialManager = credentialManager, scope = scope)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("sign_out_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimsonContainer, contentColor = LiveCrimson)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
