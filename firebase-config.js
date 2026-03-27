import { initializeApp, getApps, getApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { getDatabase } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-database.js";

export const firebaseConfig = {
  apiKey: "AIzaSyCvBqtqu9Cy-_yA3rxLCE_aR0kpvsWomSw",
  authDomain: "student-attend-b28a8.firebaseapp.com",
  projectId: "student-attend-b28a8",
  databaseURL: "https://student-attend-b28a8-default-rtdb.firebaseio.com",
  storageBucket: "student-attend-b28a8.firebasestorage.app",
  messagingSenderId: "468349685878",
  appId: "1:468349685878:web:819dd8406f37c062550f57"
};

export const app = getApps().length ? getApp() : initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getDatabase(app);
export const SITE_URL = `${window.location.origin}${window.location.pathname.replace(/\/[^/]*$/, "")}`;
