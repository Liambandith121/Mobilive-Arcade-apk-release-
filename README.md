# Mobilive Arcade

**Mobilive Arcade** is an Android social gaming, live-streaming, and multiplayer LAN community platform built with Kotlin, Jetpack Compose, Material Design 3, Firebase Authentication (Google Sign-In via Jetpack Credential Manager), and Cloud Firestore.

---

## Features

### Authentication & Gamer Profile
- **Login & Sign Up**: Google Sign-In via Jetpack Credential Manager (`GetSignInWithGoogleOption`) with startup silent auto sign-in (`GetGoogleIdOption`) and safe `NonCancellable` sign-out.
- **Profile Face Avatars**: Choose from preset arcade avatars (`Cyber Visor`, `Voxel Knight`, `Neon Ace`, `Pixel Fox`, `Titan Bot`) or select a custom image using the zero-permission Android Photo Picker.
- **Level System, Buffs & Gift Coins**: Earn XP to level up, claim daily streak Gift Coins, and activate buffs (`2x XP Overdrive`, `Stream Hype Aura`, `LAN Relay Boost`).

### Live Streaming & Creator Tools
- **Go Live**: Launch a live stream with low-latency mode and optional facecam permission (`CAMERA`), or transition a live broadcast into a saved **Stream Replay**.
- **Real-Time Stream Chat & Gift Coins**: Send live messages and Gift Coins directly inside stream rooms with real-time Firestore snapshot updates.
- **Posts, Stories, Shorts & Media Gallery**: Create and browse Posts, Stories, and vertical Shorts with likes, comments, external sharing, delete controls, and Photo Picker uploads for **Videos**, **Screenshots**, and **Highlights**.

### Multiplayer Lobbies & Minecraft LAN World Bridge
- **LAN Hosting & Joining Guide**:
  - **Hosting**: Open your world, then turn on **Multiplayer Game** and **Visible to LAN Players**, and publish the lobby with room code and LAN port (`19132`).
  - **Joining**: Press **Join Here**, then open **Minecraft > Play > Worlds** and tap the world.
- **Party System & Voice Chat**: Create or join squad voice parties (`RECORD_AUDIO` runtime permission) with live mute/speaking toggles.
- **Matchmaking & Tournaments**: Queue for quick matchmaking and register for bracket tournaments with Gift Coin prizes.

### Social, Analytics & Moderation
- **Friends, Requests & Messaging**: Send/accept/decline friend requests, follow/unfollow players, and message in 1:1 Direct Chats or multi-member Group Chats.
- **Game Communities**: Discover, create, and join game-specific communities.
- **Analytics, Achievements & Badges**: View stream/social analytics and claim Coin + XP rewards from unlockable badges.
- **Search, Notifications & Privacy Controls**: Global search across streams, lobbies, players, communities, and posts; real-time notifications; profile visibility controls (`Public`, `Friends Only`, `Private`); **Remove All People Views See**; user blocking; and content/user moderation reporting.

---

## Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material Design 3 (`Obsidian Neon Arcade` dark theme, bundled `Space Grotesk`, `Plus Jakarta Sans`, and `JetBrains Mono` fonts)
- **Architecture**: MVVM (`ArcadeViewModel`, `ArcadeRepository`, `UiState`) with Kotlin Coroutines & `Flow` (`WhileSubscribed(5000)`)
- **Backend**: Firebase Authentication (Google Sign-In via Credential Manager) and Cloud Firestore (named database configured via `R.string.firestore_database_id`)
- **Media & Navigation**: Coil Compose, Android Photo Picker (`PickVisualMedia`), Android `BackHandler` & adaptive `NavigationBar` / `NavigationRail`

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge activity entry point
├── auth/
│   └── AuthManager.kt               # Credential Manager Google Sign-In & sign-out
├── data/
│   ├── Models.kt                    # Firestore-mapped data classes & catalogs
│   ├── ArcadeRepository.kt          # Real-time Firestore flows & Result<T> mutations
│   └── FirestoreErrorHandler.kt     # Structured JSON Firestore error logger
└── ui/
    ├── MobiliveArcadeApp.kt         # Auth-gated root navigation & adaptive scaffold
    ├── ArcadeViewModel.kt           # StateFlows & action handlers
    ├── components/
    │   └── ArcadeComponents.kt      # Profile Face avatars, cards, dialogs
    ├── screens/
    │   ├── AuthScreen.kt            # Login / Sign Up screen
    │   ├── HomeScreen.kt            # Hero stage, daily claim, stories & feed
    │   ├── LiveHubScreen.kt         # Go Live, stream watch room, chat & replays
    │   ├── PlayArenaScreen.kt       # LAN worlds, lobbies, voice party, matchmaking, cups
    │   ├── SocialHubScreen.kt       # Friends, DMs/group chats, communities & gallery
    │   ├── ProfileRewardsScreen.kt  # Profile face editor, XP/buffs, analytics & badges
    │   └── UtilityScreens.kt        # Search, notifications, settings & privacy controls
    └── theme/
        ├── Color.kt                 # Obsidian Neon Arcade color tokens
        ├── Type.kt                  # Bundled Google Fonts typography
        └── Theme.kt                 # Material 3 theme & shapes
```

---

## Firebase & Security Rules

- `firebase-blueprint.json`: Schema blueprint for all 18 Firestore entities and collections.
- `firestore.rules`: Zero-trust security rules enforcing authentication, ownership isolation, strict field allowlists, timestamp bounds, and query alignment.
- `firestore.test.js`: Node.js (`@firebase/rules-unit-testing`) test suite verifying unauthenticated denial, cross-user isolation, partial updates, and query alignment.

### Running Security Rules Tests

Start the single-database Firestore emulator (`firebase.json`) and run:

```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

### Building the Android App

Compile debug Kotlin sources or build the debug APK using Gradle:

```bash
gradle :app:compileDebugKotlin
gradle :app:assembleDebug
```
