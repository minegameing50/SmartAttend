// firebase-config.js
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { getFirestore } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

const firebaseConfig = {
  apiKey:            "AIzaSyCvBqtqu9Cy-_yA3rxLCE_aR0kpvsWomSw",
  authDomain:        "student-attend-b28a8.firebaseapp.com",
  projectId:         "student-attend-b28a8",
  storageBucket:     "student-attend-b28a8.firebasestorage.app",
  messagingSenderId: "468349685878",
  appId:             "1:468349685878:web:819dd8406f37c062550f57"
};

const app = initializeApp(firebaseConfig);

export const auth = getAuth(app);
export const db = getFirestore(app);

