package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.random.Random

class ArcadeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val tsBehavior = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.logFirestoreErrors(uid: String, operation: OperationType, path: String): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, operation, path)
            }
            throw error
        }

    // --- Profile & Private Account ---

    fun observeMyProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId).snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, tsBehavior) }
                .logFirestoreErrors(uid, OperationType.GET, path)
        )
    }

    fun observePublicPlayers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .whereIn("profileVisibility", listOf(ProfileVisibility.PUBLIC.name, ProfileVisibility.FRIENDS_ONLY.name))
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, tsBehavior)
                        .sortedByDescending { it.level }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun ensureUserProfile(
        displayNameHint: String?,
        emailHint: String?
    ): Result<UserProfile> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val userDoc = db.collection("users").document(uid)
        val privateDoc = userDoc.collection("private").document("account")
        return try {
            val existingSnap = userDoc.get().await()
            val profile = if (existingSnap.exists()) {
                existingSnap.toObject(UserProfile::class.java, tsBehavior)
                    ?: UserProfile(userId = uid)
            } else {
                val safeName = (displayNameHint?.takeIf { it.isNotBlank() } ?: "Arcade Player").take(60)
                val safeTag = safeName.lowercase().replace(Regex("[^a-z0-9_]"), "")
                    .ifBlank { "player" }.take(20) + "_" + uid.take(4)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "gamerTag" to safeTag.take(32),
                    "avatarId" to "avatar_cyber",
                    "bio" to "Ready for live streams, LAN worlds & squad parties!",
                    "level" to 1L,
                    "xp" to 40L,
                    "coins" to 300L,
                    "dailyStreak" to 0L,
                    "lastClaimEpochDay" to 0L,
                    "activeBuff" to "NONE",
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "profileVisibility" to ProfileVisibility.PUBLIC.name,
                    "hideViewerCounts" to false,
                    "allowFriendRequests" to true,
                    "allowPartyInvites" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc.set(payload).await()
                UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = safeName,
                    gamerTag = safeTag.take(32),
                    avatarId = "avatar_cyber",
                    bio = "Ready for live streams, LAN worlds & squad parties!",
                    level = 1L,
                    xp = 40L,
                    coins = 300L
                )
            }
            val privPayload = mapOf(
                "userId" to uid,
                "email" to emailHint?.take(120),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            privateDoc.set(privPayload).await()
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDoc.path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        gamerTag: String,
        avatarId: String,
        avatarUri: String?,
        bio: String
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim().take(60)
            val cleanTag = gamerTag.trim().take(32)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty" }
            require(cleanTag.length >= 2) { "GamerTag must be at least 2 characters" }
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val updateMap = mutableMapOf<String, Any>(
                "displayName" to cleanName,
                "gamerTag" to cleanTag,
                "avatarId" to avatarId.take(40),
                "bio" to bio.trim().take(240),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarUri.isNullOrBlank()) {
                updateMap["avatarUri"] = avatarUri.take(500)
            }
            docRef.update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun grantXpAndCoins(
        profile: UserProfile,
        xpGain: Long,
        coinGain: Long
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val multipliedXp = if (profile.activeBuff == "2X_XP_BOOST") xpGain * 2L else xpGain
            val newTotalXp = (profile.xp + multipliedXp).coerceAtMost(10_000_000L)
            val newLevel = (1L + newTotalXp / 250L).coerceIn(1L, 999L)
            val newCoins = (profile.coins + coinGain).coerceIn(0L, 10_000_000L)
            docRef.update(
                mapOf(
                    "xp" to newTotalXp,
                    "level" to newLevel,
                    "coins" to newCoins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun claimDailyReward(profile: UserProfile, todayEpochDay: Long): Result<Long> {
        var path = "users"
        return try {
            val uid = requireUserId()
            require(profile.lastClaimEpochDay < todayEpochDay) { "Today's streak reward already claimed!" }
            val nextStreak = if (profile.lastClaimEpochDay == todayEpochDay - 1L) {
                (profile.dailyStreak + 1L).coerceAtMost(3650L)
            } else {
                1L
            }
            val bonusCoins = 100L + (nextStreak.coerceAtMost(7L) * 25L)
            val newXp = (profile.xp + 75L).coerceAtMost(10_000_000L)
            val newLevel = (1L + newXp / 250L).coerceIn(1L, 999L)
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            docRef.update(
                mapOf(
                    "coins" to (profile.coins + bonusCoins).coerceAtMost(10_000_000L),
                    "xp" to newXp,
                    "level" to newLevel,
                    "dailyStreak" to nextStreak,
                    "lastClaimEpochDay" to todayEpochDay,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            unlockAchievementInternal(uid, "DAILY_STREAK_3", "Arcade Regular")
            Result.success(bonusCoins)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun activateBuff(profile: UserProfile, buff: ArcadeBuff): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            require(profile.coins >= buff.costCoins) { "Not enough Gift Coins for ${buff.title}" }
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            docRef.update(
                mapOf(
                    "activeBuff" to buff.key,
                    "coins" to (profile.coins - buff.costCoins).coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        visibility: ProfileVisibility,
        hideViewerCounts: Boolean,
        allowFriendRequests: Boolean,
        allowPartyInvites: Boolean
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            docRef.update(
                mapOf(
                    "profileVisibility" to visibility.name,
                    "hideViewerCounts" to hideViewerCounts,
                    "allowFriendRequests" to allowFriendRequests,
                    "allowPartyInvites" to allowPartyInvites,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllPeopleViews(): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            docRef.update(
                mapOf(
                    "hideViewerCounts" to true,
                    "viewsClearedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Live Streams, Chat, Replays, Gifts ---

    fun observeStreams(): Flow<List<LiveStream>> = flow {
        val uid = requireUserId()
        val path = "streams"
        emitAll(
            db.collection("streams")
                .whereIn("status", listOf(StreamStatus.LIVE.name, StreamStatus.REPLAY.name))
                .snapshots()
                .map { snap ->
                    snap.toObjects(LiveStream::class.java, tsBehavior)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeStreamMessages(streamId: String): Flow<List<StreamChatMessage>> = flow {
        val uid = requireUserId()
        val path = "streams/$streamId/messages"
        emitAll(
            db.collection("streams").document(streamId).collection("messages")
                .whereEqualTo("streamId", streamId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StreamChatMessage::class.java, tsBehavior)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun goLive(
        profile: UserProfile,
        title: String,
        gameTitle: String,
        lowLatency: Boolean
    ): Result<String> {
        var path = "streams"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(100)
            require(cleanTitle.isNotEmpty()) { "Stream title is required" }
            val docRef = db.collection("streams").document()
            path = docRef.path
            val payload = mapOf(
                "hostId" to uid,
                "hostName" to profile.displayName.ifBlank { "Streamer" }.take(60),
                "hostAvatarId" to profile.avatarId.take(40),
                "title" to cleanTitle,
                "gameTitle" to gameTitle.ifBlank { "Minecraft" }.take(60),
                "status" to StreamStatus.LIVE.name,
                "viewerCount" to 1L,
                "likesCount" to 0L,
                "giftCoinsTotal" to 0L,
                "durationMinutes" to 1L,
                "lowLatency" to lowLatency,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            unlockAchievementInternal(uid, "FIRST_LIVE", "First Broadcast")
            grantXpAndCoins(profile, 60L, 25L)
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun endStreamToReplay(streamId: String): Result<Unit> {
        var path = "streams/$streamId"
        return try {
            requireUserId()
            val docRef = db.collection("streams").document(streamId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "status" to StreamStatus.REPLAY.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStream(streamId: String): Result<Unit> {
        var path = "streams/$streamId"
        return try {
            requireUserId()
            val docRef = db.collection("streams").document(streamId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun likeStream(streamId: String): Result<Unit> {
        var path = "streams/$streamId"
        return try {
            requireUserId()
            val docRef = db.collection("streams").document(streamId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "likesCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendStreamChat(
        stream: LiveStream,
        profile: UserProfile,
        text: String,
        giftCoins: Long = 0L
    ): Result<Unit> {
        var path = "streams/${stream.id}/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(300)
            require(cleanText.isNotEmpty()) { "Message cannot be empty" }
            if (giftCoins > 0L) {
                require(profile.coins >= giftCoins) { "Not enough Gift Coins" }
            }
            val msgRef = db.collection("streams").document(stream.id).collection("messages").document()
            path = msgRef.path
            val payload = mapOf(
                "streamId" to stream.id,
                "authorId" to uid,
                "authorName" to profile.displayName.ifBlank { "Viewer" }.take(60),
                "text" to cleanText,
                "kind" to if (giftCoins > 0L) StreamMsgKind.GIFT.name else StreamMsgKind.CHAT.name,
                "giftCoins" to giftCoins,
                "createdAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(payload).await()
            if (giftCoins > 0L) {
                db.collection("users").document(uid).update(
                    mapOf(
                        "coins" to (profile.coins - giftCoins).coerceAtLeast(0L),
                        "xp" to (profile.xp + giftCoins).coerceAtMost(10_000_000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (stream.status == StreamStatus.LIVE.name) {
                    db.collection("streams").document(stream.id).update(
                        mapOf(
                            "giftCoinsTotal" to FieldValue.increment(giftCoins),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Multiplayer Lobbies & Minecraft LAN Worlds ---

    fun observeLobbies(): Flow<List<MultiplayerLobby>> = flow {
        val uid = requireUserId()
        val path = "lobbies"
        emitAll(
            db.collection("lobbies")
                .whereIn("visibility", listOf("PUBLIC", "FRIENDS"))
                .snapshots()
                .map { snap ->
                    snap.toObjects(MultiplayerLobby::class.java, tsBehavior)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun hostLanWorld(
        profile: UserProfile,
        worldName: String,
        gameTitle: String,
        gameMode: String,
        lanPort: Long,
        maxPlayers: Long,
        visibility: String
    ): Result<String> {
        var path = "lobbies"
        return try {
            val uid = requireUserId()
            val cleanWorld = worldName.trim().take(80)
            require(cleanWorld.isNotEmpty()) { "World name is required" }
            val roomCode = "MC-${Random.nextInt(1000, 9999)}"
            val docRef = db.collection("lobbies").document()
            path = docRef.path
            val payload = mapOf(
                "hostId" to uid,
                "hostName" to profile.displayName.ifBlank { "Host" }.take(60),
                "worldName" to cleanWorld,
                "gameTitle" to gameTitle.ifBlank { "Minecraft" }.take(60),
                "gameMode" to gameMode.ifBlank { "Survival" }.take(40),
                "roomCode" to roomCode,
                "lanPort" to lanPort.coerceIn(1024L, 65535L),
                "maxPlayers" to maxPlayers.coerceIn(2L, 16L),
                "currentPlayers" to 1L,
                "visibility" to if (visibility == "FRIENDS") "FRIENDS" else "PUBLIC",
                "lanEnabled" to true,
                "memberUids" to listOf(uid),
                "playerNames" to listOf(profile.displayName.ifBlank { "Host" }.take(60)),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            unlockAchievementInternal(uid, "LAN_ARCHITECT", "LAN World Host")
            grantXpAndCoins(profile, 50L, 20L)
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleJoinLobby(lobby: MultiplayerLobby, profile: UserProfile): Result<Boolean> {
        var path = "lobbies/${lobby.id}"
        return try {
            val uid = requireUserId()
            val isJoined = uid in lobby.memberUids
            val nextMembers = if (isJoined) {
                lobby.memberUids.filterNot { it == uid }
            } else {
                require(lobby.memberUids.size < lobby.maxPlayers) { "Lobby is full" }
                (lobby.memberUids + uid).distinct().take(16)
            }
            val nextNames = if (isJoined) {
                lobby.playerNames.filterNot { it == profile.displayName }
            } else {
                (lobby.playerNames + profile.displayName.ifBlank { "Player" }).distinct().take(16)
            }
            val docRef = db.collection("lobbies").document(lobby.id)
            path = docRef.path
            docRef.update(
                mapOf(
                    "memberUids" to nextMembers,
                    "playerNames" to nextNames,
                    "currentPlayers" to max(1L, nextMembers.size.toLong()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(!isJoined)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLobby(lobbyId: String): Result<Unit> {
        var path = "lobbies/$lobbyId"
        return try {
            requireUserId()
            val docRef = db.collection("lobbies").document(lobbyId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Party & Voice Chat ---

    fun observeOpenParties(): Flow<List<PartyRoom>> = flow {
        val uid = requireUserId()
        val path = "parties"
        emitAll(
            db.collection("parties")
                .whereEqualTo("openParty", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PartyRoom::class.java, tsBehavior)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun createParty(profile: UserProfile, partyName: String, openParty: Boolean): Result<String> {
        var path = "parties"
        return try {
            val uid = requireUserId()
            val cleanName = partyName.trim().take(60)
            require(cleanName.isNotEmpty()) { "Party name is required" }
            val docRef = db.collection("parties").document()
            path = docRef.path
            val payload = mapOf(
                "leaderId" to uid,
                "leaderName" to profile.displayName.ifBlank { "Leader" }.take(60),
                "partyName" to cleanName,
                "openParty" to openParty,
                "voiceEnabled" to true,
                "memberUids" to listOf(uid),
                "mutedUids" to emptyList<String>(),
                "memberNames" to listOf(profile.displayName.ifBlank { "Leader" }.take(60)),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            unlockAchievementInternal(uid, "SQUAD_VOICE", "Squad Leader")
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleJoinParty(party: PartyRoom, profile: UserProfile): Result<Unit> {
        var path = "parties/${party.id}"
        return try {
            val uid = requireUserId()
            val joined = uid in party.memberUids
            val nextMembers = if (joined) party.memberUids.filterNot { it == uid } else (party.memberUids + uid).distinct().take(8)
            val nextNames = if (joined) party.memberNames.filterNot { it == profile.displayName } else (party.memberNames + profile.displayName.ifBlank { "Member" }).distinct().take(8)
            val nextMuted = party.mutedUids.filterNot { it == uid }
            val docRef = db.collection("parties").document(party.id)
            path = docRef.path
            if (nextMembers.isEmpty() && party.leaderId == uid) {
                docRef.delete().await()
            } else {
                docRef.update(
                    mapOf(
                        "memberUids" to nextMembers,
                        "memberNames" to nextNames,
                        "mutedUids" to nextMuted,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun togglePartyMute(party: PartyRoom): Result<Unit> {
        var path = "parties/${party.id}"
        return try {
            val uid = requireUserId()
            val isMuted = uid in party.mutedUids
            val nextMuted = if (isMuted) party.mutedUids.filterNot { it == uid } else (party.mutedUids + uid).distinct().take(8)
            val docRef = db.collection("parties").document(party.id)
            path = docRef.path
            docRef.update(
                mapOf(
                    "mutedUids" to nextMuted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Communities & Tournaments ---

    fun observeCommunities(): Flow<List<Community>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("communities")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap -> snap.toObjects(Community::class.java, tsBehavior).sortedByDescending { it.memberCount } }
                .logFirestoreErrors(uid, OperationType.LIST, "communities")
        )
    }

    suspend fun createCommunity(profile: UserProfile, name: String, gameTag: String, description: String): Result<Unit> {
        var path = "communities"
        return try {
            val uid = requireUserId()
            require(name.trim().isNotEmpty()) { "Community name required" }
            val docRef = db.collection("communities").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "creatorId" to uid,
                    "name" to name.trim().take(80),
                    "gameTag" to gameTag.ifBlank { "Arcade" }.take(50),
                    "description" to description.trim().ifBlank { "Official player hub" }.take(240),
                    "active" to true,
                    "memberUids" to listOf(uid),
                    "memberCount" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleJoinCommunity(community: Community): Result<Unit> {
        var path = "communities/${community.id}"
        return try {
            val uid = requireUserId()
            val joined = uid in community.memberUids
            val nextMembers = if (joined) community.memberUids.filterNot { it == uid } else (community.memberUids + uid).distinct().take(50)
            val docRef = db.collection("communities").document(community.id)
            path = docRef.path
            docRef.update(
                mapOf(
                    "memberUids" to nextMembers,
                    "memberCount" to max(1L, nextMembers.size.toLong()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeTournaments(): Flow<List<Tournament>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("tournaments")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap -> snap.toObjects(Tournament::class.java, tsBehavior).sortedByDescending { it.prizeCoins } }
                .logFirestoreErrors(uid, OperationType.LIST, "tournaments")
        )
    }

    suspend fun createTournament(title: String, gameTitle: String, prizeCoins: Long, bracketSize: Long): Result<Unit> {
        var path = "tournaments"
        return try {
            val uid = requireUserId()
            require(title.trim().isNotEmpty()) { "Tournament title required" }
            val docRef = db.collection("tournaments").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "organizerId" to uid,
                    "title" to title.trim().take(80),
                    "gameTitle" to gameTitle.ifBlank { "Cyber Strikers" }.take(60),
                    "prizeCoins" to prizeCoins.coerceIn(0L, 1_000_000L),
                    "bracketSize" to bracketSize.coerceIn(4L, 64L),
                    "active" to true,
                    "participantUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleRegisterTournament(tournament: Tournament): Result<Unit> {
        var path = "tournaments/${tournament.id}"
        return try {
            val uid = requireUserId()
            val joined = uid in tournament.participantUids
            val next = if (joined) tournament.participantUids.filterNot { it == uid } else (tournament.participantUids + uid).distinct().take(32)
            val docRef = db.collection("tournaments").document(tournament.id)
            path = docRef.path
            docRef.update(
                mapOf(
                    "participantUids" to next,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Posts, Stories, Shorts & Comments ---

    fun observePosts(): Flow<List<SocialPost>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("posts")
                .whereIn("kind", listOf(PostKind.POST.name, PostKind.STORY.name, PostKind.SHORT.name))
                .snapshots()
                .map { snap ->
                    snap.toObjects(SocialPost::class.java, tsBehavior)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, "posts")
        )
    }

    fun observePostComments(postId: String): Flow<List<PostComment>> = flow {
        val uid = requireUserId()
        val path = "posts/$postId/comments"
        emitAll(
            db.collection("posts").document(postId).collection("comments")
                .whereEqualTo("postId", postId)
                .snapshots()
                .map { snap -> snap.toObjects(PostComment::class.java, tsBehavior).sortedBy { it.createdAt ?: Timestamp(0, 0) } }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun createPost(
        profile: UserProfile,
        kind: PostKind,
        caption: String,
        gameTag: String,
        mediaType: MediaType = MediaType.NONE,
        mediaLabel: String? = null
    ): Result<Unit> {
        var path = "posts"
        return try {
            val uid = requireUserId()
            val cleanCaption = caption.trim().take(400)
            require(cleanCaption.isNotEmpty()) { "Caption cannot be empty" }
            val docRef = db.collection("posts").document()
            path = docRef.path
            val payload = mapOf(
                "authorId" to uid,
                "authorName" to profile.displayName.ifBlank { "Player" }.take(60),
                "authorAvatarId" to profile.avatarId.take(40),
                "kind" to kind.name,
                "caption" to cleanCaption,
                "gameTag" to gameTag.ifBlank { "Arcade" }.take(50),
                "mediaType" to mediaType.name,
                "mediaLabel" to mediaLabel?.take(500),
                "likesCount" to 0L,
                "likedByUids" to emptyList<String>(),
                "commentsCount" to 0L,
                "sharesCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            unlockAchievementInternal(uid, "CLIP_CREATOR", "Shorts & Highlights")
            grantXpAndCoins(profile, 35L, 15L)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleLikePost(post: SocialPost): Result<Unit> {
        var path = "posts/${post.id}"
        return try {
            val uid = requireUserId()
            val liked = uid in post.likedByUids
            val nextLiked = if (liked) post.likedByUids.filterNot { it == uid } else (post.likedByUids + uid).distinct().take(100)
            val nextCount = if (liked) max(0L, post.likesCount - 1L) else post.likesCount + 1L
            val docRef = db.collection("posts").document(post.id)
            path = docRef.path
            docRef.update(
                mapOf(
                    "likedByUids" to nextLiked,
                    "likesCount" to nextCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addPostComment(post: SocialPost, profile: UserProfile, text: String): Result<Unit> {
        var path = "posts/${post.id}/comments"
        return try {
            val uid = requireUserId()
            val clean = text.trim().take(300)
            require(clean.isNotEmpty()) { "Comment cannot be empty" }
            val commentRef = db.collection("posts").document(post.id).collection("comments").document()
            path = commentRef.path
            commentRef.set(
                mapOf(
                    "postId" to post.id,
                    "authorId" to uid,
                    "authorName" to profile.displayName.ifBlank { "Player" }.take(60),
                    "text" to clean,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("posts").document(post.id).update(
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementPostShare(postId: String): Result<Unit> {
        var path = "posts/$postId"
        return try {
            requireUserId()
            val docRef = db.collection("posts").document(postId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "sharesCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        var path = "posts/$postId"
        return try {
            requireUserId()
            val docRef = db.collection("posts").document(postId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Friend Requests, Messages & Group Chats ---

    fun observeIncomingFriendRequests(userId: String): Flow<List<FriendRequest>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("friend_requests")
                .whereEqualTo("toUserId", userId)
                .snapshots()
                .map { snap -> snap.toObjects(FriendRequest::class.java, tsBehavior) }
                .logFirestoreErrors(uid, OperationType.LIST, "friend_requests")
        )
    }

    fun observeOutgoingFriendRequests(userId: String): Flow<List<FriendRequest>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("friend_requests")
                .whereEqualTo("fromUserId", userId)
                .snapshots()
                .map { snap -> snap.toObjects(FriendRequest::class.java, tsBehavior) }
                .logFirestoreErrors(uid, OperationType.LIST, "friend_requests")
        )
    }

    suspend fun sendFriendRequest(profile: UserProfile, target: UserProfile): Result<Unit> {
        var path = "friend_requests"
        return try {
            val uid = requireUserId()
            require(target.userId != uid) { "Cannot add yourself" }
            val docId = "${uid}_${target.userId}"
            val docRef = db.collection("friend_requests").document(docId)
            path = docRef.path
            docRef.set(
                mapOf(
                    "fromUserId" to uid,
                    "fromUserName" to profile.displayName.ifBlank { "Player" }.take(60),
                    "toUserId" to target.userId,
                    "toUserName" to target.displayName.ifBlank { "Player" }.take(60),
                    "status" to FriendReqStatus.PENDING.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("users").document(target.userId).collection("notifications").document().set(
                mapOf(
                    "userId" to target.userId,
                    "fromUserId" to uid,
                    "title" to "New Friend Request",
                    "body" to "${profile.displayName} sent you a friend request!",
                    "kind" to NotifKind.FRIEND.name,
                    "unread" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun respondFriendRequest(req: FriendRequest, accept: Boolean): Result<Unit> {
        var path = "friend_requests/${req.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("friend_requests").document(req.id)
            path = docRef.path
            docRef.update(
                mapOf(
                    "status" to if (accept) FriendReqStatus.ACCEPTED.name else FriendReqStatus.DECLINED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (accept) {
                db.collection("users").document(uid).update(
                    mapOf(
                        "followersCount" to FieldValue.increment(1L),
                        "followingCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeChats(userId: String): Flow<List<ChatThread>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("chats")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatThread::class.java, tsBehavior)
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, "chats")
        )
    }

    fun observeChatMessages(chatId: String, userId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap -> snap.toObjects(ChatMessage::class.java, tsBehavior).sortedBy { it.createdAt ?: Timestamp(0, 0) } }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun createChatThread(
        profile: UserProfile,
        title: String,
        groupChat: Boolean,
        otherUsers: List<UserProfile>
    ): Result<String> {
        var path = "chats"
        return try {
            val uid = requireUserId()
            val members = (listOf(uid) + otherUsers.map { it.userId }).distinct().take(20)
            val names = (listOf(profile.displayName.ifBlank { "Me" }) + otherUsers.map { it.displayName }).distinct().take(20)
            val docRef = db.collection("chats").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "creatorId" to uid,
                    "title" to title.trim().ifBlank { if (groupChat) "Squad Group Chat" else names.lastOrNull() ?: "Direct Chat" }.take(80),
                    "groupChat" to groupChat,
                    "memberUids" to members,
                    "memberNames" to names,
                    "lastMessage" to "Chat started",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendChatMessage(chat: ChatThread, profile: UserProfile, text: String): Result<Unit> {
        var path = "chats/${chat.id}/messages"
        return try {
            val uid = requireUserId()
            val clean = text.trim().take(500)
            require(clean.isNotEmpty()) { "Message cannot be empty" }
            val msgRef = db.collection("chats").document(chat.id).collection("messages").document()
            path = msgRef.path
            msgRef.set(
                mapOf(
                    "chatId" to chat.id,
                    "senderId" to uid,
                    "senderName" to profile.displayName.ifBlank { "Player" }.take(60),
                    "memberUids" to chat.memberUids.take(20),
                    "text" to clean,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("chats").document(chat.id).update(
                mapOf(
                    "lastMessage" to clean.take(200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Gallery, Notifications, Blocks, Moderation, Achievements ---

    fun observeGallery(userId: String): Flow<List<GalleryMedia>> = flow {
        val uid = requireUserId()
        val path = "users/$userId/gallery"
        emitAll(
            db.collection("users").document(userId).collection("gallery")
                .snapshots()
                .map { snap -> snap.toObjects(GalleryMedia::class.java, tsBehavior).sortedByDescending { it.createdAt ?: Timestamp(0, 0) } }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun addGalleryMedia(
        profile: UserProfile,
        title: String,
        category: GalleryCategory,
        gameTag: String,
        mediaUri: String
    ): Result<Unit> {
        var path = "users/${profile.userId}/gallery"
        return try {
            val uid = requireUserId()
            require(title.trim().isNotEmpty()) { "Title required" }
            require(mediaUri.isNotBlank()) { "Media item required" }
            val docRef = db.collection("users").document(uid).collection("gallery").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to title.trim().take(100),
                    "category" to category.name,
                    "gameTag" to gameTag.ifBlank { "Minecraft" }.take(50),
                    "mediaUri" to mediaUri.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            unlockAchievementInternal(uid, "CLIP_CREATOR", "Shorts & Highlights")
            grantXpAndCoins(profile, 30L, 15L)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGalleryMedia(mediaId: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("gallery").document(mediaId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeNotifications(userId: String): Flow<List<UserNotification>> = flow {
        val uid = requireUserId()
        val path = "users/$userId/notifications"
        emitAll(
            db.collection("users").document(userId).collection("notifications")
                .snapshots()
                .map { snap -> snap.toObjects(UserNotification::class.java, tsBehavior).sortedByDescending { it.createdAt ?: Timestamp(0, 0) } }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notifications").document(notifId)
            path = docRef.path
            docRef.update(mapOf("unread" to false)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeBlockedUsers(userId: String): Flow<List<BlockedUser>> = flow {
        val uid = requireUserId()
        val path = "users/$userId/blocks"
        emitAll(
            db.collection("users").document(userId).collection("blocks")
                .snapshots()
                .map { snap -> snap.toObjects(BlockedUser::class.java, tsBehavior) }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun blockUser(targetUid: String, targetName: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            require(targetUid != uid) { "Cannot block yourself" }
            val docRef = db.collection("users").document(uid).collection("blocks").document(targetUid)
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "blockedUserId" to targetUid,
                    "blockedUserName" to targetName.ifBlank { "User" }.take(60),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(blockedUid: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("blocks").document(blockedUid)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(targetId: String, targetType: ReportTargetType, reason: String): Result<Unit> {
        var path = "reports"
        return try {
            val uid = requireUserId()
            val clean = reason.trim().take(300)
            require(clean.length >= 3) { "Please provide at least 3 characters for report reason" }
            val docRef = db.collection("reports").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "reporterId" to uid,
                    "targetId" to targetId.take(128),
                    "targetType" to targetType.name,
                    "reason" to clean,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeAchievements(userId: String): Flow<List<UserAchievement>> = flow {
        val uid = requireUserId()
        val path = "users/$userId/achievements"
        emitAll(
            db.collection("users").document(userId).collection("achievements")
                .snapshots()
                .map { snap -> snap.toObjects(UserAchievement::class.java, tsBehavior) }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    private suspend fun unlockAchievementInternal(uid: String, badgeKey: String, title: String) {
        val docRef = db.collection("users").document(uid).collection("achievements").document(badgeKey)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "badgeKey" to badgeKey,
                    "title" to title,
                    "rewardClaimed" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun claimAchievementReward(profile: UserProfile, badge: BadgeTemplate): Result<Unit> {
        var path = "users/${profile.userId}/achievements/${badge.key}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("achievements").document(badge.key)
            path = docRef.path
            val snap = docRef.get().await()
            if (snap.exists()) {
                val alreadyClaimed = snap.getBoolean("rewardClaimed") ?: false
                require(!alreadyClaimed) { "Badge reward already claimed" }
                docRef.update(mapOf("rewardClaimed" to true)).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "badgeKey" to badge.key,
                        "title" to badge.title,
                        "rewardClaimed" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            grantXpAndCoins(profile, badge.xpReward, badge.rewardCoins)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun followUserToggle(profile: UserProfile, currentlyFollowing: Boolean): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val next = if (currentlyFollowing) max(0L, profile.followingCount - 1L) else profile.followingCount + 1L
            docRef.update(
                mapOf(
                    "followingCount" to next,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
