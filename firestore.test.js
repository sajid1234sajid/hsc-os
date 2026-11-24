const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any user collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("chapters").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("mistakes").add({
      userId: ALICE_UID,
      subjectId: "physics",
      subjectNameBn: "পদার্থবিজ্ঞান",
      subjectNameEn: "Physics",
      chapterTitle: "ভেক্টর",
      question: "Test?",
      correctAnswer: "Answer",
      date: "2026-11-24",
      revised: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Owner can create and update UserProfile; cross-user and extra keys are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const validProfile = {
    userId: ALICE_UID,
    displayName: "Sajeda Akter",
    email: "sajeda@example.com",
    dailyHours: 5,
    language: "BN",
    darkMode: true,
    notificationsEnabled: false,
    reminderHour: 18,
    reminderMinute: 0,
    seededDefaultChapters: true,
    createdAt: created,
    updatedAt: created,
  };

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile)
  );

  // Partial update on settings
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyHours: 3,
      language: "EN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt mutation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost field fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read & write fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      dailyHours: 3,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Listing /users root collection fails
  await assertFails(aliceDb.collection("users").get());
});

test("Owner can manage chapters, list subcollection, and update status; invalid status and @DocumentId id fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const chaptersCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("chapters");

  await assertSucceeds(
    chaptersCol.doc("phy_1").set({
      userId: ALICE_UID,
      subjectId: "physics",
      subjectGroup: "physics",
      paper: "১ম পত্র",
      startWeek: 1,
      weekNumber: 1,
      title: "ভৌতজগৎ ও পরিমাপ",
      deadline: "2026-11-30",
      status: "NOT_STARTED",
      editablePlaceholder: false,
      sortOrder: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List subcollection (even when scoped to owner)
  await assertSucceeds(chaptersCol.orderBy("sortOrder").get());

  // Status update succeeds
  await assertSucceeds(
    chaptersCol.doc("phy_1").update({
      status: "PRACTICE_DONE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid status rejected
  await assertFails(
    chaptersCol.doc("phy_1").update({
      status: "INVALID_STATUS",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Including @DocumentId "id" key in body rejected
  await assertFails(
    chaptersCol.doc("phy_2").set({
      id: "phy_2",
      userId: ALICE_UID,
      subjectId: "physics",
      subjectGroup: "physics",
      paper: "১ম পত্র",
      startWeek: 2,
      weekNumber: 2,
      title: "ভেক্টর",
      deadline: "2026-12-07",
      status: "NOT_STARTED",
      editablePlaceholder: false,
      sortOrder: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user chapter access fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chapters").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chapters")
      .doc("phy_1")
      .delete()
  );
});

test("Owner can manage weekRecords, dailyLogs, and mistakes; future timestamps and cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Week record
  const weekRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("weekRecords")
    .doc("1");

  await assertSucceeds(
    weekRef.set({
      userId: ALICE_UID,
      weekNumber: 1,
      weeklyTasks: ["task_1", "task_2"],
      fridayChecklist: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    weekRef.update({
      fridayChecklist: ["fri_1"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Daily log
  const dailyRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("dailyLogs")
    .doc("2026-11-24");

  await assertSucceeds(
    dailyRef.set({
      userId: ALICE_UID,
      dateIso: "2026-11-24",
      completedSlots: [1, 2],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Mistake entry
  const mistakeRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("mistakes")
    .doc("m_1");

  await assertSucceeds(
    mistakeRef.set({
      userId: ALICE_UID,
      subjectId: "chemistry",
      subjectNameBn: "রসায়ন",
      subjectNameEn: "Chemistry",
      chapterTitle: "পরিমাণগত রসায়ন",
      question: "মোলারিটি তাপমাত্রার ওপর নির্ভর করে কি?",
      correctAnswer: "হ্যাঁ, কারণ আয়তন তাপমাত্রার সাথে পরিবর্তিত হয়।",
      date: "2026-11-24",
      revised: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    mistakeRef.update({
      revised: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Future timestamp tampered payload fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("mistakes")
      .doc("m_future")
      .set({
        userId: ALICE_UID,
        subjectId: "chemistry",
        subjectNameBn: "রসায়ন",
        subjectNameEn: "Chemistry",
        chapterTitle: "পরিমাণগত রসায়ন",
        question: "Future timestamp test",
        correctAnswer: "Should fail",
        date: "2026-11-24",
        revised: false,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Bob cannot list Alice's mistakes or dailyLogs
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("mistakes").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("dailyLogs").get()
  );
});
