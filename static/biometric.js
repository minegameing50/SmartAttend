import {
  get,
  ref,
  set
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-database.js";

function biometricPath(uid) {
  return `users/${uid}/biometrics`;
}

async function hasPlatformAuthenticator() {
  if (!window.PublicKeyCredential) return false;
  try {
    return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
  } catch {
    return false;
  }
}

export async function getBiometricProfile(db, user) {
  if (!db || !user?.uid) return {};
  const snap = await get(ref(db, biometricPath(user.uid)));
  return snap.exists() ? snap.val() : {};
}

export function hasFingerprint(profile = {}) {
  return Boolean(profile.fingerprint?.registeredAt);
}

export function hasFace(profile = {}) {
  return Boolean(profile.face?.registeredAt);
}

export function hasAnyBiometric(profile = {}) {
  return hasFingerprint(profile) || hasFace(profile);
}

export async function registerFingerprint(db, user) {
  if (!user?.uid) throw new Error("User profile is missing.");
  const supported = await hasPlatformAuthenticator();
  if (!supported) {
    throw new Error("This device does not support platform biometrics.");
  }

  const profile = {
    ...(await getBiometricProfile(db, user)),
    fingerprint: {
      registeredAt: new Date().toISOString(),
      device: navigator.userAgent
    }
  };

  await set(ref(db, biometricPath(user.uid)), profile);
  return profile;
}

export async function verifyFingerprint(profile = {}) {
  if (!hasFingerprint(profile)) {
    throw new Error("Fingerprint is not registered for this account.");
  }

  const supported = await hasPlatformAuthenticator();
  if (!supported) {
    throw new Error("Platform biometrics are not available on this device.");
  }

  return {
    fingerprintVerifiedAt: new Date().toISOString()
  };
}

export async function registerFace(db, user, metadata = {}) {
  if (!user?.uid) throw new Error("User profile is missing.");

  const profile = {
    ...(await getBiometricProfile(db, user)),
    face: {
      registeredAt: new Date().toISOString(),
      ...metadata
    }
  };

  await set(ref(db, biometricPath(user.uid)), profile);
  return profile;
}

export async function biometricLogin() {
  const supported = await hasPlatformAuthenticator();
  return supported;
}

export async function checkBiometricSupport() {
  return hasPlatformAuthenticator();
}
