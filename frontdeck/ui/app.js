"use strict";
const native = typeof NativeDeck !== "undefined", $ = id => document.getElementById(id), pending = new Map();
let sequence = 0, connected = false, checking = false, windowChecking = false;
let active = localStorage.getItem("profile") || "work", page = 0, editedId = "", apps = [], appLoading = false;
let windowSignature = "", windowOrder = [], editorBusy = false, appSelection = "", returnFocus = null, editorGeneration = 0;
let config = JSON.parse($("fallback").textContent);
try { config = JSON.parse(localStorage.getItem("config")) || config; } catch (_) {}
window.FrontDeck = {
  reply(id, result) { const w = pending.get(id); if (w) { pending.delete(id); clearTimeout(w.timer); result.error ? w.reject(new Error(result.error)) : w.resolve(result); } },
  refresh() { check(); checkWindows(); },
  back() { if (!$("editor").hidden) closeEditor(); else if (native) NativeDeck.openSettings(); }
};
function request(route, body) {
  return new Promise((resolve, reject) => {
    if (!native) return reject(new Error("미리보기에서는 PC를 제어하지 않습니다."));
    const id = String(++sequence), timer = setTimeout(() => { pending.delete(id); reject(new Error("PC 연결을 확인하세요.")); }, 7000);
    pending.set(id, {resolve, reject, timer});
    try { NativeDeck.request(id, route, JSON.stringify(body || {})); } catch (_) { clearTimeout(timer); pending.delete(id); reject(new Error("PC 연결을 확인하세요.")); }
  });
}
function requestId() { return Date.now().toString(36) + "_" + (++sequence).toString(36) + "_" + Math.random().toString(36).slice(2, 10); }
function status(value, message) {
  connected = value; document.body.classList.toggle("connected", value); $("connection-text").textContent = message;
  document.querySelectorAll(".tile,.window,.input-key").forEach(b => b.disabled = Boolean(b.dataset.busy) || (native && !connected));
  if (!value && window.DeckInput) window.DeckInput.cancel();
}
function feedback(message) { $("feedback").textContent = message; }
function appGraphic(holder, image, fallback) {
  const valid = typeof image === "string" && image.length <= 16000 && /^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(image);
  const signature = valid ? image : "fallback:" + fallback;
  if (holder.frontDeckGraphic === signature) return; holder.frontDeckGraphic = signature;
  holder.innerHTML = icon(fallback);
  if (!valid) return;
  const picture = document.createElement("img"); picture.src = image; picture.alt = ""; picture.draggable = false; picture.decoding = "async";
  picture.addEventListener("error", () => { holder.innerHTML = icon(fallback); }, {once: true});
  holder.replaceChildren(picture);
}
function adopt(value) { config = value; localStorage.setItem("config", JSON.stringify(config)); render(); }
function option(value, label) { const item = document.createElement("option"); item.value = value; item.textContent = label; return item; }
function render() {
  $("pc-name").textContent = config.name || "내 PC";
  const profiles = [...config.profiles];
  if (!profiles.some(p => p.id === "custom") && profiles.length < 6) profiles.push({id: "custom", name: "내 버튼", actions: []});
  if (!profiles.some(p => p.id === active)) active = profiles[0].id;
  $("profiles").replaceChildren();
  profiles.forEach(profile => {
    const b = document.createElement("button"); b.textContent = profile.name; b.setAttribute("aria-pressed", String(profile.id === active));
    b.addEventListener("click", () => { active = profile.id; page = 0; localStorage.setItem("profile", active); render(); }); $("profiles").append(b);
  });
  const profile = profiles.find(p => p.id === active), grid = $("buttons"); grid.replaceChildren();
  const pages = Math.max(1, Math.ceil(profile.actions.length / 12)); page = Math.max(0, Math.min(page, pages - 1));
  $("pager").dataset.pages = pages; $("pager").hidden = pages === 1 || (window.DeckInput && window.DeckInput.mode !== "panel"); $("page-number").textContent = `${page + 1} / ${pages}`;
  $("page-prev").disabled = page === 0; $("page-next").disabled = page === pages - 1;
  if (!profile.actions.length) {
    const b = document.createElement("button"); b.className = "empty-grid"; b.textContent = "+ 버튼을 추가해 나만의 패널을 만드세요";
    b.addEventListener("click", () => openEditor()); grid.append(b);
  }
  profile.actions.slice(page * 12, page * 12 + 12).forEach(action => {
    const b = document.createElement("button"); b.className = "tile"; b.dataset.id = action.id;
    b.dataset.tone = ["blue", "violet", "green", "red"].includes(action.tone) ? action.tone : "slate"; b.disabled = native && !connected;
    const graphic = document.createElement("span"); graphic.className = "icon"; appGraphic(graphic, action.image, action.icon);
    const label = document.createElement("span"); label.className = "label"; label.textContent = action.label; b.append(graphic, label);
    let hold = null, held = false, x = 0, y = 0;
    const cancel = () => { clearTimeout(hold); hold = null; };
    b.addEventListener("pointerdown", e => { held = false; x = e.clientX; y = e.clientY; hold = setTimeout(() => { held = true; openEditor(action.id); }, 550); });
    b.addEventListener("pointermove", e => { if (Math.hypot(e.clientX - x, e.clientY - y) > 12) cancel(); });
    ["pointerup", "pointercancel", "pointerleave"].forEach(name => b.addEventListener(name, cancel));
    b.addEventListener("contextmenu", e => { e.preventDefault(); cancel(); if (!held) openEditor(action.id); held = true; });
    b.addEventListener("click", async () => {
      if (held) { held = false; return; } if (b.dataset.busy) return;
      if (!native) return feedback(action.label + " · 미리보기"); b.dataset.busy = "1";
      try { const result = await request("action", {id: action.id, request_id: requestId()}); feedback(action.label + (result.preview ? " · 시험 모드" : result.focused ? " 창 열림" : " 실행됨")); setTimeout(checkWindows, 600); }
      catch (error) { feedback(error.message); } finally { delete b.dataset.busy; }
    }); grid.append(b);
  });
}
async function check() {
  if (checking || !native || document.hidden) return; checking = true;
  try { const next = await request("config"); if (JSON.stringify(next) !== JSON.stringify(config)) adopt(next); if (!connected) feedback("버튼을 눌러 PC를 제어하세요"); status(true, next.transport === "wifi" ? "PC 연결됨 · Wi-Fi" : next.transport === "bluetooth" ? "PC 연결됨 · Bluetooth" : "PC 연결됨 · USB"); }
  catch (error) { status(false, "PC 연결 확인 필요"); feedback(error.message); } finally { checking = false; }
}
function renderWindows(rows) {
  const incoming = new Map(rows.map(row => [row.id, row]));
  windowOrder = windowOrder.filter(id => incoming.has(id));
  rows.forEach(row => { if (!windowOrder.includes(row.id)) windowOrder.push(row.id); });
  rows = windowOrder.map(id => incoming.get(id));
  const signature = JSON.stringify(rows); if (signature === windowSignature) return; windowSignature = signature;
  const list = $("windows"), left = list.scrollLeft, existing = new Map([...list.querySelectorAll(".window")].map(b => [b.dataset.id, b]));
  list.querySelectorAll(".empty-windows").forEach(n => n.remove());
  existing.forEach((b, id) => { if (!incoming.has(id)) b.remove(); }); $("window-count").textContent = rows.length;
  if (!rows.length) { const p = document.createElement("p"); p.className = "empty-windows"; p.textContent = "열린 PC 창이 없습니다"; list.append(p); }
  rows.forEach(row => {
    let b = existing.get(row.id);
    if (!b) {
      b = document.createElement("button"); b.className = "window"; b.dataset.id = row.id;
      for (const cls of ["window-icon", "window-app", "window-title", "window-indicator"]) { const n = document.createElement("span"); n.className = cls; b.append(n); }
      b.addEventListener("click", async () => {
        if (!native) return feedback("창 전환 · 미리보기"); if (b.dataset.busy) return; b.dataset.busy = "1"; b.disabled = true;
        try { await request("focus", {id: row.id, request_id: requestId()}); feedback(b.querySelector(".window-app").textContent + " 창 열림"); }
        catch (error) { feedback(error.message); } finally { delete b.dataset.busy; b.disabled = !connected; checkWindows(); }
      }); list.append(b);
    }
    b.classList.toggle("active", row.active); b.disabled = Boolean(b.dataset.busy) || (native && !connected);
    appGraphic(b.querySelector(".window-icon"), row.image, "desktop");
    b.setAttribute("aria-label", row.title + (row.minimized ? " · 최소화됨 · 창 열기" : " · 창 열기"));
    b.querySelector(".window-app").textContent = row.app; b.querySelector(".window-title").textContent = row.title;
    b.querySelector(".window-indicator").textContent = row.active ? "●" : row.minimized ? "―" : "";
  }); list.scrollLeft = left;
}
async function checkWindows() {
  if (!native || !connected || windowChecking || document.hidden || !$("editor").hidden || (window.DeckInput && window.DeckInput.mode !== "panel")) return; windowChecking = true;
  try { const result = await request("windows"); renderWindows(result.windows); }
  catch (_) {} finally { windowChecking = false; }
}
const iconNames = {plus: "추가", desktop: "PC", windows: "창", note: "메모", folder: "폴더", calculator: "계산기", music: "음악", video: "영상", search: "검색", capture: "캡처", copy: "복사", paste: "붙여넣기", undo: "되돌리기", previous: "이전 곡", play: "재생", next: "다음 곡", "volume-up": "볼륨 +", "volume-down": "볼륨 −", mute: "음소거", back: "뒤로", forward: "앞으로", fullscreen: "전체 화면", refresh: "새로고침", escape: "닫기", settings: "설정"};
Object.keys(ICONS).forEach(name => $("edit-icon").append(option(name, iconNames[name] || name)));
function fields() {
  const kind = $("edit-type").value;
  document.querySelectorAll("[data-fields]").forEach(n => n.hidden = n.dataset.fields !== kind);
  $("focus-option").hidden = !["application", "launch"].includes(kind); $("edit-url").disabled = kind !== "url";
}
function renderApps() {
  const query = $("app-search").value.trim().toLocaleLowerCase(), selected = $("edit-app").value || appSelection;
  const visible = apps.filter(a => (a.name + " " + (a.keywords || "")).toLocaleLowerCase().includes(query)); $("edit-app").replaceChildren();
  $("edit-app").append(option("", visible.length ? "실행할 프로그램 선택" : "검색 결과 없음"));
  visible.forEach(a => $("edit-app").append(option(a.id, a.name))); if (visible.some(a => a.id === selected)) $("edit-app").value = selected;
}
async function loadApps(generation) {
  if (appLoading || $("editor").hidden || generation !== editorGeneration) return; appLoading = true;
  try {
    const result = await request("apps"); if (generation !== editorGeneration) return; apps = result.apps; renderApps();
    $("app-status").textContent = result.loading ? "PC 앱 목록을 불러오는 중… 기본 앱은 바로 선택할 수 있어요." : result.unavailable ? "전체 목록을 불러오지 못했습니다. 실행 파일 경로로 등록할 수 있어요." : `${apps.length}개 앱 · 목록에 없으면 실행 파일 직접 등록을 선택하세요.`;
    if (result.loading) setTimeout(() => loadApps(generation), 1500);
  } catch (error) { if (generation === editorGeneration) $("app-status").textContent = error.message; }
  finally { appLoading = false; if (generation !== editorGeneration && !$("editor").hidden) loadApps(editorGeneration); }
}
async function openEditor(id = "") {
  if (!native) return feedback("버튼 추가·수정은 PC에 연결된 기기에서 사용할 수 있어요"); if (!connected) return feedback("PC를 연결한 뒤 버튼을 편집하세요");
  returnFocus = document.activeElement; $("editor").hidden = false; $("editor-form").reset(); $("editor-message").textContent = "설정 불러오는 중…";
  document.querySelector(".sheet").scrollTop = 0; document.querySelector(".deck").setAttribute("inert", "");
  $("editor-title").textContent = id ? "버튼 편집" : "버튼 추가"; editedId = id; editorBusy = true; $("save").disabled = true;
  $("delete").hidden = !id; $("delete").textContent = "삭제"; $("delete").disabled = true; delete $("delete").dataset.confirm;
  const generation = ++editorGeneration;
  try {
    const data = await request("editor"); if (generation !== editorGeneration) return; if (!data.writable) throw new Error("PC 프로그램이 시험 모드로 실행 중입니다.");
    $("edit-profile").replaceChildren(); data.profiles.forEach(p => $("edit-profile").append(option(p.id, p.name)));
    if (!data.profiles.some(p => p.id === "custom") && data.profiles.length < 6) $("edit-profile").append(option("custom", "내 버튼"));
    $("edit-profile").value = id ? active : data.profiles.some(p => p.id === "custom") || data.profiles.length < 6 ? "custom" : active;
    const a = id ? data.actions[id] : {label: "", icon: "desktop", tone: "blue", type: "application", focus_existing: true}; if (!a) throw new Error("이미 삭제된 버튼입니다.");
    $("edit-label").value = a.label; $("edit-icon").value = a.icon || "plus"; $("edit-tone").value = a.tone || "slate";
    let kind = a.type; appSelection = "";
    if (kind === "app") { kind = "application"; appSelection = a.catalog_id || ""; }
    if (kind === "launch" && !(a.args || []).length && ["notepad.exe", "calc.exe", "explorer.exe", "mspaint.exe"].includes(a.executable)) { kind = "application"; appSelection = "builtin-" + a.executable.slice(0, -4); }
    $("edit-type").value = kind; $("edit-exe").value = a.executable || ""; $("edit-args").value = a.args && a.args.length ? JSON.stringify(a.args) : "";
    $("edit-focus").checked = a.focus_existing !== false; $("edit-keys").value = (a.keys || []).join("+"); $("edit-media").value = a.key || "PLAY_PAUSE"; $("edit-url").value = a.url || "";
    $("editor-message").textContent = ""; fields(); renderApps(); loadApps(generation); $("editor-close").focus();
    editorBusy = false; $("save").disabled = false; $("delete").disabled = false;
  } catch (error) { $("editor-message").textContent = error.message; }
}
function closeEditor() {
  if (editorBusy && $("save").textContent === "저장 중…") return;
  ++editorGeneration; $("editor").hidden = true; editorBusy = false; document.querySelector(".deck").removeAttribute("inert");
  if (returnFocus && returnFocus.isConnected) returnFocus.focus(); else $("add").focus(); checkWindows();
}
$("editor-form").addEventListener("submit", async event => {
  event.preventDefault(); if (editorBusy) return;
  const type = $("edit-type").value, a = {id: editedId, label: $("edit-label").value.trim(), icon: $("edit-icon").value, tone: $("edit-tone").value, type};
  if (!a.label) { $("editor-message").textContent = "버튼 이름을 입력하세요."; $("edit-label").focus(); return; }
  if (type === "application") { a.application_id = $("edit-app").value; a.focus_existing = $("edit-focus").checked; }
  else if (type === "launch") {
    a.executable = $("edit-exe").value.trim(); a.focus_existing = $("edit-focus").checked;
    try { a.args = $("edit-args").value.trim() ? JSON.parse($("edit-args").value) : []; } catch (_) { $("editor-message").textContent = '실행 인수를 ["--option", "value"] 형태로 입력하세요.'; return; }
  } else if (type === "hotkey") a.keys = $("edit-keys").value.toUpperCase().split("+").map(k => k.trim()).filter(Boolean);
  else if (type === "media") a.key = $("edit-media").value; else a.url = $("edit-url").value.trim();
  editorBusy = true; $("save").disabled = true; $("delete").disabled = true; $("save").textContent = "저장 중…";
  try {
    const profile = $("edit-profile").value, result = await request("save", {action: a, profile_id: profile, request_id: requestId()});
    active = profile; localStorage.setItem("profile", active); page = editedId ? page : Math.floor((result.config.profiles.find(p => p.id === active).actions.length - 1) / 12);
    adopt(result.config); editorBusy = false; $("save").textContent = "저장"; closeEditor(); feedback(a.label + " 저장됨");
  } catch (error) { $("editor-message").textContent = error.message; }
  finally { editorBusy = false; $("save").disabled = false; $("delete").disabled = false; $("save").textContent = "저장"; }
});
$("delete").addEventListener("click", async () => {
  if (editorBusy || !editedId) return;
  if (!$("delete").dataset.confirm) { $("delete").dataset.confirm = "1"; $("delete").textContent = "삭제 확인"; $("editor-message").textContent = "이 버튼을 모든 모음에서 삭제할까요? 다시 누르면 삭제됩니다."; return; }
  editorBusy = true; $("delete").disabled = true; $("save").disabled = true;
  try { const result = await request("delete", {id: editedId, request_id: requestId()}); adopt(result.config); editorBusy = false; closeEditor(); feedback("버튼 삭제됨"); }
  catch (error) { $("editor-message").textContent = error.message; } finally { editorBusy = false; $("delete").disabled = false; $("save").disabled = false; }
});
$("edit-type").addEventListener("change", fields); $("app-search").addEventListener("input", renderApps);
$("edit-app").addEventListener("change", () => { appSelection = $("edit-app").value; const a = apps.find(a => a.id === appSelection); if (a && !$("edit-label").value.trim()) $("edit-label").value = a.name.slice(0, 40); });
$("editor-close").addEventListener("click", closeEditor); $("editor").addEventListener("click", e => { if (e.target === $("editor")) closeEditor(); });
document.addEventListener("keydown", e => {
  if ($("editor").hidden) return; if (e.key === "Escape") { e.preventDefault(); closeEditor(); }
  if (e.key === "Tab") {
    const nodes = [...$("editor").querySelectorAll("button,input,select,textarea")].filter(n => !n.disabled && n.getClientRects().length), first = nodes[0], last = nodes[nodes.length - 1];
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  }
});
$("page-prev").addEventListener("click", () => { --page; render(); }); $("page-next").addEventListener("click", () => { ++page; render(); });
$("add").innerHTML = icon("plus"); $("add").addEventListener("click", () => openEditor());
$("settings").innerHTML = icon("settings"); $("settings").addEventListener("click", () => native ? NativeDeck.openSettings() : feedback("읽기 전용 미리보기 · 기기에서 PC를 연결하세요"));
$("music").innerHTML = icon("music"); $("music").addEventListener("click", () => native ? NativeDeck.openMusic() : feedback("뮤직플레이어 · 기기에서 사용할 수 있어요"));
function clock() { $("clock").textContent = new Intl.DateTimeFormat("ko-KR", {hour: "2-digit", minute: "2-digit", hour12: false}).format(new Date()); }
clock(); setInterval(clock, 30000); render();
if (native) { check().then(checkWindows); setInterval(check, 5000); setInterval(checkWindows, 2000); }
else {
  status(true, "화면 미리보기"); feedback("미리보기 · 실제 PC 동작은 실행되지 않습니다");
  renderWindows([{id: "preview-note", app: "메모장", title: "아이디어 메모", active: true, minimized: false}, {id: "preview-browser", app: "브라우저", title: "작업 공간", active: false, minimized: true}]);
  fetch("demo.json").then(r => r.json()).then(adopt).catch(() => {});
}
