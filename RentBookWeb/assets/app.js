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
  imageAction: "",
  imagePreviewUrl: "",
  imagePreviewName: "",
  scrollLockY: 0,
  dueDateRoomId: null,
  settlementRoomId: null,
  settlementPreview: null,
  settlementPreviewRequest: 0,
  loadErrors: {},
};

const roomImageLoader = {
  observer: null,
  queue: [],
  active: 0,
  maxActive: 3,
  loaded: new Set(),
};

const mutationRequests = new Map();

const labels = {
  dashboard: "总览",
  properties: "房态收租",
};

const hints = {
  dashboard: "让老娘今天先看看哪些该收、哪些空着",
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

const dueDateReasonText = {
  ENTRY_ERROR: "录入错误",
  RENT_FREE_PERIOD: "免租期",
  SCHEDULE_CHANGE: "约定调整",
  OTHER: "其他",
};

const roomStatusSearchTerms = {
  VACANT: "空置 未租 待租 可出租",
  RESERVED: "预定 已预定 预约",
  RENTED: "已出租 出租 已租 在租",
  MAINTENANCE: "维修 维护",
  OFFLINE: "下架 停用",
};

const PROPERTY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTWXYZ#".split("");
const PROPERTY_SEARCH_TARGET = "SEARCH";
const PROPERTY_PINYIN_BOUNDARIES = [
  ["A", "阿"], ["B", "八"], ["C", "擦"], ["D", "搭"], ["E", "蛾"], ["F", "发"],
  ["G", "噶"], ["H", "哈"], ["J", "击"], ["K", "喀"], ["L", "拉"], ["M", "妈"],
  ["N", "拿"], ["O", "哦"], ["P", "啪"], ["Q", "期"], ["R", "然"], ["S", "撒"],
  ["T", "塌"], ["W", "挖"], ["X", "西"], ["Y", "压"], ["Z", "匝"],
];
const PROPERTY_PINYIN_OVERRIDES = { "长": "C", "重": "C", "厦": "X", "乐": "L" };
const propertyPinyinCollator = (() => {
  try {
    return new Intl.Collator("zh-Hans-CN-u-co-pinyin");
  } catch (error) {
    return new Intl.Collator("zh-CN");
  }
})();
let propertyAlphabetDragging = false;
let propertyAlphabetPreviewTimer = 0;
let propertyAlphabetPreviewShownAt = 0;

const demo = {
  dashboard: {
    summary: { roomCount: 4, vacantCount: 2, rentedCount: 1, monthIncome: 1800, monthReceivable: 3450, dueSoonCount: 1, overdueCount: 1 },
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
    { id: 1, propertyId: 1, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "301-A", status: "RENTED", rentAmount: 1800, depositAmount: 1800, payCycleMonths: 1, leaseStartDate: "2026-06-01", leaseEndDate: "2027-05-31", nextDueDate: "2026-06-25", nextPeriodStartDate: "2026-06-01" },
    { id: 2, propertyId: 1, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "301-B", status: "VACANT", rentAmount: 1500, depositAmount: 1500 },
    { id: 3, propertyId: 1, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "302-A", status: "RESERVED", rentAmount: 1650, depositAmount: 1650 },
    { id: 4, propertyId: 2, propertyName: "滨江大道 12 号滨江公寓 A 座", roomNo: "1201", status: "VACANT", rentAmount: 4200, depositAmount: 4200 },
  ],
  payments: [
    { id: 1, propertyName: "人民路 88 号阳光花园 3 栋", roomNo: "301-A", amount: 1800, paidDate: "2026-06-01", periodStart: "2026-06-01", periodEnd: "2026-06-30", method: "微信" },
  ],
};

const $ = (selector) => document.querySelector(selector);
const toastHomeParent = $("#toast").parentElement;
const toastHomeNextSibling = $("#toast").nextSibling;
let dialogOpenSequence = 0;
const toYmd = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
};
const today = () => toYmd(new Date());
const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (s) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[s]));
const fmtAmount = (value) => Number(value || 0).toLocaleString("zh-CN");
const fmtMoney = (value) => `￥${fmtAmount(value)}`;
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
const runtimeConfig = {
  apiBaseUrl: window.location.origin,
  rentCollectAdvanceDays: 7,
  allowDemoData: false,
};
const apiBase = () => runtimeConfig.apiBaseUrl.replace(/\/$/, "");
const rentCollectAdvanceDays = () => Math.max(0, Number(runtimeConfig.rentCollectAdvanceDays || 7));
const toCamel = (key) => key.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase());
const normalize = (value) => {
  if (Array.isArray(value)) return value.map(normalize);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [toCamel(key), normalize(item)]));
};

const userFieldLabels = {
  address: "房源地址",
  propertyId: "所属房源",
  roomNo: "房号",
  rentAmount: "月租金",
  depositAmount: "押金",
  leaseStartDate: "租期开始日期",
  leaseEndDate: "租期结束日期",
  nextDueDate: "下次收租日",
  payCycleMonths: "几个月一收",
  moveOutDate: "实际退租日期",
  rentRefundAmount: "实际退还租金",
  depositDeductionAmount: "押金扣款",
  reason: "原因",
  notes: "补充说明",
};

function friendlyMessage(message, status = 0) {
  let text = String(message || "").trim();
  if (!text) return status >= 500 ? "系统开小差了，请稍后再试" : "操作没有完成，请稍后再试";
  if (/failed to fetch|networkerror|load failed|network request failed/i.test(text)) {
    return "暂时无法连接，请检查网络后重试";
  }
  if (/invalid payment cursor/i.test(text)) return "收租记录加载失败，请刷新后重试";
  if (/当前出租轮次缺失|数据库升级脚本/.test(text)) {
    return "这间房的出租信息不完整，请暂停操作并联系维护人员";
  }
  if (/幂等|序列化|请求摘要|写操作未返回|不在HTTP请求|traceId/i.test(text)) {
    return "操作没有完成，请稍后再试";
  }
  if (/^请求失败（\d+）$/.test(text)) return "操作没有完成，请稍后再试";
  if (text === "数据库操作失败，请稍后重试") return "操作没有保存成功，请稍后再试";
  if (text === "请求地址不存在") return "当前功能暂时不可用，请刷新后再试";
  if (text === "系统开小差了，请查看后端日志") return "系统开小差了，请稍后再试";
  Object.entries(userFieldLabels).some(([field, label]) => {
    const prefix = `${field}:`;
    if (!text.startsWith(prefix)) return false;
    text = `${label}：${text.slice(prefix.length).trim()}`;
    return true;
  });
  if (status >= 500 && !["操作没有保存成功，请稍后再试", "系统开小差了，请稍后再试"].includes(text)) {
    return "系统开小差了，请稍后再试";
  }
  return text;
}

function createIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`;
}

function api(path, options = {}) {
  return requestApi(path, options, typeof options.body === "string" ? options.body : "");
}

function apiForm(path, formData) {
  const file = formData.get("file");
  const signatureBody = file instanceof File
    ? `${file.name}|${file.size}|${file.lastModified}`
    : "multipart";
  return requestApi(path, { method: "POST", body: formData }, signatureBody);
}

function requestApi(path, options = {}, signatureBody = "") {
  const method = String(options.method || "GET").toUpperCase();
  const mutation = !["GET", "HEAD", "OPTIONS"].includes(method);
  const signature = mutation ? `${method}|${path}|${signatureBody}` : "";
  if (mutation && mutationRequests.has(signature)) return mutationRequests.get(signature);

  const headers = new Headers(options.headers || {});
  if (!(options.body instanceof FormData) && options.body != null && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (mutation) headers.set("X-Idempotency-Key", createIdempotencyKey());

  const request = fetch(`${apiBase()}${path}`, { ...options, method, headers })
    .then(async (response) => {
      const body = await response.json().catch(() => null);
      if (!response.ok || !body?.success) {
        const error = new Error(friendlyMessage(body?.message, response.status));
        error.status = response.status;
        throw error;
      }
      return normalize(body.data);
    })
    .catch((error) => {
      if (error instanceof Error) error.message = friendlyMessage(error.message, error.status);
      throw error;
    })
    .finally(() => {
      if (mutationRequests.get(signature) === request) mutationRequests.delete(signature);
    });

  if (mutation) mutationRequests.set(signature, request);
  return request;
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
  dialog.dataset.openSequence = String(++dialogOpenSequence);
  dialog.showModal();
  syncToastLayer();
}

function closeDialog(dialog) {
  dialog.close();
  unlockPageScrollIfIdle();
}

function syncToastLayer() {
  const toast = $("#toast");
  const activeDialog = [...document.querySelectorAll("dialog[open]")]
    .sort((left, right) => Number(left.dataset.openSequence || 0) - Number(right.dataset.openSequence || 0))
    .at(-1);
  const dialogHost = activeDialog?.querySelector(".modal-box, .confirm-box");

  if (dialogHost) {
    dialogHost.appendChild(toast);
    toast.classList.add("dialog-toast");
    return;
  }

  if (toast.parentElement !== toastHomeParent) {
    toastHomeParent.insertBefore(toast, toastHomeNextSibling);
  }
  toast.classList.remove("dialog-toast");
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

function propertyAlphabetInitial(value) {
  const first = String(value || "").trim().charAt(0);
  if (!first) return "#";
  const latin = first.toUpperCase();
  if (/^[A-Z]$/.test(latin)) return PROPERTY_ALPHABET.includes(latin) ? latin : "#";
  if (PROPERTY_PINYIN_OVERRIDES[first]) return PROPERTY_PINYIN_OVERRIDES[first];
  for (let index = PROPERTY_PINYIN_BOUNDARIES.length - 1; index >= 0; index -= 1) {
    const [letter, boundary] = PROPERTY_PINYIN_BOUNDARIES[index];
    if (propertyPinyinCollator.compare(first, boundary) >= 0) return letter;
  }
  return "#";
}

function comparePropertiesByPinyin(left, right) {
  const leftTitle = propertyTitle(left);
  const rightTitle = propertyTitle(right);
  const leftRank = PROPERTY_ALPHABET.indexOf(propertyAlphabetInitial(leftTitle));
  const rightRank = PROPERTY_ALPHABET.indexOf(propertyAlphabetInitial(rightTitle));
  return leftRank - rightRank || propertyPinyinCollator.compare(leftTitle, rightTitle);
}

function renderPropertyAlphabet() {
  const rail = $("#propertyAlphabetIndex");
  const preview = $("#propertyAlphabetPreview");
  const groups = Array.from(document.querySelectorAll(".property-group[data-property-initial]"));
  const available = new Set(groups.map((group) => group.dataset.propertyInitial));
  rail.innerHTML = `<button type="button" class="property-search-jump" data-property-search
      aria-label="定位到搜索栏"><span class="rail-search-icon" aria-hidden="true"></span></button>` + PROPERTY_ALPHABET.map((letter) => `
    <button type="button" data-property-letter="${letter}" class="${available.has(letter) ? "" : "unavailable"}"
      aria-label="定位到 ${letter} 开头的房源" aria-disabled="${available.has(letter) ? "false" : "true"}">${letter}</button>`).join("");
  const show = state.view === "properties" && groups.length > 0 && window.matchMedia("(max-width: 620px)").matches;
  rail.hidden = !show;
  rail.setAttribute("aria-hidden", String(!show));
  if (!show) {
    preview.hidden = true;
    preview.setAttribute("aria-hidden", "true");
  }
}

function syncPropertyAlphabetActive(groups, stickyTop) {
  const rail = $("#propertyAlphabetIndex");
  if (rail.hidden || !groups.length) return;
  let currentGroup = groups[0];
  for (const group of groups) {
    if (group.getBoundingClientRect().top > stickyTop + 12) break;
    currentGroup = group;
  }
  const activeLetter = currentGroup.dataset.propertyInitial || "#";
  rail.querySelectorAll("[data-property-letter]").forEach((button) => {
    const active = button.dataset.propertyLetter === activeLetter;
    button.classList.toggle("active", active);
    if (active) button.setAttribute("aria-current", "true");
    else button.removeAttribute("aria-current");
  });
}

function showPropertyAlphabetPreview(letter, autoHide = false) {
  const preview = $("#propertyAlphabetPreview");
  const letterButton = $("#propertyAlphabetIndex")?.querySelector(`[data-property-letter="${letter}"]`);
  const letterRect = letterButton?.getBoundingClientRect();
  const previewY = letterRect ? letterRect.top + letterRect.height / 2 : window.innerHeight / 2;
  window.clearTimeout(propertyAlphabetPreviewTimer);
  propertyAlphabetPreviewShownAt = Date.now();
  preview.textContent = letter;
  preview.style.setProperty("--alphabet-preview-y", `${Math.max(42, Math.min(window.innerHeight - 42, previewY))}px`);
  preview.hidden = false;
  preview.setAttribute("aria-hidden", "false");
  if (autoHide) propertyAlphabetPreviewTimer = window.setTimeout(hidePropertyAlphabetPreview, 420);
}

function hidePropertyAlphabetPreview() {
  window.clearTimeout(propertyAlphabetPreviewTimer);
  const preview = $("#propertyAlphabetPreview");
  preview.hidden = true;
  preview.setAttribute("aria-hidden", "true");
}

function jumpToPropertyLetter(letter, smooth = false) {
  showPropertyAlphabetPreview(letter, smooth);
  const target = document.querySelector(`.property-group[data-property-initial="${letter}"]`);
  if (!target) return;
  const stickyTop = Math.ceil($(".sidebar")?.getBoundingClientRect().height || 0);
  const top = window.scrollY + target.getBoundingClientRect().top - stickyTop - 8;
  window.scrollTo({ top: Math.max(0, top), behavior: smooth ? "smooth" : "auto" });
}

function jumpToPropertySearch(smooth = true) {
  const target = document.querySelector(".toolbar-panel");
  if (!target) return;
  const stickyTop = Math.ceil($(".sidebar")?.getBoundingClientRect().height || 0);
  const top = window.scrollY + target.getBoundingClientRect().top - stickyTop - 8;
  window.scrollTo({ top: Math.max(0, top), behavior: smooth ? "smooth" : "auto" });
}

function propertyLetterAtPoint(clientX, clientY) {
  const rail = $("#propertyAlphabetIndex");
  const rect = rail.getBoundingClientRect();
  if (clientX < rect.left - 8 || clientX > rect.right + 8 || clientY < rect.top || clientY > rect.bottom) return "";
  const targets = [PROPERTY_SEARCH_TARGET, ...PROPERTY_ALPHABET];
  const index = Math.min(targets.length - 1, Math.floor((clientY - rect.top) / rect.height * targets.length));
  return targets[Math.max(0, index)] || "";
}

function finishPropertyAlphabetDrag(event) {
  if (!propertyAlphabetDragging) return;
  propertyAlphabetDragging = false;
  if ($("#propertyAlphabetIndex").hasPointerCapture?.(event.pointerId)) {
    $("#propertyAlphabetIndex").releasePointerCapture(event.pointerId);
  }
  window.clearTimeout(propertyAlphabetPreviewTimer);
  propertyAlphabetPreviewTimer = window.setTimeout(hidePropertyAlphabetPreview, 300);
}

function syncPropertyContexts() {
  const mobile = window.matchMedia("(max-width: 620px)").matches;
  const stickyTop = mobile ? Math.ceil($(".sidebar")?.getBoundingClientRect().height || 0) : 0;
  document.documentElement.style.setProperty("--property-context-top", stickyTop + 6 + "px");
  const context = $("#propertyContext");
  const rail = $("#propertyAlphabetIndex");
  const groups = Array.from(document.querySelectorAll(".property-group"));
  const showRail = mobile && state.view === "properties" && groups.length > 0;
  rail.hidden = !showRail;
  rail.setAttribute("aria-hidden", String(!showRail));
  let activeGroup = null;

  groups.forEach((group) => {
    const head = group.querySelector(".property-head");
    if (!mobile || !head || group.classList.contains("collapsed")) return;
    const headRect = head.getBoundingClientRect();
    const groupRect = group.getBoundingClientRect();
    const nextHead = group.nextElementSibling?.querySelector(".property-head");
    const nextPropertyVisible = nextHead && nextHead.getBoundingClientRect().top < window.innerHeight - 140;
    const visible = headRect.bottom <= stickyTop + 4
      && groupRect.bottom > stickyTop + 48
      && !nextPropertyVisible;
    if (visible && !activeGroup) activeGroup = group;
  });

  context.hidden = !activeGroup;
  context.setAttribute("aria-hidden", String(!activeGroup));
  syncPropertyAlphabetActive(groups, stickyTop);
  if (!activeGroup) return;
  $("#propertyContextIndex").textContent = activeGroup.dataset.propertyContextIndex || "";
  $("#propertyContextLabel").textContent = activeGroup.dataset.propertyContextLabel || "";
}

function schedulePropertyContextSync() {
  window.cancelAnimationFrame(schedulePropertyContextSync.frame);
  schedulePropertyContextSync.frame = window.requestAnimationFrame(syncPropertyContexts);
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
  document.body.dataset.view = state.view;
  $("#pageTitle").textContent = labels[state.view];
  $("#pageHint").textContent = hints[state.view];
  $("#quickAddBtn").hidden = state.view !== "properties";
  $("#quickAddBtn").textContent = "新增房间";
  $("#propertyAddBtn").hidden = state.view !== "properties";
  setBusy(true);
  try {
    if (state.view === "dashboard") state.data.dashboard = await api("/api/dashboard");
    if (state.view === "properties") await loadProperties();
    delete state.loadErrors[state.view];
  } catch (error) {
    state.loadErrors[state.view] = true;
    if (runtimeConfig.allowDemoData) {
      state.data[state.view] = demo[state.view];
      if (state.view === "properties") {
        state.data.rooms = demo.rooms;
        state.data.payments = demo.payments;
        state.paymentsLoaded = true;
        state.paymentsHasMore = false;
      }
      showToast("暂时无法连接，当前显示示例内容");
    } else {
      if (state.view === "dashboard") {
        state.data.dashboard = { summary: {}, dueRent: [], vacantRooms: [] };
      } else {
        state.data.properties = [];
        state.data.rooms = [];
        state.data.payments = [];
      }
      showToast("暂时无法加载数据，请检查网络后重试");
    }
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
  if (state.loadErrors[state.view] && !runtimeConfig.allowDemoData) {
    $("#content").innerHTML = `<section class="panel">${empty("数据暂时无法加载，请点击右上角“刷新”重试")}</section>`;
  } else {
    if (state.view === "dashboard") $("#content").innerHTML = renderDashboard(state.data.dashboard || demo.dashboard);
    if (state.view === "properties") $("#content").innerHTML = renderProperties(state.data.properties || demo.properties, state.data.rooms || demo.rooms, state.data.payments || []);
  }
  renderPropertyAlphabet();
  scheduleRoomImages();
  schedulePropertyContextSync();
}

function renderDashboard(data) {
  const s = data.summary || {};
  const allDueRows = data.dueRent || [];
  const dueRows = filterDueRows(allDueRows);
  const dueCountText = searchKeyword() ? `找到 ${dueRows.length} 间` : `待收 ${allDueRows.length} 间`;
  const hasMonthReceivable = s.monthReceivable !== null
    && s.monthReceivable !== undefined
    && s.monthReceivable !== "";
  const monthReceivable = hasMonthReceivable ? s.monthReceivable : null;
  const monthReceivableText = hasMonthReceivable ? fmtAmount(monthReceivable) : "--";
  const monthIncomeProgress = `<span>${fmtMoney(s.monthIncome)}</span><span>/ ${monthReceivableText}</span>`;
  const incomeProgressClass = Math.max(Math.abs(Number(s.monthIncome || 0)), Math.abs(Number(monthReceivable || 0))) >= 100000
    ? "income-progress income-progress-wide"
    : "income-progress";
  return `
    <section class="metrics">
      ${metric("本月已收 / 应收", monthIncomeProgress, incomeProgressClass)}
      ${metric("空置房间 / 总房间", `${s.vacantCount || 0}/${s.roomCount || 0}`, "")}
      ${metric("7天内应收", s.dueSoonCount || 0, "warn")}
      ${metric("逾期未收", s.overdueCount || 0, "danger")}
    </section>
    <section class="panel dashboard-due-panel">
      <div class="panel-head">
        <div class="panel-title"><h3>该收租的房间</h3><small>收到租金后，点一下“收租”</small></div>
        <span class="tag warn" id="dashboardDueCount">${dueCountText}</span>
      </div>
      ${searchBox("搜房源地址、房号或应收日期")}
      <div id="dashboardDueResults">${renderDashboardDueResults(dueRows)}</div>
    </section>
    <section class="panel">
      <div class="panel-head"><h3>空置房间</h3><span class="tag">${(data.vacantRooms || []).length} 间</span></div>
      <div class="room-grid">${(data.vacantRooms || []).map((r) => roomMiniCard(r)).join("") || empty("暂无空房")}</div>
    </section>`;
}

function renderDashboardDueResults(rows) {
  return table(["房源/房间", "应收日", "金额", "状态", "操作"], rows, (r) => `
    <td><strong>${esc(r.propertyName)} ${esc(r.roomNo)}</strong></td>
    <td>${esc(r.nextDueDate || "-")}</td>
    <td>${fmtMoney(r.receivableAmount ?? Number(r.rentAmount || 0) * Number(r.payCycleMonths || 1))}</td>
    <td>${tag(statusText[r.urgency] || "待收", r.urgency === "OVERDUE" ? "danger" : "warn")}</td>
    <td class="row-actions">${collectButton(r)}</td>`, searchKeyword() ? "没有找到匹配的待收房间" : "暂无待收房间");
}

function renderProperties(properties, rooms, payments) {
  return `
    <section class="panel toolbar-panel">
      ${searchBox("搜地址、房号、房态")}
    </section>
    <section class="property-stack" id="propertySearchResults">
      ${renderPropertySearchResults(properties, rooms)}
    </section>
    ${renderPaymentRecords(payments)}`;
}

function renderPropertySearchResults(properties, rooms) {
  const keyword = searchKeyword();
  const grouped = properties.slice().sort(comparePropertiesByPinyin).map((property) => {
    const propertyRooms = rooms.filter((room) => Number(room.propertyId) === Number(property.id));
    if (!keyword) return { property, rooms: propertyRooms };

    const propertyMatches = matchesSearchValues(
      [property.name, property.address, property.district, propertyTitle(property)],
      keyword,
    );
    const matchingRooms = propertyRooms.filter((room) => roomMatchesSearch(room, keyword));
    if (!propertyMatches && matchingRooms.length === 0) return null;
    return { property, rooms: propertyMatches ? propertyRooms : matchingRooms };
  }).filter(Boolean).map(({ property, rooms: propertyRooms }) => ({
    property,
    rooms: propertyRooms,
    initial: propertyAlphabetInitial(propertyTitle(property)),
  }));

  return grouped.map(({ property, rooms: propertyRooms, initial }, index) => propertyBlock(property, propertyRooms, index, initial, Boolean(keyword))).join("")
    || empty(keyword ? "没有找到匹配的房源或房间" : "暂无房源");
}

function propertyBlock(property, rooms, index, initial, forceExpanded = false) {
  const summary = roomSummary(rooms);
  const expanded = forceExpanded || isPropertyExpanded(property, rooms);
  const dueCount = rooms.filter((room) => collectInfo(room).enabled).length;
  return `<section class="property-group property-tone-${index % 6} ${expanded ? "expanded" : "collapsed"}" data-property-context-index="${index + 1}" data-property-context-label="${esc(propertyTitle(property))}" data-property-initial="${initial}">
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
        ${dueCount ? tag(`待收 ${dueCount}`, "property-due") : ""}
      </div>
      <div class="panel-tools property-actions">
        <button class="mini primary" data-form="room" data-property-id="${property.id}">加房间</button>
        <button class="mini ghost" data-form="property" data-id="${property.id}">编辑</button>
        <button class="mini danger" data-delete="property:${property.id}">删除</button>
      </div>
    </div>
    ${expanded ? `<div class="room-list">${rooms.map((room) => roomCard(room, index % 6)).join("") || empty("还没有房间")}</div>` : ""}
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
  const actions = room.status === "RENTED"
    ? `${collectButton(room)}
      <button class="mini ghost" data-form="rent" data-id="${room.id}">收租设置</button>
      <button class="mini settle" data-settle-room="${room.id}">退租</button>`
    : `<button class="mini primary" data-form="rent" data-id="${room.id}">出租</button>
      <button class="mini" data-room-status="${room.id}:${room.status === "RESERVED" ? "VACANT" : "RESERVED"}">${room.status === "RESERVED" ? "空置" : "预定"}</button>`;
  return `<article class="room-row property-room-tone-${propertyTone} status-${room.status || "UNKNOWN"}">
    <button class="room-photo ${image ? "has-image" : "empty"}" data-room-image="${room.id}" aria-label="${image ? "查看或更换房间图片" : "添加房间图片"}">
      ${image ? `<span class="room-photo-placeholder">图片加载中</span><img data-room-card-image data-src="${esc(image)}" data-fallback-src="${esc(fallbackImage)}" alt="${esc(room.roomNo)}房间图片" decoding="async">` : `<span>添加图片</span>`}
    </button>
    <div class="room-main">
      <strong>${esc(room.roomNo)}</strong>
      <span>${due}</span>
      <small>${fmtMoney(room.rentAmount)} / 押${Number(room.depositAmount || 0).toLocaleString("zh-CN")}</small>
      ${room.leaseStartDate && room.leaseEndDate ? `<small>租期：${esc(room.leaseStartDate)} 至 ${esc(room.leaseEndDate)}</small>` : ""}
      ${room.nextDueDate ? `<small>下次收租：${esc(room.nextDueDate)}，${room.payCycleMonths || 1}个月一收</small>` : ""}
      ${room.nextPeriodStartDate && room.nextPeriodStartDate !== room.nextDueDate ? `<small>下次租金从：${esc(room.nextPeriodStartDate)} 起</small>` : ""}
    </div>
    <div class="row-actions">
      ${actions}
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
    : state.paymentsLoaded ? "暂无收租记录" : "点开查看最近收租";
  const countText = state.paymentsLoaded ? `已加载 ${filtered.length} 笔` : "未加载";
  return `<section class="panel payment-history ${expanded ? "expanded" : "collapsed"}">
    <button class="payment-history-head" data-toggle-payments aria-expanded="${expanded}">
      <span>
        <strong>最近收租</strong>
        <small>${esc(latestText)}</small>
      </span>
      <span class="payment-history-meta">
        <span class="tag">${countText}</span>
        <span class="toggle-mark">${expanded ? "点击收起" : "点击查看"}</span>
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
    fields: [["propertyId", "所属房源", "select", "properties"], ["roomNo", "房号"], ["rentAmount", "月租金", "number"], ["depositAmount", "押金", "number"], ["notes", "备注", "textarea"]],
  },
  rent: {
    title: "出租/收租设置",
    tip: "设置好租期、租金和下次收租日，以后收到钱后点一次“收租”即可。",
    path: (ctx) => `/api/properties/rooms/${ctx.id}/rent`,
    method: () => "POST",
    fields: [["rentAmount", "月租金", "number"], ["depositAmount", "押金", "number"], ["leaseStartDate", "租期开始日期", "date", null, today()], ["leaseEndDate", "租期结束日期", "date"], ["payCycleMonths", "几个月一收", "number", null, "1"], ["nextDueDate", "下次收租日", "date"], ["notes", "备注", "textarea"]],
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
    const lockedDueDate = formType === "rent" && name === "nextDueDate" && initial.latestCoveredDate;
    const control = lockedDueDate
      ? renderLockedDueDate(initial, value)
      : renderField(name, type, options, value, required);
    const full = type === "textarea" || lockedDueDate ? "full" : "";
    return `<div class="field ${full} ${required ? "required" : ""}"><label>${label}</label>${control}</div>`;
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
  if (!room.id) return showToast("这个房间可能已被删除，请刷新后再试");
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
  $("#deleteImageBtn").textContent = state.imageUploading && state.imageAction === "delete" ? "删除中..." : "删除图片";
  $("#uploadImageBtn").disabled = state.imageUploading || !pending;
  $("#uploadImageBtn").textContent = state.imageUploading && state.imageAction === "upload"
    ? "保存中..."
    : pending ? "保存图片" : "先选图片";
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
  if (state.imageUploading) return;
  const roomId = state.imageRoomId;
  const file = $("#roomImageInput").files?.[0];
  if (!roomId) return showToast("这个房间可能已被删除，请刷新后再试");
  if (!file) return showToast("请先选择图片");
  const message = validateRoomImageFile(file);
  if (message) return showToast(message);
  const formData = new FormData();
  formData.append("file", file);
  state.imageUploading = true;
  state.imageAction = "upload";
  updateImageDialog();
  try {
    const image = await apiForm(`/api/properties/rooms/${roomId}/images`, formData);
    applyRoomImage(roomId, image);
    resetPendingImage();
    showToast("房间图片已保存");
    $("#roomImageInput").value = "";
    render();
    updateImageDialog(findRecord("room", roomId));
  } catch (error) {
    showToast(error.message);
  } finally {
    state.imageUploading = false;
    state.imageAction = "";
    updateImageDialog();
  }
}

function renderLockedDueDate(room, value) {
  const nextPeriodStart = normalizeDate(room.nextPeriodStartDate)
    || (room.latestCoveredDate ? addDays(room.latestCoveredDate, 1) : normalizeDate(room.leaseStartDate));
  return `
    <input name="nextDueDate" type="hidden" value="${esc(normalizeDate(value))}">
    <div class="due-date-locked">
      <span>
        <strong>${esc(normalizeDate(value) || "-")}</strong>
        <small>下次租金从 ${esc(nextPeriodStart || "-")} 开始计算；修改“几个月一收”不会改变下次收租日</small>
      </span>
      <button type="button" class="ghost" data-adjust-due-date="${room.id}">调日期</button>
    </div>`;
}

async function deleteRoomImage() {
  if (state.imageUploading) return;
  const roomId = state.imageRoomId;
  const room = findRecord("room", roomId);
  if (!room.id || !room.imageId) return showToast("当前房间没有图片");
  state.imageUploading = true;
  state.imageAction = "delete";
  updateImageDialog(room);
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
  } finally {
    state.imageUploading = false;
    state.imageAction = "";
    updateImageDialog();
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
    if (!payload[key]) {
      const field = def.fields.find(([name]) => name === key);
      return showToast(`请填写“${field?.[1] || "必填内容"}”`);
    }
  }
  const validationMessage = validateFormPayload(type, payload, ctx);
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
    const savedMessage = type === "rent"
      ? "收租规则已保存"
      : `${type === "property" ? "房源" : "房间"}${ctx.id ? "信息已更新" : "已新增"}`;
    showToast(savedMessage);
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

function validateFormPayload(type, payload, ctx = {}) {
  if (type !== "rent") return "";
  const start = normalizeDate(payload.leaseStartDate);
  const end = normalizeDate(payload.leaseEndDate);
  const nextDue = normalizeDate(payload.nextDueDate);
  const cycle = Number(payload.payCycleMonths || 1);
  if (cycle < 1) return "收租间隔不能少于1个月";
  if (!parseDate(start) || !parseDate(end) || !parseDate(nextDue)) return "请填写正确的租期和收租日期";
  if (end < start) return "租期结束日期不能早于开始日期";
  if (nextDue < start || nextDue > end) return "下次收租日必须在租期范围内";
  const room = ctx.id ? findRecord("room", ctx.id) : {};
  const nextPeriodStart = room.status === "RENTED"
    ? normalizeDate(room.nextPeriodStartDate)
      || (room.latestCoveredDate ? addDays(room.latestCoveredDate, 1) : start)
    : start;
  if (addMonthsMinusDay(nextPeriodStart, cycle) > end) return "按这个收租间隔，最后一次收租会超出租期结束日期，请缩短间隔或延长租期";
  return "";
}

function collectInfo(room) {
  const due = normalizeDate(room.nextDueDate);
  const id = room.id || room.roomId;
  const months = Number(room.payCycleMonths || 1);
  if (!id) return { enabled: false, reason: "这个房间可能已被删除，请刷新后再试" };
  if (!due) return { enabled: false, reason: "先设置收租日", label: "未设置" };
  const advanceDays = rentCollectAdvanceDays();
  if (due > addDays(today(), advanceDays)) {
    return { enabled: false, reason: `距离收租日还早，提前${advanceDays}天时才能登记`, label: "未到期" };
  }
  const periodStart = normalizeDate(room.nextPeriodStartDate)
    || (room.latestCoveredDate ? addDays(room.latestCoveredDate, 1) : "")
    || normalizeDate(room.leaseStartDate)
    || due;
  const periodEnd = addMonthsMinusDay(periodStart, months);
  if (room.leaseEndDate && periodStart > room.leaseEndDate) {
    return { enabled: false, reason: "租期已结束", label: "已到期" };
  }
  if (room.leaseEndDate && periodEnd > room.leaseEndDate) {
    return { enabled: false, reason: "本次会收到租期结束日之后，请先调整租期或收租间隔", label: "先改设置" };
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
    return `<button class="mini ghost collect-disabled" disabled title="${esc(info.reason)}">${esc(info.label || "暂不可收")}</button>`;
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
  if (!info.enabled) return showToast(info.reason || "现在还不能收租，请查看按钮提示");
  state.pendingConfirm = () => collectRoomRent(roomId);
  $("#confirmTitle").textContent = "收租确认";
  $("#confirmMessage").textContent = `${propertyTitle(room)} ${room.roomNo || ""}\n应收日：${normalizeDate(room.nextDueDate)}\n收款金额：${fmtMoney(info.amount)}\n这笔租金对应：${info.periodStart} 至 ${info.periodEnd}`;
  $("#confirmOkBtn").textContent = "确认收租";
  showLockedDialog($("#confirmDialog"));
}

function openDueDateDialog(roomId) {
  const room = findRecord("room", roomId);
  if (!room.id || !room.nextDueDate) return showToast("当前房间没有可调整的收租日");
  state.dueDateRoomId = Number(roomId);
  $("#dueDateRoomLabel").textContent = `${propertyTitle(room)} ${room.roomNo || ""}`;
  $("#dueCurrentDate").textContent = normalizeDate(room.nextDueDate) || "-";
  $("#dueLatestCoveredDate").textContent = normalizeDate(room.nextPeriodStartDate)
    || (room.latestCoveredDate ? addDays(room.latestCoveredDate, 1) : normalizeDate(room.leaseStartDate))
    || "-";
  $("#dueNewDate").value = normalizeDate(room.nextDueDate);
  $("#dueDateNotes").value = "";
  document.querySelectorAll("#dueDateForm input[name='reason']").forEach((radio) => {
    radio.checked = false;
  });
  updateDueDateNotesRequirement();
  updateDueDatePreview();
  showLockedDialog($("#dueDateDialog"));
}

function updateDueDateNotesRequirement() {
  const reason = $("#dueDateForm input[name='reason']:checked")?.value;
  const notes = $("#dueDateNotes");
  notes.required = reason === "OTHER";
  notes.placeholder = reason === "OTHER" ? "请简要说明调整原因" : "可选";
}

function isDateInCurrentMonth(value) {
  return Boolean(value) && normalizeDate(value).slice(0, 7) === today().slice(0, 7);
}

function updateDueDatePreview() {
  const room = findRecord("room", state.dueDateRoomId);
  const nextDueDate = normalizeDate($("#dueNewDate").value);
  const currentDueDate = normalizeDate(room.nextDueDate);
  const cycle = Number(room.payCycleMonths || 1);
  const periodStart = normalizeDate(room.nextPeriodStartDate)
    || (room.latestCoveredDate ? addDays(room.latestCoveredDate, 1) : normalizeDate(room.leaseStartDate));
  const periodEnd = periodStart ? addMonthsMinusDay(periodStart, cycle) : "";
  const amount = Number(room.rentAmount || 0) * cycle;
  const preview = $("#dueDatePreview");
  const submit = $("#dueDateSubmitBtn");

  let invalidMessage = "";
  if (!nextDueDate) invalidMessage = "请选择新的下次收租日";
  else if (nextDueDate === currentDueDate) invalidMessage = "请选择一个不同的日期";
  else if (room.leaseStartDate && nextDueDate < normalizeDate(room.leaseStartDate)) invalidMessage = "下次收租日不能早于租期开始日期";
  else if (room.leaseEndDate && nextDueDate > normalizeDate(room.leaseEndDate)) invalidMessage = "下次收租日不能晚于租期结束日期";

  if (invalidMessage) {
    preview.className = "due-date-preview invalid";
    preview.innerHTML = `<strong>${esc(invalidMessage)}</strong>`;
    submit.disabled = true;
    return;
  }

  let monthImpact = "本月应收金额不变";
  if (isDateInCurrentMonth(currentDueDate) && !isDateInCurrentMonth(nextDueDate)) {
    monthImpact = `本月应收将减少 ${fmtMoney(amount)}`;
  } else if (!isDateInCurrentMonth(currentDueDate) && isDateInCurrentMonth(nextDueDate)) {
    monthImpact = `本月应收将增加 ${fmtMoney(amount)}`;
  }
  preview.className = "due-date-preview safe";
  preview.innerHTML = `
    <div><span>新的收租日</span><strong>${esc(nextDueDate)}</strong></div>
    <div><span>这笔租金对应</span><strong>${esc(periodStart)} 至 ${esc(periodEnd)}</strong></div>
    <div><span>应收金额</span><strong>${fmtMoney(amount)}</strong></div>
    <div><span>本月应收变化</span><strong>${esc(monthImpact)}</strong></div>
    <p>只改变收租提醒和本月应收金额；已经收过的租金和记录不会改变。</p>`;
  submit.disabled = false;
}

function requestDueDateAdjustment(event) {
  event.preventDefault();
  const form = $("#dueDateForm");
  if (!form.reportValidity()) return;
  const room = findRecord("room", state.dueDateRoomId);
  const reason = form.querySelector("input[name='reason']:checked")?.value;
  const notes = $("#dueDateNotes").value.trim();
  if (!reason) return showToast("请选择调整原因");
  if (reason === "OTHER" && !notes) return showToast("选择其他原因时，请填写简短说明");
  updateDueDatePreview();
  if ($("#dueDateSubmitBtn").disabled) return;

  const nextDueDate = normalizeDate($("#dueNewDate").value);
  const payload = {
    expectedNextDueDate: normalizeDate(room.nextDueDate),
    nextDueDate,
    reason,
    notes: notes || undefined,
  };
  state.pendingConfirm = () => saveDueDateAdjustment(room.id, payload);
  $("#confirmTitle").textContent = "确认调整";
  $("#confirmMessage").textContent = `确认把下次收租日从 ${room.nextDueDate} 改为 ${nextDueDate}？\n已经收过的租金和收租记录不会改变。\n原因：${dueDateReasonText[reason]}`;
  $("#confirmOkBtn").textContent = "确认调整";
  showLockedDialog($("#confirmDialog"));
}

async function saveDueDateAdjustment(roomId, payload) {
  try {
    await api(`/api/properties/rooms/${roomId}/next-due-date`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    closeConfirm();
    closeDueDateDialog();
    closeModal();
    showToast(`下次收租日已改为 ${payload.nextDueDate}`);
    await load();
  } catch (error) {
    showToast(error.message);
  }
}

async function openSettlementDialog(roomId) {
  const room = findRecord("room", roomId);
  if (!room.id || room.status !== "RENTED") return showToast("当前房间不需要办理退租");
  state.settlementRoomId = Number(roomId);
  state.settlementPreview = null;
  $("#settlementRoomLabel").textContent = `${propertyTitle(room)} ${room.roomNo || ""}`;
  const moveOutInput = $("#settlementMoveOutDate");
  moveOutInput.min = normalizeDate(room.leaseStartDate) || "";
  moveOutInput.max = today();
  moveOutInput.value = today();
  $("#settlementRentRefund").value = "0";
  $("#settlementDepositDeduction").value = "0";
  $("#settlementNotes").value = "";
  $("#settlementForm input[name='reason'][value='EARLY_TERMINATION']").checked = true;
  $("#settlementSuggestedRent").textContent = "正在计算...";
  $("#settlementDeposit").textContent = fmtMoney(room.depositAmount);
  showLockedDialog($("#settlementDialog"));
  updateSettlementTotals();
  await loadSettlementPreview();
}

async function loadSettlementPreview() {
  const roomId = state.settlementRoomId;
  const moveOutDate = normalizeDate($("#settlementMoveOutDate").value);
  if (!roomId || !moveOutDate) return;
  const requestId = ++state.settlementPreviewRequest;
  $("#settlementSuggestedRent").textContent = "正在计算...";
  $("#settlementSubmitBtn").disabled = true;
  try {
    const preview = await api(`/api/properties/rooms/${roomId}/settlement-preview?moveOutDate=${encodeURIComponent(moveOutDate)}`);
    if (requestId !== state.settlementPreviewRequest || Number(roomId) !== Number(state.settlementRoomId)) return;
    state.settlementPreview = preview;
    $("#settlementSuggestedRent").textContent = fmtMoney(preview.suggestedRentRefundAmount);
    $("#settlementDeposit").textContent = fmtMoney(preview.depositAmount);
    $("#settlementRentRefund").value = Number(preview.suggestedRentRefundAmount || 0).toFixed(2);
    $("#settlementRentRefund").max = Number(preview.maximumRentRefundAmount || 0).toFixed(2);
    $("#settlementDepositDeduction").max = Number(preview.depositAmount || 0).toFixed(2);
    updateSettlementTotals();
    $("#settlementSubmitBtn").disabled = false;
  } catch (error) {
    if (requestId !== state.settlementPreviewRequest) return;
    state.settlementPreview = null;
    $("#settlementSuggestedRent").textContent = "暂时无法计算";
    showToast(error.message);
  }
}

function updateSettlementTotals() {
  const deposit = Number(state.settlementPreview?.depositAmount || findRecord("room", state.settlementRoomId).depositAmount || 0);
  const rentRefund = Math.max(0, Number($("#settlementRentRefund").value || 0));
  const deduction = Math.max(0, Number($("#settlementDepositDeduction").value || 0));
  const depositRefund = Math.max(0, deposit - deduction);
  $("#settlementDepositRefund").textContent = fmtMoney(depositRefund);
  $("#settlementTotalRefund").textContent = fmtMoney(rentRefund + depositRefund);
}

function requestSettlement(event) {
  event.preventDefault();
  const form = $("#settlementForm");
  if (!form.reportValidity() || !state.settlementPreview) return;
  const room = findRecord("room", state.settlementRoomId);
  const reason = form.querySelector("input[name='reason']:checked")?.value;
  const notes = $("#settlementNotes").value.trim();
  const rentRefundAmount = Number($("#settlementRentRefund").value || 0);
  const depositDeductionAmount = Number($("#settlementDepositDeduction").value || 0);
  const maximumRentRefund = Number(state.settlementPreview.maximumRentRefundAmount || 0);
  const depositAmount = Number(state.settlementPreview.depositAmount || 0);
  if (!reason) return showToast("请选择退租原因");
  if ((reason === "OTHER" || depositDeductionAmount > 0) && !notes) {
    return showToast(depositDeductionAmount > 0 ? "有押金扣款时请填写说明" : "请填写退租说明");
  }
  if (rentRefundAmount > maximumRentRefund) return showToast("退还租金不能超过已收但尚未使用的租金");
  if (depositDeductionAmount > depositAmount) return showToast("押金扣款不能超过当前押金");
  const payload = {
    settlementDate: today(),
    moveOutDate: normalizeDate($("#settlementMoveOutDate").value),
    reason,
    rentRefundAmount,
    depositDeductionAmount,
    notes: notes || undefined,
  };
  const depositRefund = Math.max(0, depositAmount - depositDeductionAmount);
  state.pendingConfirm = () => saveSettlement(room.id, payload);
  $("#confirmTitle").textContent = "确认退租";
  $("#confirmMessage").textContent = `${propertyTitle(room)} ${room.roomNo || ""}\n实际退租：${payload.moveOutDate}\n退还租金：${fmtMoney(rentRefundAmount)}\n退还押金：${fmtMoney(depositRefund)}\n完成后房间自动变为空置。`;
  $("#confirmOkBtn").textContent = "确认退租";
  showLockedDialog($("#confirmDialog"));
}

async function saveSettlement(roomId, payload) {
  try {
    await api(`/api/properties/rooms/${roomId}/settle`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
    closeConfirm();
    closeSettlementDialog();
    showToast("退租已完成，房间已变为空置");
    await load();
  } catch (error) {
    showToast(error.message);
  }
}

async function collectRoomRent(roomId) {
  const room = getCollectRoom(roomId);
  if (!room.id) return showToast("这个房间可能已被删除，请刷新后再试");
  const info = collectInfo(room);
  if (!info.enabled) return showToast(info.reason || "现在还不能收租，请查看按钮提示");
  const months = Number(room.payCycleMonths || 1);
  try {
    await api(`/api/properties/rooms/${roomId}/collect`, { method: "POST", body: JSON.stringify({ months, paidDate: today() }) });
    closeConfirm();
    const nextCollectionDate = addMonths(normalizeDate(room.nextDueDate), months);
    showToast(`收租成功，已登记${months}个月租金${nextCollectionDate ? `；下次收租日 ${nextCollectionDate}` : ""}`);
    await load();
  } catch (error) {
    showToast(error.message);
  }
}

async function updateRoomStatus(roomId, status) {
  try {
    await api(`/api/properties/rooms/${roomId}/status`, { method: "PATCH", body: JSON.stringify({ status }) });
    closeConfirm();
    showToast(`已设为“${statusText[status] || status}”`);
    await load();
  } catch (error) {
    showToast(error.message);
  }
}

function requestRoomStatus(roomId, status) {
  const room = findRecord("room", roomId);
  const target = statusText[status] || status;
  state.pendingConfirm = () => updateRoomStatus(roomId, status);
  $("#confirmTitle").textContent = `确认${target}`;
  $("#confirmMessage").textContent = `确认把 ${propertyTitle(room)} ${room.roomNo || ""} 改为“${target}”？`;
  $("#confirmOkBtn").textContent = `确认${target}`;
  showLockedDialog($("#confirmDialog"));
}

function requestDelete(token) {
  const [type] = token.split(":");
  const messages = {
    property: "确认删除这个房源？房源下还有已出租房间时不允许删除。",
    room: "删除后无法恢复，确认删除这个房间？",
    payment: "只能从最新一笔开始撤销；撤销后，下次收租日和已收租期会一起恢复。",
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
    const successMessages = {
      property: "房源已删除",
      room: "房间已删除",
      payment: "收租记录已撤销",
    };
    showToast(successMessages[type] || "操作已完成");
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
  if (!start?.value) return;
  if (event?.target === start && !nextDue.value) nextDue.value = start.value;
  if (event?.target === start && !end.value) end.value = addMonthsMinusDay(start.value, 12);
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
  const urgencyRows = state.filter === "overdue"
    ? rows.filter((row) => row.urgency === "OVERDUE")
    : rows;
  return filterRows(urgencyRows, ["propertyName", "roomNo", "nextDueDate"]);
}

function searchKeyword() {
  return (state.keyword || "").trim().toLowerCase();
}

function matchesSearchValues(values, keyword = searchKeyword()) {
  if (!keyword) return true;
  return values.some((value) => String(value || "").toLowerCase().includes(keyword));
}

function roomMatchesSearch(room, keyword = searchKeyword()) {
  let rentState = "";
  if (room.status === "RENTED") {
    if (!room.nextDueDate) rentState = "未设置应收日";
    else if (room.nextDueDate < today()) rentState = "逾期 逾期未收";
    else if (room.nextDueDate <= addDays(today(), 7)) rentState = "待收 近期应收";
    else rentState = "正常";
  }
  return matchesSearchValues([
    room.propertyName,
    room.roomNo,
    room.status,
    statusText[room.status],
    roomStatusSearchTerms[room.status],
    rentState,
    room.nextDueDate,
    room.tags,
  ], keyword);
}

function filterRows(rows, keys) {
  const keyword = searchKeyword();
  if (!keyword) return rows;
  return rows.filter((row) => matchesSearchValues(keys.map((key) => row[key]), keyword));
}

function renderSearchResults() {
  if (state.view === "dashboard") {
    const data = state.data.dashboard || demo.dashboard;
    const allDueRows = data.dueRent || [];
    const dueRows = filterDueRows(allDueRows);
    const count = $("#dashboardDueCount");
    const results = $("#dashboardDueResults");
    if (count) count.textContent = searchKeyword() ? `找到 ${dueRows.length} 间` : `待收 ${allDueRows.length} 间`;
    if (results) results.innerHTML = renderDashboardDueResults(dueRows);
    return;
  }

  if (state.view === "properties") {
    const results = $("#propertySearchResults");
    if (!results) return;
    results.innerHTML = renderPropertySearchResults(
      state.data.properties || demo.properties,
      state.data.rooms || demo.rooms,
    );
    renderPropertyAlphabet();
    scheduleRoomImages();
    schedulePropertyContextSync();
  }
}

function scheduleSearchResultsRender() {
  window.cancelAnimationFrame(scheduleSearchResultsRender.frame);
  scheduleSearchResultsRender.frame = window.requestAnimationFrame(renderSearchResults);
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
  if (room.nextDueDate < today()) return tag("逾期未收", "danger room-overdue");
  if (room.nextDueDate <= addDays(today(), 7)) return tag("近期应收", "warn room-due-soon");
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
  return `<div class="searchbar"><input id="keyword" type="search" value="${esc(state.keyword || "")}" placeholder="${placeholder}" autocomplete="off" enterkeyhint="search" data-search><button class="ghost" data-clear-search>清空</button></div>`;
}

function table(heads, rows, cells, emptyText = "暂无数据") {
  if (!rows || rows.length === 0) return empty(emptyText);
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
  toast.textContent = friendlyMessage(message);
  syncToastLayer();
  toast.hidden = false;
  window.clearTimeout(showToast.timer);
  const visibleTime = Math.min(6000, Math.max(3200, toast.textContent.length * 100));
  showToast.timer = window.setTimeout(() => {
    toast.hidden = true;
    syncToastLayer();
  }, visibleTime);
}

function setBusy(busy) {
  [$("#refreshBtn"), $("#mobileRefreshBtn")].forEach((refresh) => {
    if (!refresh) return;
    refresh.disabled = busy;
    refresh.textContent = busy ? "刷新中..." : "刷新";
  });
}

function setFormBusy(busy) {
  const submit = $("#modalForm button[type='submit']");
  setButtonLoading(submit, busy, "保存中...");
}

function setButtonLoading(button, busy, loadingText = "处理中...") {
  if (!button) return;
  if (busy) {
    if (button.dataset.loading === "true") return;
    button.dataset.loading = "true";
    button.dataset.idleText = button.textContent;
    button.disabled = true;
    button.classList.add("is-loading");
    button.setAttribute("aria-busy", "true");
    button.textContent = loadingText;
    return;
  }
  button.disabled = false;
  button.classList.remove("is-loading");
  button.removeAttribute("aria-busy");
  if (button.dataset.idleText) button.textContent = button.dataset.idleText;
  delete button.dataset.loading;
  delete button.dataset.idleText;
}

async function runButtonAction(button, action, loadingText = "处理中...") {
  if (!button || button.dataset.loading === "true" || typeof action !== "function") return;
  setButtonLoading(button, true, loadingText);
  try {
    await action();
  } finally {
    setButtonLoading(button, false);
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
  state.imageAction = "";
}

function closeConfirm() {
  state.pendingConfirm = null;
  closeDialog($("#confirmDialog"));
}

function closeDueDateDialog() {
  state.dueDateRoomId = null;
  closeDialog($("#dueDateDialog"));
  $("#dueDateForm").reset();
}

function closeSettlementDialog() {
  state.settlementRoomId = null;
  state.settlementPreview = null;
  state.settlementPreviewRequest += 1;
  closeDialog($("#settlementDialog"));
  $("#settlementForm").reset();
}

function resetViewScroll() {
  window.scrollTo({ top: 0, left: 0, behavior: "auto" });
  document.documentElement.scrollTop = 0;
  document.body.scrollTop = 0;
}

async function goView(view) {
  if (state.view === view) return;
  resetViewScroll();
  state.view = view;
  state.keyword = "";
  state.filter = "all";
  const nav = $(".nav");
  nav.classList.remove("nav-flowing");
  void nav.offsetWidth;
  nav.dataset.activeView = view;
  nav.classList.add("nav-flowing");
  document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item.dataset.view === view));
  await load();
  window.requestAnimationFrame(() => {
    resetViewScroll();
    schedulePropertyContextSync();
  });
}

document.querySelectorAll(".nav-item").forEach((button) => button.addEventListener("click", () => goView(button.dataset.view)));
$("#refreshBtn").addEventListener("click", load);
$("#mobileRefreshBtn").addEventListener("click", load);
$("#propertyAddBtn").addEventListener("click", () => openForm("property"));
$("#quickAddBtn").addEventListener("click", () => openForm("room"));
$("#modalForm").addEventListener("submit", submitForm);
$("#modalForm").addEventListener("input", syncRentDateDefaults);
$("#modalBody").addEventListener("click", (event) => {
  const button = event.target.closest("[data-adjust-due-date]");
  if (button) openDueDateDialog(button.dataset.adjustDueDate);
});
$("#dueDateForm").addEventListener("submit", requestDueDateAdjustment);
$("#dueDateForm").addEventListener("input", updateDueDatePreview);
$("#dueDateForm").addEventListener("change", () => {
  updateDueDateNotesRequirement();
  updateDueDatePreview();
});
$("#settlementForm").addEventListener("submit", requestSettlement);
$("#settlementMoveOutDate").addEventListener("change", loadSettlementPreview);
$("#settlementRentRefund").addEventListener("input", updateSettlementTotals);
$("#settlementDepositDeduction").addEventListener("input", updateSettlementTotals);
document.querySelectorAll("[data-close-modal]").forEach((button) => button.addEventListener("click", closeModal));
document.querySelectorAll("[data-cancel-confirm]").forEach((button) => button.addEventListener("click", closeConfirm));
document.querySelectorAll("[data-close-due-date]").forEach((button) => button.addEventListener("click", closeDueDateDialog));
document.querySelectorAll("[data-close-settlement]").forEach((button) => button.addEventListener("click", closeSettlementDialog));
document.querySelectorAll("[data-close-image]").forEach((button) => button.addEventListener("click", closeImageDialog));
document.querySelectorAll("dialog").forEach((dialog) => dialog.addEventListener("close", () => {
  delete dialog.dataset.openSequence;
  syncToastLayer();
  unlockPageScrollIfIdle();
}));
$("#confirmOkBtn").addEventListener("click", (event) => {
  const action = state.pendingConfirm;
  return runButtonAction(event.currentTarget, action);
});
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
  const settlementButton = event.target.closest("[data-settle-room]");
  if (settlementButton) return openSettlementDialog(settlementButton.dataset.settleRoom);
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
    const input = $("#keyword");
    if (input) {
      input.value = "";
      input.focus({ preventScroll: true });
    }
    scheduleSearchResultsRender();
  }
});

$("#content").addEventListener("input", (event) => {
  if (!event.target.matches("[data-search]") || state.composing || event.isComposing) return;
  state.keyword = event.target.value;
  scheduleSearchResultsRender();
});

$("#content").addEventListener("compositionstart", (event) => {
  if (event.target.matches("[data-search]")) state.composing = true;
});

$("#content").addEventListener("compositionend", (event) => {
  if (!event.target.matches("[data-search]")) return;
  state.composing = false;
  state.keyword = event.target.value;
  scheduleSearchResultsRender();
});

window.addEventListener("scroll", schedulePropertyContextSync, { passive: true });
window.addEventListener("resize", schedulePropertyContextSync, { passive: true });
$("#propertyAlphabetIndex").addEventListener("pointerdown", (event) => {
  if (event.pointerType === "mouse" && event.button !== 0) return;
  const letter = propertyLetterAtPoint(event.clientX, event.clientY);
  if (!letter) return;
  propertyAlphabetDragging = true;
  $("#propertyAlphabetIndex").setPointerCapture?.(event.pointerId);
  if (letter === PROPERTY_SEARCH_TARGET) jumpToPropertySearch(false);
  else jumpToPropertyLetter(letter);
  event.preventDefault();
});
$("#propertyAlphabetIndex").addEventListener("pointermove", (event) => {
  if (!propertyAlphabetDragging) return;
  const letter = propertyLetterAtPoint(event.clientX, event.clientY);
  if (letter === PROPERTY_SEARCH_TARGET) jumpToPropertySearch(false);
  else if (letter && $("#propertyAlphabetPreview").textContent !== letter) jumpToPropertyLetter(letter);
  event.preventDefault();
});
$("#propertyAlphabetIndex").addEventListener("pointerup", finishPropertyAlphabetDrag);
$("#propertyAlphabetIndex").addEventListener("pointercancel", finishPropertyAlphabetDrag);
window.addEventListener("pointerup", finishPropertyAlphabetDrag);
window.addEventListener("pointercancel", finishPropertyAlphabetDrag);
document.addEventListener("pointerdown", (event) => {
  if (!event.target.closest("#propertyAlphabetIndex")) hidePropertyAlphabetPreview();
});
window.addEventListener("scroll", () => {
  const previewAge = Date.now() - propertyAlphabetPreviewShownAt;
  if (!propertyAlphabetDragging && previewAge > 300) hidePropertyAlphabetPreview();
}, { passive: true });
window.addEventListener("blur", hidePropertyAlphabetPreview);
$("#propertyAlphabetIndex").addEventListener("click", (event) => {
  if (event.detail !== 0) return;
  if (event.target.closest("[data-property-search]")) return jumpToPropertySearch(true);
  const letter = event.target.closest("[data-property-letter]")?.dataset.propertyLetter;
  if (letter) jumpToPropertyLetter(letter, true);
});
loadRuntimeConfig().finally(load);
