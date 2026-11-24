/* Smart Hospital Resource & Treatment Management System
   B.Tech 3rd Year Operating Systems & DBMS Project
   
   Full Stack Architecture:
   - Frontend: Vanilla HTML5 / CSS3 / JavaScript
   - Java Backend: Lightweight HttpServer (HospitalServer.java)
   - OS Engine: HospitalOSModule.java (FCFS, SJF, Priority, Round Robin, Banker's Algorithm)
   - JDBC Layer: HospitalDAO.java + DBConnection.java (PreparedStatement, ACID Transactions)
   - MySQL Database: model_db (Patients, Doctors, Beds, Resources, Treatment Processes)

   Features:
   1. Complete Patient Management (CRUD: Register, Edit, Delete with Bed Release, Real-Time Search)
   2. Complete Doctor Management (CRUD: Add, Edit, Delete, Search, Availability Tracking)
   3. 50 Realistic Patients & 10 Doctors stored in MySQL
   4. Strict 1:1 Inpatient Bed Allocation
   5. 5 Separated OS Workload Categories (Emergency, Interactive, OPD, Diagnostic, Batch)
   6. Workload-Specific CPU Scheduling with Gantt Chart & Metrics
*/

const API_BASE = window.location.origin.includes(":8080") ? "" : "http://localhost:8080";

const state = {
  page: "overview",
  dbConnected: false,
  selectedWorkload: "Emergency",
  patients: [],
  doctors: [],
  beds: [],
  resources: [],
  appointments: [],
  treatment_processes: [],
  scheduling_runs: [],
  scheduling_results: [],
  resource_allocations: []
};

const pages = {
  overview: { crumb: "Overview", title: "", subtitle: "B.Tech 3rd Year Project: OS CPU Scheduling, Deadlock Avoidance & DBMS Transactions.", action: "Register patient", kind: "none" },
  patients: { crumb: "Patients", title: "Inpatient & Outpatient Management", subtitle: "Full CRUD operations connected to MySQL. Admitted patients maintain strict 1:1 dedicated bed allocation.", action: "Register patient", kind: "patients" },
  doctors: { crumb: "Doctors", title: "Medical Specialists Directory", subtitle: "Full CRUD doctor profiles, clinical departments, and availability statuses stored in MySQL.", action: "Add doctor", kind: "doctors" },
  appointments: { crumb: "Appointments", title: "Appointment Desk", subtitle: "Relational records linking registered patients and doctors with foreign key constraints.", action: "New appointment", kind: "appointments" },
  resources: { crumb: "Beds & resources", title: "Hospital Inventory & Beds", subtitle: "Track dedicated bed occupancy (1:1 mapping) and shared hospital devices via ACID transactions.", action: "Add resource", kind: "resources" },
  treatments: { crumb: "Treatment processes", title: "Hospital Software Task Queue (5 Workloads)", subtitle: "Clearly separated workload categories: Emergency, Interactive, OPD, Diagnostic, and Batch.", action: "Add task", kind: "treatments" },
  scheduler: { crumb: "Treatment scheduling", title: "Workload-Specific CPU Scheduling", subtitle: "Dispatches different OS algorithms for different workloads (Priority, Round Robin, FCFS, SJF).", action: "Run scheduler", kind: "scheduler" },
  allocation: { crumb: "Banker’s algorithm", title: "Deadlock Avoidance Simulator", subtitle: "Banker's Algorithm safety verification on shared hospital resources (Beds, Doctors, Devices).", action: "Check safe state", kind: "allocation" },
  history: { crumb: "Run history", title: "Scheduling Audit Trail", subtitle: "Audit history of OS algorithm runs and completion metrics persisted in MySQL.", action: "Refresh history", kind: "history" }
};

const $ = id => document.getElementById(id);

// Workload descriptions and algorithm recommendations for the 5 categories
const workloadInfo = {
  Emergency: {
    title: "Critical Emergency Tasks",
    icon: "🚨",
    recommended: "Priority",
    tagClass: "workload-emergency",
    sectionClass: "emergency",
    desc: "ICU telemetry alarms, cardiac arrhythmia monitors, and acute oxygen desaturation alerts.",
    rationale: "Requires Non-Preemptive Priority Scheduling (Priority 1 = Highest Alert). Critical events must be dispatched immediately to prevent patient telemetry latency."
  },
  Interactive: {
    title: "Interactive Doctor Tasks",
    icon: "💬",
    recommended: "Round Robin",
    tagClass: "workload-interactive",
    sectionClass: "interactive",
    desc: "Physician prescription order entry, vital signs updates, and telehealth video sessions.",
    rationale: "Requires Round Robin Scheduling (Time-shared with Quantum). Slicing execution ensures fair responsiveness across clinicians without starvation."
  },
  OPD: {
    title: "OPD Clinical Tasks",
    icon: "🩺",
    recommended: "Round Robin",
    tagClass: "workload-opd",
    sectionClass: "opd",
    desc: "Outpatient registration desks, consultation slips, queue tokens, and follow-up schedulers.",
    rationale: "Requires Round Robin / Fair Queuing. Fair time slices prevent lengthy registrations from blocking incoming outpatient queue requests."
  },
  Diagnostic: {
    title: "Rapid Diagnostic Screening",
    icon: "🧪",
    recommended: "SJF",
    tagClass: "workload-diagnostic",
    sectionClass: "diagnostic",
    desc: "Blood glucose rapid checks, viral antigen assays, CBC analyzers, and chest X-ray renderings.",
    rationale: "Requires Shortest Job First (SJF). Mathematically provable to yield the minimum average waiting time across quick diagnostic screenings."
  },
  Batch: {
    title: "Batch Administrative Jobs",
    icon: "📊",
    recommended: "FCFS",
    tagClass: "workload-batch",
    sectionClass: "batch",
    desc: "Nightly billing generation, insurance claim batching, discharge PDF exports, and bed stats.",
    rationale: "Requires First-Come, First-Served (FCFS). Bulk background tasks have no real-time deadlines and benefit from zero context-switching overhead."
  },
  All: {
    title: "All Hospital Tasks",
    icon: "⚙️",
    recommended: "Priority",
    tagClass: "workload-batch",
    sectionClass: "batch",
    desc: "All queued software tasks combined across all departments.",
    rationale: "Manual comparative mode: Run and compare different OS algorithms across all software tasks for viva demonstration."
  }
};

let selectedAlgorithm = "Priority";
let lastSchedule = null;
let lastSafety = null;
let toastTimer;

// Banker's defaults
const bankerDefaults = {
  resources: ["Beds", "Doctors", "Monitoring Devices"],
  available: [22, 10, 20],
  patients: [
    { name: "Task 1 (ICU Telemetry)", allocation: [1, 0, 1], maximum: [3, 2, 2] },
    { name: "Task 2 (Prescription Entry)", allocation: [1, 1, 0], maximum: [2, 2, 1] },
    { name: "Task 3 (MRI Scanner Pipeline)", allocation: [1, 0, 1], maximum: [3, 1, 3] }
  ]
};

function toast(msg) {
  const el = $("toast");
  if (!el) return;
  el.textContent = msg;
  el.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("show"), 4000);
}

function escapeHtml(v) {
  return String(v ?? "").replace(/[&<>"']/g, c => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[c]));
}

function patientName(id) {
  const p = state.patients.find(x => Number(x.patient_id) === Number(id));
  return p ? p.patient_name : `Patient #${id}`;
}

function doctorName(id) {
  const d = state.doctors.find(x => Number(x.doctor_id) === Number(id));
  return d ? d.doctor_name : `Doctor #${id}`;
}

function initials(name) {
  return String(name || "?").trim().split(/\s+/).slice(0, 2).map(x => x[0] || "").join("").toUpperCase();
}

function statusClass(status) {
  const s = String(status || "").toLowerCase();
  if (s.includes("available") || s.includes("safe") || s.includes("completed")) return "status-available";
  if (s.includes("occupied") || s.includes("progress") || s.includes("admitted") || s.includes("queued") || s.includes("duty")) return "status-in-progress";
  if (s.includes("maintenance") || s.includes("unsafe") || s.includes("cancel") || s.includes("discharged") || s.includes("leave")) return "status-maintenance";
  return "status-pending";
}

function statusTag(status) {
  return `<span class="status ${statusClass(status)}">${escapeHtml(status || "Unknown")}</span>`;
}

function priorityTag(level) {
  const lvl = Number(level) || 2;
  if (lvl === 1) return `<span class="priority-badge priority-1">🔴 High (P1)</span>`;
  if (lvl === 2) return `<span class="priority-badge priority-2">🟡 Medium (P2)</span>`;
  return `<span class="priority-badge priority-3">🟢 Low (P3)</span>`;
}

function personCell(name, id, color = "cyan") {
  return `<div class="person"><span class="person-avatar ${color}">${escapeHtml(initials(name))}</span><span><span class="person-name">${escapeHtml(name)}</span><small class="person-id">${escapeHtml(id)}</small></span></div>`;
}

function dateTime(date, time) {
  let d = new Date(`${date}T${time || "00:00:00"}`);
  if (Number.isNaN(d.getTime())) return `${date || "—"} ${time || ""}`;
  return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }) + " · " + d.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
}

function availableBedsCount() {
  return state.beds.filter(b => String(b.bed_status).toLowerCase() === "available").length;
}

function totalBedsCount() {
  return state.beds.length || 60;
}

function queued() {
  return state.treatment_processes.filter(p => String(p.process_status).toLowerCase() !== "completed").length;
}

function greetingForCurrentTime(date = new Date()) {
  const hour = date.getHours();
  if (hour >= 5 && hour < 12) return "Good morning";
  if (hour >= 12 && hour < 17) return "Good afternoon";
  if (hour >= 17 && hour < 21) return "Good evening";
  return "Good night";
}

function updateGreeting() {
  const title = $("pageTitle");
  if (state.page === "overview" && title) {
    title.innerHTML = `${greetingForCurrentTime()} Anand<span class="title-dot">.</span>`;
  }
}

function updateConnectionUI(statusData) {
  const dot = $("sidebarDot");
  const title = $("sidebarStatusTitle");
  const sub = $("sidebarStatusSub");
  const pill = $("sidebarEnvPill");
  const topbar = $("topbarEnv");
  const notice = $("prototypeNotice");

  if (statusData && statusData.db_connected) {
    state.dbConnected = true;
    if (dot) dot.style.background = "#10b981";
    if (title) title.textContent = "Java + MySQL";
    if (sub) sub.textContent = "Connected (model_db)";
    if (pill) { pill.textContent = "ONLINE"; pill.style.background = "#059669"; }
    if (topbar) topbar.innerHTML = "<i></i> MYSQL CONNECTED (JDBC)";
    if (notice) notice.classList.add("hidden");
  } else {
    state.dbConnected = false;
    if (dot) dot.style.background = "#10b981";
    if (title) title.textContent = "Java Backend Live";
    if (sub) sub.textContent = "Active Memory Mode";
    if (pill) { pill.textContent = "ACTIVE"; pill.style.background = "#0284c7"; }
    if (topbar) topbar.innerHTML = "<i></i> JAVA BACKEND ACTIVE";
  }
}

// -------------------------------------------------------------
// BACKEND DATA SYNCHRONIZATION
// -------------------------------------------------------------

async function loadDataFromBackend() {
  try {
    const statusRes = await fetch(`${API_BASE}/api/status`).then(r => r.json()).catch(() => null);
    updateConnectionUI(statusRes);

    const [patients, doctors, beds, resources, appointments, processes, history, allocations] = await Promise.all([
      fetch(`${API_BASE}/api/patients`).then(r => r.json()).catch(() => []),
      fetch(`${API_BASE}/api/doctors`).then(r => r.json()).catch(() => []),
      fetch(`${API_BASE}/api/beds`).then(r => r.json()).catch(() => []),
      fetch(`${API_BASE}/api/resources`).then(r => r.json()).catch(() => []),
      fetch(`${API_BASE}/api/appointments`).then(r => r.json()).catch(() => []),
      fetch(`${API_BASE}/api/processes`).then(r => r.json()).catch(() => []),
      fetch(`${API_BASE}/api/history`).then(r => r.json()).catch(() => []),
      fetch(`${API_BASE}/api/allocations`).then(r => r.json()).catch(() => [])
    ]);

    if (Array.isArray(patients)) state.patients = patients;
    if (Array.isArray(doctors)) state.doctors = doctors;
    if (Array.isArray(beds)) state.beds = beds;
    if (Array.isArray(resources)) state.resources = resources;
    if (Array.isArray(appointments)) state.appointments = appointments;
    if (Array.isArray(processes)) state.treatment_processes = processes;
    if (Array.isArray(history)) state.scheduling_runs = history;
    if (Array.isArray(allocations)) state.resource_allocations = allocations;

    refreshOverview();
    if (state.page !== "overview") renderModule(state.page);

  } catch (err) {
    console.error("Backend fetch error:", err);
  }
}

function refreshOverview() {
  if ($("metricPatients")) $("metricPatients").textContent = state.patients.length;
  if ($("metricAppointments")) $("metricAppointments").textContent = state.appointments.length;
  if ($("metricBeds")) $("metricBeds").innerHTML = `${availableBedsCount()} <small>/ ${totalBedsCount()}</small>`;
  if ($("bedFoot")) $("bedFoot").textContent = `${availableBedsCount()} available (${totalBedsCount() - availableBedsCount()} assigned 1:1)`;
  if ($("metricQueue")) $("metricQueue").textContent = queued();
  if ($("navPatientCount")) $("navPatientCount").textContent = state.patients.length;
  if ($("overviewProcesses")) $("overviewProcesses").textContent = queued();

  if ($("overviewResources")) {
    $("overviewResources").innerHTML = state.resources.map((r, i) => {
      let pct = r.total_quantity ? Math.round((r.available_quantity / r.total_quantity) * 100) : 0;
      return `<div class="resource-line">
        <div class="resource-icon ${["green", "violet", "cyan", "amber", "green"][i % 5]}">${["🛏️", "⚕", "◉", "🫁", "✚"][i % 5]}</div>
        <div class="resource-detail">
          <b>${escapeHtml(r.resource_name)}</b>
          <small>${r.available_quantity} available of ${r.total_quantity}</small>
          <div class="resource-bar"><i style="width:${Math.min(100, Math.max(0, pct))}%"></i></div>
        </div>
        <span class="resource-amount">${pct}%</span>
      </div>`;
    }).join("");
  }

  const search = ($("overviewSearch")?.value || "").toLowerCase();
  const rows = state.appointments.filter(a =>
    (patientName(a.patient_id) + " " + doctorName(a.doctor_id)).toLowerCase().includes(search)
  ).slice(0, 5);

  if ($("overviewAppointments")) {
    $("overviewAppointments").innerHTML = rows.length ? rows.map((a, i) =>
      `<tr>
        <td><b>#APT-${String(a.appointment_id).padStart(3, "0")}</b></td>
        <td>${personCell(patientName(a.patient_id), `Patient ID ${a.patient_id}`, ["cyan", "violet", "green"][i % 3])}</td>
        <td>${escapeHtml(doctorName(a.doctor_id))}</td>
        <td>${escapeHtml(dateTime(a.appointment_date, a.appointment_time))}</td>
        <td>${statusTag(a.appointment_status)}</td>
      </tr>`
    ).join("") : `<tr><td colspan="5" class="empty-state">No appointments recorded yet.</td></tr>`;
  }

  if (lastSchedule) {
    if ($("overviewAlgorithm")) $("overviewAlgorithm").textContent = `${lastSchedule.algorithm} (${lastSchedule.work_category || "Workload"})`;
    if ($("overviewWait")) $("overviewWait").innerHTML = `${Number(lastSchedule.avgWait || 0).toFixed(1)} <small>units</small>`;
  } else {
    if ($("overviewAlgorithm")) $("overviewAlgorithm").textContent = "Priority (Emergency)";
    if ($("overviewWait")) $("overviewWait").innerHTML = "— <small>units</small>";
  }
}

function go(page) {
  if (!pages[page]) page = "overview";
  state.page = page;
  const cfg = pages[page];

  document.querySelectorAll(".nav-item").forEach(b => b.classList.toggle("active", b.dataset.page === page));
  if ($("crumb")) $("crumb").textContent = cfg.crumb;
  if (page === "overview") {
    updateGreeting();
  } else {
    if ($("pageTitle")) $("pageTitle").textContent = cfg.title;
  }
  if ($("pageSubtitle")) $("pageSubtitle").textContent = cfg.subtitle;
  if ($("primaryAction")) $("primaryAction").innerHTML = `＋ <span>${escapeHtml(cfg.action)}</span>`;

  if ($("overviewView")) $("overviewView").classList.toggle("hidden", page !== "overview");
  if ($("moduleView")) $("moduleView").classList.toggle("hidden", page === "overview");
  if ($("sidebar")) $("sidebar").classList.remove("open");

  if (page !== "overview") renderModule(page);
  refreshOverview();
}

function table(headers, rows, empty = "No records found.") {
  return `<div class="data-table-wrap"><table><thead><tr>${headers.map(h => `<th>${h}</th>`).join("")}</tr></thead><tbody>${rows.length ? rows.join("") : `<tr><td colspan="${headers.length}" class="empty-state">${empty}</td></tr>`}</tbody></table></div>`;
}

function intro(kicker, title, desc, actionLabel = "", action = "") {
  return `<div class="module-intro"><div><div class="section-kicker">${kicker}</div><h2>${title}</h2><p>${desc}</p></div>${actionLabel ? `<button class="button button-primary" data-action="${action}">＋ ${actionLabel}</button>` : ""}</div>`;
}

function card(title, desc, body) {
  return `<article class="module-card"><h3>${title}</h3><p class="muted">${desc}</p>${body}</article>`;
}

function field(label, name, type = "text", placeholder = "", extra = "", value = "") {
  return `<div class="field"><label for="f-${name}">${label}</label><input id="f-${name}" name="${name}" type="${type}" placeholder="${placeholder}" value="${escapeHtml(value)}" ${extra}></div>`;
}

function selectField(label, name, options, selectedValue = "") {
  return `<div class="field"><label for="f-${name}">${label}</label><select id="f-${name}" name="${name}">${options.map(o => `<option value="${escapeHtml(o.value)}" ${String(o.value) === String(selectedValue) ? "selected" : ""}>${escapeHtml(o.label)}</option>`).join("")}</select></div>`;
}

// -------------------------------------------------------------
// MODULE RENDERING
// -------------------------------------------------------------

function renderModule(page) {
  const host = $("moduleContent");
  if (!host) return;
  let html = "";

  if (page === "patients") {
    html = intro("DBMS CRUD & 1:1 CARDINALITY · PATIENTS & BEDS", "Patient Management", "Complete Create, Read, Update, Delete with real-time MySQL synchronization and strict 1:1 bed mapping.", "Register patient", "new-patient");
    
    // Departments extracted from state
    const depts = ["All", ...new Set(state.patients.map(p => p.department).filter(Boolean))];
    const deptOptions = depts.map(d => ({ value: d, label: d === "All" ? "All Departments" : d }));

    html += card("Patient Registry (Live MySQL Data)", "Search patients by name, ID, phone number, department, or diagnosis. Actions update MySQL immediately.", `
      <div class="filter-row">
        <div class="search-wrap">
          <span>⌕</span>
          <input id="patientSearch" placeholder="Search by name, ID, phone, department, diagnosis..." aria-label="Search patients">
        </div>
        ${selectField("Filter Department", "patientDeptFilter", deptOptions)}
        <span class="table-meta" id="patientTableCount"></span>
      </div>
      <div id="patientsTable"></div>
      <div class="inline-note">
        <b>1:1 Bed Relational Integrity</b>
        <span>Each admitted patient is allocated exactly one dedicated bed. Editing admission status to Discharged or deleting the patient automatically frees their bed in MySQL.</span>
      </div>
    `);

  } else if (page === "doctors") {
    html = intro("DBMS CRUD · DOCTORS & CLINICAL STAFF", "Doctor Management", "Medical specialists and hospital departments with full Edit, Delete, Search, and Availability status.", "Add doctor", "new-doctor");
    html += card("Registered Doctors (Live MySQL Data)", "Search doctors by name, specialization, ID, or department. Availability updates reflect immediately.", `
      <div class="filter-row">
        <div class="search-wrap">
          <span>⌕</span>
          <input id="doctorSearch" placeholder="Search by doctor name, specialization, ID, or department...">
        </div>
        <span class="table-meta" id="doctorTableCount"></span>
      </div>
      <div id="doctorsTable"></div>
    `);

  } else if (page === "appointments") {
    html = intro("DBMS · APPOINTMENTS", "Appointment Management", "Relational foreign keys joining patients and doctors.", "New appointment", "new-appointment");
    html += card("Appointment Schedule", "Joined patient and doctor consultation records.", `
      <div class="filter-row">
        <div class="search-wrap">
          <span>⌕</span>
          <input id="appointmentSearch" placeholder="Search patient, doctor or status">
        </div>
        ${selectField("Status", "appointmentFilter", [
          { value: "All", label: "All statuses" },
          { value: "Scheduled", label: "Scheduled" },
          { value: "Completed", label: "Completed" },
          { value: "Cancelled", label: "Cancelled" }
        ])}
      </div>
      <div id="appointmentsTable"></div>
    `);

  } else if (page === "resources") {
    html = intro("OS RESOURCE MANAGEMENT & DBMS INVENTORY", "Hospital Beds & Shared Resources", "Real-time bed occupancy tracking with dedicated 1:1 patient assignment, plus transactional allocation for shared devices.", "Add resource", "new-resource");
    html += `<div class="metric-grid os-resource-grid">
      <article class="metric-card">
        <div class="metric-head"><span>Beds (1:1 Dedicated)</span><span class="metric-icon green">🛏️</span></div>
        <div class="metric-number">${availableBedsCount()} <small>/ ${totalBedsCount()}</small></div>
        <div class="metric-foot"><span class="tiny-dot green-dot"></span><span>${availableBedsCount()} available (${totalBedsCount() - availableBedsCount()} occupied)</span></div>
        <div class="resource-bar"><i style="width:${(availableBedsCount() / totalBedsCount()) * 100}%"></i></div>
      </article>
      <article class="metric-card">
        <div class="metric-head"><span>Doctors</span><span class="metric-icon violet">⚕</span></div>
        <div class="metric-number">${state.doctors.length} <small>Staff</small></div>
        <div class="metric-foot"><span class="tiny-dot violet-dot"></span><span>Available across ${new Set(state.doctors.map(d=>d.department)).size} departments</span></div>
        <div class="resource-bar"><i style="width:100%"></i></div>
      </article>
      <article class="metric-card">
        <div class="metric-head"><span>Monitoring Devices</span><span class="metric-icon cyan">◉</span></div>
        <div class="metric-number">20 <small>/ 25</small></div>
        <div class="metric-foot"><span class="tiny-dot cyan-dot"></span><span>20 units available for ICU & telemetry</span></div>
        <div class="resource-bar"><i style="width:80%"></i></div>
      </article>
    </div>
    <div class="module-grid equal">
      ${card("Hospital Bed Status (1 Dedicated Bed per Patient)", "Live occupancy status. Discharging a patient frees up their bed immediately.", `
        <div class="mini-stat-grid">
          <div class="mini-stat"><span>Available beds</span><b>${availableBedsCount()}</b></div>
          <div class="mini-stat"><span>Occupied beds</span><b>${totalBedsCount() - availableBedsCount()}</b></div>
        </div>
        <div id="bedsTable"></div>
      `)}
      ${card("Shared Hospital Resources (ACID Transactions)", "Allocate and release devices using MySQL row-level locking.", `
        <form id="osResourceForm">
          <div class="field-grid">
            ${selectField("Resource", "osResource", state.resources.filter(x => !x.resource_name.toLowerCase().includes("bed")).map(r => ({ value: r.resource_name, label: r.resource_name })))}
            ${field("Quantity", "osQuantity", "number", "Enter quantity", 'min="1" step="1" value="1" required')}
          </div>
          <div class="form-actions">
            <button type="button" class="button button-light" data-action="release-os-resource">Release device</button>
            <button type="submit" class="button button-primary">Allocate device</button>
          </div>
        </form>
        <div id="osResourceMessage" class="inline-note">
          <b>ACID guarantees</b>
          <span>Executed using <code>SELECT ... FOR UPDATE</code> with commit/rollback in MySQL.</span>
        </div>
      `)}
    </div>`;

  } else if (page === "treatments") {
    html = intro("OS WORKLOAD DISPATCHER · 5 CATEGORIES", "Hospital Software Tasks by Workload", "Hospital software tasks clearly separated into 5 categories: Emergency, Interactive, OPD, Diagnostic, and Batch. Different workloads dispatch different CPU algorithms.", "Add software task", "new-process");
    
    html += card("Hospital Software Workloads Overview", "Academic simulation: background software tasks (telemetry processing, billing, doctor orders) executed by the Java OS scheduler.", `
      <div class="filter-row">
        <div class="search-wrap" style="width:340px">
          <span>⌕</span>
          <input id="processSearch" placeholder="Filter tasks by name, patient, or category...">
        </div>
        <span class="table-meta" id="totalTasksCount">${state.treatment_processes.length} total tasks</span>
      </div>
      <div id="workloadSectionsContainer" class="workload-sections"></div>
    `);

  } else if (page === "scheduler") {
    html = intro("OPERATING SYSTEMS MODULE", "Treatment Scheduling Simulator", "Select a hospital workload to automatically dispatch the optimal OS scheduling algorithm, or choose an algorithm manually.");
    
    const currentWl = state.selectedWorkload || "Emergency";
    html += `<div class="workload-selector">
      ${Object.entries(workloadInfo).map(([key, info]) => `
        <div class="workload-card ${key === currentWl ? "active" : ""}" data-workload="${key}">
          <h4><span>${info.icon} ${info.title}</span> <span class="badge-workload ${info.tagClass}">${info.recommended}</span></h4>
          <p>${info.desc}</p>
          <span class="workload-algo-tag">Optimal: ${info.recommended} Scheduling</span>
        </div>
      `).join("")}
    </div>`;

    html += `<div class="module-grid">
      <div>
        ${card("Run Configuration", "Select algorithm or use the workload recommendation.", `
          <div class="algorithm-tabs" id="algorithmTabs">
            ${["Priority", "Round Robin", "FCFS", "SJF"].map(a => `<button data-algorithm="${a}" class="${a === selectedAlgorithm ? "active" : ""}">${a}</button>`).join("")}
          </div>
          <div class="field-grid" id="quantumField" style="${selectedAlgorithm === "Round Robin" ? "" : "display:none"}">
            ${field("Time quantum (q)", "quantum", "number", "e.g. 2", 'min="1" value="2"')}
          </div>
          <div class="inline-note" id="workloadRationaleNote">
            <b>Academic Rationale (${currentWl} Workload):</b>
            <span>${escapeHtml(workloadInfo[currentWl]?.rationale || "Configurable algorithm execution.")}</span>
          </div>
          <div class="form-actions">
            <button class="button button-primary" data-action="run-scheduler">▶ Run ${selectedAlgorithm} Scheduling (${currentWl} Workload)</button>
          </div>
        `)}
      </div>
      <div>
        ${card("OS Scheduling Metrics", "Computed by Java OS Module (HospitalOSModule.java):", `
          <div class="algorithm-explainer">
            <div class="explain-row"><span class="explain-num">CT</span><div><b>Completion Time</b><p>Exact clock time when the software task completes execution.</p></div></div>
            <div class="explain-row"><span class="explain-num">TAT</span><div><b>Turnaround Time = CT − Arrival</b><p>Total time spent by the task from arrival to completion.</p></div></div>
            <div class="explain-row"><span class="explain-num">WT</span><div><b>Waiting Time = TAT − Burst</b><p>Time spent waiting in the ready queue before receiving CPU service.</p></div></div>
          </div>
        `)}
      </div>
    </div>
    <div id="scheduleOutput">${renderScheduleOutput()}</div>`;

  } else if (page === "allocation") {
    html = intro("OS DEADLOCK AVOIDANCE", "Banker’s Algorithm Safety Check", "Evaluated by Dijkstra's Banker's Algorithm in Java to verify shared hospital resources (Beds, Doctors, Devices) never deadlock.", "Calculate safety state", "check-banker-input");
    html += `<div class="module-grid">
      <div>
        ${card("Banker’s Algorithm Input", "Resource vectors snapshot. Need matrix is computed as Maximum − Allocation by Java.", `
          <div class="banker-inputs">
            <div class="banker-available">
              <b>Available Resources (Beds, Doctors, Devices)</b>
              ${bankerDefaults.resources.map((r, i) => `<div class="field"><label for="bank-av-${i}">${r}</label><input id="bank-av-${i}" type="number" min="0" step="1" value="${bankerDefaults.available[i]}"></div>`).join("")}
            </div>
            <div class="data-table-wrap">
              <table class="banker-table">
                <thead><tr><th>Task / Patient</th><th>Allocation (B, D, M)</th><th>Maximum Demand (B, D, M)</th></tr></thead>
                <tbody>
                  ${bankerDefaults.patients.map((p, i) => `<tr>
                    <td><b>${p.name}</b></td>
                    <td>${p.allocation.map((v, j) => `<input id="bank-a-${i}-${j}" aria-label="${p.name} allocation ${bankerDefaults.resources[j]}" type="number" min="0" step="1" value="${v}">`).join("")}</td>
                    <td>${p.maximum.map((v, j) => `<input id="bank-m-${i}-${j}" aria-label="${p.name} maximum ${bankerDefaults.resources[j]}" type="number" min="0" step="1" value="${v}">`).join("")}</td>
                  </tr>`).join("")}
                </tbody>
              </table>
            </div>
          </div>
          <div class="form-actions">
            <button class="button button-primary" data-action="check-banker-input">Calculate safety state (Java)</button>
          </div>
        `)}
      </div>
      <div>
        ${card("Java Safety Result", "Real-time deadlock evaluation returned by backend Java OS Module.", `
          <div id="bankerInputResult">${renderSafetyBanner()}</div>
          <div class="algorithm-explainer">
            <div class="explain-row"><span class="explain-num">1</span><div><b>Need = Max − Allocation</b><p>Remaining resources each process may still request before completing.</p></div></div>
            <div class="explain-row"><span class="explain-num">2</span><div><b>Safe Sequence Search</b><p>Finds an execution sequence where all tasks safely finish and return resources.</p></div></div>
          </div>
        `)}
      </div>
    </div>`;

  } else if (page === "history") {
    html = intro("OS OUTPUT · AUDIT LOG", "Run History", "Persistent record of all CPU scheduling runs and results saved in MySQL database.");
    html += `<div class="module-grid equal">
      ${card("Scheduling Runs", "Persisted in MySQL `scheduling_runs` table.", `<div id="runsTable"></div>`)}
      ${card("Latest Process Results", "Persisted in MySQL `scheduling_results` table.", `<div id="historyResults"></div>`)}
    </div>`;
  }

  host.innerHTML = html;
  wireModule(page);
}

// -------------------------------------------------------------
// TABLE RENDERERS (WITH CRUD BUTTONS & ACTIONS)
// -------------------------------------------------------------

function renderPatients() {
  const q = ($("patientSearch")?.value || "").toLowerCase();
  const deptFilter = $("f-patientDeptFilter")?.value || "All";

  const rows = state.patients.filter(p => {
    const matchDept = (deptFilter === "All" || String(p.department).toLowerCase() === deptFilter.toLowerCase());
    const matchSearch = [p.patient_name, p.phone, p.address, p.bed_number, p.department, p.diagnosis, String(p.patient_id)].join(" ").toLowerCase().includes(q);
    return matchDept && matchSearch;
  });

  if ($("patientTableCount")) $("patientTableCount").textContent = `${rows.length} patient record${rows.length === 1 ? "" : "s"}`;
  
  if ($("patientsTable")) {
    $("patientsTable").innerHTML = table(
      ["PATIENT", "AGE & GENDER", "DIAGNOSIS & DEPT", "ASSIGNED DOCTOR", "BED (1:1)", "PRIORITY", "STATUS", "ACTIONS"],
      rows.map(p => `<tr>
        <td>${personCell(p.patient_name, `ID #${p.patient_id}`)}</td>
        <td>${p.age} yrs (${p.gender || "—"})</td>
        <td><b>${escapeHtml(p.diagnosis || "General Consultation")}</b><small class="person-id">${escapeHtml(p.department || "General Medicine")}</small></td>
        <td>${escapeHtml(p.doctor_name || "Unassigned")}</td>
        <td><span class="badge-bed ${p.bed_number && p.bed_number !== 'Unassigned' ? 'occupied' : 'available'}">🛏️ ${escapeHtml(p.bed_number || "Unassigned")}</span></td>
        <td>${priorityTag(p.priority_level)}</td>
        <td>${statusTag(p.admission_status || "Admitted")}</td>
        <td>
          <div class="action-buttons">
            <button class="btn-action btn-edit" data-action="edit-patient" data-id="${p.patient_id}">✏️ Edit</button>
            <button class="btn-action btn-delete" data-action="delete-patient" data-id="${p.patient_id}" data-name="${escapeHtml(p.patient_name)}">🗑️ Delete</button>
          </div>
        </td>
      </tr>`)
    );
  }
}

function renderDoctors() {
  const q = ($("doctorSearch")?.value || "").toLowerCase();
  const rows = state.doctors.filter(d => 
    (d.doctor_name + " " + d.specialization + " " + (d.department || "") + " " + (d.phone || "") + " " + d.doctor_id).toLowerCase().includes(q)
  );

  if ($("doctorTableCount")) $("doctorTableCount").textContent = `${rows.length} specialist${rows.length === 1 ? "" : "s"}`;

  if ($("doctorsTable")) {
    $("doctorsTable").innerHTML = table(
      ["DOCTOR", "SPECIALIZATION", "DEPARTMENT", "CONTACT", "AVAILABILITY / STATUS", "ACTIONS"],
      rows.map(d => `<tr>
        <td>${personCell(d.doctor_name, `Doctor ID #${d.doctor_id}`, "violet")}</td>
        <td><b>${escapeHtml(d.specialization)}</b></td>
        <td>${escapeHtml(d.department || "General Medicine")}</td>
        <td>${escapeHtml(d.phone || "—")}</td>
        <td>${statusTag(d.availability_status || "Available")}</td>
        <td>
          <div class="action-buttons">
            <button class="btn-action btn-edit" data-action="edit-doctor" data-id="${d.doctor_id}">✏️ Edit</button>
            <button class="btn-action btn-delete" data-action="delete-doctor" data-id="${d.doctor_id}" data-name="${escapeHtml(d.doctor_name)}">🗑️ Delete</button>
          </div>
        </td>
      </tr>`)
    );
  }
}

function renderAppointments() {
  const q = ($("appointmentSearch")?.value || "").toLowerCase();
  const filter = $("f-appointmentFilter")?.value || "All";
  const rows = state.appointments.filter(a =>
    (filter === "All" || a.appointment_status === filter) &&
    (patientName(a.patient_id) + " " + doctorName(a.doctor_id) + " " + a.appointment_status).toLowerCase().includes(q)
  );

  if ($("appointmentsTable")) {
    $("appointmentsTable").innerHTML = table(
      ["APPOINTMENT", "PATIENT", "DOCTOR", "DATE & TIME", "STATUS"],
      rows.map((a, i) => `<tr>
        <td><b>#APT-${String(a.appointment_id).padStart(3, "0")}</b></td>
        <td>${personCell(patientName(a.patient_id), `Patient ID ${a.patient_id}`, ["cyan", "violet", "green"][i % 3])}</td>
        <td>${escapeHtml(doctorName(a.doctor_id))}</td>
        <td>${escapeHtml(dateTime(a.appointment_date, a.appointment_time))}</td>
        <td>${statusTag(a.appointment_status)}</td>
      </tr>`)
    );
  }
}

function renderBeds() {
  if ($("bedsTable")) {
    $("bedsTable").innerHTML = table(
      ["BED NUMBER", "WARD", "CURRENT OCCUPANT (1:1 DEDICATED)", "STATUS", "ACTION"],
      state.beds.map(b => {
        const isOccupied = String(b.bed_status).toLowerCase() === "occupied";
        return `<tr>
          <td><b>🛏️ ${escapeHtml(b.bed_number)}</b><small class="person-id">Bed ID ${b.bed_id}</small></td>
          <td>${escapeHtml(b.ward)}</td>
          <td>${isOccupied ? `<b>${escapeHtml(b.patient_name || patientName(b.patient_id))}</b> (Patient ID ${b.patient_id || '—'})` : `<span class="muted">No patient assigned</span>`}</td>
          <td>${statusTag(b.bed_status)}</td>
          <td>${isOccupied ? `<button class="btn-discharge" data-action="release-bed" data-bed-id="${b.bed_id}">Discharge & Free Bed</button>` : `<span class="muted">Available</span>`}</td>
        </tr>`;
      })
    );
  }
}

// -------------------------------------------------------------
// 5 SEPARATED WORKLOAD CATEGORIES RENDERING
// -------------------------------------------------------------

function renderWorkloadSections() {
  const container = $("workloadSectionsContainer");
  if (!container) return;

  const q = ($("processSearch")?.value || "").toLowerCase();
  const categories = ["Emergency", "Interactive", "OPD", "Diagnostic", "Batch"];

  container.innerHTML = categories.map(cat => {
    const info = workloadInfo[cat];
    const tasks = state.treatment_processes.filter(p => 
      String(p.work_category).toLowerCase() === cat.toLowerCase() &&
      (p.process_name + " " + patientName(p.patient_id) + " " + p.process_id).toLowerCase().includes(q)
    );

    const taskRows = tasks.map(t => `<tr>
      <td><b>P-${t.process_id}</b><small class="person-id">${escapeHtml(t.process_name)}</small></td>
      <td>${escapeHtml(t.patient_name || patientName(t.patient_id))}</td>
      <td>${t.arrival_time} units</td>
      <td><b>${t.burst_time} units</b></td>
      <td>${priorityTag(t.priority)}</td>
      <td>${statusTag(t.process_status || "Queued")}</td>
      <td><span class="workload-algo-tag">${info.recommended} Scheduling</span></td>
      <td>
        <button class="btn-action btn-delete" data-action="delete-process" data-id="${t.process_id}">🗑️</button>
      </td>
    </tr>`);

    return `<div class="workload-section-panel">
      <div class="workload-section-header ${info.sectionClass}">
        <div class="workload-section-title">
          <span style="font-size:18px">${info.icon}</span>
          <div>
            <h3>${escapeHtml(info.title)} <span class="workload-badge-count">${tasks.length} tasks</span></h3>
            <p class="workload-section-desc">${escapeHtml(info.desc)}</p>
          </div>
        </div>
        <div>
          <button class="btn-run-category" data-action="run-category-schedule" data-category="${cat}">▶ Run ${info.recommended} (${cat})</button>
        </div>
      </div>
      <div class="workload-table-body">
        ${tasks.length ? table(
          ["TASK ID & NAME", "PATIENT", "ARRIVAL", "BURST", "PRIORITY", "STATUS", "OPTIMAL ALGORITHM", "ACTION"],
          taskRows
        ) : `<div class="empty-state">No tasks currently queued in ${cat} category. Click "Add software task" to create one.</div>`}
      </div>
    </div>`;
  }).join("");
}

// -------------------------------------------------------------
// SCHEDULING OUTPUT & GANTT CHART
// -------------------------------------------------------------

function renderScheduleOutput() {
  if (!lastSchedule) {
    return `<article class="module-card">
      <h3>Scheduling Output</h3>
      <p class="muted">Select a workload category and click "Run Scheduling" to execute the algorithm in Java.</p>
      <div class="inline-note"><b>Ready to schedule</b><span>Click the Run Scheduling button above to compute CPU execution metrics.</span></div>
    </article>`;
  }

  const s = lastSchedule;
  const max = Math.max(1, ...(s.segments || []).map(x => x.end));
  const distinctPids = [...new Set((s.segments || []).map(x => x.process_id))];

  const gantt = `<div class="gantt-result">
    <div class="gantt-labels"><span>Process</span><span>Execution Timeline (Java OS Module Output)</span></div>
    ${distinctPids.map(pid =>
      `<div class="gantt-row">
        <span class="process-label">P-${pid}</span>
        <div class="gantt-track">
          ${(s.segments || []).filter(seg => seg.process_id === pid).map(seg =>
            `<i class="gantt-segment ${["seg-blue", "seg-lilac", "seg-green", "seg-amber"][pid % 4]}" 
               style="left:${(seg.start / max) * 100}%;width:${Math.max(1, ((seg.end - seg.start) / max) * 100)}%" 
               title="[${seg.start} to ${seg.end}]">${seg.start}–${seg.end}</i>`
          ).join("")}
        </div>
      </div>`
    ).join("")}
    <div class="gantt-ticks">
      <span>0</span>
      <span>${(max * 0.25).toFixed(1)}</span>
      <span>${(max * 0.5).toFixed(1)}</span>
      <span>${(max * 0.75).toFixed(1)}</span>
      <span>${max}</span>
    </div>
  </div>`;

  return `<article class="module-card">
    <div class="panel-title-row">
      <div>
        <h3>Run #${s.run_id || "Live"} · ${escapeHtml(s.algorithm)} Scheduling (${escapeHtml(s.work_category || "Workload")})</h3>
        <p class="muted">${escapeHtml(s.rationale || "Calculated by Java backend · Persisted to MySQL table scheduling_results.")}</p>
      </div>
      ${statusTag("Java Execution Active")}
    </div>
    <div class="result-cards">
      <div class="result-card"><span>Average waiting time (WT)</span><b>${Number(s.avgWait || 0).toFixed(2)} units</b></div>
      <div class="result-card"><span>Average turnaround (TAT)</span><b>${Number(s.avgTat || 0).toFixed(2)} units</b></div>
      <div class="result-card"><span>Tasks executed</span><b>${(s.rows || []).length} tasks</b></div>
    </div>
    ${gantt}
    <div style="margin-top:19px">
      ${table(
        ["PROCESS", "WORKLOAD", "ARRIVAL", "BURST", "PRIORITY", "COMPLETION (CT)", "TURNAROUND (TAT)", "WAITING (WT)"],
        (s.rows || []).map(r => `<tr>
          <td><b>P-${r.process_id}</b><small class="person-id">${escapeHtml(r.process_name)}</small></td>
          <td><span class="badge-workload ${workloadInfo[r.work_category]?.tagClass || 'workload-batch'}">${escapeHtml(r.work_category || 'Workload')}</span></td>
          <td>${r.arrival_time}</td>
          <td>${r.burst_time}</td>
          <td>${r.priority}</td>
          <td><b>${r.completion_time}</b></td>
          <td>${r.turnaround_time}</td>
          <td>${r.waiting_time}</td>
        </tr>`)
      )}
    </div>
  </article>`;
}

function renderSafetyBanner() {
  if (!lastSafety) {
    return `<div class="safe-banner safe"><span class="big-check">?</span><div><b>Awaiting calculation</b><span>Click "Calculate safety state" to invoke Java Banker’s Algorithm.</span></div></div>`;
  }
  if (lastSafety.safe) {
    return `<div class="safe-banner safe">
      <span class="big-check">✓</span>
      <div><b>SAFE state (Java OS Module)</b><span>${escapeHtml(lastSafety.message || "A safe sequence exists. No deadlock possible.")}</span></div>
    </div>
    <b style="font-size:10px;color:#58796d">Verified Safe Sequence:</b>
    <div class="safe-sequence">${(lastSafety.safeSequence || []).map((p, i) => `${i ? '<i>→</i>' : ''}<span>${escapeHtml(p)}</span>`).join("")}</div>`;
  }
  return `<div class="safe-banner unsafe">
    <span class="big-check">!</span>
    <div><b>UNSAFE state (Java OS Module)</b><span>${escapeHtml(lastSafety.message || "No safe execution sequence found.")}</span></div>
  </div>`;
}

function renderHistory() {
  if ($("runsTable")) {
    $("runsTable").innerHTML = table(
      ["RUN ID", "ALGORITHM", "WORKLOAD CATEGORY", "TIME QUANTUM", "EXECUTED AT"],
      state.scheduling_runs.map(r => `<tr>
        <td><b>#RUN-${r.run_id}</b></td>
        <td>${escapeHtml(r.algorithm)}</td>
        <td><span class="badge-workload ${workloadInfo[r.work_category]?.tagClass || 'workload-batch'}">${escapeHtml(r.work_category || 'All Tasks')}</span></td>
        <td>${r.time_quantum ?? "—"}</td>
        <td>${escapeHtml(r.executed_at)}</td>
      </tr>`)
    );
  }

  if ($("historyResults")) {
    $("historyResults").innerHTML = lastSchedule ? table(
      ["PROCESS", "WORKLOAD", "CT", "TAT", "WT"],
      lastSchedule.rows.map(r => `<tr>
        <td><b>P-${r.process_id}</b></td>
        <td>${escapeHtml(r.work_category || '—')}</td>
        <td>${r.completion_time}</td>
        <td>${r.turnaround_time}</td>
        <td>${r.waiting_time}</td>
      </tr>`)
    ) : `<div class="inline-note"><b>Ready to record</b><span>Execute a scheduling run to inspect process-level results persisted in MySQL.</span></div>`;
  }
}

function wireModule(page) {
  if (page === "patients") {
    renderPatients();
    $("patientSearch")?.addEventListener("input", renderPatients);
    $("f-patientDeptFilter")?.addEventListener("change", renderPatients);
  }
  if (page === "doctors") {
    renderDoctors();
    $("doctorSearch")?.addEventListener("input", renderDoctors);
  }
  if (page === "appointments") {
    renderAppointments();
    $("appointmentSearch")?.addEventListener("input", renderAppointments);
    $("f-appointmentFilter")?.addEventListener("change", renderAppointments);
  }
  if (page === "resources") {
    renderBeds();
    $("osResourceForm")?.addEventListener("submit", e => {
      e.preventDefault();
      changeOSResource(true);
    });
  }
  if (page === "treatments") {
    renderWorkloadSections();
    $("processSearch")?.addEventListener("input", renderWorkloadSections);
  }
  if (page === "scheduler") {
    document.querySelectorAll("[data-algorithm]").forEach(b => b.addEventListener("click", () => {
      selectedAlgorithm = b.dataset.algorithm;
      renderModule("scheduler");
    }));

    document.querySelectorAll("[data-workload]").forEach(card => card.addEventListener("click", () => {
      const wl = card.dataset.workload;
      state.selectedWorkload = wl;
      selectedAlgorithm = workloadInfo[wl].recommended;
      renderModule("scheduler");
    }));
  }
  if (page === "history") {
    renderHistory();
  }
}

// -------------------------------------------------------------
// MODALS (PATIENT & DOCTOR CRUD WITH FULL EDIT & REGISTER)
// -------------------------------------------------------------

function openModal(kind, editData = null) {
  const dialog = $("recordDialog");
  const fields = $("modalFields");
  let title = "", subtitle = "", html = "";

  const availableBeds = state.beds.filter(b => String(b.bed_status).toLowerCase() === "available");
  const doctorOptions = [
    { value: "", label: "Unassigned Doctor" },
    ...state.doctors.map(d => ({ value: d.doctor_id, label: `${d.doctor_name} · ${d.specialization} (${d.department})` }))
  ];

  if (kind === "new-patient" || kind === "edit-patient") {
    const isEdit = (kind === "edit-patient" && editData);
    title = isEdit ? `Edit Patient #${editData.patient_id} (${editData.patient_name})` : "Register Patient & Allocate Dedicated Bed";
    subtitle = isEdit ? "Update patient details in MySQL. Modifying status to Discharged frees their bed." : "1:1 Mapping: Exactly one bed is allocated to each admitted patient in MySQL.";

    // Bed options: if edit, include currently assigned bed as option
    let bedOptions = [];
    if (isEdit && editData.bed_id) {
      bedOptions.push({ value: editData.bed_id, label: `🛏️ Keep Current Bed ${editData.bed_number || editData.bed_id} (${editData.ward || 'General'})` });
    }
    bedOptions.push({ value: "", label: "Auto-Assign Next Available Bed" });
    availableBeds.forEach(b => {
      if (!isEdit || b.bed_id !== editData.bed_id) {
        bedOptions.push({ value: b.bed_id, label: `🛏️ Bed ${b.bed_number} (${b.ward})` });
      }
    });

    html = `<div class="field-grid">
      ${isEdit ? `<input type="hidden" name="patient_id" value="${editData.patient_id}">` : ""}
      ${field("Full Name", "patient_name", "text", "e.g. Priya Sharma", "required", isEdit ? editData.patient_name : "")}
      ${field("Age", "age", "number", "Age", 'min="0" max="120" required', isEdit ? editData.age : "")}
      ${selectField("Gender", "gender", [{ value: "Male", label: "Male" }, { value: "Female", label: "Female" }, { value: "Other", label: "Other" }], isEdit ? editData.gender : "Male")}
      ${field("Phone Number", "phone", "tel", "10-digit phone number", "", isEdit ? editData.phone : "")}
      ${field("Disease / Diagnosis", "diagnosis", "text", "e.g. Acute Bronchitis", "required", isEdit ? editData.diagnosis : "")}
      ${field("Department", "department", "text", "e.g. Cardiology, Neurology", "required", isEdit ? editData.department : "General Medicine")}
      ${selectField("Assigned Doctor", "doctor_id", doctorOptions, isEdit ? editData.doctor_id : "")}
      ${selectField("Admission Status", "admission_status", [
        { value: "Admitted", label: "Admitted (Occupies Dedicated Bed 1:1)" },
        { value: "Outpatient", label: "Outpatient (No Bed Occupied)" },
        { value: "Discharged", label: "Discharged (Frees Bed)" }
      ], isEdit ? editData.admission_status : "Admitted")}
      ${selectField("Priority Level", "priority_level", [
        { value: "1", label: "🔴 Priority 1 (Critical / Emergency)" },
        { value: "2", label: "🟡 Priority 2 (Moderate / General Care)" },
        { value: "3", label: "🟢 Priority 3 (Low / Routine Follow-up)" }
      ], isEdit ? editData.priority_level : "2")}
      ${selectField("Dedicated Hospital Bed (1:1)", "bed_id", bedOptions, isEdit ? editData.bed_id : "")}
      ${field("Address", "address", "text", "City or residential address", "", isEdit ? editData.address : "")}
    </div>`;

  } else if (kind === "new-doctor" || kind === "edit-doctor") {
    const isEdit = (kind === "edit-doctor" && editData);
    title = isEdit ? `Edit Doctor #${editData.doctor_id} (${editData.doctor_name})` : "Add Medical Specialist";
    subtitle = isEdit ? "Update doctor profile and availability in MySQL." : "Inserts a doctor into MySQL `doctors` table.";

    html = `<div class="field-grid">
      ${isEdit ? `<input type="hidden" name="doctor_id" value="${editData.doctor_id}">` : ""}
      ${field("Doctor Full Name", "doctor_name", "text", "e.g. Dr. Priya Sharma", "required", isEdit ? editData.doctor_name : "")}
      ${field("Medical Specialization", "specialization", "text", "e.g. Cardiologist", "required", isEdit ? editData.specialization : "")}
      ${field("Department", "department", "text", "e.g. Cardiology", "required", isEdit ? editData.department : "General Medicine")}
      ${field("Contact Phone", "phone", "tel", "Contact number", "", isEdit ? editData.phone : "")}
      ${selectField("Availability Status", "availability_status", [
        { value: "Available", label: "🟢 Available (OPD & Rounds)" },
        { value: "On Duty", label: "🔵 On Duty (Inpatient Ward)" },
        { value: "In Surgery", label: "🟣 In Surgery (Operating Theatre)" },
        { value: "On Leave", label: "⚪ On Leave" }
      ], isEdit ? editData.availability_status : "Available")}
    </div>`;

  } else if (kind === "new-appointment") {
    title = "Schedule New Appointment";
    subtitle = "Relational appointment joining registered patient and doctor in MySQL.";
    const patientOptions = state.patients.map(p => ({ value: p.patient_id, label: `${p.patient_name} · #${p.patient_id}` }));
    html = `<div class="field-grid">
      ${selectField("Patient", "patient_id", patientOptions)}
      ${selectField("Doctor", "doctor_id", doctorOptions.filter(d => d.value))}
      ${field("Appointment Date", "appointment_date", "date", "", "required")}
      ${field("Appointment Time", "appointment_time", "time", "", "required")}
      ${selectField("Status", "appointment_status", [
        { value: "Scheduled", label: "Scheduled" },
        { value: "Completed", label: "Completed" },
        { value: "Cancelled", label: "Cancelled" }
      ])}
    </div>`;

  } else if (kind === "new-resource") {
    title = "Add Hospital Resource";
    subtitle = "Inserts a resource inventory row into MySQL `resources` table.";
    html = `<div class="field-grid">
      ${field("Resource Name", "resource_name", "text", "e.g. Defibrillator", "required")}
      ${field("Available Quantity", "available_quantity", "number", "0", 'min="0" value="0" required')}
      ${field("Total Quantity", "total_quantity", "number", "0", 'min="0" value="0" required')}
    </div>`;

  } else if (kind === "new-process") {
    title = "Add Software Task to Queue";
    subtitle = "Creates a software task in MySQL `treatment_processes` categorized by workload.";
    const patientOptions = state.patients.map(p => ({ value: p.patient_id, label: `${p.patient_name} · #${p.patient_id}` }));
    html = `<div class="field-grid">
      ${selectField("Patient", "patient_id", patientOptions)}
      ${selectField("Workload Category", "work_category", [
        { value: "Emergency", label: "🚨 Critical Emergency (Priority Scheduling)" },
        { value: "Interactive", label: "💬 Interactive Doctor (Round Robin Scheduling)" },
        { value: "OPD", label: "🩺 OPD Clinical Queue (Round Robin / Fair Slicing)" },
        { value: "Diagnostic", label: "🧪 Rapid Diagnostic (SJF Scheduling)" },
        { value: "Batch", label: "📊 Batch Report (FCFS Scheduling)" }
      ])}
      ${field("Task Name", "process_name", "text", "e.g. Telemetry ECG Analysis", "required")}
      ${field("Arrival Time", "arrival_time", "number", "0", 'min="0" value="0" required')}
      ${field("Burst Time", "burst_time", "number", "1", 'min="1" value="1" required')}
      ${field("Priority (1 = Highest)", "priority", "number", "1", 'min="1" value="1" required')}
    </div>`;
  }

  if ($("modalKicker")) $("modalKicker").textContent = kind.includes("edit") ? "DBMS · UPDATE RECORD" : "DBMS · NEW RECORD";
  if ($("modalTitle")) $("modalTitle").textContent = title;
  if ($("modalSubtitle")) $("modalSubtitle").textContent = subtitle;
  if (fields) fields.innerHTML = html;
  if ($("saveModal")) $("saveModal").textContent = kind.includes("edit") ? "Update record" : "Save record";
  if ($("recordForm")) $("recordForm").dataset.kind = kind;
  dialog.showModal();
}

async function submitModal(e) {
  e.preventDefault();
  const kind = e.currentTarget.dataset.kind;
  const fd = new FormData(e.currentTarget);
  const v = Object.fromEntries(fd.entries());
  const num = x => Number(v[x]);

  try {
    let endpoint = "";
    let method = "POST";
    let payload = {};

    if (kind === "new-patient" || kind === "edit-patient") {
      if (!v.patient_name?.trim() || v.age === "" || num("age") < 0) {
        return toast("Enter a valid patient name and age.");
      }
      endpoint = (kind === "edit-patient") ? "/api/patients/update" : "/api/patients";
      payload = {
        patient_name: v.patient_name.trim(),
        age: num("age"),
        gender: v.gender,
        phone: v.phone?.trim() || null,
        address: v.address?.trim() || null,
        diagnosis: v.diagnosis?.trim() || "General Consultation",
        department: v.department?.trim() || "General Medicine",
        doctor_id: v.doctor_id ? num("doctor_id") : null,
        bed_id: v.bed_id ? num("bed_id") : null,
        admission_status: v.admission_status || "Admitted",
        priority_level: num("priority_level") || 2
      };
      if (kind === "edit-patient") {
        payload.patient_id = num("patient_id");
      }

    } else if (kind === "new-doctor" || kind === "edit-doctor") {
      if (!v.doctor_name?.trim() || !v.specialization?.trim()) {
        return toast("Doctor name and specialization are required.");
      }
      endpoint = (kind === "edit-doctor") ? "/api/doctors/update" : "/api/doctors";
      payload = {
        doctor_name: v.doctor_name.trim(),
        specialization: v.specialization.trim(),
        department: v.department?.trim() || "General Medicine",
        phone: v.phone?.trim() || null,
        availability_status: v.availability_status || "Available"
      };
      if (kind === "edit-doctor") {
        payload.doctor_id = num("doctor_id");
      }

    } else if (kind === "new-appointment") {
      if (!v.appointment_date || !v.appointment_time) {
        return toast("Select appointment date and time.");
      }
      endpoint = "/api/appointments";
      payload = {
        patient_id: num("patient_id"),
        doctor_id: num("doctor_id"),
        appointment_date: v.appointment_date,
        appointment_time: v.appointment_time,
        appointment_status: v.appointment_status
      };

    } else if (kind === "new-resource") {
      if (!v.resource_name?.trim() || num("available_quantity") < 0 || num("total_quantity") < 0) {
        return toast("Valid non-negative quantities required.");
      }
      endpoint = "/api/resources";
      payload = {
        resource_name: v.resource_name.trim(),
        available_quantity: num("available_quantity"),
        total_quantity: num("total_quantity")
      };

    } else if (kind === "new-process") {
      if (!v.process_name?.trim() || num("arrival_time") < 0 || num("burst_time") <= 0) {
        return toast("Valid task name, arrival (≥0) and burst (>0) required.");
      }
      endpoint = "/api/processes";
      payload = {
        patient_id: num("patient_id"),
        process_name: v.process_name.trim(),
        work_category: v.work_category || "Emergency",
        arrival_time: num("arrival_time"),
        burst_time: num("burst_time"),
        priority: num("priority") || 1
      };
    }

    const res = await fetch(`${API_BASE}${endpoint}`, {
      method: method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    const data = await res.json();
    if (!res.ok || data.error) {
      toast("Error: " + (data.message || "Operation failed"));
      return;
    }

    $("recordDialog").close();
    e.currentTarget.reset();
    await loadDataFromBackend();
    toast(data.message || "Saved successfully to MySQL!");

  } catch (err) {
    toast("Connection/Database error: " + err.message);
  }
}

// -------------------------------------------------------------
// DELETE HANDLERS WITH CONFIRMATION
// -------------------------------------------------------------

async function deletePatientPrompt(id, name) {
  const p = state.patients.find(x => Number(x.patient_id) === Number(id));
  const bedInfo = (p && p.bed_number && p.bed_number !== "Unassigned") ? `\nBed ${p.bed_number} will be released.` : "";
  
  if (!confirm(`Are you sure you want to delete Patient #${id} (${name})?${bedInfo}\nThis action is permanent in MySQL.`)) {
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/patients/delete`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ patient_id: Number(id) })
    });
    const data = await res.json();
    if (!res.ok || data.error) {
      toast("Delete failed: " + (data.message || "Error"));
      return;
    }
    toast(data.message || `Patient #${id} deleted.`);
    await loadDataFromBackend();
  } catch (e) {
    toast("Error deleting patient: " + e.message);
  }
}

async function deleteDoctorPrompt(id, name) {
  if (!confirm(`Are you sure you want to delete Dr. ${name} (ID #${id})?\nAny assigned patient links will be cleared in MySQL.`)) {
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/doctors/delete`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ doctor_id: Number(id) })
    });
    const data = await res.json();
    if (!res.ok || data.error) {
      toast("Delete failed: " + (data.message || "Error"));
      return;
    }
    toast(data.message || `Doctor #${id} deleted.`);
    await loadDataFromBackend();
  } catch (e) {
    toast("Error deleting doctor: " + e.message);
  }
}

async function deleteProcessPrompt(id) {
  if (!confirm(`Are you sure you want to delete task P-${id} from the software queue?`)) {
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/processes/delete`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ process_id: Number(id) })
    });
    const data = await res.json();
    if (!res.ok || data.error) {
      toast("Delete failed: " + (data.message || "Error"));
      return;
    }
    toast(`Task P-${id} deleted.`);
    await loadDataFromBackend();
  } catch (e) {
    toast("Error deleting task: " + e.message);
  }
}

async function dischargeBed(bedId) {
  if (!confirm("Are you sure you want to discharge the patient and free this hospital bed in MySQL?")) {
    return;
  }
  try {
    const res = await fetch(`${API_BASE}/api/beds/release`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ bed_id: Number(bedId) })
    });
    const data = await res.json();
    if (!res.ok || data.error) {
      toast(data.message || "Failed to release bed");
      return;
    }
    toast(data.message);
    await loadDataFromBackend();
  } catch (e) {
    toast("Error discharging bed: " + e.message);
  }
}

// -------------------------------------------------------------
// SCHEDULER & BANKER'S ALGORITHM
// -------------------------------------------------------------

async function runScheduler() {
  const quantum = Math.max(1, Number($("f-quantum")?.value || 2));
  const workload = state.selectedWorkload || "Emergency";

  toast(`Running ${selectedAlgorithm} scheduling for ${workload} Workload...`);

  try {
    const res = await fetch(`${API_BASE}/api/schedule`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        work_category: workload,
        algorithm: selectedAlgorithm,
        quantum: quantum
      })
    });

    const data = await res.json();
    if (!res.ok || data.error) {
      toast(data.message || "Scheduling execution failed");
      return;
    }

    lastSchedule = data;

    const historyRes = await fetch(`${API_BASE}/api/history`).then(r => r.json()).catch(() => []);
    if (Array.isArray(historyRes)) state.scheduling_runs = historyRes;

    if ($("scheduleOutput")) $("scheduleOutput").innerHTML = renderScheduleOutput();
    refreshOverview();
    toast(`${selectedAlgorithm} scheduling completed for ${workload} Workload (Run #${data.run_id}).`);

  } catch (err) {
    toast("Scheduler error: " + err.message);
  }
}

async function checkBankerInput() {
  const n = bankerDefaults.patients.length;
  const m = bankerDefaults.resources.length;

  const read = id => {
    const el = $(id);
    const v = Number(el?.value);
    if (!el || !Number.isInteger(v) || v < 0) {
      throw new Error("All matrix values must be non-negative whole numbers.");
    }
    return v;
  };

  try {
    const available = bankerDefaults.resources.map((_, j) => read(`bank-av-${j}`));
    const alloc = bankerDefaults.patients.map((_, i) => bankerDefaults.resources.map((_, j) => read(`bank-a-${i}-${j}`)));
    const max = bankerDefaults.patients.map((_, i) => bankerDefaults.resources.map((_, j) => read(`bank-m-${i}-${j}`)));

    for (let i = 0; i < n; i++) {
      for (let j = 0; j < m; j++) {
        if (alloc[i][j] > max[i][j]) {
          throw new Error(`${bankerDefaults.patients[i].name}: Allocation (${alloc[i][j]}) cannot exceed Maximum Demand (${max[i][j]}) for ${bankerDefaults.resources[j]}.`);
        }
      }
    }

    toast("Calculating safety state in Java OS Module...");

    const res = await fetch(`${API_BASE}/api/banker`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        available,
        allocation: alloc,
        max,
        patients: bankerDefaults.patients.map(p => p.name)
      })
    });

    const data = await res.json();
    if (!res.ok || data.error) {
      toast(data.message || "Banker calculation error");
      return;
    }

    lastSafety = data;
    if ($("bankerInputResult")) $("bankerInputResult").innerHTML = renderSafetyBanner();
    toast(data.safe ? "Java safety check: SAFE state (Deadlock Avoided)." : "Java safety check: UNSAFE state.");

  } catch (e) {
    toast(e.message);
  }
}

async function changeOSResource(allocate) {
  const key = $("f-osResource")?.value;
  const qty = Number($("f-osQuantity")?.value);

  if (!key || !Number.isInteger(qty) || qty <= 0) {
    toast("Enter a positive whole-number quantity.");
    return;
  }

  const endpoint = allocate ? "/api/resources/allocate" : "/api/resources/release";

  try {
    const res = await fetch(`${API_BASE}${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ resource: key, quantity: qty })
    });

    const data = await res.json();
    if (!res.ok || data.error) {
      toast(data.message || (allocate ? "Allocation failed" : "Release failed"));
      return;
    }

    toast(data.message);
    await loadDataFromBackend();

  } catch (err) {
    toast("Connection error: " + err.message);
  }
}

// -------------------------------------------------------------
// GLOBAL ACTION ROUTER & EVENT LISTENERS
// -------------------------------------------------------------

function action(kind, target) {
  if (kind.startsWith("new-")) return openModal(kind);

  if (kind === "edit-patient") {
    const id = target.dataset.id;
    const p = state.patients.find(x => Number(x.patient_id) === Number(id));
    if (p) openModal("edit-patient", p);
    return;
  }

  if (kind === "delete-patient") {
    const id = target.dataset.id;
    const name = target.dataset.name || "Patient";
    deletePatientPrompt(id, name);
    return;
  }

  if (kind === "edit-doctor") {
    const id = target.dataset.id;
    const d = state.doctors.find(x => Number(x.doctor_id) === Number(id));
    if (d) openModal("edit-doctor", d);
    return;
  }

  if (kind === "delete-doctor") {
    const id = target.dataset.id;
    const name = target.dataset.name || "Doctor";
    deleteDoctorPrompt(id, name);
    return;
  }

  if (kind === "delete-process") {
    const id = target.dataset.id;
    deleteProcessPrompt(id);
    return;
  }

  if (kind === "run-category-schedule") {
    const cat = target.dataset.category || "Emergency";
    state.selectedWorkload = cat;
    selectedAlgorithm = workloadInfo[cat]?.recommended || "Priority";
    go("scheduler");
    runScheduler();
    return;
  }

  if (kind === "run-scheduler") return runScheduler();
  if (kind === "release-os-resource") return changeOSResource(false);
  if (kind === "check-banker-input") return checkBankerInput();
  if (kind === "release-bed") {
    const bedId = target?.dataset?.bedId;
    if (bedId) dischargeBed(bedId);
    return;
  }
}

document.addEventListener("click", e => {
  const nav = e.target.closest("[data-page]");
  if (nav) { go(nav.dataset.page); return; }

  const goto = e.target.closest("[data-goto]");
  if (goto) { go(goto.dataset.goto); return; }

  const act = e.target.closest("[data-action]");
  if (act) { action(act.dataset.action, act); return; }

  const alg = e.target.closest("[data-algorithm]");
  if (alg) {
    selectedAlgorithm = alg.dataset.algorithm;
    renderModule("scheduler");
  }
});

$("primaryAction")?.addEventListener("click", () => {
  const p = state.page;
  if (p === "overview" || p === "patients") openModal("new-patient");
  else if (p === "doctors") openModal("new-doctor");
  else if (p === "appointments") openModal("new-appointment");
  else if (p === "resources") openModal("new-resource");
  else if (p === "treatments") openModal("new-process");
  else if (p === "scheduler") runScheduler();
  else if (p === "allocation") checkBankerInput();
  else if (p === "history") {
    loadDataFromBackend();
    toast("Refreshed history audit trail.");
  }
});

$("recordForm")?.addEventListener("submit", submitModal);
$("closeModal")?.addEventListener("click", () => $("recordDialog")?.close());
$("cancelModal")?.addEventListener("click", () => $("recordDialog")?.close());
$("mobileMenu")?.addEventListener("click", () => $("sidebar")?.classList.toggle("open"));
$("refreshButton")?.addEventListener("click", async () => {
  toast("Refreshing live database snapshot...");
  await loadDataFromBackend();
  toast("Data refreshed.");
});
$("overviewSearch")?.addEventListener("input", refreshOverview);
$("dismissNotice")?.addEventListener("click", () => $("prototypeNotice")?.classList.add("hidden"));
$("helpButton")?.addEventListener("click", () => {
  toast("B.Tech 3rd Year Project: OS CPU Scheduling + DBMS CRUD & 1:1 Bed Allocation");
});

if ($("todayLabel")) {
  $("todayLabel").textContent = new Intl.DateTimeFormat("en-IN", {
    day: "2-digit", month: "short", year: "numeric"
  }).format(new Date()).toUpperCase();
}

updateGreeting();
loadDataFromBackend();
