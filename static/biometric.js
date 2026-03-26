// biometric.js

export async function checkBiometricSupport() {
  if (!window.PublicKeyCredential) {
    console.log("Biometric NOT supported");
    return false;
  }

  try {
    const available = await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
    console.log("Biometric available:", available);
    return available;
  } catch (e) {
    console.error("Error checking biometric:", e);
    return false;
  }
}

// Fake biometric auth (trigger device prompt)
export async function biometricLogin() {
  try {
    if (!window.PublicKeyCredential) {
      alert("Biometric not supported on this device");
      return false;
    }

    // This triggers device verification (fingerprint / face / PIN)
    await navigator.credentials.get({
      publicKey: {
        challenge: new Uint8Array(32),
        timeout: 60000,
        userVerification: "required"
      }
    });

    console.log("Biometric success");
    return true;

  } catch (err) {
    console.error("Biometric failed:", err);
    alert("Biometric authentication failed");
    return false;
  }
}
