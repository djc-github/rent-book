const state = {
  view: "dashboard",
  data: {},
  keyword: "",
  filter: "all",
  composing: false,
  pendingConfirm: null,
  expandedProperties: {},
  paymentsExpanded: false,
  paymentsLoading: false,
  paymentsLoaded: false,
  paymentsNextCursor: null,
  paymentsHasMore: false,
  imageRoomId: null,
  imageUploading: false,
  imagePreviewUrl: "",
  imagePreviewName: "",
  scrollLockY: 0,
};

const roomImageLoader = {
  observer: null,
  queue: [],
  active: 0,
  maxActive: 3,
  loaded: new Set(),
};

const labels = {
  dashboard: "总览",
  properties: "房态收租",
};

const hints = {
  dashboard: "今天先看哪些该收、哪些空着",
  properties: "按房源分组看房间，出租、收租、改房态都在这里处理",
};

const statusText = {
  VACANT: "空置",
  RESERVED: "预定",
  RENTED: "已出租",
  MAINTENANCE: "维修",
  OFFLINE: "下架",
  OVERDUE: "逾期",
  DUE_SOON: "待收",
};

const demo = {
  dashboard: {
    summary: { roomCount: 4, vacantCount: 2, rentedCount: 1, monthIncome: 1800, dueSoonCount: 1, overdueCount: 1 },
    dueRent: [
      { roomId: 1, nextDueDate: "2026-06-25", rentAmount: 1800, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "301-A", urgency: "OVERDUE" },
      { roomId: 3, nextDueDate: "2026-07-02", rentAmount: 1650, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "302-A", urgency: "DUE_SOON" },
    ],
    vacantRooms: [
      { roomId: 2, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "301-B", rentAmount: 1500, depositAmount: 1500, tags: "近地铁" },
      { roomId: 4, propertyName: "滨江大道 12 号滨江公寓 A 座", roomNo: "1201", rentAmount: 4200, depositAmount: 4200, tags: "整租" },
    ],
  },
  properties: [
    { id: 1, name: "人民路 88 号阳光花园 3 栋", address: "人民路 88 号阳光花园 3 栋", district: "城东", roomCount: 3, vacantCount: 1, rentedCount: 1, reservedCount: 1 },
    { id: 2, name: "滨江大道 12 号滨江公寓 A 座", address: "滨江大道 12 号滨江公寓 A 座", district: "江北", roomCount: 1, vacantCount: 1, rentedCount: 0, reservedCount: 0 },
  ],
  rooms: [
    { id: 1, propertyId: 1, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "301-A", status: "RENTED", rentAmount: 1800, depositAmount: 1800, payCycleMonths: 1, leaseStartDate: "2026-06-01", leaseEndDate: "2027-05-31", nextDueDate: "2026-06-25" },
    { id: 2, propertyId: 1, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "301-B", status: "VACANT", rentAmount: 1500, depositAmount: 1500 },
    { id: 3, propertyId: 1, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "302-A", status: "RESERVED", rentAmount: 1650, depositAmount: 1650 },
    { id: 4, propertyId: 2, propertyName: "滨江大道 12 号滨江公寓 A 座", roomNo: "1201", status: "VACANT", rentAmount: 4200, depositAmount: 4200 },
  ],
  payments: [
    { id: 1, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "301-A", amount: 1800, paidDate: "2026-06-01", periodStart: "2026-06-01", periodEnd: "2026-06-30", method: "微信" },
  ],
};

const $ = (selector) => document.querySelector(selector);
const toYmd = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
};
const today = () => toYmd(new Date());
const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (s) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[s]));
const fmtMoney = (value) => `￥${Number(value || 0).toLocaleString("zh-CN")}`;
const normalizeDate = (value) => value ? String(value).trim().replace(/[./]/g, "-").replaceAll("/", "-") : "";
const parseDate = (value) => {
  const date = value ? new Date(`${normalizeDate(value)}T00:00:00`) : null;
  return date && !Number.isNaN(date.getTime()) ? date : null;
};
const addMonths = (value, months) => {
  const date = parseDate(value);
  if (!date) return "";
  const day = date.getDate();
  date.setMonth(date.getMonth() + Number(months || 1));
  if (date.getDate() !== day) date.setDate(0);
  return toYmd(date);
};
const addMonthsMinusDay = (value, months) => addDays(addMonths(value, months), -1);
const addDays = (value, days) => {
  const date = parseDate(value);
  if (!date) return "";
  date.setDate(date.getDate() + Number(days || 0));
  return toYmd(date);
};
const runtimeConfig = { apiBaseUrl: window.location.origin, rentCollectAdvanceDays: 7 };
const apiBase = () => runtimeConfig.apiBaseUrl.replace(/\/$/, "");
const rentCollectAdvanceDays = () => Math.max(0, Number(runtimeConfig.rentCollectAdvanceDays || 7));
const toCamel = (key) => key.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase());
const normalize = (value) => {
  if (Array.isArray(value)) return value.map(normalize);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [toCamel(key), normalize(item)]));
};

async function api(path, options = {}) {
  const response = await fetch(`${apiBase()}${path}`, { headers: { "Content-Type": "application/json" }, ...options });
  const body = await response.json();
  if (!response.ok || !body.success) throw new Error(body.message || "请求失败");
  return normalize(body.data);
}

async function apiForm(path, formData) {
  const response = await fetch(`${apiBase()}${path}`, { method: "POST", body: formData });
  const body = await response.json();
  if (!response.ok || !body.success) throw new Error(body.message || "请求失败");
  return normalize(body.data);
}

const mediaUrl = (url) => {
  if (!url) return "";
  if (/^https?:\/\//i.test(url)) return url;
  return `${apiBase()}${url.startsWith("/") ? url : `/${url}`}`;
};

function lockPageScroll() {
  if (document.body.classList.contains("scroll-locked")) return;
  state.scrollLockY = window.scrollY || document.documentElement.scrollTop || 0;
  document.body.style.top = `-${state.scrollLockY}px`;
  document.body.classList.add("scroll-locked");
}

function unlockPageScrollIfIdle() {
  if (document.querySelector("dialog[open]")) return;
  if (!document.body.classList.contains("scroll-locked")) return;
  const scrollY = state.scrollLockY || 0;
  document.body.classList.remove("scroll-locked");
  document.body.style.top = "";
  window.scrollTo(0, scrollY);
  state.scrollLockY = 0;
}

function showLockedDialog(dialog) {
  lockPageScroll();
  dialog.showModal();
}

function closeDialog(dialog) {
  dialog.close();
  unlockPageScrollIfIdle();
}

function scheduleRoomImages() {
  if (roomImageLoader.observer) roomImageLoader.observer.disconnect();
  roomImageLoader.queue = roomImageLoader.queue.filter((img) => img.isConnected && img.dataset.imageState === "queued");
  const images = [...document.querySelectorAll("[data-room-card-image]")];
  if (!images.length) return;
  if (!("IntersectionObserver" in window)) {
    images.forEach((img) => queueRoomImage(img, false));
    return;
  }
  roomImageLoader.observer = new IntersectionObserver((entries) => {
    entries
      .filter((entry) => entry.isIntersecting)
      .forEach((entry) => {
        roomImageLoader.observer?.unobserve(entry.target);
        queueRoomImage(entry.target, true);
      });
  }, { rootMargin: "420px 0px", threshold: 0.01 });
  images.forEach((img) => {
    const src = img.dataset.src;
    if (!src) return;
    if (roomImageLoader.loaded.has(src)) {
      setRoomCardImageSrc(img, src);
      return;
    }
    roomImageLoader.observer.observe(img);
  });
}

function queueRoomImage(img, priority) {
  if (!img?.isConnected || !img.dataset.src || img.dataset.imageState === "queued" || img.dataset.imageState === "loading" || img.classList.contains("loaded")) return;
  img.dataset.imageState = "queued";
  if (priority) roomImageLoader.queue.unshift(img);
  else roomImageLoader.queue.push(img);
  processRoomImageQueue();
}

function processRoomImageQueue() {
  while (roomImageLoader.active < roomImageLoader.maxActive && roomImageLoader.queue.length) {
    const img = roomImageLoader.queue.shift();
    if (!img?.isConnected || !img.dataset.src) continue;
    const src = img.dataset.src;
    if (roomImageLoader.loaded.has(src)) {
      setRoomCardImageSrc(img, src);
      continue;
    }
    roomImageLoader.active += 1;
    startRoomImageLoad(img, src);
  }
}

function startRoomImageLoad(img, src) {
  img.dataset.imageState = "loading";
  img.closest(".room-photo")?.classList.add("loading");
  img.onload = () => {
    roomImageLoader.loaded.add(src);
    if (img.isConnected) setRoomCardImageSrc(img, src);
    finishRoomImageLoad();
  };
  img.onerror = () => {
    const fallback = img.dataset.fallbackSrc;
    if (fallback && fallback !== src && img.dataset.fallbackTried !== "true") {
      img.dataset.fallbackTried = "true";
      img.dataset.src = fallback;
      startRoomImageLoad(img, fallback);
      return;
    }
    if (img.isConnected) markRoomCardImageError(img);
    finishRoomImageLoad();
  };
  img.src = src;
}

function finishRoomImageLoad() {
  roomImageLoader.active = Math.max(0, roomImageLoader.active - 1);
  processRoomImageQueue();
}

function setRoomCardImageSrc(img, src) {
  img.onload = null;
  img.onerror = null;
  img.src = src;
  img.classList.add("loaded");
  img.dataset.imageState = "loaded";
  img.closest(".room-photo")?.classList.remove("loading");
}

function markRoomCardImageError(img) {
  img.dataset.imageState = "error";
  img.closest(".room-photo")?.classList.remove("loading");
  const placeholder = img.closest(".room-photo")?.querySelector(".room-photo-placeholder");
  if (placeholder) placeholder.textContent = "图片加载失败";
}

async function loadRuntimeConfig() {
  try {
    const response = await fetch("./config/app-config.json", { cache: "no-store" });
    if (response.ok) Object.assign(runtimeConfig, await response.json());
  } catch (error) {
    console.warn("RentBook config not loaded, fallback to current origin.", error);
  }
}

async function load() {
  $("#pageTitle").textContent = labels[state.view];
  $("#pageHint").textContent = hints[state.view];
  $("#quickAddBtn").textContent = state.view === "properties" ? "新增房间" : "新增房源";
  setBusy(true);
  try {
    if (state.view === "dashboard") state.data.dashboard = await api("/api/dashboard");
    if (state.view === "properties") await loadProperties();
  } catch (error) {
    state.data[state.view] = demo[state.view];
    if (state.view === "properties") {
      state.data.rooms = demo.rooms;
      state.data.payments = demo.payments;
      state.paymentsLoaded = true;
      state.paymentsHasMore = false;
    }
    showToast(`后端未连接，当前展示演示数据：${error.message}`);
  } finally {
    render();
    setBusy(false);
  }
}

async function loadProperties() {
  const [properties, rooms] = await Promise.all([api("/api/properties"), api("/api/properties/rooms")]);
  state.data.properties = properties;
  state.data.rooms = rooms;
  if (!state.paymentsLoaded) state.data.payments = [];
  if (state.paymentsExpanded) await loadPayments({ reset: true });
}

async function loadPayments({ reset = false } = {}) {
  if (state.paymentsLoading) return;
  state.paymentsLoading = true;
  render();
  try {
    const cursor = reset ? "" : state.paymentsNextCursor;
    const query = new URLSearchParams({ limit: "20" });
    if (cursor) query.set("cursor", cursor);
    const page = await api(`/api/payments?${query}`);
    const rows = page.rows || [];
    state.data.payments = reset ? rows : [...(state.data.payments || []), ...rows];
    state.paymentsNextCursor = page.nextCursor || null;
    state.paymentsHasMore = Boolean(page.hasMore);
    state.paymentsLoaded = true;
  } catch (error) {
    showToast(error.message);
  } finally {
    state.paymentsLoading = false;
    render();
  }
}

function render() {
  if (state.view === "dashboard") $("#content").innerHTML = renderDashboard(state.data.dashboard || demo.dashboard);
  if (state.view === "properties") $("#content").innerHTML = renderProperties(state.data.properties || demo.properties, state.data.rooms || demo.rooms, state.data.payments || []);
  scheduleRoomImages();
}

function renderDashboard(data) {
  const s = data.summary || {};
  const dueRows = filterDueRows(data.dueRent || []);
  return `
    <section class="metrics">
      ${metric("本月已收", fmtMoney(s.monthIncome), "")}
      ${metric("逾期未收", s.overdueCount || 0, "danger")}
      ${metric("7天内应收", s.dueSoonCount || 0, "warn")}
      ${metric("空置房间", `${s.vacantCount || 0}/${s.roomCount || 0}`, "")}
    </section>
    <section class="panel">
      <div class="panel-head"><h3>该收租的房间</h3><span class="tag warn">确认后入账</span></div>
      ${table(["房源/房间", "应收日", "金额", "状态", "操作"], dueRows, (r) => `
        <td><strong>${esc(r.propertyName)} ${esc(r.roomNo)}</strong></td>
        <td>${esc(r.nextDueDate || "-")}</td>
        <td>${fmtMoney(r.rentAmount)}</td>
        <td>${tag(statusText[r.urgency] || "待收", r.urgency === "OVERDUE" ? "danger" : "warn")}</td>
        <td class="row-actions">${collectButton(r)}</td>`)}
    </section>
    <section class="panel">
      <div class="panel-head"><h3>空置房间</h3><span class="tag">${(data.vacantRooms || []).length} 间</span></div>
      <div class="room-grid">${(data.vacantRooms || []).map((r) => roomMiniCard(r)).join("") || empty("暂无空房")}</div>
    </section>`;
}

function renderProperties(properties, rooms, payments) {
  const filteredProperties = filterRows(properties, ["name", "address", "district"]);
  const filteredRooms = filterRows(rooms, ["propertyName", "roomNo", "status", "tags"]);
  const grouped = filteredProperties.map((property) => ({
    property,
    rooms: filteredRooms.filter((room) => Number(room.propertyId) === Number(property.id)),
  }));
  return `
    <section class="panel toolbar-panel">
      <div class="panel-head">
        <h3>房态收租</h3>
        <div class="panel-tools"><button class="primary" data-form="property">新增房源</button><button class="primary" data-form="room">新增房间</button></div>
      </div>
      ${searchBox("搜地址、房号、房态")}
    </section>
    <section class="property-stack">
      ${grouped.map(({ property, rooms: propertyRooms }, index) => propertyBlock(property, propertyRooms, index)).join("") || empty("暂无房源")}
    </section>
    ${renderPaymentRecords(payments)}`;
}

function propertyBlock(property, rooms, index) {
  const summary = roomSummary(rooms);
  const expanded = isPropertyExpanded(property, rooms);
  const dueCount = rooms.filter((room) => collectInfo(room).enabled).length;
  return `<section class="property-group property-tone-${index % 4} ${expanded ? "expanded" : "collapsed"}">
    <div class="property-head">
      <button class="property-title property-toggle" data-toggle-property="${property.id}" aria-expanded="${expanded}">
        <span class="property-index">${index + 1}</span>
        <span class="toggle-mark">${expanded ? "收起" : "展开"}</span>
        <div><h3>${esc(propertyTitle(property))}</h3><small>${esc(property.district || "未填区域")}</small></div>
      </button>
      <div class="property-stats">
        ${tag(`房间 ${rooms.length}`, "gray")}
        ${tag(`已租 ${summary.rented}`, "")}
        ${tag(`空置 ${summary.vacant}`, "blue")}
        ${dueCount ? tag(`待收 ${dueCount}`, "warn") : ""}
      </div>
      <div class="panel-tools property-actions">
        <button class="mini primary" data-form="room" data-property-id="${property.id}">加房间</button>
        <button class="mini ghost" data-form="property" data-id="${property.id}">编辑</button>
        <button class="mini danger" data-delete="property:${property.id}">删除</button>
      </div>
    </div>
    ${expanded ? `<div class="room-list">${rooms.map((room) => roomCard(room, index % 4)).join("") || empty("还没有房间")}</div>` : ""}
  </section>`;
}

function roomCardImageUrl(room) {
  const url = room.imageThumbnailUrl || room.thumbnailUrl || room.imageUrl;
  return url ? mediaUrl(url) : "";
}

function roomCardOriginalImageUrl(room) {
  return room.imageUrl ? mediaUrl(room.imageUrl) : "";
}

function roomCard(room, propertyTone = 0) {
  const due = room.status === "RENTED" ? dueTag(room) : tag(statusText[room.status] || room.status, statusTone(room.status));
  const image = roomCardImageUrl(room);
  const fallbackImage = roomCardOriginalImageUrl(room) || image;
  return `<article class="room-row property-room-tone-${propertyTone} status-${room.status || "UNKNOWN"}">
    <button class="room-photo ${image ? "has-image" : "empty"}" data-room-image="${room.id}" aria-label="${image ? "查看或更换房间图片" : "添加房间图片"}">
      ${image ? `<span class="room-photo-placeholder">图片加载中</span><img data-room-card-image data-src="${esc(image)}" data-fallback-src="${esc(fallbackImage)}" alt="${esc(room.roomNo)}房间图片" decoding="async">` : `<span>添加图片</span>`}
    </button>
    <div class="room-main">
      <strong>${esc(room.roomNo)}</strong>
      <span>${due}</span>
      <small>${fmtMoney(room.rentAmount)} / 押${Number(room.depositAmount || 0).toLocaleString("zh-CN")}</small>
      ${room.leaseStartDate && room.leaseEndDate ? `<small>租期：${esc(room.leaseStartDate)} 至 ${esc(room.leaseEndDate)}</small>` : ""}
      ${room.nextDueDate ? `<small>下次应收：${esc(room.nextDueDate)}，${room.payCycleMonths || 1}个月一收</small>` : ""}
    </div>
    <div class="row-actions">
      ${room.status === "RENTED" ? collectButton(room) : `<button class="mini primary" data-form="rent" data-id="${room.id}">出租</button>`}
      <button class="mini ghost" data-form="rent" data-id="${room.id}">收租设置</button>
      <button class="mini" data-room-status="${room.id}:RESERVED">预定</button>
      <button class="mini" data-room-status="${room.id}:VACANT">空置</button>
      <button class="mini ghost" data-form="room" data-id="${room.id}">编辑</button>
      <button class="mini danger" data-delete="room:${room.id}">删除</button>
    </div>
  </article>`;
}

function renderPaymentRecords(rows) {
  const filtered = filterRows(rows, ["propertyName", "roomNo", "method"]);
  const latest = filtered[0];
  const expanded = state.paymentsExpanded;
  const latestText = latest
    ? `${latest.paidDate || "-"} · ${propertyTitle(latest)} ${latest.roomNo || ""} · ${fmtMoney(latest.amount)}`
    : state.paymentsLoaded ? "还没有收租记录" : "点开后加载最近记录";
  const countText = state.paymentsLoaded ? `已加载 ${filtered.length} 笔` : "未加载";
  return `<section class="panel payment-history ${expanded ? "expanded" : "collapsed"}">
    <button class="payment-history-head" data-toggle-payments aria-expanded="${expanded}">
      <span>
        <strong>最近收租</strong>
        <small>${esc(latestText)}</small>
      </span>
      <span class="payment-history-meta">
        <span class="tag">${countText}</span>
        <span class="toggle-mark">${expanded ? "收起" : "查看"}</span>
      </span>
    </button>
    ${expanded ? renderPaymentRecordBody(filtered) : ""}
  </section>`;
}

function renderPaymentRecordBody(rows) {
  if (state.paymentsLoading && !state.paymentsLoaded) return empty("正在加载收租记录...");
  const content = table(["房源/房间", "金额", "收款日", "租期", "方式", "操作"], rows, (r) => `
      <td><strong>${esc(r.propertyName)} ${esc(r.roomNo)}</strong></td>
      <td>${fmtMoney(r.amount)}</td>
      <td>${esc(r.paidDate || "-")}</td>
      <td>${esc(r.periodStart || "-")} 至 ${esc(r.periodEnd || "-")}</td>
      <td>${esc(r.method || "-")}</td>
      <td class="row-actions"><button class="mini danger" data-delete="payment:${r.id}">撤销</button></td>`);
  const more = state.paymentsHasMore
    ? `<div class="payment-history-more"><button class="mini ghost" data-load-payments ${state.paymentsLoading ? "disabled" : ""}>${state.paymentsLoading ? "加载中..." : "加载更多"}</button></div>`
    : rows.length ? `<div class="payment-history-end">已经到底了</div>` : "";
  return `${content}${more}`;
}

const formDefs = {
  property: {
    title: "房源",
    tip: "只填地址就够了，区域、房东、负责人可后补。",
    path: (ctx) => ctx.id ? `/api/properties/${ctx.id}` : "/api/properties",
    method: (ctx) => ctx.id ? "PUT" : "POST",
    fields: [["address", "房源地址"], ["district", "区域"], ["landlordName", "房东"], ["landlordPhone", "房东电话"], ["manager", "负责人"], ["notes", "备注", "textarea"]],
  },
  room: {
    title: "房间",
    tip: "房号、月租、押金先填好；出租以后就在房间上直接收租。",
    path: (ctx) => ctx.id ? `/api/properties/rooms/${ctx.id}` : "/api/properties/rooms",
    method: (ctx) => ctx.id ? "PUT" : "POST",
    fields: [["propertyId", "所属房源", "select", "properties"], ["roomNo", "房号"], ["rentAmount", "月租金", "number"], ["depositAmount", "押金", "number"], ["status", "房态", "select", ["VACANT", "RESERVED", "RENTED", "MAINTENANCE", "OFFLINE"]], ["payCycleMonths", "几个月一收", "number", null, "1"], ["nextDueDate", "下次应收日", "date"], ["notes", "备注", "textarea"]],
  },
  rent: {
    title: "出租/收租设置",
    tip: "填好租期和收租周期，以后点房间上的“收租”会自动往后推。",
    path: (ctx) => `/api/properties/rooms/${ctx.id}/rent`,
    method: () => "POST",
    fields: [["rentAmount", "月租金", "number"], ["depositAmount", "押金", "number"], ["leaseStartDate", "租期开始日期", "date", null, today()], ["leaseEndDate", "租期结束日期", "date"], ["payCycleMonths", "几个月一收", "number", null, "1"], ["nextDueDate", "下次应收日", "date"], ["notes", "备注", "textarea"]],
  },
};

async function openForm(formType, initial = {}) {
  const def = formDefs[formType];
  if (!def) return;
  if (formType === "room" && !state.data.properties) await loadProperties();
  $("#modalTitle").textContent = def.title;
  $("#modalTip").textContent = def.tip;
  $("#modalBody").innerHTML = def.fields.map(([name, label, type = "text", options, fallback = ""]) => {
    const required = requiredFields(formType).includes(name) ? "required" : "";
    const value = initial[name] ?? fallback ?? "";
    const control = renderField(name, type, options, value, required);
    return `<div class="field ${type === "textarea" ? "full" : ""} ${required ? "required" : ""}"><label>${label}</label>${control}</div>`;
  }).join("");
  $("#modalForm").dataset.type = formType;
  $("#modalForm").dataset.id = initial.id || "";
  syncRentDateDefaults();
  showLockedDialog($("#modal"));
}

function renderField(name, type, options, value, required) {
  if (type === "textarea") return `<textarea name="${name}" rows="3">${esc(value)}</textarea>`;
  if (type === "date") return `<input name="${name}" type="date" value="${esc(normalizeDate(value))}" ${required}>`;
  if (type === "select" && options === "properties") {
    const rows = state.data.properties || demo.properties;
    return `<select name="${name}" ${required}>${rows.map((p) => `<option value="${p.id}" ${Number(value) === Number(p.id) ? "selected" : ""}>${esc(propertyTitle(p))}</option>`).join("")}</select>`;
  }
  if (type === "select") return `<select name="${name}" ${required}>${options.map((o) => `<option value="${o}" ${String(value) === o ? "selected" : ""}>${statusText[o] || o}</option>`).join("")}</select>`;
  const attrs = name === "payCycleMonths" ? 'min="1" step="1"' : "";
  return `<input name="${name}" type="${type}" value="${esc(value)}" ${attrs} ${required}>`;
}

function openImageDialog(roomId) {
  const room = findRecord("room", roomId);
  if (!room.id) return showToast("房间不存在");
  state.imageRoomId = Number(roomId);
  resetPendingImage();
  $("#roomImageInput").value = "";
  updateImageDialog(room);
  showLockedDialog($("#imageDialog"));
}

function updateImageDialog(room = findRecord("room", state.imageRoomId)) {
  $("#imageTitle").textContent = `${room.roomNo || ""} 房间图片`;
  const pending = Boolean(state.imagePreviewUrl);
  const image = state.imagePreviewUrl || (room.imageUrl ? mediaUrl(room.imageUrl) : "");
  $("#imagePreview").innerHTML = image
    ? `<img src="${esc(image)}" alt="${esc(room.roomNo || "房间")}图片">`
    : `<div class="image-placeholder"><strong>还没有图片</strong><small>点这里添加房间照片</small></div>`;
  $("#imagePickTitle").textContent = pending
    ? `已选择：${state.imagePreviewName || "新图片"}`
    : room.imageUrl ? "点这里更换图片" : "点这里添加图片";
  $("#imagePickHint").textContent = pending ? "确认预览没问题后，点下方保存图片" : "支持 JPG、PNG、WEBP，单张不超过 5MB";
  $("#imagePendingNote").hidden = !pending;
  $("#deleteImageBtn").disabled = !room.imageId || state.imageUploading;
  $("#uploadImageBtn").disabled = state.imageUploading || !pending;
  $("#uploadImageBtn").textContent = state.imageUploading ? "保存中..." : pending ? "保存图片" : "先选图片";
}

function applyRoomImage(roomId, image) {
  const room = findRecord("room", roomId);
  if (!room.id) return;
  room.imageId = image?.id || null;
  room.imageUrl = image?.url || "";
  room.imageThumbnailUrl = image?.thumbnailUrl || image?.url || "";
}

function resetPendingImage() {
  if (state.imagePreviewUrl) URL.revokeObjectURL(state.imagePreviewUrl);
  state.imagePreviewUrl = "";
  state.imagePreviewName = "";
}

function validateRoomImageFile(file) {
  const allowedTypes = ["image/jpeg", "image/png", "image/webp"];
  if (!allowedTypes.includes(file.type)) return "只能选择 JPG、PNG 或 WEBP 图片";
  if (file.size > 5 * 1024 * 1024) return "图片不能超过 5MB";
  return "";
}

function previewSelectedRoomImage() {
  const input = $("#roomImageInput");
  const file = input.files?.[0];
  resetPendingImage();
  if (!file) return updateImageDialog();
  const message = validateRoomImageFile(file);
  if (message) {
    input.value = "";
    showToast(message);
    return updateImageDialog();
  }
  state.imagePreviewUrl = URL.createObjectURL(file);
  state.imagePreviewName = file.name;
  updateImageDialog();
}

async function uploadRoomImage() {
  const roomId = state.imageRoomId;
  const file = $("#roomImageInput").files?.[0];
  if (!roomId) return showToast("房间不存在");
  if (!file) return showToast("请先选择图片");
  const message = validateRoomImageFile(file);
  if (message) return showToast(message);
  const formData = new FormData();
  formData.append("file", file);
  state.imageUploading = true;
  updateImageDialog();
  try {
    const image = await apiForm(`/api/properties/rooms/${roomId}/images`, formData);
    applyRoomImage(roomId, image);
    resetPendingImage();
    showToast("图片已更新");
    $("#roomImageInput").value = "";
    render();
    updateImageDialog(findRecord("room", roomId));
  } catch (error) {
    showToast(error.message);
  } finally {
    state.imageUploading = false;
    updateImageDialog();
  }
}

async function deleteRoomImage() {
  const roomId = state.imageRoomId;
  const room = findRecord("room", roomId);
  if (!room.id || !room.imageId) return showToast("当前房间没有图片");
  try {
    await api(`/api/properties/rooms/${roomId}/images/${room.imageId}`, { method: "DELETE" });
    applyRoomImage(roomId, null);
    resetPendingImage();
    $("#roomImageInput").value = "";
    showToast("图片已删除");
    render();
    updateImageDialog(findRecord("room", roomId));
  } catch (error) {
    showToast(error.message);
  }
}

async function submitForm(event) {
  event.preventDefault();
  const type = $("#modalForm").dataset.type;
  const def = formDefs[type];
  const ctx = { id: $("#modalForm").dataset.id };
  const payload = Object.fromEntries(new FormData($("#modalForm")).entries());
  if (type === "property" && payload.address && !payload.name) payload.name = payload.address;
  for (const key of requiredFields(type)) {
    if (!payload[key]) return showToast("请先填写必填项");
  }
  const validationMessage = validateFormPayload(type, payload);
  if (validationMessage) return showToast(validationMessage);
  Object.keys(payload).forEach((key) => {
    if (["leaseStartDate", "leaseEndDate", "nextDueDate"].includes(key)) payload[key] = normalizeDate(payload[key]);
    if (payload[key] === "") delete payload[key];
    if (["propertyId", "payCycleMonths"].includes(key) && payload[key]) payload[key] = Number(payload[key]);
    if (["rentAmount", "depositAmount"].includes(key) && payload[key]) payload[key] = Number(payload[key]);
  });
  try {
    setFormBusy(true);
    await api(def.path(ctx), { method: def.method(ctx), body: JSON.stringify(payload) });
    closeDialog($("#modal"));
    showToast(type === "rent" ? "收租规则已保存" : "保存成功");
    await load();
  } catch (error) {
    showToast(error.message);
  } finally {
    setFormBusy(false);
  }
}

function requiredFields(type) {
  return {
    property: ["address"],
    room: ["propertyId", "roomNo", "rentAmount", "depositAmount"],
    rent: ["rentAmount", "depositAmount", "leaseStartDate", "leaseEndDate", "payCycleMonths", "nextDueDate"],
  }[type] || [];
}

function validateFormPayload(type, payload) {
  if (type !== "rent") return "";
  const start = normalizeDate(payload.leaseStartDate);
  const end = normalizeDate(payload.leaseEndDate);
  const nextDue = normalizeDate(payload.nextDueDate);
  const cycle = Number(payload.payCycleMonths || 1);
  if (cycle < 1) return "几个月一收至少为1个月";
  if (!parseDate(start) || !parseDate(end) || !parseDate(nextDue)) return "请填写正确的租期和应收日期";
  if (end < start) return "租期结束日期不能早于开始日期";
  if (nextDue < start || nextDue > end) return "下次应收日必须在租期范围内";
  if (addMonthsMinusDay(start, cycle) > end) return "几个月一收不能超过租期长度";
  return "";
}

function collectInfo(room) {
  const due = normalizeDate(room.nextDueDate);
  const id = room.id || room.roomId;
  const months = Number(room.payCycleMonths || 1);
  if (!id) return { enabled: false, reason: "房间不存在" };
  if (room.lastPaidDate === today()) return { enabled: false, reason: "今天已登记过收租", label: "已收" };
  if (!due) return { enabled: false, reason: "先设置应收日", label: "未设置" };
  const advanceDays = rentCollectAdvanceDays();
  if (due > addDays(today(), advanceDays)) {
    return { enabled: false, reason: `应收日前${advanceDays}天内才能收租`, label: "未到期" };
  }
  const periodStart = due;
  const periodEnd = addMonthsMinusDay(periodStart, months);
  if (room.leaseEndDate && periodStart > room.leaseEndDate) {
    return { enabled: false, reason: "租期已结束", label: "已到期" };
  }
  if (room.leaseEndDate && periodEnd > room.leaseEndDate) {
    return { enabled: false, reason: "本次收租会超过租期结束日期", label: "调租期" };
  }
  return {
    enabled: true,
    id,
    months,
    periodStart,
    periodEnd,
    amount: Number(room.rentAmount || 0) * months,
    label: due < today() ? "补收" : "收租",
  };
}

function collectButton(room) {
  const info = collectInfo(room);
  if (!info.enabled) {
    return `<button class="mini ghost collect-disabled" disabled title="${esc(info.reason)}">${esc(info.label || "不可收")}</button>`;
  }
  return `<button class="mini primary" data-request-collect="${info.id}">${info.label}</button>`;
}

function getCollectRoom(roomId) {
  const room = findRecord("room", roomId);
  if (room.id) return room;
  const dueRows = state.data.dashboard?.dueRent || demo.dashboard.dueRent || [];
  const due = dueRows.find((item) => Number(item.roomId) === Number(roomId));
  return due ? { ...due, id: due.roomId, status: "RENTED", payCycleMonths: due.payCycleMonths || 1 } : {};
}

function requestCollect(roomId) {
  const room = getCollectRoom(roomId);
  const info = collectInfo(room);
  if (!info.enabled) return showToast(info.reason || "当前不能收租");
  state.pendingConfirm = () => collectRoomRent(roomId);
  $("#confirmTitle").textContent = "收租确认";
  $("#confirmMessage").textContent = `${propertyTitle(room)} ${room.roomNo || ""}\n收款金额：${fmtMoney(info.amount)}\n覆盖租期：${info.periodStart} 至 ${info.periodEnd}`;
  $("#confirmOkBtn").textContent = "确认收租";
  showLockedDialog($("#confirmDialog"));
}

async function collectRoomRent(roomId) {
  const room = getCollectRoom(roomId);
  if (!room.id) return showToast("房间不存在");
  const info = collectInfo(room);
  if (!info.enabled) return showToast(info.reason || "当前不能收租");
  const months = Number(room.payCycleMonths || 1);
  const periodStart = room.nextDueDate || today();
  const periodEnd = addMonthsMinusDay(periodStart, months);
  if (room.leaseEndDate && periodStart > room.leaseEndDate) return showToast("租期已结束，不能继续收租");
  if (room.leaseEndDate && periodEnd > room.leaseEndDate) return showToast("本次收租会超过租期结束日期，请先调整租期");
  try {
    await api(`/api/properties/rooms/${roomId}/collect`, { method: "POST", body: JSON.stringify({ months, paidDate: today() }) });
    closeConfirm();
    showToast(`已收${months}个月租金`);
    await load();
  } catch (error) {
    showToast(error.message);
  }
}

async function updateRoomStatus(roomId, status) {
  try {
    await api(`/api/properties/rooms/${roomId}/status`, { method: "PATCH", body: JSON.stringify({ status }) });
    closeConfirm();
    showToast(`房态已改为${statusText[status] || status}`);
    await load();
  } catch (error) {
    showToast(error.message);
  }
}

function requestRoomStatus(roomId, status) {
  const room = findRecord("room", roomId);
  const target = statusText[status] || status;
  state.pendingConfirm = () => updateRoomStatus(roomId, status);
  $("#confirmTitle").textContent = "房态确认";
  $("#confirmMessage").textContent = `确认把 ${propertyTitle(room)} ${room.roomNo || ""} 改为“${target}”？`;
  $("#confirmOkBtn").textContent = `确认${target}`;
  showLockedDialog($("#confirmDialog"));
}

function requestDelete(token) {
  const [type] = token.split(":");
  const messages = {
    property: "确认删除这个房源？房源下还有已出租房间时不允许删除。",
    room: "确认删除这个房间？",
    payment: "确认撤销这笔收租记录？系统会回退下次应收日。",
  };
  if (!messages[type]) return;
  state.pendingConfirm = () => deleteRecord(token);
  $("#confirmTitle").textContent = type === "payment" ? "撤销确认" : "删除确认";
  $("#confirmMessage").textContent = messages[type];
  $("#confirmOkBtn").textContent = type === "payment" ? "确认撤销" : "确认删除";
  showLockedDialog($("#confirmDialog"));
}

async function deleteRecord(token) {
  const [type, id] = token.split(":");
  const config = {
    property: { path: `/api/properties/${id}` },
    room: { path: `/api/properties/rooms/${id}` },
    payment: { path: `/api/payments/${id}` },
  }[type];
  if (!config) return;
  try {
    await api(config.path, { method: "DELETE" });
    closeConfirm();
    showToast("已处理");
    await load();
  } catch (error) {
    showToast(error.message);
  }
}

function openFormFromButton(button) {
  const type = button.dataset.form;
  const id = button.dataset.id;
  const initial = id ? { ...findRecord(type === "rent" ? "room" : type, id), id } : {};
  if (button.dataset.propertyId) initial.propertyId = Number(button.dataset.propertyId);
  if (type === "rent") {
    initial.payCycleMonths ||= 1;
    initial.leaseStartDate ||= today();
    initial.leaseEndDate ||= addMonthsMinusDay(initial.leaseStartDate, 12);
    initial.nextDueDate ||= initial.leaseStartDate;
  }
  openForm(type, initial);
}

function syncRentDateDefaults(event) {
  if ($("#modalForm").dataset.type !== "rent") return;
  const start = $("#modalForm [name='leaseStartDate']");
  const end = $("#modalForm [name='leaseEndDate']");
  const nextDue = $("#modalForm [name='nextDueDate']");
  const cycle = $("#modalForm [name='payCycleMonths']");
  if (!start?.value) return;
  if (event?.target === start && !nextDue.value) nextDue.value = start.value;
  if (event?.target === start && !end.value) end.value = addMonthsMinusDay(start.value, 12);
  if (event?.target === cycle && Number(cycle.value || 0) < 1) cycle.value = 1;
}

function findRecord(type, id) {
  const rows = { property: state.data.properties || demo.properties, room: state.data.rooms || demo.rooms }[type] || [];
  return rows.find((item) => Number(item.id) === Number(id)) || {};
}

function isPropertyExpanded(property, rooms) {
  const saved = state.expandedProperties[property.id];
  if (saved !== undefined) return saved;
  return rooms.some((room) => collectInfo(room).enabled);
}

function toggleProperty(propertyId) {
  const properties = state.data.properties || demo.properties;
  const rooms = state.data.rooms || demo.rooms;
  const property = properties.find((item) => Number(item.id) === Number(propertyId));
  if (!property) return;
  const propertyRooms = rooms.filter((room) => Number(room.propertyId) === Number(propertyId));
  state.expandedProperties[propertyId] = !isPropertyExpanded(property, propertyRooms);
  render();
}

async function togglePayments() {
  state.paymentsExpanded = !state.paymentsExpanded;
  render();
  if (state.paymentsExpanded && !state.paymentsLoaded) await loadPayments({ reset: true });
}

function filterDueRows(rows) {
  if (state.filter === "overdue") return rows.filter((row) => row.urgency === "OVERDUE");
  return rows;
}

function filterRows(rows, keys) {
  const keyword = (state.keyword || "").trim().toLowerCase();
  if (!keyword) return rows;
  return rows.filter((row) => keys.some((key) => String(row[key] || "").toLowerCase().includes(keyword)));
}

function propertyTitle(record) {
  return record?.address || record?.propertyAddress || record?.propertyName || record?.name || "未填地址";
}

function roomSummary(rooms) {
  return {
    rented: rooms.filter((room) => room.status === "RENTED").length,
    vacant: rooms.filter((room) => room.status === "VACANT").length,
  };
}

function dueTag(room) {
  if (!room.nextDueDate) return tag("未设置应收日", "warn");
  if (room.nextDueDate < today()) return tag("逾期未收", "danger");
  if (room.nextDueDate <= addDays(today(), 7)) return tag("近期应收", "warn");
  return tag("正常", "blue");
}

function statusTone(status) {
  if (status === "VACANT") return "blue";
  if (status === "RESERVED") return "warn";
  if (status === "MAINTENANCE" || status === "OFFLINE") return "gray";
  return "";
}

function roomMiniCard(room) {
  return `<article class="room"><strong>${esc(room.propertyName)} ${esc(room.roomNo)}</strong>${tag(fmtMoney(room.rentAmount), "blue")} ${tag(`押${Number(room.depositAmount || 0)}`, "gray")}<p>${esc(room.tags || "空置可出租")}</p></article>`;
}

function searchBox(placeholder) {
  return `<div class="searchbar"><input id="keyword" value="${esc(state.keyword || "")}" placeholder="${placeholder}" data-search><button class="ghost" data-clear-search>清空</button></div>`;
}

function table(heads, rows, cells) {
  if (!rows || rows.length === 0) return empty("暂无数据");
  return `<div class="table-wrap"><table><thead><tr>${heads.map((h) => `<th>${h}</th>`).join("")}</tr></thead><tbody>${rows.map((row) => `<tr>${labelCells(cells(row), heads)}</tr>`).join("")}</tbody></table></div>`;
}

function labelCells(html, heads) {
  let index = 0;
  return html.replace(/<td(.*?)>/g, (match, attrs) => `<td${attrs} data-label="${esc(heads[index++] || "")}">`);
}

function metric(name, value, tone) {
  return `<div class="metric ${tone}"><span>${name}</span><strong>${value}</strong></div>`;
}

function tag(text, tone = "") {
  return `<span class="tag ${tone}">${esc(text)}</span>`;
}

function empty(text) {
  return `<div class="empty">${text}</div>`;
}

function showToast(message) {
  const toast = $("#toast");
  toast.textContent = message;
  toast.hidden = false;
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.hidden = true, 2800);
}

function setBusy(busy) {
  const refresh = $("#refreshBtn");
  if (refresh) {
    refresh.disabled = busy;
    refresh.textContent = busy ? "刷新中..." : "刷新";
  }
}

function setFormBusy(busy) {
  const submit = $("#modalForm button[type='submit']");
  if (submit) {
    submit.disabled = busy;
    submit.textContent = busy ? "保存中..." : "保存";
  }
}

function closeModal() {
  closeDialog($("#modal"));
  $("#modalForm").reset();
  $("#modalForm").dataset.id = "";
}

function closeImageDialog() {
  closeDialog($("#imageDialog"));
  $("#roomImageInput").value = "";
  resetPendingImage();
  state.imageRoomId = null;
  state.imageUploading = false;
}

function closeConfirm() {
  state.pendingConfirm = null;
  closeDialog($("#confirmDialog"));
}

function goView(view) {
  state.view = view;
  state.keyword = "";
  state.filter = "all";
  document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item.dataset.view === view));
  load();
}

document.querySelectorAll(".nav-item").forEach((button) => button.addEventListener("click", () => goView(button.dataset.view)));
$("#refreshBtn").addEventListener("click", load);
$("#quickAddBtn").addEventListener("click", () => openForm(state.view === "properties" ? "room" : "property"));
$("#modalForm").addEventListener("submit", submitForm);
$("#modalForm").addEventListener("input", syncRentDateDefaults);
document.querySelectorAll("[data-close-modal]").forEach((button) => button.addEventListener("click", closeModal));
document.querySelectorAll("[data-cancel-confirm]").forEach((button) => button.addEventListener("click", closeConfirm));
document.querySelectorAll("[data-close-image]").forEach((button) => button.addEventListener("click", closeImageDialog));
document.querySelectorAll("dialog").forEach((dialog) => dialog.addEventListener("close", unlockPageScrollIfIdle));
$("#confirmOkBtn").addEventListener("click", () => state.pendingConfirm?.());
$("#uploadImageBtn").addEventListener("click", uploadRoomImage);
$("#deleteImageBtn").addEventListener("click", deleteRoomImage);
$("#roomImageInput").addEventListener("change", previewSelectedRoomImage);

$("#content").addEventListener("click", (event) => {
  const viewButton = event.target.closest("[data-view-go]");
  if (viewButton) return goView(viewButton.dataset.viewGo);
  const toggleButton = event.target.closest("[data-toggle-property]");
  if (toggleButton) return toggleProperty(toggleButton.dataset.toggleProperty);
  if (event.target.closest("[data-toggle-payments]")) return togglePayments();
  if (event.target.closest("[data-load-payments]")) return loadPayments();
  const imageButton = event.target.closest("[data-room-image]");
  if (imageButton) return openImageDialog(imageButton.dataset.roomImage);
  const formButton = event.target.closest("[data-form]");
  if (formButton) return openFormFromButton(formButton);
  const collectButton = event.target.closest("[data-request-collect]");
  if (collectButton) return requestCollect(collectButton.dataset.requestCollect);
  const roomStatusButton = event.target.closest("[data-room-status]");
  if (roomStatusButton) {
    const [roomId, status] = roomStatusButton.dataset.roomStatus.split(":");
    return requestRoomStatus(roomId, status);
  }
  const deleteButton = event.target.closest("[data-delete]");
  if (deleteButton) return requestDelete(deleteButton.dataset.delete);
  const filterButton = event.target.closest("[data-filter]");
  if (filterButton) {
    state.filter = state.filter === filterButton.dataset.filter ? "all" : filterButton.dataset.filter;
    return render();
  }
  if (event.target.closest("[data-clear-search]")) {
    state.keyword = "";
    render();
  }
});

$("#content").addEventListener("input", (event) => {
  if (!event.target.matches("[data-search]") || state.composing) return;
  state.keyword = event.target.value;
  render();
  $("#keyword")?.focus();
});

$("#content").addEventListener("compositionstart", (event) => {
  if (event.target.matches("[data-search]")) state.composing = true;
});

$("#content").addEventListener("compositionend", (event) => {
  if (!event.target.matches("[data-search]")) return;
  state.composing = false;
  state.keyword = event.target.value;
  render();
});

loadRuntimeConfig().finally(load);
