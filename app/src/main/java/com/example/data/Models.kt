package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ProfileVisibility(val label: String) {
    PUBLIC("Public"),
    FRIENDS_ONLY("Friends Only"),
    PRIVATE("Private")
}

enum class StreamStatus { LIVE, REPLAY }
enum class StreamMsgKind { CHAT, GIFT, SYSTEM }
enum class PostKind { POST, STORY, SHORT }
enum class MediaType { NONE, IMAGE, VIDEO }
enum class GalleryCategory { VIDEO, SCREENSHOT, HIGHLIGHT }
enum class FriendReqStatus { PENDING, ACCEPTED, DECLINED }
enum class NotifKind { FRIEND, GIFT, LOBBY, REWARD, SYSTEM }
enum class ReportTargetType { USER, STREAM, POST, MESSAGE }

inline fun <reified T : Enum<T>> safeEnum(raw: String?, fallback: T): T {
    if (raw.isNullOrBlank()) return fallback
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val gamerTag: String = "",
    val avatarId: String = "avatar_cyber",
    val avatarUri: String? = null,
    val bio: String? = null,
    val level: Long = 1L,
    val xp: Long = 0L,
    val coins: Long = 250L,
    val dailyStreak: Long = 0L,
    val lastClaimEpochDay: Long = 0L,
    val activeBuff: String = "NONE",
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val profileVisibility: String = ProfileVisibility.PUBLIC.name,
    val hideViewerCounts: Boolean = false,
    val allowFriendRequests: Boolean = true,
    val allowPartyInvites: Boolean = true,
    val viewsClearedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveStream(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val hostAvatarId: String = "avatar_cyber",
    val title: String = "",
    val gameTitle: String = "",
    val status: String = StreamStatus.LIVE.name,
    val viewerCount: Long = 1L,
    val likesCount: Long = 0L,
    val giftCoinsTotal: Long = 0L,
    val durationMinutes: Long = 1L,
    val lowLatency: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StreamChatMessage(
    @DocumentId val id: String = "",
    val streamId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val kind: String = StreamMsgKind.CHAT.name,
    val giftCoins: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MultiplayerLobby(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val worldName: String = "",
    val gameTitle: String = "Minecraft",
    val gameMode: String = "Survival",
    val roomCode: String = "",
    val lanPort: Long = 19132L,
    val maxPlayers: Long = 8L,
    val currentPlayers: Long = 1L,
    val visibility: String = "PUBLIC",
    val lanEnabled: Boolean = true,
    val memberUids: List<String> = emptyList(),
    val playerNames: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PartyRoom(
    @DocumentId val id: String = "",
    val leaderId: String = "",
    val leaderName: String = "",
    val partyName: String = "",
    val openParty: Boolean = true,
    val voiceEnabled: Boolean = true,
    val memberUids: List<String> = emptyList(),
    val mutedUids: List<String> = emptyList(),
    val memberNames: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Community(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val name: String = "",
    val gameTag: String = "",
    val description: String = "",
    val active: Boolean = true,
    val memberUids: List<String> = emptyList(),
    val memberCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Tournament(
    @DocumentId val id: String = "",
    val organizerId: String = "",
    val title: String = "",
    val gameTitle: String = "",
    val prizeCoins: Long = 1000L,
    val bracketSize: Long = 16L,
    val active: Boolean = true,
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SocialPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarId: String = "avatar_cyber",
    val kind: String = PostKind.POST.name,
    val caption: String = "",
    val gameTag: String = "",
    val mediaType: String = MediaType.NONE.name,
    val mediaLabel: String? = null,
    val likesCount: Long = 0L,
    val likedByUids: List<String> = emptyList(),
    val commentsCount: Long = 0L,
    val sharesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PostComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendRequest(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val toUserId: String = "",
    val toUserName: String = "",
    val status: String = FriendReqStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val title: String = "",
    val groupChat: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val memberNames: List<String> = emptyList(),
    val lastMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GalleryMedia(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = GalleryCategory.SCREENSHOT.name,
    val gameTag: String = "",
    val mediaUri: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fromUserId: String = "",
    val title: String = "",
    val body: String = "",
    val kind: String = NotifKind.SYSTEM.name,
    val unread: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val blockedUserId: String = "",
    val blockedUserName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserAchievement(
    @DocumentId val id: String = "",
    val userId: String = "",
    val badgeKey: String = "",
    val title: String = "",
    val rewardClaimed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class ArcadeBuff(
    val key: String,
    val title: String,
    val effect: String,
    val costCoins: Long
)

data class BadgeTemplate(
    val key: String,
    val title: String,
    val description: String,
    val rewardCoins: Long,
    val xpReward: Long
)

object ArcadeCatalog {
    val avatarFaces = listOf(
        "avatar_cyber" to "Cyber Visor",
        "avatar_voxel" to "Voxel Knight",
        "avatar_neon_ace" to "Neon Ace",
        "avatar_pixel_fox" to "Pixel Fox",
        "avatar_titan_bot" to "Titan Bot"
    )

    val buffs = listOf(
        ArcadeBuff("NONE", "No Active Buff", "Activate a buff to supercharge XP or stream hype", 0L),
        ArcadeBuff("2X_XP_BOOST", "2x XP Overdrive", "+100% XP earned on streams, posts & lobbies", 40L),
        ArcadeBuff("STREAM_HYPE", "Stream Hype Aura", "Boosts live lobby & stream discovery", 60L),
        ArcadeBuff("LAN_TURBO", "LAN Relay Boost", "Priority party voice & LAN world slot", 50L)
    )

    val badges = listOf(
        BadgeTemplate("FIRST_LIVE", "First Broadcast", "Start your first Live Stream or Replay", 150L, 120L),
        BadgeTemplate("LAN_ARCHITECT", "LAN World Host", "Host a Multiplayer / Minecraft LAN World", 200L, 150L),
        BadgeTemplate("SQUAD_VOICE", "Squad Leader", "Create or join a Voice Party room", 120L, 100L),
        BadgeTemplate("CLIP_CREATOR", "Shorts & Highlights", "Share a Post, Story, Short, or Gallery clip", 130L, 110L),
        BadgeTemplate("DAILY_STREAK_3", "Arcade Regular", "Claim Daily Coin Rewards", 180L, 140L)
    )

    val gameTitles = listOf(
        "Minecraft",
        "Cyber Strikers",
        "Pixel Kart Arena",
        "Voxel Legends",
        "Neon Battle Royale"
    )
}
