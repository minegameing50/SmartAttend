// login.js
import { auth } from "./firebase-config.js";
import { signInWithEmailAndPassword } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";

window.login = function () {
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  signInWithEmailAndPassword(auth, email, password)
    .then((userCredential) => {
      if (email.includes("admin")) {
        window.location.href = "admin.html";
      } else if (email.includes("faculty")) {
        window.location.href = "faculty.html";
      } else {
        window.location.href = "student.html";
      }
    })
    .catch((error) => {
      alert(error.message);
    });
};
