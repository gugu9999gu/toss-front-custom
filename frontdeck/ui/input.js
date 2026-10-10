"use strict";
(() => {
  const session = requestId(), touches = new Map(), modifiers = new Set();
  let seq = 0, inputBusy = false, movement = {dx: 0, dy: 0, vertical: 0, horizontal: 0};
  let start = 0, distance = 0, fingers = 0, speed = Number(localStorage.getItem("pointer-speed")) || 2;
  let composing = false;
  const pad = $("touchpad"), dot = $("touch-dot"), cap = (x, bound) => Math.max(-bound, Math.min(bound, Math.round(x)));
  async function send(kind, values) {
    if (document.hidden || (native && !connected)) throw new Error("PC를 연결한 뒤 사용하세요");
    if (!native) { feedback("PC 입력 · 미리보기"); return {ok: true}; }
    return request("input", {session, seq: ++seq, kind, ...values});
  }
  const cancel = () => { touches.clear(); movement = {dx: 0, dy: 0, vertical: 0, horizontal: 0}; dot.hidden = true; };
  window.DeckInput = {mode: "panel", cancel};
  function select(mode) {
    cancel(); DeckInput.mode = mode;
    document.querySelectorAll("#tools button").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.mode === mode)));
    $("profiles").hidden = $("buttons").hidden = document.querySelector(".taskbar").hidden = mode !== "panel";
    $("pager").hidden = mode !== "panel" || Number($("pager").dataset.pages) <= 1;
    $("touchpad-panel").hidden = mode !== "touchpad"; $("keyboard-panel").hidden = mode !== "keyboard";
    feedback(mode === "touchpad" ? "한 손가락 이동 · 두 손가락 스크롤" : mode === "keyboard" ? "PC에서 입력할 창을 먼저 선택하세요" : "버튼을 눌러 PC를 제어하세요");
    if (mode === "panel") checkWindows();
  }
  document.querySelectorAll("#tools button").forEach(b => b.addEventListener("click", () => select(b.dataset.mode)));
  $("pointer-speed").value = speed; $("speed-value").textContent = speed + "×";
  $("pointer-speed").addEventListener("input", e => { speed = Number(e.target.value); $("speed-value").textContent = speed + "×"; localStorage.setItem("pointer-speed", speed); });
  function center() { const points = [...touches.values()]; return {x: points.reduce((n, p) => n + p.x, 0) / points.length, y: points.reduce((n, p) => n + p.y, 0) / points.length}; }
  pad.addEventListener("pointerdown", e => {
    e.preventDefault(); if (native && !connected) return;
    if (!touches.size) { start = performance.now(); distance = 0; fingers = 0; }
    touches.set(e.pointerId, {x: e.clientX, y: e.clientY}); fingers = Math.max(fingers, touches.size); pad.setPointerCapture(e.pointerId);
  });
  pad.addEventListener("pointermove", e => {
    if (!touches.has(e.pointerId)) return; e.preventDefault();
    const before = center(); touches.set(e.pointerId, {x: e.clientX, y: e.clientY}); const after = center();
    const dx = after.x - before.x, dy = after.y - before.y; distance += Math.hypot(dx, dy);
    if (touches.size === 1 && fingers === 1) { movement.dx += dx * speed; movement.dy += dy * speed; }
    else if (touches.size === 2) { movement.vertical += dy * 5; movement.horizontal += dx * 5; }
    const bounds = pad.getBoundingClientRect(); dot.hidden = false;
    dot.style.left = (after.x - bounds.left) + "px"; dot.style.top = (after.y - bounds.top) + "px";
  });
  pad.addEventListener("pointerup", e => {
    if (!touches.has(e.pointerId)) return; touches.delete(e.pointerId);
    if (!touches.size) { dot.hidden = true; if (distance < 8 && performance.now() - start < 450 && fingers <= 2) click(fingers === 2 ? "right" : "left"); }
  });
  pad.addEventListener("pointercancel", cancel); pad.addEventListener("contextmenu", e => e.preventDefault());
  async function flush() {
    if (inputBusy || document.hidden || DeckInput.mode !== "touchpad" || (native && !connected)) return;
    const wheel = movement.vertical || movement.horizontal;
    const kind = wheel ? "wheel" : "move", names = wheel ? ["vertical", "horizontal"] : ["dx", "dy"], bound = wheel ? 1200 : 512;
    const values = Object.fromEntries(names.map(k => [k, cap(movement[k], bound)]));
    if (!Object.values(values).some(Boolean)) return;
    names.forEach(k => movement[k] -= values[k]); inputBusy = true;
    try { await send(kind, values); }
    catch (error) { cancel(); feedback(error.message); }
    finally { inputBusy = false; }
  }
  setInterval(flush, 32);
  async function click(button) { try { await flush(); await send("click", {button}); } catch (error) { feedback(error.message); } }
  document.querySelectorAll("[data-click]").forEach(b => b.addEventListener("click", () => click(b.dataset.click)));
  const rows = [
    [["ESC", "Esc"], ["TAB", "Tab"], ["HOME", "Home"], ["END", "End"], ["PAGEUP", "Pg↑"], ["PAGEDOWN", "Pg↓"]],
    "1234567890".split(""), "QWERTYUIOP".split(""), "ASDFGHJKL".split(""), "ZXCVBNM".split(""),
    [["CTRL", "Ctrl"], ["ALT", "Alt"], ["SHIFT", "Shift"], ["WIN", "Win"], ["BACKSPACE", "⌫"], ["DELETE", "Del"]],
    [["HANGUL", "한/영"], ["SPACE", "Space"], ["LEFT", "←"], ["UP", "↑"], ["DOWN", "↓"], ["RIGHT", "→"], ["ENTER", "↵"]]
  ];
  rows.forEach(row => {
    const container = document.createElement("div"); container.className = "key-row";
    row.forEach(value => {
      const [key, label] = Array.isArray(value) ? value : [value, value]; const b = document.createElement("button");
      b.className = "input-key"; b.textContent = label; b.dataset.key = key; b.disabled = native && !connected;
      if (["CTRL", "ALT", "SHIFT", "WIN"].includes(key)) {
        b.setAttribute("aria-pressed", "false"); b.addEventListener("click", () => { modifiers.has(key) ? modifiers.delete(key) : modifiers.add(key); b.setAttribute("aria-pressed", String(modifiers.has(key))); });
      } else b.addEventListener("click", async () => {
        if (b.dataset.busy) return; b.dataset.busy = "1"; b.disabled = true;
        try { await send("key", {keys: [...modifiers, key]}); }
        catch (error) { feedback(error.message); }
        finally { delete b.dataset.busy; b.disabled = native && !connected; }
      }); container.append(b);
    }); $("keyboard-keys").append(container);
  });
  $("pc-text").addEventListener("compositionstart", () => { composing = true; });
  $("pc-text").addEventListener("compositionend", () => { composing = false; });
  $("send-text").addEventListener("click", async () => {
    const b = $("send-text"), field = $("pc-text"), text = field.value;
    if (composing) return feedback("한글 입력을 먼저 완성하세요"); if (!text || b.dataset.busy) return;
    b.dataset.busy = "1"; b.disabled = true;
    try { await send("text", {text}); if (field.value === text) field.value = ""; feedback("PC로 텍스트를 보냈습니다"); }
    catch (error) { feedback(error.message + " · 일부 입력됐을 수 있으니 PC 화면을 확인하세요"); }
    finally { delete b.dataset.busy; b.disabled = native && !connected; }
  });
  document.addEventListener("visibilitychange", () => { if (document.hidden) cancel(); }); window.addEventListener("blur", cancel);
})();
