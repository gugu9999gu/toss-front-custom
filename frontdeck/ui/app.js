"use strict";
const native = typeof NativeDeck !== "undefined";
const pending = new Map();
let sequence = 0, connected = false, checking = false, active = localStorage.getItem("profile") || "work";
let config = JSON.parse(document.getElementById("fallback").textContent);
try { config = JSON.parse(localStorage.getItem("config")) || config; } catch (_) {}
window.FrontDeck = {
  reply(id, result) { const waiter = pending.get(id); if (waiter) { pending.delete(id); clearTimeout(waiter.timer); result.error ? waiter.reject(new Error(result.error)) : waiter.resolve(result); } },
  refresh() { check(); }
};
function request(route, body) {
  return new Promise((resolve, reject) => {
    if (!native) return reject(new Error("미리보기에서는 PC를 제어하지 않습니다."));
    const id = String(++sequence);
    const timer = setTimeout(() => { pending.delete(id); reject(new Error("PC 연결을 확인하세요.")); }, 6000);
    pending.set(id, {resolve, reject, timer});
    try { NativeDeck.request(id, route, JSON.stringify(body || {})); } catch (_) { clearTimeout(timer); pending.delete(id); reject(new Error("PC 연결을 확인하세요.")); }
  });
}
function status(value, message) {
  connected = value;
  document.body.classList.toggle("connected", value);
  document.getElementById("connection-text").textContent = message;
  document.querySelectorAll(".tile").forEach(button => button.disabled = native && !connected);
}
function feedback(message) { document.getElementById("feedback").textContent = message; }
function render() {
  document.getElementById("pc-name").textContent = config.name || "내 PC";
  if (!config.profiles.some(p => p.id === active)) active = config.profiles[0].id;
  const tabs = document.getElementById("profiles"); tabs.replaceChildren();
  config.profiles.forEach(profile => {
    const button = document.createElement("button"); button.textContent = profile.name;
    button.setAttribute("aria-pressed", String(profile.id === active));
    button.addEventListener("click", () => { active = profile.id; localStorage.setItem("profile", active); render(); });
    tabs.append(button);
  });
  const grid = document.getElementById("buttons"); grid.replaceChildren();
  config.profiles.find(p => p.id === active).actions.forEach(action => {
    const button = document.createElement("button"); button.className = "tile";
    button.dataset.tone = ["blue", "violet", "green", "red"].includes(action.tone) ? action.tone : "slate";
    button.disabled = native && !connected;
    const graphic = document.createElement("span"); graphic.className = "icon"; graphic.innerHTML = icon(action.icon);
    const label = document.createElement("span"); label.className = "label"; label.textContent = action.label;
    button.append(graphic, label);
    button.addEventListener("click", async event => {
      if (button.dataset.busy) return;
      if (!native) return feedback(action.label + " · 미리보기");
      button.dataset.busy = "1";
      if (event.detail > 0) { button.classList.add("pressed"); setTimeout(() => button.classList.remove("pressed"), 100); }
      try {
        const requestId = Date.now().toString(36) + "_" + (++sequence).toString(36) + "_" + Math.random().toString(36).slice(2, 10);
        const result = await request("action", {id: action.id, request_id: requestId});
        feedback(action.label + (result.preview ? " · 시험 모드" : " 실행됨"));
      } catch (error) { feedback(error.message); status(false, "PC 연결 확인 필요"); }
      finally { delete button.dataset.busy; }
    });
    grid.append(button);
  });
}
async function check() {
  if (checking || !native) return;
  checking = true;
  try {
    const next = await request("config");
    if (JSON.stringify(next) !== JSON.stringify(config)) { config = next; localStorage.setItem("config", JSON.stringify(config)); render(); }
    status(true, "PC 연결됨");
  } catch (error) { status(false, "PC 연결 확인 필요"); feedback(error.message); }
  finally { checking = false; }
}
document.getElementById("settings").innerHTML = icon("settings");
document.getElementById("settings").addEventListener("click", () => native ? NativeDeck.openSettings() : feedback("읽기 전용 미리보기 · 기기에서 PC를 연결하세요"));
function clock() { document.getElementById("clock").textContent = new Intl.DateTimeFormat("ko-KR", {hour: "2-digit", minute: "2-digit", hour12: false}).format(new Date()); }
clock(); setInterval(clock, 30000); render();
if (native) { check(); setInterval(check, 5000); }
else {
  status(true, "화면 미리보기"); feedback("미리보기 · 실제 PC 동작은 실행되지 않습니다");
  fetch("demo.json").then(r => r.json()).then(value => { config = value; render(); }).catch(() => {});
}
