package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.ArcadeViewModel
import com.example.ui.components.ArcadePillBadge
import com.example.ui.components.ProfileFaceAvatar
import com.example.ui.theme.*

@Composable
fun ProfileRewardsScreen(
    viewModel: ArcadeViewModel,
    profile: UserProfile,
    streams: List<LiveStream>,
    posts: List<SocialPost>,
    achievements: List<UserAchievement>,
    onOpenSettings: () -> Unit
) {
    var showEditProfileDialog by remember { mutableStateOf(false) }

    val myStreamsCount = remember(streams, profile.userId) { streams.count { it.hostId == profile.userId } }
    val myTotalLikes = remember(streams, posts, profile.userId) {
        streams.filter { it.hostId == profile.userId }.sumOf { it.likesCount } +
            posts.filter { it.authorId == profile.userId }.sumOf { it.likesCount }
    }
    val myGiftCoinsReceived = remember(streams, profile.userId) {
        streams.filter { it.hostId == profile.userId }.sumOf { it.giftCoinsTotal }
    }

    val xpInCurrentLevel = (profile.xp % 250L).toFloat()
    val levelProgress = (xpInCurrentLevel / 250f).coerceIn(0f, 1f)
    val todayEpoch = remember { System.currentTimeMillis() / 86_400_000L }
    val canClaimDaily = profile.lastClaimEpochDay < todayEpoch

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianNight),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Header Card
        item {
            Surface(
                color = ObsidianSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileFaceAvatar(
                            avatarId = profile.avatarId,
                            avatarUri = profile.avatarUri,
                            size = 68.dp,
                            onClick = { showEditProfileDialog = true }
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                            Text("@${profile.gamerTag}", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                            Spacer(Modifier.height(4.dp))
                            Text(profile.bio ?: "Mobilive Arcade gamer", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("profile_settings_button")) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArcadePillBadge("LV ${profile.level}", NeonCyanContainer, NeonCyan)
                        ArcadePillBadge("${profile.coins} Coins", CoinGoldContainer, CoinGold, Icons.Default.MonetizationOn)
                        ArcadePillBadge("${profile.dailyStreak}d Streak", EmeraldContainer, EmeraldPulse, Icons.Default.LocalFireDepartment)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Level Progress (${profile.xp} XP)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text("${xpInCurrentLevel.toInt()}/250 XP", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
                        }
                        LinearProgressIndicator(
                            progress = { levelProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = NeonCyan,
                            trackColor = ObsidianSurfaceHighest
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.weight(1f).testTag("edit_profile_face_button")
                        ) {
                            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Profile Face")
                        }
                        Button(
                            onClick = { viewModel.levelUpBoostPractice() },
                            modifier = Modifier.weight(1f).testTag("level_up_quest_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ArcadeViolet)
                        ) {
                            Icon(Icons.Default.Upgrade, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Level Up +XP")
                        }
                    }
                }
            }
        }

        // Daily Gift Coins & Buff Shop
        item {
            Surface(
                color = ObsidianSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, CoinGold.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gift Coins & Daily Claim", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Active Buff: ${profile.activeBuff}", style = MaterialTheme.typography.bodySmall, color = EmeraldPulse)
                        }
                        Button(
                            onClick = { viewModel.claimDailyCoins() },
                            enabled = canClaimDaily,
                            colors = ButtonDefaults.buttonColors(containerColor = CoinGold, contentColor = ObsidianNight)
                        ) {
                            Text(if (canClaimDaily) "Claim Day" else "Claimed")
                        }
                    }

                    Text("Buff Rewards", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ArcadeCatalog.buffs.filter { it.key != "NONE" }) { buff ->
                            val active = profile.activeBuff == buff.key
                            Surface(
                                color = if (active) EmeraldContainer else ObsidianSurfaceElevated,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, if (active) EmeraldPulse else ObsidianBorder),
                                modifier = Modifier.width(196.dp).clickable {
                                    if (!active) viewModel.activateBuff(buff)
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(buff.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text(buff.effect, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text(
                                        if (active) "ACTIVE" else "${buff.costCoins} Coins",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (active) EmeraldPulse else CoinGold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Creator & Gamer Analytics (Custom Canvas Bar Telemetry)
        item {
            Surface(
                color = ObsidianSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Arcade Analytics", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        AnalyticStat("Streams", "$myStreamsCount")
                        AnalyticStat("Total Likes", "$myTotalLikes")
                        AnalyticStat("Gifts Earned", "$myGiftCoinsReceived")
                        AnalyticStat("Followers", "${profile.followersCount}")
                    }
                    val bars = listOf(
                        0.35f + (myStreamsCount.coerceAtMost(5) * 0.12f),
                        0.42f,
                        0.58f,
                        (levelProgress.coerceAtLeast(0.25f)),
                        0.72f,
                        (0.3f + (myTotalLikes.coerceAtMost(10) * 0.06f)),
                        0.85f
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                    ) {
                        val barCount = bars.size
                        val slot = size.width / barCount
                        val barWidth = slot * 0.55f
                        bars.forEachIndexed { idx, ratio ->
                            val h = size.height * ratio.coerceIn(0.15f, 1f)
                            drawRoundRect(
                                color = if (idx == barCount - 1) NeonCyan else ArcadeViolet,
                                topLeft = Offset(idx * slot + (slot - barWidth) / 2f, size.height - h),
                                size = Size(barWidth, h),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }
                    }
                }
            }
        }

        // Achievements & Badges
        item {
            Text("Achievements & Badges", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        items(ArcadeCatalog.badges, key = { it.key }) { badge ->
            val unlocked = achievements.firstOrNull { it.badgeKey == badge.key }
            val claimed = unlocked?.rewardClaimed == true
            Surface(
                color = ObsidianSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, if (claimed) EmeraldPulse.copy(alpha = 0.6f) else ObsidianBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (claimed) Icons.Default.Verified else Icons.Default.MilitaryTech,
                        contentDescription = null,
                        tint = if (claimed) EmeraldPulse else CoinGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(badge.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(badge.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("+${badge.rewardCoins} Coins • +${badge.xpReward} XP", style = MaterialTheme.typography.labelSmall, color = CoinGold)
                    }
                    Button(
                        onClick = { viewModel.claimAchievement(badge) },
                        enabled = !claimed
                    ) {
                        Text(if (claimed) "Claimed" else "Claim")
                    }
                }
            }
        }
    }

    if (showEditProfileDialog) {
        EditProfileFaceDialog(
            profile = profile,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, tag, avatarId, avatarUri, bio ->
                viewModel.updateProfile(name, tag, avatarId, avatarUri, bio)
                showEditProfileDialog = false
            }
        )
    }
}

@Composable
private fun AnalyticStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = NeonCyan)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun EditProfileFaceDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?, String) -> Unit
) {
    var displayName by remember { mutableStateOf(profile.displayName) }
    var gamerTag by remember { mutableStateOf(profile.gamerTag) }
    var bio by remember { mutableStateOf(profile.bio ?: "") }
    var selectedAvatarId by remember { mutableStateOf(profile.avatarId) }
    var selectedAvatarUri by remember { mutableStateOf(profile.avatarUri) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedAvatarUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Profile Face") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ArcadeCatalog.avatarFaces) { (id, label) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedAvatarId = id
                                selectedAvatarUri = null
                            }
                        ) {
                            ProfileFaceAvatar(
                                avatarId = id,
                                size = 50.dp,
                                showLiveRing = selectedAvatarId == id && selectedAvatarUri == null
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pick Custom Photo Face")
                }
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display Name") })
                OutlinedTextField(value = gamerTag, onValueChange = { gamerTag = it }, label = { Text("GamerTag") })
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(displayName, gamerTag, selectedAvatarId, selectedAvatarUri, bio) }) {
                Text("Save Profile")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
