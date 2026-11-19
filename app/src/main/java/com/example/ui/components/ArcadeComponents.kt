package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.*
import com.example.ui.theme.*

@Composable
fun ProfileFaceAvatar(
    avatarId: String,
    avatarUri: String? = null,
    size: Dp = 44.dp,
    showLiveRing: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val ringColor = if (showLiveRing) LiveCrimson else NeonCyan
    val boxModifier = Modifier
        .size(size)
        .clip(CircleShape)
        .border(
            width = if (showLiveRing) 2.5.dp else 1.5.dp,
            brush = Brush.linearGradient(listOf(ringColor, ArcadeViolet)),
            shape = CircleShape
        )
        .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }

    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
        when {
            !avatarUri.isNullOrBlank() -> {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "Profile avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            avatarId == "avatar_cyber" -> {
                Image(
                    painter = painterResource(id = R.drawable.img_avatar_cyber),
                    contentDescription = "Cyber Visor profile face",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            avatarId == "avatar_voxel" -> {
                Image(
                    painter = painterResource(id = R.drawable.img_avatar_voxel),
                    contentDescription = "Voxel Knight profile face",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                val (bgTop, bgBot, accent) = when (avatarId) {
                    "avatar_neon_ace" -> Triple(Color(0xFF132A4A), Color(0xFF0A1426), NeonCyan)
                    "avatar_pixel_fox" -> Triple(Color(0xFF3E1854), Color(0xFF1A0D2B), ArcadeViolet)
                    else -> Triple(Color(0xFF0E3B2E), Color(0xFF081B18), EmeraldPulse)
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(brush = Brush.verticalGradient(listOf(bgTop, bgBot)))
                    val w = this.size.width
                    val h = this.size.height
                    // Stylized visor + gamer face eyes
                    drawCircle(color = accent.copy(alpha = 0.25f), radius = w * 0.36f, center = Offset(w / 2f, h * 0.52f))
                    drawCircle(color = accent, radius = w * 0.08f, center = Offset(w * 0.37f, h * 0.46f))
                    drawCircle(color = accent, radius = w * 0.08f, center = Offset(w * 0.63f, h * 0.46f))
                    drawLine(
                        color = accent,
                        start = Offset(w * 0.36f, h * 0.66f),
                        end = Offset(w * 0.64f, h * 0.66f),
                        strokeWidth = w * 0.06f
                    )
                }
            }
        }
    }
}

@Composable
fun ArcadePillBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
fun ArcadeEmptyCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ObsidianSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(NeonCyanContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(26.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SocialPostCard(
    post: SocialPost,
    currentUserId: String,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val liked = currentUserId in post.likedByUids
    val isMine = post.authorId == currentUserId

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ObsidianSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ProfileFaceAvatar(avatarId = post.authorAvatarId, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.authorName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArcadePillBadge(
                            text = post.kind,
                            containerColor = if (post.kind == PostKind.SHORT.name) ArcadeVioletContainer else NeonCyanContainer,
                            contentColor = if (post.kind == PostKind.SHORT.name) ArcadeViolet else NeonCyan
                        )
                        Text(post.gameTag, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("post_more_button_${post.id}")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = TextSecondary)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (isMine) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = LiveCrimson) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Report User / Post") },
                                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onReport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block ${post.authorName}") },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = LiveCrimson) },
                                onClick = {
                                    menuExpanded = false
                                    onBlock()
                                }
                            )
                        }
                    }
                }
            }

            Text(post.caption, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)

            if (post.mediaType != MediaType.NONE.name && !post.mediaLabel.isNullOrBlank()) {
                Surface(
                    color = ObsidianSurfaceElevated,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            if (post.mediaType == MediaType.VIDEO.name) Icons.Default.PlayCircleFilled else Icons.Default.Image,
                            contentDescription = null,
                            tint = NeonCyan
                        )
                        Text(
                            post.mediaLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onLike, modifier = Modifier.testTag("like_post_${post.id}")) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (liked) LiveCrimson else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${post.likesCount}", color = if (liked) LiveCrimson else TextSecondary)
                }
                TextButton(onClick = onComment, modifier = Modifier.testTag("comment_post_${post.id}")) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${post.commentsCount}", color = TextSecondary)
                }
                TextButton(onClick = onShare, modifier = Modifier.testTag("share_post_${post.id}")) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${post.sharesCount}", color = TextSecondary)
                }
            }
        }
    }
}

fun shareTextExternally(context: Context, title: String, body: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

@Composable
fun ReportDialog(
    targetName: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("Spam or toxic behavior") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $targetName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select or enter a moderation reason:", style = MaterialTheme.typography.bodyMedium)
                listOf("Spam or toxic behavior", "Harassment or abuse", "Cheating / exploit", "Inappropriate content").forEach { preset ->
                    FilterChip(
                        selected = reason == preset,
                        onClick = { reason = preset },
                        label = { Text(preset) }
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Details") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(reason) }) { Text("Submit Report") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PostCommentsDialog(
    post: SocialPost,
    comments: List<PostComment>,
    onDismiss: () -> Unit,
    onSendComment: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments (${post.commentsCount})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (comments.isEmpty()) {
                    Text("No comments yet. Start the conversation!", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 220.dp)) {
                        comments.forEach { comment ->
                            Surface(color = ObsidianSurfaceElevated, shape = RoundedCornerShape(10.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                    Text(comment.authorName, style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                                    Text(comment.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Write a comment") },
                    modifier = Modifier.fillMaxWidth().testTag("comment_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        onSendComment(input)
                        input = ""
                    }
                }
            ) { Text("Post") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
