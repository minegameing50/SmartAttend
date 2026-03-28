const admin = require("firebase-admin");
const { onRequest } = require("firebase-functions/v2/https");

if (admin.apps.length === 0) {
  admin.initializeApp();
}

function buildError(status, message) {
  const error = new Error(message);
  error.status = status;
  return error;
}

async function requireAdminRequest(req) {
  const authorization = req.headers.authorization || "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    throw buildError(401, "Missing bearer token.");
  }

  const decoded = await admin.auth().verifyIdToken(match[1]);
  const profileSnapshot = await admin.database().ref(`users/${decoded.uid}`).get();
  const profile = profileSnapshot.val();

  if (!profile || String(profile.role || "").toLowerCase() !== "admin") {
    throw buildError(403, "Admin access required.");
  }

  return {
    requesterUid: decoded.uid,
    requesterOrgTag: String(profile.orgTag || "").trim()
  };
}

exports.deleteAdminManagedUser = onRequest(
  {
    region: "us-central1",
    cors: true
  },
  async (req, res) => {
    try {
      if (req.method !== "POST") {
        res.status(405).json({ error: "Method not allowed." });
        return;
      }

      const { requesterUid, requesterOrgTag } = await requireAdminRequest(req);
      const body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : (req.body || {});
      const userId = String(body.userId || "").trim();

      if (!userId) {
        res.status(400).json({ error: "userId is required." });
        return;
      }
      if (userId === requesterUid) {
        res.status(400).json({ error: "Admins cannot delete themselves here." });
        return;
      }

      const userSnapshot = await admin.database().ref(`users/${userId}`).get();
      const userProfile = userSnapshot.val();

      if (!userProfile) {
        res.status(404).json({ error: "Account not found." });
        return;
      }
      if (String(userProfile.role || "").toLowerCase() === "admin") {
        res.status(400).json({ error: "Admin account deletion is not allowed here." });
        return;
      }
      if (String(userProfile.orgTag || "").trim() !== requesterOrgTag) {
        res.status(403).json({ error: "This account is outside the current admin tag." });
        return;
      }

      await admin.auth().deleteUser(userId);
      res.status(200).json({ ok: true });
    } catch (error) {
      res.status(error.status || 500).json({
        error: error.message || "Unable to delete Firebase Auth user."
      });
    }
  }
);
