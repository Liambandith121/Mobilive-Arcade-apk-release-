package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.*
import com.example.ui.ArcadeViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: ArcadeViewModel,
    profile: UserProfile,
    streams: List<LiveStream>,
    posts: List<SocialPost>,
    blockedUids: Set<String>,
    unreadNotifsCount: Int,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateGoLive: () -> Unit,
    onNavigateHostLan: () -> Unit,
    onOpenStream: (String) -> Unit,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
    val postCommentsState by viewModel.postCommentsState.collectAsState()
    val selectedCommentsPostId by viewModel.selectedCommentsPostId.collectAsState()

    var showCreatePostDialog by remember { mutableStateOf(false) }
    var initialPostKind by remember { mutableStateOf(PostKind.POST) }
    var activeStory by remember { mutableStateOf<SocialPost?>(null) }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val visiblePosts = remember(posts, blockedUids) {
        posts.filter { it.authorId !in blockedUids }
    }
    val stories = remember(visiblePosts) { visiblePosts.filter { it.kind == PostKind.STORY.name } }
    val feedItems = remember(visiblePosts) { visiblePosts.filter { it.kind != PostKind.STORY.name } }
    val liveNow = remember(streams, blockedUids) {
        streams.filter { it.status == StreamStatus.LIVE.name && it.hostId !in blockedUids }
    }

    val todayEpoch = remember { System.currentTimeMillis() / 86_400_000L }
    val canClaimDaily = profile.lastClaimEpochDay < todayEpoch

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianNight),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileFaceAvatar(
                    avatarId = profile.avatarId,
                    avatarUri = profile.avatarUri,
                    size = 44.dp,
                    onClick = onOpenProfile
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).clickable(onClick = onOpenProfile)) {
                    Text(
                        profile.displayName.ifBlank { "Arcade Player" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArcadePillBadge("LV ${profile.level}", NeonCyanContainer, NeonCyan)
                        ArcadePillBadge("${profile.coins}", CoinGoldContainer, CoinGold, Icons.Default.MonetizationOn)
                    }
                }
                IconButton(onClick = onOpenSearch, modifier = Modifier.testTag("home_search_button")) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
                }
                IconButton(onClick = onOpenNotifications, modifier = Modifier.testTag("home_notifications_button")) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifsCount > 0) {
                                Badge(containerColor = LiveCrimson) { Text("$unreadNotifsCount") }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = TextPrimary)
                    }
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("home_settings_button")) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                }
            }
        }

        // Featured Hero Banner
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(196.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_arcade_hero),
                    contentDescription = "Mobilive Arcade Stage",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(ObsidianNight.copy(alpha = 0.15f), ObsidianNight.copy(alpha = 0.92f))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArcadePillBadge("MOBILIVE ARCADE", ArcadeVioletContainer, ArcadeViolet, Icons.Default.Bolt)
                        Text(
                            "Stream Live, Host LAN Worlds & Squad Up",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onNavigateGoLive,
                                modifier = Modifier.testTag("hero_go_live_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson, contentColor = TextPrimary)
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Go Live")
                            }
                            OutlinedButton(
                                onClick = onNavigateHostLan,
                                modifier = Modifier.testTag("hero_host_lan_button"),
                                border = BorderStroke(1.dp, NeonCyan)
                            ) {
                                Icon(Icons.Default.Lan, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("LAN World", color = NeonCyan)
                            }
                        }
                    }
                }
            }
        }

        // Daily Coin Claim & Buff Strip
        item {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                color = ObsidianSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Day ${profile.dailyStreak} Streak • Buff: ${profile.activeBuff}",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            if (canClaimDaily) "Claim your daily Gift Coins & XP boost!" else "Daily reward claimed • Keep playing to level up",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Button(
                        onClick = { viewModel.claimDailyCoins() },
                        enabled = canClaimDaily,
                        modifier = Modifier.testTag("home_claim_daily_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CoinGold, contentColor = ObsidianNight)
                    ) {
                        Text(if (canClaimDaily) "Claim Coins" else "Claimed")
                    }
                }
            }
        }

        // Stories Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Stories", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    TextButton(
                        onClick = {
                            initialPostKind = PostKind.STORY
                            showCreatePostDialog = true
                        },
                        modifier = Modifier.testTag("add_story_button")
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Story")
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .width(84.dp)
                                .height(104.dp)
                                .clickable {
                                    initialPostKind = PostKind.STORY
                                    showCreatePostDialog = true
                                },
                            color = ObsidianSurfaceElevated,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Create Story", tint = NeonCyan)
                                Spacer(Modifier.height(4.dp))
                                Text("Your Story", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            }
                        }
                    }
                    items(stories, key = { it.id }) { story ->
                        Surface(
                            modifier = Modifier
                                .width(88.dp)
                                .height(104.dp)
                                .clickable { activeStory = story },
                            color = ObsidianSurface,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.5.dp, ArcadeViolet)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                ProfileFaceAvatar(avatarId = story.authorAvatarId, size = 44.dp, showLiveRing = true)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    story.authorName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Streams Now
        if (liveNow.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Live Now",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(liveNow, key = { it.id }) { stream ->
                            Surface(
                                modifier = Modifier
                                    .width(240.dp)
                                    .clickable { onOpenStream(stream.id) },
                                color = ObsidianSurface,
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, LiveCrimson.copy(alpha = 0.6f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        ArcadePillBadge("LIVE", LiveCrimsonContainer, LiveCrimson, Icons.Default.FiberManualRecord)
                                        if (!profile.hideViewerCounts) {
                                            Text("${stream.viewerCount} watching", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        }
                                    }
                                    Text(stream.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${stream.hostName} • ${stream.gameTitle}", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Feed Header + Create Post / Short buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Posts & Shorts", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            initialPostKind = PostKind.POST
                            showCreatePostDialog = true
                        },
                        modifier = Modifier.testTag("create_post_button")
                    ) {
                        Text("+ Post")
                    }
                    FilledTonalButton(
                        onClick = {
                            initialPostKind = PostKind.SHORT
                            showCreatePostDialog = true
                        },
                        modifier = Modifier.testTag("create_short_button")
                    ) {
                        Text("+ Short")
                    }
                }
            }
        }

        if (feedItems.isEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ArcadeEmptyCard(
                        icon = Icons.Default.DynamicFeed,
                        title = "No Posts or Shorts Yet",
                        subtitle = "Publish a gameplay update, highlight, or vertical Short to kick off the Arcade feed!",
                        actionLabel = "Create First Post",
                        onAction = {
                            initialPostKind = PostKind.POST
                            showCreatePostDialog = true
                        }
                    )
                }
            }
        } else {
            items(feedItems, key = { it.id }) { post ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SocialPostCard(
                        post = post,
                        currentUserId = profile.userId,
                        onLike = { viewModel.toggleLikePost(post) },
                        onComment = { viewModel.openPostComments(post.id) },
                        onShare = {
                            viewModel.incrementShare(post.id)
                            shareTextExternally(context, "${post.authorName} on Mobilive Arcade", "${post.caption} #${post.gameTag}")
                        },
                        onDelete = { viewModel.deletePost(post.id) },
                        onReport = { reportTarget = post.id to post.authorName },
                        onBlock = { viewModel.blockUser(post.authorId, post.authorName) }
                    )
                }
            }
        }
    }

    if (showCreatePostDialog) {
        CreatePostDialog(
            initialKind = initialPostKind,
            onDismiss = { showCreatePostDialog = false },
            onPublish = { kind, caption, gameTag, mediaType, mediaLabel ->
                viewModel.createPost(kind, caption, gameTag, mediaType, mediaLabel)
                showCreatePostDialog = false
            }
        )
    }

    activeStory?.let { story ->
        AlertDialog(
            onDismissRequest = { activeStory = null },
            title = { Text("${story.authorName}'s Story") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArcadePillBadge(story.gameTag, NeonCyanContainer, NeonCyan)
                    Text(story.caption, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                if (story.authorId == profile.userId) {
                    TextButton(onClick = {
                        viewModel.deletePost(story.id)
                        activeStory = null
                    }) { Text("Delete Story", color = LiveCrimson) }
                }
                Button(onClick = { activeStory = null }) { Text("Done") }
            }
        )
    }

    val activePostForComments = remember(selectedCommentsPostId, posts) {
        posts.firstOrNull { it.id == selectedCommentsPostId }
    }
    if (activePostForComments != null) {
        val comments = (postCommentsState as? UiState.Success)?.data ?: emptyList()
        PostCommentsDialog(
            post = activePostForComments,
            comments = comments,
            onDismiss = { viewModel.openPostComments(null) },
            onSendComment = { text -> viewModel.addPostComment(activePostForComments, text) }
        )
    }

    reportTarget?.let { (targetId, targetName) ->
        ReportDialog(
            targetName = targetName,
            onDismiss = { reportTarget = null },
            onSubmit = { reason ->
                viewModel.submitReport(targetId, ReportTargetType.POST, reason)
                reportTarget = null
            }
        )
    }
}

@Composable
fun CreatePostDialog(
    initialKind: PostKind,
    onDismiss: () -> Unit,
    onPublish: (PostKind, String, String, MediaType, String?) -> Unit
) {
    var kind by remember { mutableStateOf(initialKind) }
    var caption by remember { mutableStateOf("") }
    var gameTag by remember { mutableStateOf(ArcadeCatalog.gameTitles.first()) }
    var mediaLabel by remember { mutableStateOf<String?>(null) }
    var mediaType by remember { mutableStateOf(MediaType.NONE) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            mediaType = if (kind == PostKind.SHORT) MediaType.VIDEO else MediaType.IMAGE
            mediaLabel = uri.lastPathSegment ?: uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create ${kind.name.lowercase().replaceFirstChar { it.uppercase() }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PostKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption / Highlight note") },
                    modifier = Modifier.fillMaxWidth().testTag("post_caption_input")
                )
                OutlinedTextField(
                    value = gameTag,
                    onValueChange = { gameTag = it },
                    label = { Text("Game Tag") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PermMedia, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(mediaLabel ?: "Attach Clip or Screenshot")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (caption.isNotBlank()) {
                        onPublish(kind, caption, gameTag, mediaType, mediaLabel)
                    }
                },
                modifier = Modifier.testTag("publish_post_confirm")
            ) { Text("Publish") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
