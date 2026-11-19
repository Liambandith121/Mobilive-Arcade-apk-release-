package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ArcadeViewModel(
    private val repository: ArcadeRepository,
    val currentUserId: String,
    private val displayNameHint: String?,
    private val emailHint: String?
) : ViewModel() {

    private companion object {
        const val TAG = "ArcadeVM"
        const val STOP_TIMEOUT_MS = 5000L
    }

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _selectedStreamId = MutableStateFlow<String?>(null)
    val selectedStreamId: StateFlow<String?> = _selectedStreamId.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _selectedCommentsPostId = MutableStateFlow<String?>(null)
    val selectedCommentsPostId: StateFlow<String?> = _selectedCommentsPostId.asStateFlow()

    private val _followedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val followedUserIds: StateFlow<Set<String>> = _followedUserIds.asStateFlow()

    private val _matchmakingActive = MutableStateFlow(false)
    val matchmakingActive: StateFlow<Boolean> = _matchmakingActive.asStateFlow()

    private val _matchmakingGame = MutableStateFlow("Cyber Strikers")
    val matchmakingGame: StateFlow<String> = _matchmakingGame.asStateFlow()

    private val _lastMatchFoundLobbyId = MutableStateFlow<String?>(null)
    val lastMatchFoundLobbyId: StateFlow<String?> = _lastMatchFoundLobbyId.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(displayNameHint, emailHint)
                .onFailure { emitMsg(it.localizedMessage ?: "Profile sync failed") }
        }
    }

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing $label", root)
                emit(UiState.Error(root.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val myProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeMyProfile(currentUserId).toUiState("profile")

    val playersState: StateFlow<UiState<List<UserProfile>>> =
        repository.observePublicPlayers().toUiState("players")

    val streamsState: StateFlow<UiState<List<LiveStream>>> =
        repository.observeStreams().toUiState("streams")

    val lobbiesState: StateFlow<UiState<List<MultiplayerLobby>>> =
        repository.observeLobbies().toUiState("lobbies")

    val partiesState: StateFlow<UiState<List<PartyRoom>>> =
        repository.observeOpenParties().toUiState("parties")

    val communitiesState: StateFlow<UiState<List<Community>>> =
        repository.observeCommunities().toUiState("communities")

    val tournamentsState: StateFlow<UiState<List<Tournament>>> =
        repository.observeTournaments().toUiState("tournaments")

    val postsState: StateFlow<UiState<List<SocialPost>>> =
        repository.observePosts().toUiState("posts")

    val incomingRequestsState: StateFlow<UiState<List<FriendRequest>>> =
        repository.observeIncomingFriendRequests(currentUserId).toUiState("incoming_requests")

    val outgoingRequestsState: StateFlow<UiState<List<FriendRequest>>> =
        repository.observeOutgoingFriendRequests(currentUserId).toUiState("outgoing_requests")

    val chatsState: StateFlow<UiState<List<ChatThread>>> =
        repository.observeChats(currentUserId).toUiState("chats")

    val galleryState: StateFlow<UiState<List<GalleryMedia>>> =
        repository.observeGallery(currentUserId).toUiState("gallery")

    val notificationsState: StateFlow<UiState<List<UserNotification>>> =
        repository.observeNotifications(currentUserId).toUiState("notifications")

    val blockedUsersState: StateFlow<UiState<List<BlockedUser>>> =
        repository.observeBlockedUsers(currentUserId).toUiState("blocks")

    val achievementsState: StateFlow<UiState<List<UserAchievement>>> =
        repository.observeAchievements(currentUserId).toUiState("achievements")

    val streamMessagesState: StateFlow<UiState<List<StreamChatMessage>>> =
        _selectedStreamId.flatMapLatest { streamId ->
            if (streamId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeStreamMessages(streamId)
        }.toUiState("stream_messages")

    val chatMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        _selectedChatId.flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeChatMessages(chatId, currentUserId)
        }.toUiState("chat_messages")

    val postCommentsState: StateFlow<UiState<List<PostComment>>> =
        _selectedCommentsPostId.flatMapLatest { postId ->
            if (postId.isNullOrBlank()) flowOf(emptyList())
            else repository.observePostComments(postId)
        }.toUiState("post_comments")

    private fun currentProfileOrDefault(): UserProfile =
        (myProfileState.value as? UiState.Success)?.data ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            displayName = displayNameHint ?: "Arcade Player"
        )

    private fun emitMsg(msg: String) {
        _toastMessage.tryEmit(msg)
    }

    fun openStreamRoom(streamId: String?) {
        _selectedStreamId.value = streamId
    }

    fun openChatThread(chatId: String?) {
        _selectedChatId.value = chatId
    }

    fun openPostComments(postId: String?) {
        _selectedCommentsPostId.value = postId
    }

    fun updateProfile(
        displayName: String,
        gamerTag: String,
        avatarId: String,
        avatarUri: String?,
        bio: String
    ) {
        viewModelScope.launch {
            repository.updateProfile(displayName, gamerTag, avatarId, avatarUri, bio)
                .onSuccess { emitMsg("Profile Face & details saved!") }
                .onFailure { emitMsg(it.localizedMessage ?: "Failed to update profile") }
        }
    }

    fun claimDailyCoins() {
        viewModelScope.launch {
            val todayEpoch = System.currentTimeMillis() / 86_400_000L
            repository.claimDailyReward(currentProfileOrDefault(), todayEpoch)
                .onSuccess { coins -> emitMsg("Claimed +$coins Gift Coins & +75 XP!") }
                .onFailure { emitMsg(it.localizedMessage ?: "Daily reward unavailable") }
        }
    }

    fun activateBuff(buff: ArcadeBuff) {
        viewModelScope.launch {
            repository.activateBuff(currentProfileOrDefault(), buff)
                .onSuccess { emitMsg("Activated ${buff.title}!") }
                .onFailure { emitMsg(it.localizedMessage ?: "Could not activate buff") }
        }
    }

    fun levelUpBoostPractice() {
        viewModelScope.launch {
            repository.grantXpAndCoins(currentProfileOrDefault(), 80L, 25L)
                .onSuccess { emitMsg("Arcade Quest complete: +80 XP & +25 Coins!") }
                .onFailure { emitMsg(it.localizedMessage ?: "Action failed") }
        }
    }

    fun goLive(title: String, gameTitle: String, lowLatency: Boolean, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.goLive(currentProfileOrDefault(), title, gameTitle, lowLatency)
                .onSuccess { id ->
                    _selectedStreamId.value = id
                    emitMsg("You are LIVE!")
                    onCreated(id)
                }
                .onFailure { emitMsg(it.localizedMessage ?: "Failed to go live") }
        }
    }

    fun endStreamToReplay(streamId: String) {
        viewModelScope.launch {
            repository.endStreamToReplay(streamId)
                .onSuccess { emitMsg("Stream saved to Replays!") }
                .onFailure { emitMsg(it.localizedMessage ?: "Could not end stream") }
        }
    }

    fun deleteStream(streamId: String) {
        viewModelScope.launch {
            repository.deleteStream(streamId)
                .onSuccess {
                    if (_selectedStreamId.value == streamId) _selectedStreamId.value = null
                    emitMsg("Stream deleted")
                }
                .onFailure { emitMsg(it.localizedMessage ?: "Delete failed") }
        }
    }

    fun likeStream(streamId: String) {
        viewModelScope.launch {
            repository.likeStream(streamId)
                .onFailure { emitMsg(it.localizedMessage ?: "Like failed") }
        }
    }

    fun sendStreamMessage(stream: LiveStream, text: String, giftCoins: Long = 0L) {
        viewModelScope.launch {
            repository.sendStreamChat(stream, currentProfileOrDefault(), text, giftCoins)
                .onSuccess {
                    if (giftCoins > 0L) emitMsg("Sent $giftCoins Gift Coins!")
                }
                .onFailure { emitMsg(it.localizedMessage ?: "Failed to send message") }
        }
    }

    fun hostLanWorld(
        worldName: String,
        gameTitle: String,
        gameMode: String,
        lanPort: Long,
        maxPlayers: Long,
        visibility: String
    ) {
        viewModelScope.launch {
            repository.hostLanWorld(
                currentProfileOrDefault(), worldName, gameTitle, gameMode, lanPort, maxPlayers, visibility
            ).onSuccess {
                emitMsg("LAN World published! Visible to players.")
            }.onFailure {
                emitMsg(it.localizedMessage ?: "Failed to host world")
            }
        }
    }

    fun toggleJoinLobby(lobby: MultiplayerLobby) {
        viewModelScope.launch {
            repository.toggleJoinLobby(lobby, currentProfileOrDefault())
                .onSuccess { joined ->
                    emitMsg(
                        if (joined) "Joined ${lobby.worldName}! Open Minecraft > Play > Worlds to tap the world."
                        else "Left ${lobby.worldName}"
                    )
                }
                .onFailure { emitMsg(it.localizedMessage ?: "Lobby action failed") }
        }
    }

    fun deleteLobby(lobbyId: String) {
        viewModelScope.launch {
            repository.deleteLobby(lobbyId)
                .onSuccess { emitMsg("Lobby closed") }
                .onFailure { emitMsg(it.localizedMessage ?: "Failed to close lobby") }
        }
    }

    fun createParty(partyName: String, openParty: Boolean) {
        viewModelScope.launch {
            repository.createParty(currentProfileOrDefault(), partyName, openParty)
                .onSuccess { emitMsg("Party Voice Room created!") }
                .onFailure { emitMsg(it.localizedMessage ?: "Failed to create party") }
        }
    }

    fun toggleJoinParty(party: PartyRoom) {
        viewModelScope.launch {
            repository.toggleJoinParty(party, currentProfileOrDefault())
                .onFailure { emitMsg(it.localizedMessage ?: "Party action failed") }
        }
    }

    fun togglePartyMute(party: PartyRoom) {
        viewModelScope.launch {
            repository.togglePartyMute(party)
                .onFailure { emitMsg(it.localizedMessage ?: "Mic toggle failed") }
        }
    }

    fun startMatchmaking(gameTitle: String) {
        _matchmakingGame.value = gameTitle
        _matchmakingActive.value = true
        _lastMatchFoundLobbyId.value = null
        viewModelScope.launch {
            delay(1600L)
            if (_matchmakingActive.value) {
                repository.hostLanWorld(
                    profile = currentProfileOrDefault(),
                    worldName = "$gameTitle Match Arena",
                    gameTitle = gameTitle,
                    gameMode = "Competitive",
                    lanPort = 19132L,
                    maxPlayers = 8L,
                    visibility = "PUBLIC"
                ).onSuccess { id ->
                    _matchmakingActive.value = false
                    _lastMatchFoundLobbyId.value = id
                    emitMsg("Match found for $gameTitle!")
                }.onFailure {
                    _matchmakingActive.value = false
                    emitMsg(it.localizedMessage ?: "Matchmaking failed")
                }
            }
        }
    }

    fun cancelMatchmaking() {
        _matchmakingActive.value = false
    }

    fun createCommunity(name: String, gameTag: String, description: String) {
        viewModelScope.launch {
            repository.createCommunity(currentProfileOrDefault(), name, gameTag, description)
                .onSuccess { emitMsg("Community launched!") }
                .onFailure { emitMsg(it.localizedMessage ?: "Failed to create community") }
        }
    }

    fun toggleJoinCommunity(community: Community) {
        viewModelScope.launch {
            repository.toggleJoinCommunity(community)
                .onFailure { emitMsg(it.localizedMessage ?: "Community action failed") }
        }
    }

    fun createTournament(title: String, gameTitle: String, prizeCoins: Long, bracketSize: Long) {
        viewModelScope.launch {
            repository.createTournament(title, gameTitle, prizeCoins, bracketSize)
                .onSuccess { emitMsg("Tournament created!") }
                .onFailure { emitMsg(it.localizedMessage ?: "Failed to create tournament") }
        }
    }

    fun toggleRegisterTournament(tournament: Tournament) {
        viewModelScope.launch {
            repository.toggleRegisterTournament(tournament)
                .onSuccess { emitMsg("Tournament registration updated") }
                .onFailure { emitMsg(it.localizedMessage ?: "Registration failed") }
        }
    }

    fun createPost(
        kind: PostKind,
        caption: String,
        gameTag: String,
        mediaType: MediaType = MediaType.NONE,
        mediaLabel: String? = null
    ) {
        viewModelScope.launch {
            repository.createPost(currentProfileOrDefault(), kind, caption, gameTag, mediaType, mediaLabel)
                .onSuccess { emitMsg("Published ${kind.name.lowercase().replaceFirstChar { it.uppercase() }}!") }
                .onFailure { emitMsg(it.localizedMessage ?: "Publish failed") }
        }
    }

    fun toggleLikePost(post: SocialPost) {
        viewModelScope.launch {
            repository.toggleLikePost(post)
                .onFailure { emitMsg(it.localizedMessage ?: "Like failed") }
        }
    }

    fun addPostComment(post: SocialPost, text: String) {
        viewModelScope.launch {
            repository.addPostComment(post, currentProfileOrDefault(), text)
                .onFailure { emitMsg(it.localizedMessage ?: "Comment failed") }
        }
    }

    fun incrementShare(postId: String) {
        viewModelScope.launch {
            repository.incrementPostShare(postId)
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deletePost(postId)
                .onSuccess { emitMsg("Deleted item") }
                .onFailure { emitMsg(it.localizedMessage ?: "Delete failed") }
        }
    }

    fun addGalleryMedia(title: String, category: GalleryCategory, gameTag: String, mediaUri: String) {
        viewModelScope.launch {
            repository.addGalleryMedia(currentProfileOrDefault(), title, category, gameTag, mediaUri)
                .onSuccess { emitMsg("Uploaded to Gallery! +15 Coins") }
                .onFailure { emitMsg(it.localizedMessage ?: "Upload failed") }
        }
    }

    fun deleteGalleryMedia(mediaId: String) {
        viewModelScope.launch {
            repository.deleteGalleryMedia(mediaId)
                .onSuccess { emitMsg("Removed from Gallery") }
                .onFailure { emitMsg(it.localizedMessage ?: "Delete failed") }
        }
    }

    fun sendFriendRequest(target: UserProfile) {
        viewModelScope.launch {
            repository.sendFriendRequest(currentProfileOrDefault(), target)
                .onSuccess { emitMsg("Friend request sent to ${target.displayName}") }
                .onFailure { emitMsg(it.localizedMessage ?: "Request failed") }
        }
    }

    fun respondFriendRequest(req: FriendRequest, accept: Boolean) {
        viewModelScope.launch {
            repository.respondFriendRequest(req, accept)
                .onSuccess { emitMsg(if (accept) "Friend added!" else "Request declined") }
                .onFailure { emitMsg(it.localizedMessage ?: "Action failed") }
        }
    }

    fun toggleFollow(targetUid: String) {
        val current = _followedUserIds.value
        val wasFollowing = targetUid in current
        _followedUserIds.value = if (wasFollowing) current - targetUid else current + targetUid
        viewModelScope.launch {
            repository.followUserToggle(currentProfileOrDefault(), wasFollowing)
        }
    }

    fun createChat(title: String, groupChat: Boolean, members: List<UserProfile>) {
        viewModelScope.launch {
            repository.createChatThread(currentProfileOrDefault(), title, groupChat, members)
                .onSuccess { id ->
                    _selectedChatId.value = id
                    emitMsg(if (groupChat) "Group chat created!" else "Direct message opened!")
                }
                .onFailure { emitMsg(it.localizedMessage ?: "Failed to open chat") }
        }
    }

    fun sendChatMessage(chat: ChatThread, text: String) {
        viewModelScope.launch {
            repository.sendChatMessage(chat, currentProfileOrDefault(), text)
                .onFailure { emitMsg(it.localizedMessage ?: "Send failed") }
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notifId)
        }
    }

    fun updatePrivacy(
        visibility: ProfileVisibility,
        hideViewerCounts: Boolean,
        allowFriendRequests: Boolean,
        allowPartyInvites: Boolean
    ) {
        viewModelScope.launch {
            repository.updatePrivacySettings(visibility, hideViewerCounts, allowFriendRequests, allowPartyInvites)
                .onSuccess { emitMsg("Privacy controls updated") }
                .onFailure { emitMsg(it.localizedMessage ?: "Update failed") }
        }
    }

    fun removeAllPeopleViews() {
        viewModelScope.launch {
            repository.clearAllPeopleViews()
                .onSuccess { emitMsg("All people views hidden & cleared") }
                .onFailure { emitMsg(it.localizedMessage ?: "Action failed") }
        }
    }

    fun blockUser(targetUid: String, targetName: String) {
        viewModelScope.launch {
            repository.blockUser(targetUid, targetName)
                .onSuccess { emitMsg("Blocked $targetName") }
                .onFailure { emitMsg(it.localizedMessage ?: "Block failed") }
        }
    }

    fun unblockUser(blockedUid: String) {
        viewModelScope.launch {
            repository.unblockUser(blockedUid)
                .onSuccess { emitMsg("User unblocked") }
                .onFailure { emitMsg(it.localizedMessage ?: "Unblock failed") }
        }
    }

    fun submitReport(targetId: String, targetType: ReportTargetType, reason: String) {
        viewModelScope.launch {
            repository.submitReport(targetId, targetType, reason)
                .onSuccess { emitMsg("Report submitted to moderation") }
                .onFailure { emitMsg(it.localizedMessage ?: "Report failed") }
        }
    }

    fun claimAchievement(badge: BadgeTemplate) {
        viewModelScope.launch {
            repository.claimAchievementReward(currentProfileOrDefault(), badge)
                .onSuccess { emitMsg("Claimed ${badge.title}: +${badge.rewardCoins} Coins!") }
                .onFailure { emitMsg(it.localizedMessage ?: "Already claimed") }
        }
    }
}
