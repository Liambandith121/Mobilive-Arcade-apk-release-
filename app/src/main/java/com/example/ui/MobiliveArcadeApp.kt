package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.authStateFlow
import com.example.data.*
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    LIVE("Live", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
    PLAY("Play & LAN", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
    SOCIAL("Social", Icons.Filled.Groups, Icons.Outlined.Groups),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

enum class UtilityOverlay {
    NONE, SEARCH, NOTIFICATIONS, SETTINGS
}

@Composable
fun MobiliveArcadeApp(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            val arcadeViewModel: ArcadeViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        ArcadeViewModel(
                            repository = ArcadeRepository(db, auth),
                            currentUserId = user.uid,
                            displayNameHint = user.displayName,
                            emailHint = user.email
                        )
                    }
                }
            )
            AuthenticatedArcadeScaffold(viewModel = arcadeViewModel)
        }
    }
}

@Composable
private fun AuthenticatedArcadeScaffold(viewModel: ArcadeViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    val profileState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val playersState by viewModel.playersState.collectAsStateWithLifecycle()
    val streamsState by viewModel.streamsState.collectAsStateWithLifecycle()
    val lobbiesState by viewModel.lobbiesState.collectAsStateWithLifecycle()
    val partiesState by viewModel.partiesState.collectAsStateWithLifecycle()
    val communitiesState by viewModel.communitiesState.collectAsStateWithLifecycle()
    val tournamentsState by viewModel.tournamentsState.collectAsStateWithLifecycle()
    val postsState by viewModel.postsState.collectAsStateWithLifecycle()
    val incomingReqState by viewModel.incomingRequestsState.collectAsStateWithLifecycle()
    val outgoingReqState by viewModel.outgoingRequestsState.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val galleryState by viewModel.galleryState.collectAsStateWithLifecycle()
    val notifsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val blocksState by viewModel.blockedUsersState.collectAsStateWithLifecycle()
    val achievementsState by viewModel.achievementsState.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data ?: UserProfile(
        id = viewModel.currentUserId,
        userId = viewModel.currentUserId,
        displayName = "Arcade Player",
        gamerTag = "player"
    )
    val players = (playersState as? UiState.Success)?.data ?: emptyList()
    val streams = (streamsState as? UiState.Success)?.data ?: emptyList()
    val lobbies = (lobbiesState as? UiState.Success)?.data ?: emptyList()
    val parties = (partiesState as? UiState.Success)?.data ?: emptyList()
    val communities = (communitiesState as? UiState.Success)?.data ?: emptyList()
    val tournaments = (tournamentsState as? UiState.Success)?.data ?: emptyList()
    val posts = (postsState as? UiState.Success)?.data ?: emptyList()
    val incomingReqs = (incomingReqState as? UiState.Success)?.data ?: emptyList()
    val outgoingReqs = (outgoingReqState as? UiState.Success)?.data ?: emptyList()
    val chats = (chatsState as? UiState.Success)?.data ?: emptyList()
    val gallery = (galleryState as? UiState.Success)?.data ?: emptyList()
    val notifs = (notifsState as? UiState.Success)?.data ?: emptyList()
    val blockedUsers = (blocksState as? UiState.Success)?.data ?: emptyList()
    val achievements = (achievementsState as? UiState.Success)?.data ?: emptyList()
    val blockedUids = remember(blockedUsers) { blockedUsers.map { it.blockedUserId }.toSet() }
    val unreadNotifsCount = remember(notifs) { notifs.count { it.unread } }

    var activeTab by remember { mutableStateOf(MainTab.HOME) }
    var overlay by remember { mutableStateOf(UtilityOverlay.NONE) }

    if (overlay != UtilityOverlay.NONE) {
        BackHandler { overlay = UtilityOverlay.NONE }
    } else if (activeTab != MainTab.HOME) {
        BackHandler { activeTab = MainTab.HOME }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianNight)) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianNight,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavRail && overlay == UtilityOverlay.NONE) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        contentColor = TextPrimary
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyanContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && overlay == UtilityOverlay.NONE) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        contentColor = TextPrimary
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyanContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.fillMaxSize().widthIn(max = 760.dp)) {
                        when (overlay) {
                            UtilityOverlay.SEARCH -> SearchScreen(
                                players = players,
                                streams = streams,
                                lobbies = lobbies,
                                communities = communities,
                                posts = posts,
                                blockedUids = blockedUids,
                                onBack = { overlay = UtilityOverlay.NONE },
                                onOpenStream = { id ->
                                    overlay = UtilityOverlay.NONE
                                    viewModel.openStreamRoom(id)
                                    activeTab = MainTab.LIVE
                                }
                            )
                            UtilityOverlay.NOTIFICATIONS -> NotificationsScreen(
                                notifications = notifs,
                                onBack = { overlay = UtilityOverlay.NONE },
                                onMarkRead = { viewModel.markNotificationRead(it) }
                            )
                            UtilityOverlay.SETTINGS -> SettingsPrivacyScreen(
                                viewModel = viewModel,
                                profile = profile,
                                blockedUsers = blockedUsers,
                                onBack = { overlay = UtilityOverlay.NONE }
                            )
                            UtilityOverlay.NONE -> when (activeTab) {
                                MainTab.HOME -> HomeScreen(
                                    viewModel = viewModel,
                                    profile = profile,
                                    streams = streams,
                                    posts = posts,
                                    blockedUids = blockedUids,
                                    unreadNotifsCount = unreadNotifsCount,
                                    onOpenSearch = { overlay = UtilityOverlay.SEARCH },
                                    onOpenNotifications = { overlay = UtilityOverlay.NOTIFICATIONS },
                                    onOpenSettings = { overlay = UtilityOverlay.SETTINGS },
                                    onNavigateGoLive = { activeTab = MainTab.LIVE },
                                    onNavigateHostLan = { activeTab = MainTab.PLAY },
                                    onOpenStream = { id ->
                                        viewModel.openStreamRoom(id)
                                        activeTab = MainTab.LIVE
                                    },
                                    onOpenProfile = { activeTab = MainTab.PROFILE }
                                )
                                MainTab.LIVE -> LiveHubScreen(
                                    viewModel = viewModel,
                                    profile = profile,
                                    streams = streams,
                                    posts = posts,
                                    blockedUids = blockedUids
                                )
                                MainTab.PLAY -> PlayArenaScreen(
                                    viewModel = viewModel,
                                    profile = profile,
                                    lobbies = lobbies,
                                    parties = parties,
                                    tournaments = tournaments,
                                    blockedUids = blockedUids
                                )
                                MainTab.SOCIAL -> SocialHubScreen(
                                    viewModel = viewModel,
                                    profile = profile,
                                    players = players,
                                    incomingRequests = incomingReqs,
                                    outgoingRequests = outgoingReqs,
                                    chats = chats,
                                    communities = communities,
                                    gallery = gallery,
                                    blockedUids = blockedUids
                                )
                                MainTab.PROFILE -> ProfileRewardsScreen(
                                    viewModel = viewModel,
                                    profile = profile,
                                    streams = streams,
                                    posts = posts,
                                    achievements = achievements,
                                    onOpenSettings = { overlay = UtilityOverlay.SETTINGS }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
