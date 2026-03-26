# 💬 PulseChat

<div align="center">

![PulseChat Logo](pulsechat-logo.png)

**Real-time multi-user chat app — Web + Android**

[![GitHub Pages](https://img.shields.io/badge/Live-Demo-6C63FF?style=for-the-badge&logo=github)](https://minegameing50.github.io/pulsechat)
[![Firebase](https://img.shields.io/badge/Firebase-Powered-FFCA28?style=for-the-badge&logo=firebase)](https://firebase.google.com)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Web-34D399?style=for-the-badge)](https://minegameing50.github.io/pulsechat)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

</div>

---

## 📱 Screenshots

| Login | Global Chat | Direct Message |
|-------|-------------|----------------|
| Register with email & password | Chat with everyone | Private 1-on-1 messages |

---

## ✨ Features

- 🔐 **Email & Password Authentication** — Secure login powered by Firebase Auth
- 🌐 **Global Chat** — Public channel where all users can talk together
- 📩 **Private Direct Messages** — One-on-one private conversations
- 🔔 **Push Notifications** — Get notified even when app is closed
- 👤 **Auto Profile** — Unique avatar & color assigned automatically
- ⚡ **Real-Time Sync** — Messages appear instantly via Firebase Realtime Database
- 📱 **Mobile Responsive** — Works perfectly on Android, iOS, and desktop
- 🔒 **Secure Database Rules** — Only authorized users can read/write their data

---

## 🚀 Live Demo

🌐 **[minegameing50.github.io/pulsechat](https://minegameing50.github.io/pulsechat)**

---

## 📲 Download

| Platform | Link |
|----------|------|
| 🤖 Android APK | [Download Latest Release](https://github.com/minegameing50/pulsechat/releases/latest) |
| 🌐 Web App | [Open in Browser](https://minegameing50.github.io/pulsechat) |

---

## 🛠️ Tech Stack

| Technology | Purpose |
|------------|---------|
| **HTML / CSS / JavaScript** | Frontend UI |
| **React 18** | UI Components |
| **Firebase Auth** | User Authentication |
| **Firebase Realtime Database** | Live Messages & Presence |
| **Firebase Cloud Messaging** | Push Notifications |
| **Electron** | Desktop App (.exe) |
| **GitHub Pages** | Web Hosting |

---

## 🔥 Firebase Setup

### Step 1 — Create Firebase Project
1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **Add project** → name it `pulsechat`

### Step 2 — Enable Services
- **Authentication** → Sign-in method → **Email/Password** → Enable
- **Build** → **Realtime Database** → Create → **Test mode**
- **Project Settings** → Cloud Messaging → **Web Push certificates** → Generate key pair

### Step 3 — Paste Config
Open `index.html` and replace the `FB_CFG` values:
```js
window.FB_CFG = {
  apiKey:            "YOUR_API_KEY",
  authDomain:        "YOUR_PROJECT.firebaseapp.com",
  databaseURL:       "https://YOUR_PROJECT-default-rtdb.firebaseio.com",
  projectId:         "YOUR_PROJECT",
  storageBucket:     "YOUR_PROJECT.firebasestorage.app",
  messagingSenderId: "YOUR_SENDER_ID",
  appId:             "YOUR_APP_ID"
};
```

### Step 4 — Set Database Rules
Go to **Realtime Database → Rules** and paste:
```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null",
        ".write": "$uid === auth.uid"
      }
    },
    "presence": {
      "$uid": {
        ".read": "auth != null",
        ".write": "$uid === auth.uid"
      }
    },
    "groupMessages": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    "dms": {
      "$chatKey": {
        ".read": "auth != null && $chatKey.contains(auth.uid)",
        ".write": "auth != null && $chatKey.contains(auth.uid)"
      }
    },
    "fcmTokens": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    }
  }
}
```

---

## 💻 Run as Desktop App (.exe)

### Requirements
- [Node.js](https://nodejs.org) (LTS version)

### Steps
```bash
# 1. Clone the repo
git clone https://github.com/minegameing50/pulsechat.git
cd pulsechat-exe

# 2. Install dependencies
npm install

# 3. Run in development
npm start

# 4. Build .exe
npm run build
```
Your `.exe` file will be in the `dist/` folder.

---

## 📁 Project Structure

```
pulsechat/
├── index.html                  ← Main app (all-in-one)
├── firebase-messaging-sw.js    ← Service Worker for push notifications
├── manifest.json               ← PWA manifest (installable app)
├── pulsechat-logo.png          ← App icon
├── pulsechat-exe/
│   ├── main.js                 ← Electron main process
│   ├── package.json            ← Build config
│   └── index.html              ← Same app for desktop
└── functions/
    ├── index.js                ← Cloud Functions (send notifications)
    └── package.json
```

---

## 📦 Files in This Repo

| File | Description |
|------|-------------|
| `index.html` | Complete chat app — paste Firebase config here |
| `firebase-messaging-sw.js` | Background push notification handler |
| `manifest.json` | Makes the website installable as a PWA |
| `pulsechat-logo.png` | App icon (512×512) |

---

## 🔔 Push Notifications Setup

1. Get VAPID key from Firebase → Project Settings → Cloud Messaging → Web Push certificates
2. Paste it in `index.html`:
```js
const VAPID_KEY = "YOUR_VAPID_KEY_HERE";
```
3. Deploy Cloud Functions (sends notifications when app is closed):
```bash
npm install -g firebase-tools
firebase login
firebase init functions
firebase deploy --only functions
```

---

## 📱 Install as Mobile App (PWA)

1. Open [minegameing50.github.io/pulsechat](https://minegameing50.github.io/pulsechat) in **Chrome on Android**
2. Tap the **Install** banner at the bottom
3. Tap **Install** → added to home screen like a real app ✅

---

## 🔒 Security

- All messages require user authentication
- Direct messages only readable by the 2 participants
- FCM tokens only accessible by the token owner
- Message length limited to 2000 characters
- Fake timestamp prevention

---

## 🗺️ Roadmap

- [ ] Image & file sharing
- [ ] Message reactions (emoji)
- [ ] Read receipts (double tick)
- [ ] Group chats (multiple users)
- [ ] Message search
- [ ] Dark / Light theme toggle
- [ ] iOS App Store release

---

## 🤝 Contributing

Pull requests are welcome! For major changes please open an issue first.

1. Fork the repo
2. Create your branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push: `git push origin feature/amazing-feature`
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **MIT License** — free to use, modify and distribute.

---

## 👨‍💻 Developer

**minegameing50**
- 🌐 Website: [minegameing50.github.io/pulsechat](https://minegameing50.github.io/pulsechat)
- 🐙 GitHub: [@minegameing50](https://github.com/minegameing50)

---

<div align="center">

Made with ❤️ and ☕

⭐ **Star this repo if you found it helpful!** ⭐

</div>
