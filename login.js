window.login = function () {
  if (typeof window.doLogin === "function") {
    window.doLogin();
  }
};
