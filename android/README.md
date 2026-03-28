# SmartAttend Android

This folder contains the native Android migration for SmartAttend.

## Current State

- Jetpack Compose app scaffold
- Firebase Auth + Realtime Database integration points
- Student dashboard shell
- Real device biometric attendance using `BiometricPrompt`
- Faculty/Admin placeholder screens for the next migration steps

## What You Need To Do

1. Open the `android/` folder in Android Studio.
2. Use Android Studio's bundled JDK 17.
3. Add an Android app in your Firebase project with package name:
   - `com.SmartAttend.app`
4. Download `google-services.json`.
5. Place it here:
   - `android/app/google-services.json`
6. Sync Gradle.
7. Run the app on a real Android device with fingerprint configured.

## Notes

- This app currently expects the same Firebase Realtime Database structure used by the web app.
- The visible system Java in this shell is too old for Android Gradle Plugin 8.x, so build/sync should be done from Android Studio.
- `BiometricPrompt` is real Android biometric verification, but it does not expose raw fingerprint templates.
- For production without Firebase Blaze, deploy the repo's `server.js` backend to Render and set `smartAttend.adminApiBaseUrl=https://YOUR-RENDER-SERVICE.onrender.com` in `android/local.properties`.
- The Firebase Cloud Function path exists in the repo, but it requires Firebase Blaze.
- For local development only, you can still override the backend with `smartAttend.adminApiBaseUrl=http://<host>:3000` in `android/local.properties`.
- If you deploy the function outside `us-central1`, set `smartAttend.adminDeleteFunctionRegion=<region>` in `android/local.properties`.

## Next Native Work

- QR scan with CameraX + ML Kit
- Faculty class/session management
- Admin user/class management
- Face enrollment and matching
- External fingerprint scanner SDK integration
