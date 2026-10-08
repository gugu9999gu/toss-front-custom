"use strict";
const ICONS = {
  settings: '<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="2" fill="currentColor"/><circle cx="16" cy="12" r="2" fill="currentColor"/><circle cx="10" cy="18" r="2" fill="currentColor"/>',
  note: '<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 8h6M9 12h6M9 16h4"/>',
  calculator: '<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M8 7h8M8 11h1M12 11h1M16 11h.1M8 15h1M12 15h1M16 15h.1M8 18h1M12 18h1M16 18h.1"/>',
  folder: '<path d="M3 7V5a2 2 0 0 1 2-2h4l3 4h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z"/>',
  desktop: '<rect x="3" y="3" width="18" height="13" rx="2"/><path d="M8 21h8M12 16v5"/>',
  windows: '<rect x="2" y="5" width="14" height="14" rx="2"/><path d="M18 5h3v14h-3M6 9h6"/>',
  capture: '<path d="M8 3H3v5M16 3h5v5M3 16v5h5M21 16v5h-5"/><rect x="7" y="7" width="10" height="10" rx="2"/>',
  copy: '<rect x="8" y="8" width="12" height="13" rx="2"/><path d="M16 5V3H4v13h1"/>',
  paste: '<rect x="5" y="5" width="14" height="16" rx="2"/><rect x="9" y="3" width="6" height="4" rx="1"/><path d="M9 12h6M9 16h5"/>',
  undo: '<path d="m8 4-5 5 5 5M3 9h10a7 7 0 0 1 0 14"/>',
  search: '<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/>',
  plus: '<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M12 7v10M7 12h10"/>',
  escape: '<path d="m7 7 10 10M17 7 7 17"/>',
  previous: '<path d="M5 4v16M19 5 8 12l11 7Z"/>',
  play: '<path d="m5 5 9 7-9 7Z M18 6v12M21 6v12"/>',
  next: '<path d="M19 4v16M5 5l11 7-11 7Z"/>',
  'volume-down': '<path d="M11 4 6 8H2v8h4l5 4ZM16 12h6"/>',
  mute: '<path d="M11 4 6 8H2v8h4l5 4ZM16 9l6 6M22 9l-6 6"/>',
  'volume-up': '<path d="M11 4 6 8H2v8h4l5 4ZM16 12h6M19 9v6"/>',
  back: '<path d="m10 5-7 7 7 7M3 12h18"/>',
  forward: '<path d="m14 5 7 7-7 7M21 12H3"/>',
  fullscreen: '<path d="M9 3H3v6M15 3h6v6M3 15v6h6M21 15v6h-6"/>',
  refresh: '<path d="M20 8a9 9 0 1 0 1 7M20 3v5h-5"/>',
  video: '<rect x="2" y="5" width="20" height="14" rx="4"/><path d="m10 9 5 3-5 3Z"/>'
};
function icon(name) { return '<svg viewBox="0 0 24 24" aria-hidden="true">' + (ICONS[name] || ICONS.plus) + '</svg>'; }
