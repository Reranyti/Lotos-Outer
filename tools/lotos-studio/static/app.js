"use strict";

/* ------------------------------------------------------------------ helpers */
const $ = (s, r = document) => r.querySelector(s);
function h(tag, attrs, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === false || v == null) continue;
    if (k === "class") e.className = v;
    else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else if (k === "value") e.value = v;
    else if (k === "checked") e.checked = !!v;
    else e.setAttribute(k, v === true ? "" : v);
  }
  for (const c of kids.flat(Infinity)) {
    if (c == null || c === false) continue;
    e.append(c.nodeType ? c : document.createTextNode(String(c)));
  }
  return e;
}
async function api(path, opts) {
  const r = await fetch(path, opts);
  if (!r.ok) throw new Error((await r.text()) || r.status);
  return r.json();
}
const post = (path, obj) => api(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(obj) });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const esc = s => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const lc = t => (t || "").toLowerCase();
function store(k, v) { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { return null; } }
async function copyText(t) {
  try { await navigator.clipboard.writeText(t); } catch (e) {
    const ta = h("textarea", { value: t }); document.body.append(ta); ta.select(); document.execCommand("copy"); ta.remove();
  }
  setSave("скопировано");
}
function setSave(text, err) { const e = $("#savestate"); e.textContent = text; e.className = "savestate" + (err ? " err" : ""); }

/* ------------------------------------------------------------------ presets */
const P = {
  locations: ["Лаборатория", "Лестничная клетка", "Кабинет директора", "Камера", "Тёмная комната", "Коридор", "Лимб", "Зал Архитекторов", "Снаружи (мир)"],
  characters: ["Рей (игрок)", "Хончо", "Сейри", "Люйдок", "Альтер", "Делантор", "Сержант 418", "Директор лаборатории", "Архитекторы", "Фигура с клинками"],
  sizes: ["Общий план", "Средний план", "Крупный план", "Деталь (рука, предмет)", "Через плечо"],
  angles: ["На уровне глаз", "Сверху", "Снизу", "Со спины", "Сбоку", "От первого лица", "Голландский угол"],
  moves: ["Статика", "Медленный наезд", "Медленный отъезд", "Ручная камера (дрожь)", "Панорама", "Облёт", "Резкий рывок"],
  emotions: ["", "Спокойствие", "Злость", "Страх", "Пустота", "Боль", "Решимость", "Растерянность", "Ложь"],
  transitions: ["Резкая склейка", "Затемнение", "Вспышка", "Глитч", "Наплыв"],
  lights: [
    { name: "Холодный фиолет (как 13-я концовка)", key: "#6a4cff", fill: "#0a0818" },
    { name: "Красная тревога", key: "#ff2a2a", fill: "#1a0303" },
    { name: "Тёплый кабинет", key: "#ff9a4a", fill: "#1c0e05" },
    { name: "Тьма и один луч", key: "#cfd8ff", fill: "#02030a" },
    { name: "Бирюза лаборатории", key: "#4ad8d0", fill: "#031516" },
    { name: "Белая вспышка", key: "#ffffff", fill: "#1a1a26" },
    { name: "Свой цвет", key: null, fill: null },
  ],
  statuses: { draft: "✎ Черновик", ask: "? Нужно подтвердить", ok: "✔ Подтверждено", redo: "✖ Переделать" },
};

function blankShot(title = "Новый кадр") {
  return {
    id: Math.random().toString(16).slice(2, 10), title, location: "", characters: [], action: "",
    size: P.sizes[1], angle: P.angles[0], move: P.moves[0], lightPreset: P.lights[0].name,
    keyColor: P.lights[0].key, fillColor: P.lights[0].fill, contrast: 70, duration: 4, sound: "", text: "",
    emotion: "", transition: P.transitions[0], notes: "", refs: [], status: "draft", comment: "",
  };
}
const norm = s => Object.assign(blankShot(), s);

/* ------------------------------------------------------------------ colours */
function hex2rgb(x) { x = String(x || "#000000").replace("#", ""); if (x.length === 3) x = x.split("").map(c => c + c).join(""); const n = parseInt(x, 16) || 0; return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
const rgba = (x, a) => { const [r, g, b] = hex2rgb(x); return `rgba(${r},${g},${b},${a})`; };
const mixc = (a, b, t) => { const A = hex2rgb(a), B = hex2rgb(b); return `rgb(${A.map((v, i) => Math.round(v + (B[i] - v) * t)).join(",")})`; };

/* ------------------------------------------------------------------ retelling */
function retell(s, i) {
  const p = [];
  p.push(`Кадр ${i + 1}${s.title ? ` «${s.title}»` : ""}.`);
  if (s.location) p.push(`Место: ${s.location}.`);
  p.push(s.characters.length ? `В кадре: ${s.characters.join(", ")}.` : "В кадре никого нет.");
  if (s.action.trim()) p.push(`Происходит: ${s.action.trim().replace(/[.!]+$/, "")}.`);
  if (s.emotion) p.push(`Эмоция: ${lc(s.emotion)}.`);
  p.push(`Камера: ${lc(s.size)}, ${lc(s.angle)}, ${lc(s.move)}.`);
  p.push(`Свет: ${s.lightPreset === "Свой цвет" ? "свой цвет " + s.keyColor : lc(s.lightPreset)}, контраст ${s.contrast}%.`);
  p.push(`Длительность ${s.duration} с, переход: ${lc(s.transition)}.`);
  if (s.sound.trim()) p.push(`Звук: ${s.sound.trim()}.`);
  if (s.text.trim()) p.push(`Текст на экране: «${s.text.trim()}».`);
  return p.join("\n");
}

/* ------------------------------------------------------------------ preview */
function wrapText(ctx, text, maxW) {
  const words = text.split(/\s+/), lines = []; let line = "";
  for (const w of words) { const t = line ? line + " " + w : w; if (ctx.measureText(t).width > maxW && line) { lines.push(line); line = w; } else line = t; }
  if (line) lines.push(line);
  return lines;
}
function drawPreview(cv, s) {
  const W = cv.width = 640, H = cv.height = 360, ctx = cv.getContext("2d");
  const key = s.keyColor || "#6a4cff", fill = s.fillColor || "#0a0818", con = (s.contrast ?? 70) / 100;
  const ang = s.angle || "", size = s.size || "";
  ctx.fillStyle = "#000"; ctx.fillRect(0, 0, W, H);
  ctx.save();
  if (ang.startsWith("Голландский")) { ctx.translate(W / 2, H / 2); ctx.rotate(-0.14); ctx.scale(1.3, 1.3); ctx.translate(-W / 2, -H / 2); }
  const g = ctx.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, mixc(fill, "#000000", .15)); g.addColorStop(1, mixc(fill, "#000000", .6));
  ctx.fillStyle = g; ctx.fillRect(-W * .3, -H * .3, W * 1.6, H * 1.6);
  const hz = ang.startsWith("Сверху") ? .3 : ang.startsWith("Снизу") ? .84 : .62;
  ctx.fillStyle = mixc(fill, "#000000", .45); ctx.fillRect(-W * .3, H * hz, W * 1.6, H);
  ctx.strokeStyle = rgba(key, .25); ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(-W * .3, H * hz); ctx.lineTo(W * 1.3, H * hz); ctx.stroke();
  const lx = W * (ang.startsWith("Сбоку") ? .18 : .72), ly = H * .14;
  const rg = ctx.createRadialGradient(lx, ly, 0, lx, ly, W * .75);
  rg.addColorStop(0, rgba(key, .25 + .5 * con)); rg.addColorStop(1, rgba(key, 0));
  ctx.fillStyle = rg; ctx.fillRect(-W * .3, -H * .3, W * 1.6, H * 1.6);

  const names = s.characters.slice(0, 6);
  ctx.fillStyle = "#05040a"; ctx.strokeStyle = rgba(key, .9);
  if (size.startsWith("Деталь")) {
    ctx.lineWidth = 3;
    ctx.beginPath(); ctx.ellipse(W * .5, H * .58, W * .2, H * .25, -.3, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
    for (let i = 0; i < 4; i++) { ctx.beginPath(); ctx.ellipse(W * (.37 + i * .085), H * .3, W * .026, H * .13, -.15 + i * .09, 0, Math.PI * 2); ctx.fill(); ctx.stroke(); }
  } else {
    const sc = size.startsWith("Общий") ? .32 : size.startsWith("Средний") ? .6 : size.startsWith("Крупный") ? 1.15 : .95;
    names.forEach((nm, i) => {
      const cx = W * (i + 1) / (names.length + 1) + (size.startsWith("Через") && i === 0 ? -W * .14 : 0);
      const bodyH = H * .62 * sc, bodyW = bodyH * .34, headR = bodyH * .115;
      const baseY = H * hz + bodyH * .28 * (size.startsWith("Крупный") ? 1.9 : 1);
      const topY = baseY - bodyH;
      ctx.lineWidth = Math.max(1.5, 3 * sc);
      ctx.beginPath(); ctx.rect(cx - bodyW / 2, topY + headR * 2.1, bodyW, bodyH - headR * 2.1); ctx.fill(); ctx.stroke();
      ctx.beginPath(); ctx.arc(cx, topY + headR, headR, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      if (sc <= .7) { ctx.fillStyle = "rgba(255,255,255,.7)"; ctx.font = "11px Segoe UI"; ctx.textAlign = "center"; ctx.fillText(nm.replace(/\s*\(.*\)/, ""), cx, topY - 6); ctx.textAlign = "left"; ctx.fillStyle = "#05040a"; }
    });
  }
  ctx.restore();

  const vg = ctx.createRadialGradient(W / 2, H / 2, H * .3, W / 2, H / 2, W * .66);
  vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, `rgba(0,0,0,${.3 + .45 * con})`);
  ctx.fillStyle = vg; ctx.fillRect(0, 0, W, H);

  const mv = s.move || "";
  ctx.fillStyle = "rgba(255,255,255,.85)"; ctx.font = "13px Segoe UI"; ctx.textBaseline = "top";
  ctx.fillText(`${size} · ${mv}`, 12, 10);
  if (/наезд|отъезд/.test(mv)) {
    const inward = /наезд/.test(mv); ctx.strokeStyle = "rgba(255,255,255,.7)"; ctx.lineWidth = 2;
    for (const [x, y, dx, dy] of [[30, 50, 1, 1], [W - 30, 50, -1, 1], [30, H - 50, 1, -1], [W - 30, H - 50, -1, -1]]) {
      const s2 = inward ? 1 : -1; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + 26 * dx * s2, y + 26 * dy * s2); ctx.stroke();
    }
  }
  if (s.text) {
    ctx.font = "16px Georgia, serif"; ctx.textAlign = "center"; ctx.textBaseline = "alphabetic";
    const lines = wrapText(ctx, s.text, W * .8);
    lines.forEach((ln, i) => { const y = H - 20 - (lines.length - 1 - i) * 22; ctx.fillStyle = "rgba(0,0,0,.75)"; ctx.fillText(ln, W / 2 + 1, y + 1); ctx.fillStyle = "#fff"; ctx.fillText(ln, W / 2, y); });
    ctx.textAlign = "left";
  }
}

/* ------------------------------------------------------------------ state */
const S = { tab: store("ls_tab") || "episode", eps: [], ep: null, sel: 0, dirty: false, timer: null };
const TABS = [["episode", "Эпизод"], ["check", "Проверка"], ["panel", "Панель мода"], ["logs", "Логи"], ["status", "Состояние"]];
const cur = () => (S.ep && S.ep.shots[S.sel]) || null;

function renderTabs() {
  const nav = $("#tabs"); nav.innerHTML = "";
  for (const [id, name] of TABS) nav.append(h("button", { class: S.tab === id ? "on" : "", onclick: () => { S.tab = id; store("ls_tab", id); renderTabs(); render(); } }, name));
}
function render() {
  const root = $("#app"); root.innerHTML = "";
  stopLogTimer();
  ({ episode: renderEpisode, check: renderCheck, panel: renderPanel, logs: renderLogs, status: renderStatus }[S.tab])(root);
}

/* ------------------------------------------------------------------ episode tab */
async function loadEpisode(id) {
  const ep = await api(`/api/episode?id=${id}`);
  ep.shots = (ep.shots || []).map(norm);
  if (!ep.shots.length) ep.shots.push(blankShot("Вступление"));
  S.ep = ep; S.sel = 0; S.dirty = false; store("ls_ep", id);
}
function changed() { S.dirty = true; refreshList(); refreshSide(); scheduleSave(); }
function scheduleSave() { setSave("есть изменения…"); clearTimeout(S.timer); S.timer = setTimeout(saveNow, 900); }
async function saveNow() {
  clearTimeout(S.timer);
  if (!S.ep) return;
  try {
    const r = await post(`/api/episode?id=${S.ep.id}`, S.ep);
    S.dirty = false; setSave("сохранено " + new Date().toLocaleTimeString() + (r.changed ? " · выгружено в art/episodes" : ""));
  } catch (e) { setSave("ОШИБКА сохранения: " + e.message, true); }
}
window.addEventListener("beforeunload", e => { if (S.dirty) { e.preventDefault(); e.returnValue = ""; } });
window.addEventListener("keydown", e => { if ((e.ctrlKey || e.metaKey) && e.key === "s") { e.preventDefault(); if (S.tab === "episode") saveNow(); } });

async function uploadFile(file) {
  const r = await fetch("/api/upload?name=" + encodeURIComponent(file.name || "paste.png"), { method: "POST", body: await file.arrayBuffer() });
  if (!r.ok) throw new Error(await r.text());
  return (await r.json()).file;
}
async function addRefs(files) {
  const s = cur(); if (!s) return;
  for (const f of files) { if (!f.type.startsWith("image/")) continue; try { s.refs.push(await uploadFile(f)); } catch (e) { setSave("не загрузилось: " + e.message, true); } }
  buildForm($("#formcol")); changed();
}
document.addEventListener("paste", e => {
  if (S.tab !== "episode") return;
  const files = [...(e.clipboardData?.items || [])].filter(i => i.type.startsWith("image/")).map(i => i.getAsFile()).filter(Boolean);
  if (files.length) { e.preventDefault(); addRefs(files); }
});

async function renderEpisode(root) {
  if (!S.ep) {
    root.append(h("p", { class: "muted" }, "Загрузка…"));
    try {
      S.eps = await api("/api/episodes");
      const want = store("ls_ep");
      await loadEpisode(S.eps.some(e => e.id === want) ? want : S.eps[0].id);
    } catch (e) { root.innerHTML = ""; root.append(h("div", { class: "card" }, "Не удалось загрузить: " + e.message)); return; }
    if (S.tab !== "episode") return;
    root.innerHTML = "";
  }
  const left = h("aside", { class: "col", id: "listcol" }), mid = h("section", { class: "col", id: "formcol" }), right = h("aside", { class: "col", id: "sidecol" });
  root.append(
    h("datalist", { id: "dl-loc" }, P.locations.map(l => h("option", { value: l }))),
    h("div", { class: "ep-layout" }, left, mid, right));
  buildList(left); buildForm(mid); buildSide(right);
}

function buildList(left) {
  const epSel = h("select", { onchange: async () => { await saveNow(); await loadEpisode(epSel.value); render(); } },
    S.eps.map(e => h("option", { value: e.id }, `${e.title} (${e.shots})`)));
  epSel.value = S.ep.id;
  const title = h("input", { type: "text", value: S.ep.title, oninput: () => { S.ep.title = title.value; S.dirty = true; scheduleSave(); } });
  const move = d => { const a = S.ep.shots, j = S.sel + d; if (j < 0 || j >= a.length) return; [a[S.sel], a[j]] = [a[j], a[S.sel]]; S.sel = j; refreshList(); scheduleSave(); };
  left.append(
    h("div", { class: "card" }, h("h2", {}, "Эпизод"),
      h("div", { class: "row" }, epSel, h("button", { class: "btn sm", onclick: async () => {
        const t = prompt("Название нового эпизода:", "Эпизод " + (S.eps.length + 1)); if (!t) return;
        await saveNow(); const r = await post("/api/episode_new", { title: t }); S.eps = await api("/api/episodes"); await loadEpisode(r.id); render();
      } }, "+ новый")),
      h("label", { class: "f", style: "margin-top:8px" }, h("span", {}, "Название"), title),
      h("div", { class: "muted", id: "epstat" })),
    h("div", { class: "card" }, h("h2", {}, "Кадры"), h("div", { id: "shotlist" }),
      h("div", { class: "row", style: "margin-top:8px" },
        h("button", { class: "btn sm primary", onclick: () => { S.ep.shots.splice(S.sel + 1, 0, blankShot()); S.sel = Math.min(S.sel + 1, S.ep.shots.length - 1); selectShot(S.sel); scheduleSave(); } }, "+ кадр"),
        h("button", { class: "btn sm", onclick: () => { const c = JSON.parse(JSON.stringify(cur())); c.id = Math.random().toString(16).slice(2, 10); c.status = "draft"; S.ep.shots.splice(S.sel + 1, 0, c); selectShot(S.sel + 1); scheduleSave(); } }, "копия"),
        h("button", { class: "btn sm", onclick: () => move(-1) }, "↑"),
        h("button", { class: "btn sm", onclick: () => move(1) }, "↓"),
        h("button", { class: "btn sm danger", onclick: () => { if (S.ep.shots.length < 2 || !confirm("Удалить кадр?")) return; S.ep.shots.splice(S.sel, 1); selectShot(Math.max(0, S.sel - 1)); scheduleSave(); } }, "удалить"))),
    h("div", { class: "card" }, h("h2", {}, "Экспорт и история"),
      h("div", { class: "toolbar" },
        h("button", { class: "btn sm", onclick: printSheet }, "Лист для печати"),
        h("button", { class: "btn sm", onclick: () => copyText(S.ep.shots.map((s, i) => retell(s, i)).join("\n\n")) }, "Скопировать пересказ эпизода")),
      h("div", { class: "muted", style: "font-size:12px" }, "Каждое сохранение выгружает art/episodes/<эпизод>.md и .json: оттуда это читает Claude."),
      h("div", { id: "hist", style: "margin-top:8px" }, h("button", { class: "btn sm", onclick: showHistory }, "История версий"))));
  refreshList();
}
function refreshList() {
  const box = $("#shotlist"); if (!box) return;
  box.innerHTML = "";
  S.ep.shots.forEach((s, i) => box.append(h("div", { class: "shotitem" + (i === S.sel ? " on" : ""), onclick: () => selectShot(i) },
    h("div", { class: "n" }, i + 1),
    h("div", { style: "min-width:0;flex:1" }, h("div", { class: "t" }, s.title || "(без названия)"), h("div", { class: "s" }, `${s.size} · ${s.duration} с`)),
    h("div", { class: "badge " + s.status }, P.statuses[s.status].split(" ")[0]))));
  const total = S.ep.shots.reduce((a, s) => a + (Number(s.duration) || 0), 0);
  const ok = S.ep.shots.filter(s => s.status === "ok").length;
  const st = $("#epstat"); if (st) st.textContent = `Кадров: ${S.ep.shots.length} · ${total.toFixed(1)} с · подтверждено ${ok}/${S.ep.shots.length}`;
}
function selectShot(i) { S.sel = i; refreshList(); buildForm($("#formcol")); refreshSide(); }

function chipsField(options, arr) {
  const wrap = h("div", {}), box = h("div", { class: "chips" });
  const draw = () => {
    box.innerHTML = "";
    for (const o of [...options, ...arr.filter(x => !options.includes(x))])
      box.append(h("button", { type: "button", class: "chip" + (arr.includes(o) ? " on" : ""), onclick: () => { const k = arr.indexOf(o); if (k >= 0) arr.splice(k, 1); else arr.push(o); draw(); changed(); } }, o));
  };
  draw();
  const add = h("input", { type: "text", placeholder: "добавить своё и нажать Enter", onkeydown: e => { if (e.key === "Enter" && add.value.trim()) { arr.push(add.value.trim()); add.value = ""; draw(); changed(); } } });
  wrap.append(box, add); return wrap;
}

function buildForm(mid) {
  mid.innerHTML = "";
  const s = cur();
  if (!s) { mid.append(h("div", { class: "card muted" }, "Нет кадров.")); return; }
  const f = (label, el) => h("label", { class: "f" }, h("span", {}, label), el);
  const text = (key, ph) => { const el = h("input", { type: "text", placeholder: ph || "" }); el.value = s[key] ?? ""; el.addEventListener("input", () => { s[key] = el.value; changed(); }); return el; };
  const area = (key, ph) => { const el = h("textarea", { placeholder: ph || "" }); el.value = s[key] ?? ""; el.addEventListener("input", () => { s[key] = el.value; changed(); }); return el; };
  const select = (key, opts) => { const o = opts.includes(s[key]) ? opts : [...opts, s[key]]; const el = h("select", {}, o.map(x => h("option", { value: x }, x || "—"))); el.value = s[key]; el.addEventListener("change", () => { s[key] = el.value; changed(); }); return el; };

  const lightSel = h("select", {}, P.lights.map(l => h("option", { value: l.name }, l.name)));
  const keyIn = h("input", { type: "color" }), fillIn = h("input", { type: "color" });
  lightSel.value = s.lightPreset; keyIn.value = s.keyColor; fillIn.value = s.fillColor;
  lightSel.addEventListener("change", () => {
    s.lightPreset = lightSel.value; const L = P.lights.find(x => x.name === s.lightPreset);
    if (L && L.key) { s.keyColor = L.key; s.fillColor = L.fill; keyIn.value = s.keyColor; fillIn.value = s.fillColor; } changed();
  });
  const custom = () => { s.keyColor = keyIn.value; s.fillColor = fillIn.value; s.lightPreset = "Свой цвет"; lightSel.value = "Свой цвет"; changed(); };
  keyIn.addEventListener("input", custom); fillIn.addEventListener("input", custom);
  const contrast = h("input", { type: "range", min: 0, max: 100, value: s.contrast });
  contrast.addEventListener("input", () => { s.contrast = Number(contrast.value); changed(); });
  const dur = h("input", { type: "number", min: 0.5, max: 600, step: 0.5, value: s.duration });
  dur.addEventListener("input", () => { s.duration = Number(dur.value) || 0; changed(); });

  const loc = text("location", "где происходит"); loc.setAttribute("list", "dl-loc");
  const refs = h("div", { class: "refs" }, s.refs.map((r, i) => h("div", { class: "ref" },
    h("a", { href: "/refs/" + r, target: "_blank" }, h("img", { src: "/refs/" + r })),
    h("button", { class: "x", type: "button", onclick: () => { s.refs.splice(i, 1); buildForm($("#formcol")); changed(); } }, "×"))));
  const file = h("input", { type: "file", accept: "image/*", multiple: true, onchange: () => addRefs([...file.files]) });
  const drop = h("div", { class: "muted", style: "border:1px dashed var(--line);border-radius:8px;padding:10px;margin-top:8px;text-align:center" }, "Перетащи сюда свой рисунок, фото или скриншот. Или вставь из буфера Ctrl+V.");
  drop.addEventListener("dragover", e => { e.preventDefault(); });
  drop.addEventListener("drop", e => { e.preventDefault(); addRefs([...e.dataTransfer.files]); });

  mid.append(
    h("div", { class: "card" }, h("h2", {}, `Кадр ${S.sel + 1}: что в кадре`),
      f("Название кадра", text("title", "коротко, для списка")),
      f("Место", loc),
      f("Кто в кадре", chipsField(P.characters, s.characters)),
      f("Что происходит (своими словами)", area("action", "что видно и что делают")),
      f("Эмоция", select("emotion", P.emotions))),
    h("div", { class: "card" }, h("h2", {}, "Камера"),
      h("div", { class: "grid3" }, f("План", select("size", P.sizes)), f("Ракурс", select("angle", P.angles)), f("Движение", select("move", P.moves)))),
    h("div", { class: "card" }, h("h2", {}, "Свет"),
      f("Пресет", lightSel),
      h("div", { class: "row" }, h("span", { class: "muted" }, "основной"), keyIn, h("span", { class: "muted" }, "фон"), fillIn, h("span", { class: "muted", style: "margin-left:10px" }, "контраст"), contrast)),
    h("div", { class: "card" }, h("h2", {}, "Время, звук, текст"),
      h("div", { class: "grid2" }, f("Длительность, с", dur), f("Переход", select("transition", P.transitions))),
      f("Звук и музыка", text("sound", "что слышно: шаги, дыхание, гул, тишина…")),
      f("Текст или субтитр на экране", area("text", "если есть")),
      f("Заметки для себя", area("notes", ""))),
    h("div", { class: "card" }, h("h2", {}, "Референсы (твои)"), refs, drop, h("div", { style: "margin-top:8px" }, file)));
}

function buildSide(right) {
  right.append(
    h("div", { class: "card" }, h("h2", {}, "Предпросмотр кадра"), h("canvas", { class: "preview", id: "pv" }),
      h("div", { class: "muted", style: "font-size:12px;margin-top:6px" }, "Схема: свет, ракурс, масштаб героев, движение камеры. Это не финальная картинка.")),
    h("div", { class: "card" }, h("h2", {}, "Как я это понял"), h("div", { class: "retell", id: "retell" }),
      h("div", { class: "stbtns", id: "stbtns" }),
      h("label", { class: "f" }, h("span", {}, "Твой комментарий (что не так / что важно)"), h("textarea", { id: "comment" })),
      h("div", { class: "row" }, h("button", { class: "btn sm", onclick: () => copyText(retell(cur(), S.sel)) }, "Скопировать пересказ кадра"))));
  const cm = $("#comment");
  cm.value = cur()?.comment || "";
  cm.addEventListener("input", () => { const s = cur(); if (s) { s.comment = cm.value; S.dirty = true; scheduleSave(); } });
  refreshSide();
}
function refreshSide() {
  const s = cur(); if (!s || !$("#pv")) return;
  drawPreview($("#pv"), s);
  $("#retell").textContent = retell(s, S.sel);
  const cm = $("#comment"); if (document.activeElement !== cm) cm.value = s.comment || "";
  const box = $("#stbtns"); box.innerHTML = "";
  for (const [k, label] of Object.entries(P.statuses))
    box.append(h("button", { class: `btn ${k}` + (s.status === k ? " on" : ""), onclick: () => { s.status = k; changed(); } }, label));
}

async function showHistory() {
  const box = $("#hist"); box.innerHTML = "";
  const files = await api(`/api/history?id=${S.ep.id}`);
  if (!files.length) { box.append(h("div", { class: "muted" }, "Пока нет старых версий.")); return; }
  for (const f of files.slice(0, 12)) {
    const m = f.match(/_(\d{8})_(\d{6})/); const label = m ? `${m[1].slice(6)}.${m[1].slice(4, 6)} ${m[2].slice(0, 2)}:${m[2].slice(2, 4)}:${m[2].slice(4)}` : f;
    box.append(h("div", { class: "row", style: "margin-bottom:4px" }, h("span", {}, label), h("button", { class: "btn sm", onclick: async () => {
      if (!confirm("Вернуть эту версию? Текущая сохранится в истории.")) return;
      const ep = await api("/api/history_get?file=" + encodeURIComponent(f)); ep.id = S.ep.id; ep.shots = ep.shots.map(norm); S.ep = ep; S.sel = 0; S.dirty = true; await saveNow(); render();
    } }, "вернуть")));
  }
}
function printSheet() {
  const cv = document.createElement("canvas"); const w = window.open("", "_blank"); if (!w) { setSave("браузер заблокировал окно печати", true); return; }
  let html = `<!doctype html><meta charset="utf-8"><title>${esc(S.ep.title)}</title><style>body{font:13px Segoe UI,sans-serif;margin:20px}.s{display:grid;grid-template-columns:330px 1fr;gap:12px;border:1px solid #999;margin:0 0 12px;padding:8px;page-break-inside:avoid}img{width:330px}pre{white-space:pre-wrap;margin:0;font:inherit}</style><h1>${esc(S.ep.title)}</h1>`;
  S.ep.shots.forEach((s, i) => { drawPreview(cv, s); html += `<div class="s"><img src="${cv.toDataURL()}"><pre>${esc(retell(s, i))}\n\n[${esc(P.statuses[s.status])}]${s.comment ? "\nКомментарий: " + esc(s.comment) : ""}</pre></div>`; });
  w.document.write(html); w.document.close(); setTimeout(() => w.print(), 400);
}

/* ------------------------------------------------------------------ jobs */
function appendLines(con, lines) {
  for (const ln of lines) {
    const cls = /^\$/.test(ln) ? "c" : /error|ошибк|fail|exception|не удалось|завершено с ошибкой|ВНИМАНИЕ/i.test(ln) ? "e" : /\bOK\b|готово|ALL OK|скопировано/i.test(ln) ? "g" : "";
    con.append(h("div", { class: cls }, ln));
  }
  con.scrollTop = con.scrollHeight;
}
function showImages(box, images) {
  box.innerHTML = "";
  for (const im of images) {
    const body = im.before
      ? h("div", { class: "imgs" }, h("a", { href: im.before, target: "_blank" }, h("img", { src: im.before })), h("a", { href: im.after, target: "_blank" }, h("img", { src: im.after })))
      : h("div", {}, h("a", { href: im.after, target: "_blank" }, h("img", { src: im.after })));
    box.append(h("div", { class: "pair" }, h("div", { class: "muted", style: "margin-bottom:4px" }, im.name + (im.before ? "  ·  слева без шейдера, справа с ним" : "")), body));
  }
}
async function runJob(kind, params, con, results, btn) {
  if (btn) btn.disabled = true;
  con.innerHTML = ""; if (results) results.innerHTML = "";
  try {
    const { id } = await post("/api/job", { kind, params: params || {} });
    let since = 0;
    for (;;) {
      const j = await api(`/api/job?id=${id}&since=${since}`);
      appendLines(con, j.lines); since = j.next;
      if (j.status !== "running") { if (results) showImages(results, j.images); return j; }
      await sleep(600);
    }
  } catch (e) { appendLines(con, ["Ошибка связи с сервером: " + e.message]); }
  finally { if (btn) btn.disabled = false; }
}
const jobBtn = (label, kind, params, con, results, cls = "btn") => { const b = h("button", { class: cls }, label); b.addEventListener("click", () => runJob(kind, typeof params === "function" ? params() : params, con, results, b)); return b; };

/* ------------------------------------------------------------------ check tab */
async function renderCheck(root) {
  const con = h("div", { class: "console" }, "Здесь появится вывод.");
  const results = h("div", { class: "pairs" });
  const picked = new Set();
  const shotsBox = h("div", { class: "shots" }, h("span", { class: "muted" }, "Загрузка…"));
  const times = h("input", { type: "text", value: "83, 100.5, 120", style: "max-width:280px" });
  root.append(
    h("p", { class: "muted" }, "Проверки идут здесь, без запуска Minecraft: шейдер собирается видеокартой в скрытом окне."),
    h("div", { class: "card" }, h("h2", {}, "1. Шейдер собирается без ошибок?"),
      h("div", { class: "row" }, jobBtn("Проверить компиляцию", "shader_compile", {}, con, results, "btn primary"))),
    h("div", { class: "card" }, h("h2", {}, "2. Как шейдер выглядит на твоих скриншотах"),
      h("p", { class: "muted" }, "Отметь скриншоты (F2 в игре). Если ничего не отмечено, возьму два последних. Слева без шейдера, справа с ним."),
      shotsBox,
      h("div", { class: "row", style: "margin-top:10px" }, jobBtn("Прогнать выбранные", "shader_run", () => ({ names: [...picked] }), con, results, "btn primary"))),
    h("div", { class: "card" }, h("h2", {}, "3. Кадры сцены 13-й концовки"),
      h("p", { class: "muted" }, "Времена в секундах от начала сцены. Нужен собранный мод (build)."),
      h("div", { class: "row" }, times, jobBtn("Нарисовать кадры", "scene_frames", () => ({ times: times.value }), con, results, "btn primary"))),
    h("div", { class: "card" }, h("h2", {}, "4. Положить шейдер-пак в игру"),
      h("div", { class: "row" }, jobBtn("Установить LotosCinema в shaderpacks", "shader_install", {}, con, results, "btn"))),
    h("div", { class: "card" }, h("h2", {}, "Вывод"), con), results);
  try {
    const list = await api("/api/screenshots"); shotsBox.innerHTML = "";
    if (!list.length) shotsBox.append(h("span", { class: "muted" }, "Скриншотов нет."));
    for (const s of list) {
      const cb = h("input", { type: "checkbox" }); const lab = h("label", {}, cb, h("img", { src: "/mcshots/" + encodeURIComponent(s.name), loading: "lazy" }), h("div", { class: "cap" }, `${s.time} · ${s.mb} МБ`));
      cb.addEventListener("change", () => { if (cb.checked) picked.add(s.name); else picked.delete(s.name); lab.classList.toggle("on", cb.checked); });
      shotsBox.append(lab);
    }
  } catch (e) { shotsBox.textContent = "Не удалось получить скриншоты: " + e.message; }
}

/* ------------------------------------------------------------------ panel tab */
function renderPanel(root) {
  const con = h("div", { class: "console" }, "Здесь появится вывод.");
  const msg = h("input", { type: "text", placeholder: "сообщение коммита (по-человечески, без упоминаний ИИ)" });
  const scopes = ["src", "shaderpack", "art", "tools"]; const chosen = new Set(scopes);
  const push = h("input", { type: "checkbox" });
  root.append(
    h("div", { class: "card" }, h("h2", {}, "Сборка мода"),
      h("div", { class: "toolbar" },
        jobBtn("Собрать", "build", {}, con, null, "btn primary"),
        jobBtn("Положить jar в mods", "copy_jar", {}, con, null),
        jobBtn("Собрать и положить", "build_copy", {}, con, null)),
      h("div", { class: "muted" }, "Собирает через gradlew (минуты). Закрой Minecraft перед копированием: он держит jar.")),
    h("div", { class: "card" }, h("h2", {}, "Git"),
      h("div", { class: "toolbar" }, jobBtn("Что изменилось", "git_status", {}, con, null), jobBtn("Отправить на GitHub (push)", "git_push", {}, con, null)),
      msg,
      h("div", { class: "row", style: "margin:8px 0" }, h("span", { class: "muted" }, "Добавить:"),
        scopes.map(sc => h("label", { class: "row", style: "gap:4px" }, h("input", { type: "checkbox", checked: true, onchange: e => { if (e.target.checked) chosen.add(sc); else chosen.delete(sc); } }), sc)),
        h("label", { class: "row", style: "gap:4px;margin-left:12px" }, push, "сразу отправить")),
      jobBtn("Закоммитить", "git_commit", () => ({ message: msg.value, scope: [...chosen], push: push.checked }), con, null, "btn primary")),
    h("div", { class: "card" }, h("h2", {}, "Диск"), h("div", { class: "toolbar" }, jobBtn("Свободное место", "disk", {}, con, null))),
    h("div", { class: "card" }, h("h2", {}, "Вывод"), con));
}

/* ------------------------------------------------------------------ logs tab */
let logTimer = null;
function stopLogTimer() { if (logTimer) { clearInterval(logTimer); logTimer = null; } }
function renderLogs(root) {
  const out = h("div", { class: "console", style: "max-height:70vh" });
  const which = h("select", {}, [["latest", "latest.log (игра)"], ["crash", "последний вылет"], ["debug", "debug.log"], ["launcher", "лог лаунчера"]].map(([v, t]) => h("option", { value: v }, t)));
  const filter = h("input", { type: "text", placeholder: "фильтр, например: shader|error|Lotos", style: "max-width:320px" });
  const auto = h("input", { type: "checkbox" }); const info = h("span", { class: "muted" });
  const load = async () => {
    try {
      const r = await api("/api/log?name=" + which.value); info.textContent = `${r.path} ${r.mtime ? "· " + r.mtime : ""}`;
      const re = filter.value ? new RegExp(filter.value, "i") : null; out.innerHTML = "";
      appendLines(out, r.text.split("\n").filter(l => !re || re.test(l)));
    } catch (e) { out.textContent = "Ошибка: " + e.message; }
  };
  which.addEventListener("change", load); filter.addEventListener("input", load);
  auto.addEventListener("change", () => { stopLogTimer(); if (auto.checked) logTimer = setInterval(load, 3000); });
  root.append(h("div", { class: "card" }, h("div", { class: "row" }, which, filter, h("button", { class: "btn sm", onclick: load }, "обновить"), h("label", { class: "row", style: "gap:4px" }, auto, "каждые 3 с")), h("div", { style: "margin:6px 0" }, info), out));
  load();
}

/* ------------------------------------------------------------------ status tab */
async function renderStatus(root) {
  try {
    const st = await api("/api/status"); const c = st.config;
    const names = { project: "Папка проекта", gradlew: "gradlew.bat", minecraft: "Папка .minecraft", mods_dir: "Папка mods", java: "Java 17 (для проверок)", lwjgl: "LWJGL (видеокарта для проверок)", shaderpack: "Шейдер-пак в проекте", classes: "Собранные классы мода" };
    root.append(
      h("div", { class: "card" }, h("h2", {}, "Что найдено"), h("div", { class: "kv" }, Object.entries(st.checks).map(([k, v]) => [h("div", {}, names[k] || k), h("div", { class: v ? "ok" : "bad" }, v ? "✔ найдено" : "✖ не найдено")]))),
      h("div", { class: "card" }, h("h2", {}, "Пути"), h("div", { class: "kv" }, ["project", "minecraft", "gradle_home", "tmp", "java_home", "jar_name", "port"].map(k => [h("div", { class: "muted" }, k), h("div", {}, String(c[k]))])),
        h("p", { class: "muted" }, "Чтобы поменять, создай файл tools/lotos-studio/data/config.json с нужными ключами и перезапусти программу.")));
  } catch (e) { root.append(h("div", { class: "card" }, "Ошибка: " + e.message)); }
}

/* ------------------------------------------------------------------ start */
renderTabs();
render();
