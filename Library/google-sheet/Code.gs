const TOKEN = 'sIiNCldrubvUZr72Mc-7au_-UYrTsNgv';

const SHEET_NAME = 'Books';

const SYS = { id: 'ID', added: 'Added', modified: 'Modified', addedBy: 'Added by', modifiedBy: 'Modified by' };

const CORE = {
  title:    { name: 'Title',    aliases: ['title', 'titlu', 'titlul', 'book', 'book title', 'carte', 'nume carte', 'denumire'] },
  author:   { name: 'Author',   aliases: ['author', 'authors', 'autor', 'autori', 'autorul', 'writer', 'scriitor'] },
  year:     { name: 'Year',     aliases: ['year', 'an', 'anul', 'an aparitie', 'an aparitie tiparit', 'anul aparitiei', 'year published', 'published', 'publication year'] },
  location: { name: 'Location', aliases: ['location', 'locatie', 'locatia', 'loc', 'where', 'unde', 'place', 'adresa', 'casa'] },
  comments: { name: 'Comments', aliases: ['comments', 'comment', 'notes', 'note', 'other notes', 'observatii', 'comentarii', 'mentiuni', 'remarks', 'detalii'] },
};
const CORE_KEYS = Object.keys(CORE);
const SYS_KEYS = Object.keys(SYS);

function doGet(e) {
  const p = (e && e.parameter) || {};
  if (p.token !== TOKEN) return json_({ ok: false, error: 'Bad token' });
  try {
    switch (p.action) {
      case 'version': return json_({ ok: true, version: getVersion_() });
      case 'list':    return json_(withLock_(list_));
      default:        return json_({ ok: true, message: 'Family Library backend is running' });
    }
  } catch (err) {
    return json_({ ok: false, error: String(err && err.message || err) });
  }
}

function doPost(e) {
  let body;
  try {
    body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
  } catch (err) {
    return json_({ ok: false, error: 'Invalid JSON' });
  }
  if (body.token !== TOKEN) return json_({ ok: false, error: 'Bad token' });
  const user = cleanName_(body.user);
  try {
    return json_(withLock_(function () {
      switch (body.action) {
        case 'add':    return add_(body.book || {}, user);
        case 'update': return update_(body.id, body.book || {}, user);
        case 'delete': return delete_(body.id);
        case 'import': return import_(body.headers || [], body.rows || [], user);
        default:       return { ok: false, error: 'Unknown action: ' + body.action };
      }
    }));
  } catch (err) {
    return json_({ ok: false, error: String(err && err.message || err) });
  }
}

function list_() {
  const sheet = getSheet_();
  const layout = ensureColumns_(sheet);
  assignMissingIds_(sheet, layout);
  return {
    ok: true,
    version: getVersion_(),
    sheetUrl: SpreadsheetApp.getActiveSpreadsheet().getUrl(),
    extraColumns: layout.extras.map(function (c) { return layout.headers[c]; }),
    books: readBooks_(sheet, layout),
  };
}

function add_(book, user) {
  const sheet = getSheet_();
  const layout = ensureColumns_(sheet);
  const row = new Array(layout.headers.length).fill('');
  const id = Utilities.getUuid();
  fillRow_(row, layout, book);
  const now = new Date();
  row[layout.sys.id] = id;
  row[layout.sys.added] = now;
  row[layout.sys.addedBy] = user;
  row[layout.sys.modified] = now;
  row[layout.sys.modifiedBy] = user;
  sheet.getRange(sheet.getLastRow() + 1, 1, 1, row.length).setValues([row]);
  return { ok: true, id: id, version: bumpVersion_() };
}

function update_(id, book, user) {
  const sheet = getSheet_();
  const layout = ensureColumns_(sheet);
  const r = findRow_(sheet, layout, id);
  if (!r) return { ok: false, error: 'This book no longer exists (someone may have deleted it).' };
  const range = sheet.getRange(r, 1, 1, layout.headers.length);
  const row = range.getValues()[0];
  fillRow_(row, layout, book);
  row[layout.sys.modified] = new Date();
  row[layout.sys.modifiedBy] = user;
  range.setValues([row]);
  return { ok: true, id: id, version: bumpVersion_() };
}

function delete_(id) {
  const sheet = getSheet_();
  const layout = ensureColumns_(sheet);
  const r = findRow_(sheet, layout, id);
  if (r) sheet.deleteRow(r);
  return { ok: true, version: bumpVersion_() };
}

function import_(headers, rows, user) {
  const sheet = getSheet_();
  let layout = ensureColumns_(sheet);

  const map = [];
  let idFileCol = -1;
  const newHeaders = [];
  headers.forEach(function (h, i) {
    const n = norm_(h);
    if (!n) { map[i] = -1; return; }
    if (n === norm_(SYS.id)) { idFileCol = i; map[i] = -1; return; }
    if (SYS_KEYS.some(function (k) { return norm_(SYS[k]) === n; })) { map[i] = -1; return; }
    const core = coreKeyFor_(n);
    if (core) { map[i] = layout.core[core]; return; }
    let c = layout.headers.findIndex(function (x) { return norm_(x) === n; });
    if (c < 0) {
      c = layout.headers.length + newHeaders.length;
      newHeaders.push(String(h).trim());
    }
    map[i] = c;
  });
  if (newHeaders.length) {
    sheet.getRange(1, layout.headers.length + 1, 1, newHeaders.length).setValues([newHeaders]).setFontWeight('bold');
    layout = ensureColumns_(sheet);
  }

  const width = layout.headers.length;
  const lastRow = sheet.getLastRow();
  const existing = lastRow > 1 ? sheet.getRange(2, 1, lastRow - 1, width).getValues() : [];
  const byId = {};
  const keys = {};
  existing.forEach(function (r, i) {
    if (r[layout.sys.id]) byId[String(r[layout.sys.id])] = i;
    keys[dupKey_(r[layout.core.title], r[layout.core.author], r[layout.core.location])] = true;
  });

  const now = new Date();
  const toAppend = [];
  let updated = 0, skipped = 0;
  rows.forEach(function (cells) {
    if (!cells || !cells.some(function (c) { return String(c == null ? '' : c).trim() !== ''; })) return;
    const values = new Array(width).fill('');
    cells.forEach(function (v, i) { if (map[i] >= 0) values[map[i]] = cellValue_(v); });
    if (String(values[layout.core.title]).trim() === '') { skipped++; return; }

    const fileId = idFileCol >= 0 ? String(cells[idFileCol] || '').trim() : '';
    if (fileId && byId[fileId] !== undefined) {
      const idx = byId[fileId];
      const row = existing[idx];
      let changed = false;
      map.forEach(function (c) {
        if (c >= 0 && String(row[c]) !== String(values[c])) { row[c] = values[c]; changed = true; }
      });
      if (changed) {
        row[layout.sys.modified] = now;
        row[layout.sys.modifiedBy] = user;
        sheet.getRange(idx + 2, 1, 1, width).setValues([row]);
        updated++;
      } else {
        skipped++;
      }
      return;
    }

    const key = dupKey_(values[layout.core.title], values[layout.core.author], values[layout.core.location]);
    if (keys[key]) { skipped++; return; }
    keys[key] = true;
    values[layout.sys.id] = Utilities.getUuid();
    values[layout.sys.added] = now;
    values[layout.sys.addedBy] = user;
    values[layout.sys.modified] = now;
    values[layout.sys.modifiedBy] = user;
    toAppend.push(values);
  });

  if (toAppend.length) {
    sheet.getRange(sheet.getLastRow() + 1, 1, toAppend.length, width).setValues(toAppend);
  }
  return { ok: true, added: toAppend.length, updated: updated, skipped: skipped, version: bumpVersion_() };
}

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('Family Library')
    .addItem('Set up sheet', 'setup')
    .addToUi();
}

function setup() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = getSheet_();
  const layout = ensureColumns_(sheet);
  assignMissingIds_(sheet, layout);
  const rows = Math.max(sheet.getMaxRows() - 1, 1);
  [layout.sys.added, layout.sys.modified].forEach(function (c) {
    sheet.getRange(2, c + 1, rows, 1).setNumberFormat('yyyy-mm-dd hh:mm');
  });

  const hasTrigger = ScriptApp.getProjectTriggers().some(function (t) { return t.getHandlerFunction() === 'onSheetChange'; });
  if (!hasTrigger) ScriptApp.newTrigger('onSheetChange').forSpreadsheet(ss).onChange().create();
  bumpVersion_();
  ss.toast('Family Library is set up. Now deploy it as a Web App (see SETUP.md).', 'Family Library', 8);
}

function onEdit(e) {
  if (!e || !e.range) return;
  const sheet = e.range.getSheet();
  if (sheet.getSheetId() !== getSheet_().getSheetId()) return;
  const first = Math.max(e.range.getRow(), 2);
  const last = Math.min(e.range.getLastRow(), first + 500);
  if (last < first) { bumpVersion_(); return; }
  const layout = ensureColumns_(sheet);

  const sysCols = SYS_KEYS.map(function (k) { return layout.sys[k] + 1; });
  let userColTouched = false;
  for (let c = e.range.getColumn(); c <= e.range.getLastColumn(); c++) {
    if (sysCols.indexOf(c) < 0) { userColTouched = true; break; }
  }
  if (!userColTouched) return;
  const range = sheet.getRange(first, 1, last - first + 1, layout.headers.length);
  const rows = range.getValues();
  const now = new Date();
  rows.forEach(function (r) {
    if (!hasUserContent_(r, layout)) return;
    if (!r[layout.sys.id]) {
      r[layout.sys.id] = Utilities.getUuid();
      r[layout.sys.added] = now;
      r[layout.sys.addedBy] = 'Sheet';
    }
    r[layout.sys.modified] = now;
    r[layout.sys.modifiedBy] = 'Sheet';
  });
  range.setValues(rows);
  bumpVersion_();
}

function onSheetChange() {
  bumpVersion_();
}

function getSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  return ss.getSheetByName(SHEET_NAME) || ss.getSheets()[0];
}

function ensureColumns_(sheet) {
  const lastCol = sheet.getLastColumn();
  const headers = lastCol > 0 ? sheet.getRange(1, 1, 1, lastCol).getDisplayValues()[0].map(String) : [];

  let renamed = false;
  for (let i = 0; i < headers.length; i++) {
    if (headers[i].trim() === '') {
      let n = 'Alte detalii', k = 2;
      while (headers.some(function (h) { return norm_(h) === norm_(n); })) n = 'Alte detalii ' + (k++);
      headers[i] = n;
      renamed = true;
    }
  }
  if (renamed) sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
  const toAdd = [];
  const core = {};
  CORE_KEYS.forEach(function (k) {
    let c = headers.findIndex(function (h) { return coreKeyFor_(norm_(h)) === k; });
    if (c < 0) { c = headers.length + toAdd.length; toAdd.push(CORE[k].name); }
    core[k] = c;
  });
  const sys = {};
  SYS_KEYS.forEach(function (k) {
    let c = headers.findIndex(function (h) { return norm_(h) === norm_(SYS[k]); });
    if (c < 0) { c = headers.length + toAdd.length; toAdd.push(SYS[k]); }
    sys[k] = c;
  });
  if (toAdd.length) {
    sheet.getRange(1, headers.length + 1, 1, toAdd.length).setValues([toAdd]);
    toAdd.forEach(function (h) { headers.push(h); });
    sheet.getRange(1, 1, 1, headers.length).setFontWeight('bold');
    sheet.setFrozenRows(1);
  }
  const special = {};
  CORE_KEYS.forEach(function (k) { special[core[k]] = true; });
  SYS_KEYS.forEach(function (k) { special[sys[k]] = true; });
  const extras = [];
  headers.forEach(function (h, i) { if (!special[i] && h.trim() !== '') extras.push(i); });
  return { headers: headers, core: core, sys: sys, extras: extras };
}

function assignMissingIds_(sheet, layout) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return;
  const range = sheet.getRange(2, 1, lastRow - 1, layout.headers.length);
  const rows = range.getValues();
  const now = new Date();
  let changed = false;
  rows.forEach(function (r) {
    if (!r[layout.sys.id] && hasUserContent_(r, layout)) {
      r[layout.sys.id] = Utilities.getUuid();
      if (!r[layout.sys.added]) r[layout.sys.added] = now;
      if (!r[layout.sys.modified]) r[layout.sys.modified] = now;
      changed = true;
    }
  });
  if (changed) range.setValues(rows);
}

function readBooks_(sheet, layout) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return [];
  const range = sheet.getRange(2, 1, lastRow - 1, layout.headers.length);
  const values = range.getValues();
  const display = range.getDisplayValues();
  const books = [];
  values.forEach(function (r, i) {
    const d = display[i];
    if (!r[layout.sys.id] || !hasUserContent_(r, layout)) return;
    const extra = {};
    layout.extras.forEach(function (c) { extra[layout.headers[c]] = String(d[c]).trim(); });
    books.push({
      id: String(r[layout.sys.id]),
      title: String(d[layout.core.title]).trim(),
      author: String(d[layout.core.author]).trim(),
      year: String(d[layout.core.year]).trim(),
      location: String(d[layout.core.location]).trim(),
      comments: String(d[layout.core.comments]).trim(),
      extra: extra,
      added: toMs_(r[layout.sys.added]),
      modified: toMs_(r[layout.sys.modified]),
      addedBy: String(r[layout.sys.addedBy] || ''),
      modifiedBy: String(r[layout.sys.modifiedBy] || ''),
    });
  });
  return books;
}

function fillRow_(row, layout, book) {
  CORE_KEYS.forEach(function (k) {
    if (book[k] !== undefined && book[k] !== null) row[layout.core[k]] = cellValue_(book[k]);
  });
  const extra = book.extra || {};
  layout.extras.forEach(function (c) {
    const h = layout.headers[c];
    if (Object.prototype.hasOwnProperty.call(extra, h)) row[c] = cellValue_(extra[h]);
  });
}

function findRow_(sheet, layout, id) {
  if (!id) return 0;
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return 0;
  const ids = sheet.getRange(2, layout.sys.id + 1, lastRow - 1, 1).getValues();
  for (let i = 0; i < ids.length; i++) if (String(ids[i][0]) === String(id)) return i + 2;
  return 0;
}

function hasUserContent_(r, layout) {
  const sysIdx = SYS_KEYS.map(function (k) { return layout.sys[k]; });
  for (let i = 0; i < layout.headers.length; i++) {
    if (sysIdx.indexOf(i) >= 0) continue;
    if (String(r[i] == null ? '' : r[i]).trim() !== '') return true;
  }
  return false;
}

function cellValue_(v) {
  if (v === null || v === undefined) return '';
  if (typeof v === 'number') return v;
  let s = String(v).trim();
  if (/^-?\d{1,12}(\.\d+)?$/.test(s) && !/^0\d/.test(s)) return Number(s);
  if (/^[=+\-@]/.test(s)) s = "'" + s;
  return s;
}

function coreKeyFor_(normalized) {
  if (!normalized) return null;
  for (let i = 0; i < CORE_KEYS.length; i++) {
    const k = CORE_KEYS[i];
    if (CORE[k].aliases.some(function (a) { return norm_(a) === normalized; })) return k;
  }

  for (let i = 0; i < CORE_KEYS.length; i++) {
    const k = CORE_KEYS[i];
    if (CORE[k].aliases.some(function (a) { const n = norm_(a); return n.length > 3 && normalized.indexOf(n + ' ') === 0; })) return k;
  }
  if (/^(an|anul|year)( |$)/.test(normalized)) return 'year';
  return null;
}

function norm_(s) {
  return String(s == null ? '' : s)
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

function dupKey_(title, author, location) {
  return norm_(title) + '|' + norm_(author) + '|' + norm_(location);
}

function cleanName_(s) {
  const n = String(s == null ? '' : s).trim();
  return n ? n.substring(0, 40) : 'Someone';
}

function toMs_(v) {
  if (v instanceof Date) return v.getTime();
  if (typeof v === 'number') return v;
  const t = Date.parse(String(v || ''));
  return isNaN(t) ? 0 : t;
}

function getVersion_() {
  return PropertiesService.getScriptProperties().getProperty('version') || '0';
}

function bumpVersion_() {
  const v = String(Date.now());
  PropertiesService.getScriptProperties().setProperty('version', v);
  return v;
}

function withLock_(fn) {
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try { return fn(); } finally { lock.releaseLock(); }
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
