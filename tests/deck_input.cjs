// Exercise the shipped touchpad event handlers with a slow native channel.
const fs = require('node:fs'), vm = require('node:vm'), assert = require('node:assert/strict');
class Element {
  constructor() { this.events = {}; this.dataset = {}; this.style = {}; this.hidden = false; this.value = ''; this.children = []; this.textContent = ''; }
  addEventListener(name, callback) { (this.events[name] ||= []).push(callback); }
  emit(name, event = {}) { (this.events[name] || []).forEach(f => f({preventDefault() {}, ...event})); }
  setAttribute(name, value) { this[name] = value; }
  append(value) { this.children.push(value); }
  setPointerCapture() {}
  getBoundingClientRect() { return {left: 0, top: 0}; }
}
const ids = {}, get = id => ids[id] ||= new Element();
const tools = ['panel', 'touchpad', 'keyboard'].map(mode => { const e = new Element(); e.dataset.mode = mode; return e; });
const left = new Element(), right = new Element(), taskbar = new Element(); left.dataset.click = 'left'; right.dataset.click = 'right';
let clock = 0, interval, requests = [], blocked = null, feedback = '';
const context = vm.createContext({
  native: true, connected: true, requestId: () => 'session_123', $: get, checkWindows() {},
  request(route, body) { requests.push({route, body}); return blocked || Promise.resolve({ok: true}); },
  feedback(value) { feedback = value; }, performance: {now: () => clock}, localStorage: {getItem() {}, setItem() {}},
  setInterval(callback) { interval = callback; },
  document: {hidden: false, querySelector: () => taskbar, querySelectorAll: selector => selector.includes('tools') ? tools : [left, right], createElement: () => new Element(), addEventListener() {}},
  window: {addEventListener() {}}
});
vm.runInContext(fs.readFileSync(require('node:path').join(__dirname, '../frontdeck/ui/input.js'), 'utf8'), context);
context.DeckInput = context.window.DeckInput;
const pad = get('touchpad'), settle = () => new Promise(resolve => setImmediate(resolve));
const pointer = (type, id, x, y) => pad.emit(type, {pointerId: id, clientX: x, clientY: y});
(async () => {
  tools[1].emit('click');
  let release; blocked = new Promise(resolve => release = resolve);
  pointer('pointerdown', 1, 100, 100); pointer('pointermove', 1, 110, 120); interval();
  assert.equal(requests.length, 1); assert.equal(requests[0].body.dx, 20); assert.equal(requests[0].body.dy, 40);
  pointer('pointermove', 1, 130, 140); for (let n = 0; n < 100; n++) interval();
  assert.equal(requests.length, 1, 'At most one move is in flight; slow radio cannot flood the native queue');
  blocked = null; release({ok: true}); await settle(); interval(); await settle();
  assert.equal(requests[1].body.dx, 40); assert.equal(requests[1].body.dy, 40, 'Pending movement is preserved');
  clock = 1000; pointer('pointerup', 1, 130, 140); assert.equal(requests.length, 2, 'A swipe never clicks');
  clock = 1100; pointer('pointerdown', 1, 100, 100); clock += 100; pointer('pointerup', 1, 100, 100); await settle();
  assert.equal(requests.at(-1).body.button, 'left');
  pointer('pointerdown', 1, 100, 100); pointer('pointerdown', 2, 200, 100);
  pointer('pointermove', 1, 100, 120); pointer('pointermove', 2, 200, 120); interval(); await settle();
  assert.equal(requests.at(-1).body.kind, 'wheel'); assert.equal(requests.at(-1).body.vertical, 100);
  clock += 1000; pointer('pointerup', 1, 100, 120); pointer('pointerup', 2, 200, 120);
  const count = requests.length; clock += 100;
  pointer('pointerdown', 1, 100, 100); pointer('pointerdown', 2, 200, 100); clock += 100;
  pointer('pointerup', 1, 100, 100); pointer('pointerup', 2, 200, 100); await settle();
  assert.equal(requests.length, count + 1); assert.equal(requests.at(-1).body.button, 'right');
  pointer('pointerdown', 1, 0, 0); pointer('pointermove', 1, 50, 50); pointer('pointercancel', 1, 50, 50); interval(); await settle();
  assert.equal(requests.length, count + 1, 'Cancelled gestures discard unsent input');
  tools[2].emit('click'); const field = get('pc-text'); field.value = '한글 😀';
  field.emit('compositionstart'); get('send-text').emit('click'); await settle(); assert.equal(feedback, '한글 입력을 먼저 완성하세요');
  field.emit('compositionend'); get('send-text').emit('click'); await settle();
  assert.equal(requests.at(-1).body.text, '한글 😀'); assert.equal(field.value, '');
  assert.deepEqual(requests.map(r => r.body.seq), requests.map((_, n) => n + 1));
  context.connected = false; get('send-text').emit('click'); await settle();
  console.log('Touchpad coalescing, tap/scroll gestures, cancellation and committed Unicode input passed.');
})().catch(error => { console.error(error); process.exitCode = 1; });
