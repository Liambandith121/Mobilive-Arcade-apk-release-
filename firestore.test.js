const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsPast(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

function tsFuture(msAhead = 60000) {
  return new Date(Date.now() + msAhead);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validUserPayload(uid) {
  const now = tsPast(5000);
  return {
    userId: uid,
    displayName: "Player " + uid,
    gamerTag: "gamer_" + uid.slice(0, 6),
    avatarId: "avatar_cyber",
    bio: "Arcade streamer & LAN builder",
    level: 4,
    xp: 350,
    coins: 500,
    dailyStreak: 2,
    lastClaimEpochDay: 20700,
    activeBuff: "2X_XP_BOOST",
    followersCount: 12,
    followingCount: 8,
    profileVisibility: "PUBLIC",
    hideViewerCounts: false,
    allowFriendRequests: true,
    allowPartyInvites: true,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users are denied across all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").where("profileVisibility", "in", ["PUBLIC", "FRIENDS_ONLY"]).get());
  await assertFails(unauth.collection("streams").where("status", "in", ["LIVE", "REPLAY"]).get());
  await assertFails(unauth.collection("lobbies").where("visibility", "in", ["PUBLIC", "FRIENDS"]).get());
  await assertFails(unauth.collection("posts").where("kind", "in", ["POST", "STORY", "SHORT"]).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
});

test("User profile create, partial update, private PII isolation, and query alignment", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  // Create own profile
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));

  // Reject ghost field & future timestamp
  await assertFails(
    alice.collection("users").doc("extra_user").set({
      ...validUserPayload("extra_user"),
      userId: ALICE_UID,
      isAdmin: true,
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      coins: 900,
      updatedAt: tsFuture(),
    })
  );

  // Partial update: claim daily reward + level up + clear views
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      coins: 650,
      xp: 450,
      level: 5,
      dailyStreak: 3,
      hideViewerCounts: true,
      viewsClearedAt: tsPast(1000),
      updatedAt: tsPast(1000),
    })
  );

  // Bob cannot overwrite Alice's profile
  await assertFails(bob.collection("users").doc(ALICE_UID).update({ coins: 0, updatedAt: tsPast(500) }));

  // Bob can query public profiles, but unfiltered list must fail
  await assertSucceeds(bob.collection("users").where("profileVisibility", "in", ["PUBLIC", "FRIENDS_ONLY"]).get());
  await assertFails(bob.collection("users").get());

  // Private PII isolation
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      updatedAt: tsPast(1000),
    })
  );
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("private").doc("account").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("private").get());
});

test("User-scoped subcollections: gallery, blocks, achievements, notifications", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("gallery").doc("gal_1").set({
      userId: ALICE_UID,
      title: "Boss Clutch",
      category: "HIGHLIGHT",
      gameTag: "Cyber Strikers",
      mediaUri: "content://media/picker/1",
      createdAt: tsPast(1000),
    })
  );
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("gallery").get());

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("blocks").doc(BOB_UID).set({
      userId: ALICE_UID,
      blockedUserId: BOB_UID,
      blockedUserName: "Bob",
      createdAt: tsPast(1000),
    })
  );
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).collection("blocks").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("blocks").get());

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("achievements").doc("badge_first_live").set({
      userId: ALICE_UID,
      badgeKey: "FIRST_LIVE",
      title: "First Broadcast",
      rewardClaimed: false,
      createdAt: tsPast(1000),
    })
  );
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("achievements").doc("badge_first_live").update({
      rewardClaimed: true,
    })
  );
});

test("Streams, stream chat, gift updates, and replay transition", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = tsPast(5000);

  await assertSucceeds(
    alice.collection("streams").doc("stream_1").set({
      hostId: ALICE_UID,
      hostName: "Alice",
      hostAvatarId: "avatar_cyber",
      title: "Survival LAN Speedrun",
      gameTitle: "BlockCraft Worlds",
      status: "LIVE",
      viewerCount: 18,
      likesCount: 4,
      giftCoinsTotal: 0,
      durationMinutes: 12,
      lowLatency: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Scoped query works, unfiltered fails
  await assertSucceeds(bob.collection("streams").where("status", "in", ["LIVE", "REPLAY"]).get());
  await assertFails(bob.collection("streams").get());

  // Bob sends stream chat & gift coins
  await assertSucceeds(
    bob.collection("streams").doc("stream_1").collection("messages").doc("msg_1").set({
      streamId: "stream_1",
      authorId: BOB_UID,
      authorName: "Bob",
      text: "Sent 50 Gift Coins!",
      kind: "GIFT",
      giftCoins: 50,
      createdAt: tsPast(1000),
    })
  );
  await assertSucceeds(
    bob.collection("streams").doc("stream_1").update({
      giftCoinsTotal: 50,
      likesCount: 5,
      updatedAt: tsPast(500),
    })
  );
  // Bob cannot hijack stream title
  await assertFails(
    bob.collection("streams").doc("stream_1").update({
      title: "Hacked",
      updatedAt: tsPast(300),
    })
  );
  // Alice ends stream -> REPLAY
  await assertSucceeds(
    alice.collection("streams").doc("stream_1").update({
      status: "REPLAY",
      updatedAt: tsPast(200),
    })
  );
});

test("Multiplayer Lobbies (LAN Worlds), Parties, Communities, Tournaments", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = tsPast(5000);

  await assertSucceeds(
    alice.collection("lobbies").doc("lobby_1").set({
      hostId: ALICE_UID,
      hostName: "Alice",
      worldName: "Emerald Skyblock",
      gameTitle: "Minecraft",
      gameMode: "Survival",
      roomCode: "MC-7742",
      lanPort: 19132,
      maxPlayers: 8,
      currentPlayers: 1,
      visibility: "PUBLIC",
      lanEnabled: true,
      memberUids: [ALICE_UID],
      playerNames: ["Alice"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(bob.collection("lobbies").where("visibility", "in", ["PUBLIC", "FRIENDS"]).get());
  await assertSucceeds(
    bob.collection("lobbies").doc("lobby_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      playerNames: ["Alice", "Bob"],
      currentPlayers: 2,
      updatedAt: tsPast(1000),
    })
  );

  // Party + Voice Chat
  await assertSucceeds(
    alice.collection("parties").doc("party_1").set({
      leaderId: ALICE_UID,
      leaderName: "Alice",
      partyName: "Night Raid Squad",
      openParty: true,
      voiceEnabled: true,
      memberUids: [ALICE_UID],
      mutedUids: [],
      memberNames: ["Alice"],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(bob.collection("parties").where("openParty", "==", true).get());
  await assertSucceeds(
    bob.collection("parties").doc("party_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: ["Alice", "Bob"],
      mutedUids: [BOB_UID],
      updatedAt: tsPast(1000),
    })
  );

  // Community & Tournament
  await assertSucceeds(
    alice.collection("communities").doc("comm_1").set({
      creatorId: ALICE_UID,
      name: "BlockCraft Builders",
      gameTag: "Minecraft",
      description: "LAN builders & redstone engineers",
      active: true,
      memberUids: [ALICE_UID],
      memberCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(bob.collection("communities").where("active", "==", true).get());

  await assertSucceeds(
    alice.collection("tournaments").doc("tour_1").set({
      organizerId: ALICE_UID,
      title: "Neon Cup",
      gameTitle: "Cyber Strikers",
      prizeCoins: 2500,
      bracketSize: 16,
      active: true,
      participantUids: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(bob.collection("tournaments").where("active", "==", true).get());
});

test("Posts, Stories, Shorts, Comments, Friend Requests, Chats, and Reports", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = tsPast(5000);

  await assertSucceeds(
    alice.collection("posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorAvatarId: "avatar_cyber",
      kind: "SHORT",
      caption: "Insane portal clutch!",
      gameTag: "Minecraft",
      mediaType: "VIDEO",
      mediaLabel: "portal_clutch.mp4",
      likesCount: 0,
      likedByUids: [],
      commentsCount: 0,
      sharesCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(bob.collection("posts").where("kind", "in", ["POST", "STORY", "SHORT"]).get());
  await assertSucceeds(
    bob.collection("posts").doc("post_1").collection("comments").doc("c1").set({
      postId: "post_1",
      authorId: BOB_UID,
      authorName: "Bob",
      text: "Clean play!",
      createdAt: tsPast(1000),
    })
  );

  // Friend request
  await assertSucceeds(
    alice.collection("friend_requests").doc("fr_1").set({
      fromUserId: ALICE_UID,
      fromUserName: "Alice",
      toUserId: BOB_UID,
      toUserName: "Bob",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(charlie.collection("friend_requests").doc("fr_1").get());
  await assertSucceeds(
    bob.collection("friend_requests").doc("fr_1").update({
      status: "ACCEPTED",
      updatedAt: tsPast(500),
    })
  );

  // Group / DM chat isolation
  await assertSucceeds(
    alice.collection("chats").doc("chat_1").set({
      creatorId: ALICE_UID,
      title: "LAN Squad Chat",
      groupChat: true,
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: ["Alice", "Bob"],
      lastMessage: "Ready to join!",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(charlie.collection("chats").doc("chat_1").get());
  await assertSucceeds(
    bob.collection("chats").doc("chat_1").collection("messages").doc("cm_1").set({
      chatId: "chat_1",
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      text: "Joining world now!",
      createdAt: tsPast(500),
    })
  );
  await assertFails(
    charlie.collection("chats").doc("chat_1").collection("messages").where("memberUids", "array-contains", CHARLIE_UID).get()
  );
  await assertFails(
    charlie.collection("chats").doc("chat_1").collection("messages").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Report
  await assertSucceeds(
    alice.collection("reports").doc("rep_1").set({
      reporterId: ALICE_UID,
      targetId: CHARLIE_UID,
      targetType: "USER",
      reason: "Spamming in chat",
      createdAt: tsPast(500),
    })
  );
  await assertFails(bob.collection("reports").doc("rep_1").get());
});
