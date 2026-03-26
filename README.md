# 📋 SmartAttend — Multi-Mode Attendance Management System

> Built for **Hackathon 2K26** · Problem Statement PS4 — Automated Attendance System

[![Live Demo](https://img.shields.io/badge/Live-Demo-00e5b4?style=for-the-badge)](https://minegameing50.github.io/SmartAttend/)
[![Try Demo](https://img.shields.io/badge/Try-Demo-ffb547?style=for-the-badge)](https://minegameing50.github.io/SmartAttend/demo.html)
[![GitHub Pages](https://img.shields.io/badge/Hosted-GitHub%20Pages-3d8eff?style=for-the-badge)](https://minegameing50.github.io/SmartAttend/)

---

## 🚀 Live Website

🌐 **[https://minegameing50.github.io/SmartAttend/](https://minegameing50.github.io/SmartAttend/)**

✨ **[Try Interactive Demo — No Login Required](https://minegameing50.github.io/SmartAttend/demo.html)**

---

## 📁 Assets 
⬇️ **[SmartAttend](https://github.com/minegameing50/SmartAttend/releases/tag/SmartAttendv_1.0)**

---

## 📌 Problem Statement

Traditional attendance in colleges wastes 5–10 minutes per class on manual roll calls, is prone to proxy attendance, and provides no real-time insights to faculty or administrators.

---

## ✅ Our Solution

**SmartAttend** eliminates roll calls entirely. Faculty can start timed attendance sessions with QR, fingerprint, face scan, or all three together, and students can mark attendance directly from the website. No app download needed.

---

## ✨ Key Features

| Feature | Description |
|---|---|
| ⚡ QR Generation | Faculty generates unique timed QR codes per session |
| 🫆 Fingerprint Check-In | Browser-based biometric prompt for student and faculty attendance |
| 🙂 Face Scan Check-In | Camera-based face capture flow for live attendance verification |
| 🎛️ Session Mode Control | Faculty can launch QR-only, fingerprint-only, face-only, or hybrid sessions |
| ⏱️ Custom Timer | Set QR validity from 2 to 60+ minutes with live countdown |
| 🚫 Anti-Proxy | Single-use UUID tokens — cannot be shared or reused |
| 📊 Analytics | Pie charts, attendance %, per-class breakdown |
| 📡 Live Feed | Real-time list of students as they scan |
| ✏️ Manual Mark | Faculty can mark any student present manually |
| ⬇️ Export CSV | Download full attendance records as CSV |
| 📷 In-App Scanner | Students can scan QR directly from the website |
| 🛡️ Role-Based Access | Separate dashboards for Admin, Faculty, Student |
| 📱 Mobile Ready | Works on any device, no app install needed |
| ✨ Demo Mode | Fully interactive demo with fake data, no login needed |

---

## 👥 User Roles

### 🛡️ Admin
- Create faculty, student, and admin accounts
- Create classes and assign to faculty
- View all students with attendance history modal
- Monitor system-wide stats (Present Today, Total Records)
- See absent students for today

### 👨‍🏫 Faculty
- Start and end classes
- Launch QR, fingerprint, face scan, or all-method attendance sessions
- Reopen timed attendance sessions for late students
- Mark their own faculty attendance with fingerprint or face scan
- View live attendance feed (auto-refreshes every 5 sec)
- Manually mark students present
- Export full attendance to CSV

### 🎓 Student
- Scan QR with phone camera or in-built browser scanner
- Mark attendance with fingerprint when the faculty enables it
- Mark attendance with face scan when the faculty enables it
- View attendance % per subject with progress bars
- Get warned if below 75% threshold
- Enroll in available classes

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Frontend | HTML5, CSS3, Vanilla JavaScript |
| Authentication | Firebase Authentication (Email/Password) |
| Database | Firebase Realtime Database |
| QR Generation | QRCode.js |
| QR Scanning | Html5-QRCode |
| Biometrics | WebAuthn + MediaDevices Camera API |
| Charts | Chart.js |
| Hosting | GitHub Pages (Free) |

---

## 📁 Project Structure

```
SmartAttend/
├── index.html           # Landing page
├── login.html           # Login for all roles
├── demo.html            # Interactive demo — no Firebase needed
├── faculty.html         # Faculty dashboard
├── student.html         # Student dashboard
├── admin.html           # Admin panel
├── scan.html            # QR scan handler
├── firebase-config.js   # Firebase credentials (edit this)
├── static/
│   └── logo.jpeg        # App logo
└── README.md
```

---

## ⚙️ Setup & Deployment

### 1. Clone the repository
```bash
git clone https://github.com/minegameing50/SmartAttend.git
cd SmartAttend
```

### 2. Set up Firebase (Free)
1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Create a new project
3. Enable **Authentication** → Email/Password
4. Enable **Realtime Database** → Start in test mode
5. Go to Project Settings → Your Apps → Web → Copy config

### 3. Edit `firebase-config.js`
```js
export const firebaseConfig = {
  apiKey:            "YOUR_API_KEY",
  authDomain:        "YOUR_PROJECT.firebaseapp.com",
  projectId:         "YOUR_PROJECT_ID",
  databaseURL:       "https://YOUR_PROJECT-default-rtdb.firebaseio.com",
  storageBucket:     "YOUR_PROJECT.appspot.com",
  messagingSenderId: "YOUR_SENDER_ID",
  appId:             "YOUR_APP_ID"
};
```

### 4. Create First Admin Account
1. Go to Firebase Console → **Authentication** → Add User
2. Copy the generated **UID**
3. Go to **Realtime Database** → Add this structure manually:
```
users/
  └── YOUR_UID_HERE
        ├── name: "Admin"
        ├── email: "your@email.com"
        ├── role: "admin"
        └── rollNo: ""
```

### 5. Deploy to GitHub Pages
1. Push all files to your GitHub repo
2. Go to **Settings → Pages**
3. Source: **Deploy from branch → main → / (root)**
4. ✅ Live at `https://USERNAME.github.io/SmartAttend/`

---

## 🔒 Security

- Only Admin can create accounts — no self-registration on login page
- QR tokens are UUID v4, single-use per student per session
- Sessions expire when faculty end the class
- Enrollment verified before marking attendance (no gate-crashing)
- Role-based routing — wrong role gets redirected to login
- Manual attendance logged with faculty name + timestamp
- Fingerprint uses the device WebAuthn prompt, and face scan uses live camera capture in the browser

---

## 🗄️ Firebase Database Structure

```
users/
  └── uid → { name, email, role, rollNo, createdAt }

classes/
  └── id  → { name, subject, facultyId, facultyName, classActive }

sessions/
  └── id  → { classId, token, active, expiresAt, date, createdAt, attendanceMethod, allowedMethods[] }

enrollments/
  └── id  → { studentId, classId, studentName, rollNo, enrolledAt }

attendance/
  └── id  → { studentId, classId, sessionId, markedAt, method, manual, markedBy }

facultyAttendance/
  └── id  → { facultyId, facultyName, method, markedAt }
```

---

## 🎮 Demo Mode

No login or Firebase needed! Click **✨ Try Demo** on the landing page to explore all 3 roles with fake data:

- **Student** — view attendance cards, % bars, recent scans
- **Faculty** — start class, generate QR with live countdown, pie charts, live feed
- **Admin** — stats, student grid, absent today, create account form

---

## 📱 How Attendance Works

```
1. Faculty clicks "▶ Start Class"
2. Faculty selects QR, fingerprint, face scan, or all methods
3. Faculty sets the session timer and launches attendance
4. Students either scan the QR or open the student dashboard biometric option
5. Attendance is recorded in Firebase with the selected method
6. Faculty sees the method used in the live feed and CSV export
7. Session expires after the timer or when the faculty ends class
```

---

## 👨‍💻 Team

Built with ❤️ by **Binary Coders** for **Hackathon 2K26**

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).
