// API settings. If an endpoint name changes in LabSlotApplication.java,
// change it here and nowhere else.
const API = {
  me: "/api/me",                                    // GET
  login: "/api/login",                              // POST { email, password }
  logout: "/api/logout",                            // POST
  equipment: "/api/equipment",                      // GET (with live status)
  bookings: "/api/bookings",                        // GET list, POST create
  approve: (id) => `/api/bookings/${id}/approve`,   // PUT
  checkIn: (id) => `/api/bookings/${id}/checkin`,   // PUT
  confirmReturn: (id) => `/api/bookings/${id}/complete`, // PUT (supervisor confirms the return)
  cancel: (id) => `/api/bookings/${id}`             // DELETE (only before check-in)
};

const el = (id) => document.getElementById(id);
let me = null;               // the logged-in user, as told to us by the server
let refreshTimer = null;
let lastEquipment = [];
let lastBookings = [];

function make(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
}

function showMessage(text, kind) {
  const box = el("message");
  box.textContent = text;
  box.className = "message " + kind;
  box.hidden = false;
  clearTimeout(showMessage.timer);
  showMessage.timer = setTimeout(() => (box.hidden = true), 8000);
}

async function request(url, options) {
  const res = await fetch(url, options);
  if (!res.ok) {
    let text = "";
    try {
      const body = await res.json();
      text = body.message || body.title || JSON.stringify(body);
    } catch (e) {
      text = await res.text().catch(() => "");
    }
    const error = new Error(text || "Request failed (" + res.status + ")");
    error.status = res.status;
    throw error;
  }
  if (res.status === 204) return null;
  const type = res.headers.get("content-type") || "";
  return type.includes("json") ? res.json() : null;
}

// If the session ran out while using the page, go back to the login screen.
function handleError(err) {
  if (err.status === 401 && me) {
    me = null;
    showLogin();
    showMessage("Your session ended. Please log in again.", "error");
  } else {
    showMessage(err.message, "error");
  }
}

// ---------- formatting ----------
function shortDate(start) {
  return new Date(start).toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" });
}
function clock(iso) {
  return String(iso).split("T")[1].slice(0, 5);
}
function timeRange(b) {
  return clock(b.startTime) + " to " + clock(b.endTime);
}
function nextQuarterHour() {
  const d = new Date();
  d.setMinutes(Math.ceil((d.getMinutes() + 1) / 15) * 15, 0, 0);
  const pad = (n) => String(n).padStart(2, "0");
  return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) +
    "T" + pad(d.getHours()) + ":" + pad(d.getMinutes());
}
function statusLabel(status) {
  return { AVAILABLE: "Available", IN_USE: "In use", OUT_OF_SERVICE: "Out of service" }[status] || status;
}

// ---------- screens ----------
function showLogin() {
  clearInterval(refreshTimer);
  el("app-view").hidden = true;
  el("login-view").hidden = false;
  el("password").value = "";
}

async function showApp() {
  el("login-view").hidden = true;
  el("app-view").hidden = false;

  const role = me.role.toLowerCase().replace("_", " ");
  el("who-name").textContent = me.name;
  el("who-label").textContent = me.name + " (" + role + ")";
  el("book-section").hidden = me.canApprove;   // supervisors review, they don't book
  el("stats").hidden = !me.canApprove;         // supervisors see the lab at a glance
  el("bk-h").textContent = me.canApprove ? "All bookings" : "My bookings";
  el("startTime").value = nextQuarterHour();

  await refresh();
  clearInterval(refreshTimer);
  refreshTimer = setInterval(() => {
    refresh().catch((err) => { if (err.status === 401) handleError(err); });
  }, 15000); // lets no-shows, overdue items and availability update on screen
}

// Reload everything that can change while the page is open.
async function refresh() {
  me = await request(API.me);   // penalty points can change while we watch
  await loadEquipment();
  await loadBookings();
}

function renderHero(list) {
  const open = list.filter((b) => b.status === "PENDING" || b.status === "ACTIVE");
  const ring = el("ring");
  if (me.canApprove) {
    const waiting = open.filter((b) => b.requiresApproval && !b.approved).length;
    const overdue = open.filter((b) => b.overdue).length;
    el("hero-title").textContent = waiting === 0
      ? "Nothing is waiting for your approval"
      : waiting + (waiting === 1 ? " booking is" : " bookings are") + " waiting for your approval";
    el("hero-sub").textContent = overdue > 0
      ? overdue + (overdue === 1 ? " item is" : " items are") + " overdue. Confirm the return when it comes back."
      : "Approve restricted equipment and confirm returns when items come back.";
    el("ring-value").textContent = waiting;
    el("ring-label").textContent = "to approve";
    ring.style.setProperty("--fill", waiting > 0 ? 100 : 0);
  } else {
    const used = open.length;
    el("hero-title").textContent = me.suspended
      ? "Booking is suspended"
      : used === 0
        ? "You have no active bookings"
        : "You are using " + used + " of " + me.maxActiveBookings + " booking slots";

    if (me.suspended) {
      el("hero-sub").textContent = "Because of repeated late returns, you can book again after " +
        new Date(me.suspendedUntil).toLocaleDateString("en-GB", { day: "numeric", month: "long" }) + ".";
    } else if (me.penaltyPoints > 0) {
      el("hero-sub").textContent = "You have " + me.penaltyPoints + " late-return " +
        (me.penaltyPoints === 1 ? "point" : "points") + " (" + me.maxPenaltyPoints +
        " suspends booking for a week). Return items on time.";
    } else {
      el("hero-sub").textContent = "Check in within 15 minutes of your start time, and hand items back on time.";
    }
    el("ring-value").textContent = used + "/" + me.maxActiveBookings;
    el("ring-label").textContent = "active";
    ring.style.setProperty("--fill", Math.min(100, (used / me.maxActiveBookings) * 100));
    el("book-submit").disabled = me.suspended;
  }
}

function renderStats() {
  if (!me || !me.canApprove) return;
  const total = lastEquipment.length;
  const available = lastEquipment.filter((e) => e.status === "AVAILABLE").length;
  const out = lastEquipment.filter((e) => e.status === "IN_USE").length;
  const overdue = lastBookings.filter((b) => b.overdue).length;
  const waiting = lastBookings.filter((b) =>
    (b.status === "PENDING" || b.status === "ACTIVE") && b.requiresApproval && !b.approved).length;

  el("stat-available").textContent = available + " of " + total;
  el("stat-out").textContent = out;
  el("stat-overdue").textContent = overdue;
  el("stat-approval").textContent = waiting;
  el("stat-overdue-box").classList.toggle("warn", overdue > 0);
}

// ---------- equipment ----------
async function loadEquipment() {
  const list = await request(API.equipment);
  lastEquipment = list;
  const grid = el("equipment-grid");
  const select = el("equipmentId");
  const chosen = select.value;      // keep the selection when the list refreshes
  grid.replaceChildren();
  select.replaceChildren();

  list.forEach((e) => {
    const tile = make("article", "tile" + (e.requiresApproval ? " restricted" : ""));
    tile.append(make("h3", null, e.name));
    tile.append(make("p", null, e.location + " · up to " + e.maxHours + " h"));
    tile.append(make("p", null, e.requiresApproval ? "Needs supervisor approval" : "Open to book"));

    const chips = make("div", "chips");
    chips.append(make("span", "chip " + e.status, statusLabel(e.status)));
    tile.append(chips);

    if (e.status === "IN_USE") {
      const who = e.heldBy ? "Held by " + e.heldBy + ", " : "";
      const line = make("p", e.overdue ? "late-text" : null,
        who + (e.overdue ? "overdue since " : "due back ") + clock(e.dueBack));
      tile.append(line);
    }

    if (!me.canApprove) {
      const btn = make("button", "btn soft small", "Book this");
      btn.type = "button";
      btn.addEventListener("click", () => {
        select.value = e.id;
        el("book-section").scrollIntoView({ behavior: "smooth", block: "start" });
        el("startTime").focus({ preventScroll: true });
      });
      tile.append(btn);
    }
    grid.append(tile);

    const opt = make("option", null, e.name + " (" + e.id + ")");
    opt.value = e.id;
    select.append(opt);
  });
  if (chosen) select.value = chosen;
  renderStats();
}

// ---------- bookings ----------
function actionButton(label, className, handler) {
  const b = make("button", "btn small " + className, label);
  b.type = "button";
  b.addEventListener("click", handler);
  return b;
}

function bookingCard(b) {
  const card = make("article", "booking");

  const time = make("div", "booking-time");
  time.append(make("strong", null, shortDate(b.startTime)), make("span", null, timeRange(b)));

  const info = make("div", "booking-info");
  info.append(make("h3", null, b.equipmentName));
  info.append(make("p", "booking-meta", (me.canApprove ? b.userName + " · " : "") + b.id));

  const chips = make("div", "chips");
  chips.append(make("span", "chip " + b.status, b.status.charAt(0) + b.status.slice(1).toLowerCase()));
  if (b.requiresApproval) {
    chips.append(make("span", "chip note", b.approved ? "Approved" : "Waiting for supervisor"));
  }
  if (b.checkedIn) chips.append(make("span", "chip note", "Checked out"));
  if (b.overdue) chips.append(make("span", "chip late", "Overdue by " + b.lateMinutes + " min"));
  info.append(chips);

  const open = b.status === "PENDING" || b.status === "ACTIVE";
  if (open && b.checkedIn && !me.canApprove) {
    info.append(make("p", "booking-note", "Hand the item back to a supervisor to finish this booking."));
  }

  const actions = make("div", "actions");
  if (open && me.canApprove && b.requiresApproval && !b.approved) {
    actions.append(actionButton("Approve", "primary", () => act(API.approve(b.id), "PUT", "Booking approved.")));
  }
  if (open && !b.checkedIn && (!b.requiresApproval || b.approved)) {
    actions.append(actionButton("Check in", "primary", () => act(API.checkIn(b.id), "PUT", "Checked out.")));
  }
  if (open && b.checkedIn && me.canApprove) {
    actions.append(actionButton("Confirm return", "primary", () =>
      act(API.confirmReturn(b.id), "PUT", (data) => returnMessage(b, data))));
  }
  if (open && !b.checkedIn) {   // once the item is out, it has to come back first
    actions.append(actionButton("Cancel", "danger", () => act(API.cancel(b.id), "DELETE", "Booking cancelled.")));
  }
  info.append(actions);

  card.append(time, info);
  return card;
}

function returnMessage(b, data) {
  const late = data.returnedLateMinutes;
  if (data.penaltyAdded) {
    return "Return confirmed. " + late + " min late, so " + b.userName + " got a penalty point." +
      (data.studentSuspended ? " Booking is now suspended for a week." : "");
  }
  if (late > 0) return "Return confirmed (" + late + " min late, within the grace period).";
  return "Return confirmed. Thank you!";
}

async function loadBookings() {
  const list = await request(API.bookings);
  lastBookings = list;
  renderHero(list);
  renderStats();

  const box = el("booking-list");
  box.replaceChildren();
  list.forEach((b) => box.append(bookingCard(b)));

  const empty = el("empty");
  empty.hidden = list.length > 0;
  empty.textContent = me.canApprove
    ? "No bookings to review yet."
    : "You have no bookings yet. Pick some equipment above to make your first one.";
}

// okText is a string, or a function that builds the text from the server's reply.
async function act(url, method, okText) {
  try {
    const data = await request(url, { method });
    showMessage(typeof okText === "function" ? okText(data) : okText, "ok");
  } catch (err) {
    handleError(err);
  }
  await refresh().catch(() => {});
}

// ---------- events ----------
el("book-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const payload = {
    equipmentId: el("equipmentId").value,
    startTime: el("startTime").value,          // e.g. 2026-11-09T10:00
    durationMinutes: Number(el("duration").value)
  };
  try {
    await request(API.bookings, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    showMessage("Booking created.", "ok");
  } catch (err) {
    handleError(err);
  }
  await refresh().catch(() => {});
});

el("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    me = await request(API.login, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: el("email").value, password: el("password").value })
    });
    await showApp();
  } catch (err) {
    me = null;
    showMessage(err.message, "error");
  }
});

el("logout").addEventListener("click", async () => {
  try { await request(API.logout, { method: "POST" }); } catch (e) { /* already logged out */ }
  me = null;
  showLogin();
});

async function init() {
  try {
    me = await request(API.me);
    await showApp();
  } catch (err) {
    me = null;
    showLogin();
  }
}
init();
