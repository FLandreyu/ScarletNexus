const form = document.getElementById("setup-form");
const status = document.getElementById("status");
const submit = document.getElementById("submit");
window.scarletSetup.onStatus((message) => {
  status.textContent = message;
});
form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const password = document.getElementById("password").value;
  if (password !== document.getElementById("confirmation").value) {
    status.textContent = "两次输入的密码不一致。";
    return;
  }
  submit.disabled = true;
  const result = await window.scarletSetup.submit({
    email: document.getElementById("email").value.trim().toLowerCase(),
    password,
  });
  if (!result.accepted) {
    status.textContent = result.error;
    submit.disabled = false;
    return;
  }
  document.getElementById("password").value = "";
  document.getElementById("confirmation").value = "";
  status.textContent = "正在准备你的学习空间…";
});
