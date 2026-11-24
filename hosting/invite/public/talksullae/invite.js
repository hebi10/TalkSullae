"use strict";

const message = document.getElementById("message");
const invite = document.getElementById("invite");
const input = document.getElementById("code");
const copy = document.getElementById("copy");
const open = document.getElementById("open");
const raw = window.location.hash.slice(1);
const code = raw.startsWith("code=") ? raw.slice(5) : "";
const valid = /^[A-Za-z0-9]{20}\.[A-Za-z0-9_-]{43}$/.test(code) &&
  window.location.search === "";

if (valid) {
  input.value = code;
  open.href = "talksullae://invite?code=" + code;
  invite.hidden = false;
  message.textContent = "앱에서 초대를 확인해 주세요.";
  copy.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(code);
      message.textContent = "복사했습니다. 앱에서 초대 코드를 입력해 주세요.";
    } catch {
      input.focus();
      input.select();
      message.textContent = "선택한 초대 코드를 직접 복사해 주세요.";
    }
  });
} else {
  message.textContent = "초대 링크가 올바르지 않습니다. 친구에게 새 링크를 요청해 주세요.";
}
