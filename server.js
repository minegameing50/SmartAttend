const express = require("express");
const path = require("path");
const cors = require("cors");
const admin = require("firebase-admin");

const app = express();
const port = process.env.PORT || 3000;
const root = __dirname;
const databaseUrl = process.env.FIREBASE_DATABASE_URL;
const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;

function getAdminApp() {
  if (admin.apps.length > 0) {
    return admin.app();
  }

  if (!databaseUrl) {
    throw new Error("FIREBASE_DATABASE_URL is not configured.");
  }

  const credential = serviceAccountJson
    ? admin.credential.cert(JSON.parse(serviceAccountJson))
    : admin.credential.applicationDefault();

  return admin.initializeApp({
    credential,
    databaseURL: databaseUrl
  });
}

async function requireAdminRequest(req) {
  const authorization = req.headers.authorization || "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    const error = new Error("Missing bearer token.");
    error.statusCode = 401;
    throw error;
  }

  const adminApp = getAdminApp();
  const decoded = await adminApp.auth().verifyIdToken(match[1]);
  const profileSnapshot = await adminApp.database().ref(`users/${decoded.uid}`).get();
  const profile = profileSnapshot.val();

  if (!profile || String(profile.role || "").toLowerCase() !== "admin") {
    const error = new Error("Admin access required.");
    error.statusCode = 403;
    throw error;
  }

  return {
    adminApp,
    requester: {
      uid: decoded.uid,
      orgTag: String(profile.orgTag || "").trim()
    }
  };
}

app.use(cors());
app.use(express.json());
app.use(express.static(root));

app.get("/healthz", (_req, res) => {
  res.json({
    ok: true,
    firebaseDatabaseConfigured: Boolean(databaseUrl),
    firebaseCredentialConfigured: Boolean(serviceAccountJson || process.env.GOOGLE_APPLICATION_CREDENTIALS)
  });
});

app.post("/api/admin/delete-user", async (req, res) => {
  try {
    const { adminApp, requester } = await requireAdminRequest(req);
    const userId = String(req.body?.userId || "").trim();

    if (!userId) {
      return res.status(400).json({ error: "userId is required." });
    }
    if (userId === requester.uid) {
      return res.status(400).json({ error: "Admins cannot delete themselves here." });
    }

    const userSnapshot = await adminApp.database().ref(`users/${userId}`).get();
    const userProfile = userSnapshot.val();

    if (!userProfile) {
      return res.status(404).json({ error: "Account not found." });
    }
    if (String(userProfile.role || "").toLowerCase() === "admin") {
      return res.status(400).json({ error: "Admin account deletion is not allowed here." });
    }
    if (String(userProfile.orgTag || "").trim() !== requester.orgTag) {
      return res.status(403).json({ error: "This account is outside the current admin tag." });
    }

    await adminApp.auth().deleteUser(userId);
    res.json({ ok: true });
  } catch (error) {
    const statusCode = error.statusCode || 500;
    res.status(statusCode).json({ error: error.message || "Unable to delete Firebase Auth user." });
  }
});

app.get("/", (_req, res) => {
  res.sendFile(path.join(root, "index.html"));
});

app.listen(port, () => {
  console.log(`SmartAttend running at http://localhost:${port}`);
});
