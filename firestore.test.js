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
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read family items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("families").doc("fam1").collection("vault_items").get());
});

test("Authenticated family member: can create family and add vault item", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  
  // Create family
  await assertSucceeds(
    aliceDb.collection("families").doc("fam1").set({
      id: "fam1",
      name: "Alice Family",
      createdBy: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      salt: "testsalt123",
      keyCheckHash: "testhash456"
    })
  );

  // Alice adds shared vault item
  await assertSucceeds(
    aliceDb.collection("families").doc("fam1").collection("vault_items").doc("item1").set({
      id: "item1",
      familyId: "fam1",
      isPrivate: false,
      itemType: "password",
      encryptedTitle: "enc_wifi",
      encryptedPayload: "enc_data_blob",
      createdByUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID]
    })
  );
});

test("Non-family member: cannot read family vault item", async () => {
  // Seed family with Alice and Bob
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("families").doc("fam1").set({
      id: "fam1",
      name: "Alice Family",
      createdBy: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      salt: "testsalt123",
      keyCheckHash: "testhash456"
    });
    await context.firestore().collection("families").doc("fam1").collection("vault_items").doc("item1").set({
      id: "item1",
      familyId: "fam1",
      isPrivate: false,
      itemType: "password",
      encryptedTitle: "enc_wifi",
      encryptedPayload: "enc_data_blob",
      createdByUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID]
    });
  });

  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("families").doc("fam1").collection("vault_items").doc("item1").get());
});

test("User private items: only owner can read and write", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private_items").doc("priv1").set({
      id: "priv1",
      familyId: "fam1",
      isPrivate: true,
      itemType: "password",
      encryptedTitle: "enc_personal_mail",
      encryptedPayload: "enc_secret",
      createdByUid: ALICE_UID,
      memberUids: [ALICE_UID]
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private_items").doc("priv1").get()
  );
});
