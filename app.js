/* CarePoint redesigned frontend.
   Demo records mirror Rohan's proposed DBMS tables. Replace local handlers with confirmed Java API routes during integration.
   Scheduling and Banker results below are previews, not verified OS-module output. */
const state = {
  page: "overview",
  patients: [
    {patient_id:1,patient_name:"Rohit Sharma",age:45,gender:"Male",phone:"9876543210",address:"Dehradun"},
    {patient_id:2,patient_name:"Virat Kohli",age:18,gender:"Male",phone:"9876543211",address:"Rishikesh"},
    {patient_id:3,patient_name:"MSThala",age:7,gender:"Male",phone:"9876543212",address:"Haridwar"}
  ],
  doctors: [
    {doctor_id:1,doctor_name:"Dr. Kumar",specialization:"Cardiologist",phone:"9987667661"},
    {doctor_id:2,doctor_name:"Dr. Salukhe",specialization:"General Physician",phone:"9989876592"},
    {doctor_id:3,doctor_name:"Dr. Tarika",specialization:"Doctor",phone:"8765678933"}
  ],
  beds: [
    {bed_id:1,bed_number:"A-101",ward:"General Ward",bed_status:"Available"},
    {bed_id:2,bed_number:"A-102",ward:"General Ward",bed_status:"Occupied"},
    {bed_id:3,bed_number:"ICU-01",ward:"ICU",bed_status:"In Maintenance"}
  ],
  resources: [
    {resource_id:1,resource_name:"Oxygen Cylinder",available_quantity:2,total_quantity:20},
    {resource_id:2,resource_name:"Mask",available_quantity:678,total_quantity:1000},
    {resource_id:3,resource_name:"Ventilator",available_quantity:9,total_quantity:10}
  ],
  appointments: [
    {appointment_id:1,patient_id:1,doctor_id:1,appointment_date:"2026-10-15",appointment_time:"10:00:00",appointment_status:"Scheduled"},
    {appointment_id:2,patient_id:2,doctor_id:2,appointment_date:"2026-10-15",appointment_time:"11:30:00",appointment_status:"Scheduled"},
    {appointment_id:3,patient_id:3,doctor_id:3,appointment_date:"2026-10-16",appointment_time:"09:30:00",appointment_status:"Completed"}
  ],
  treatment_processes: [
    {process_id:101,patient_id:1,appointment_id:1,process_name:"Cardiac assessment",arrival_time:0,burst_time:5,priority:1,process_status:"Queued"},
    {process_id:102,patient_id:2,appointment_id:2,process_name:"General consultation",arrival_time:1,burst_time:4,priority:3,process_status:"Queued"},
    {process_id:103,patient_id:3,appointment_id:3,process_name:"Follow-up check",arrival_time:2,burst_time:3,priority:2,process_status:"Queued"},
    {process_id:104,patient_id:1,appointment_id:1,process_name:"Diagnostic scan",arrival_time:3,burst_time:6,priority:4,process_status:"Queued"}
  ],
  scheduling_runs: [
    {run_id:1,algorithm:"Priority",time_quantum:null,executed_at:"Preview run"}
  ],
  scheduling_results: [],
  resource_allocations: [
    {process_id:101,resource_id:1,allocated_quantity:1,maximum_quantity:2,status:"Requested"},
    {process_id:102,resource_id:3,allocated_quantity:1,maximum_quantity:1,status:"Requested"},
    {process_id:103,resource_id:2,allocated_quantity:10,maximum_quantity:25,status:"Requested"}
  ],
  nextIds:{patients:4,doctors:4,beds:4,resources:4,appointments:4,treatment_processes:105}
};
const pages = {
 overview:{crumb:"Overview",title:"",subtitle:"A clear view of patient care, hospital resources and treatment scheduling.",action:"Register patient",kind:"none"},
 patients:{crumb:"Patients",title:"Patient records",subtitle:"Manage patient details stored in the patients table.",action:"Register patient",kind:"patients"},
 doctors:{crumb:"Doctors",title:"Doctor directory",subtitle:"Review doctor profiles and specializations.",action:"Add doctor",kind:"doctors"},
 appointments:{crumb:"Appointments",title:"Appointment desk",subtitle:"Connect patients and doctors through scheduled visits.",action:"New appointment",kind:"appointments"},
 resources:{crumb:"Beds & resources",title:"Hospital capacity",subtitle:"Track bed status and available resource quantities.",action:"Add resource",kind:"resources"},
 treatments:{crumb:"Treatment processes",title:"Treatment queue",subtitle:"Maintain process inputs before passing them to the OS scheduler.",action:"Add process",kind:"treatments"},
 scheduler:{crumb:"Treatment scheduling",title:"Treatment scheduling",subtitle:"Schedule patient treatment processes and inspect waiting, turnaround and completion times.",action:"Run scheduler",kind:"scheduler"},
 allocation:{crumb:"Banker’s algorithm",title:"Resource safety check",subtitle:"Inspect allocated, maximum and available resource vectors.",action:"Check safe state",kind:"allocation"},
 history:{crumb:"Run history",title:"Scheduling & allocation history",subtitle:"Review run records and their persisted result sets.",action:"Refresh history",kind:"history"}
};
const $ = id => document.getElementById(id);
function greetingForCurrentTime(date = new Date()) {
  const hour = date.getHours();
  if (hour >= 5 && hour < 12) return "Good morning";
  if (hour >= 12 && hour < 17) return "Good afternoon";
  if (hour >= 17 && hour < 21) return "Good evening";
  return "Good night";
}
function updateGreeting() {
  const title = $("pageTitle");
  if (state.page === "overview" && title) title.innerHTML = `${greetingForCurrentTime()} Anand<span class="title-dot">.</span>`;
}
const navButtons = [...document.querySelectorAll(".nav-item")];
let selectedAlgorithm = "Priority";
let lastSchedule = null;
let lastSafety = null;
let toastTimer;
// OS-module resource snapshot requested in the Phase-II specification. This is browser-local demo state.
const osInventory = [
  { key:"beds", name:"Beds", total:10, available:10 },
  { key:"doctors", name:"Doctors", total:5, available:5 },
  { key:"devices", name:"Monitoring Devices", total:8, available:8 }
];
const osResourceLedger = [];
const bankerDefaults = {
  resources:["Beds","Doctors","Monitoring Devices"],
  available:[3,2,2],
  patients:[
    {name:"Patient 1", allocation:[1,0,1], maximum:[3,2,2]},
    {name:"Patient 2", allocation:[1,1,0], maximum:[2,2,1]},
    {name:"Patient 3", allocation:[1,0,1], maximum:[3,1,3]}
  ]
};

function toast(msg){const el=$("toast");el.textContent=msg;el.classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove("show"),3200);}
function escapeHtml(v){return String(v??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));}
function patientName(id){return state.patients.find(x=>x.patient_id===Number(id))?.patient_name||`Patient #${id}`;}
function doctorName(id){return state.doctors.find(x=>x.doctor_id===Number(id))?.doctor_name||`Doctor #${id}`;}
function initials(name){return String(name||"?").trim().split(/\s+/).slice(0,2).map(x=>x[0]||"").join("").toUpperCase();}
function statusClass(status){const s=String(status||"").toLowerCase();if(s.includes("available")||s.includes("safe")||s.includes("completed"))return "status-available";if(s.includes("occupied")||s.includes("progress"))return "status-in-progress";if(s.includes("maintenance")||s.includes("unsafe")||s.includes("cancel"))return "status-maintenance";return "status-pending";}
function statusTag(status){return `<span class="status ${statusClass(status)}">${escapeHtml(status)}</span>`;}
function personCell(name,id,color="cyan"){return `<div class="person"><span class="person-avatar ${color==="violet"?"violet":color==="green"?"green":color==="amber"?"amber":"cyan"}">${escapeHtml(initials(name))}</span><span><span class="person-name">${escapeHtml(name)}</span><small class="person-id">${escapeHtml(id)}</small></span></div>`;}
function dateTime(date,time){let d=new Date(`${date}T${time||"00:00:00"}`);if(Number.isNaN(d.getTime()))return `${date||"—"} ${time||""}`;return d.toLocaleDateString("en-IN",{day:"2-digit",month:"short",year:"numeric"})+" · "+d.toLocaleTimeString("en-IN",{hour:"2-digit",minute:"2-digit"});}
function availableBeds(){return state.beds.filter(b=>b.bed_status.toLowerCase()==="available").length;}
function queued(){return state.treatment_processes.filter(p=>p.process_status.toLowerCase()==="queued").length;}
function resourceLine(icon,name,available,total,color){let pct=total?Math.round(available/total*100):0;return `<div class="resource-line"><div class="resource-icon ${color}">${icon}</div><div class="resource-detail"><b>${escapeHtml(name)}</b><small>${available} available of ${total}</small><div class="resource-bar"><i style="width:${Math.min(100,pct)}%"></i></div></div><span class="resource-amount">${pct}%</span></div>`;}
function refreshOverview(){
 $("metricPatients").textContent=state.patients.length;$("metricAppointments").textContent=state.appointments.length;
 $("metricBeds").innerHTML=`${availableBeds()} <small>/ ${state.beds.length}</small>`;$("bedFoot").textContent=`${availableBeds()} currently available`;
 $("metricQueue").textContent=queued();$("navPatientCount").textContent=state.patients.length;
 $("overviewProcesses").textContent=queued();
 $("overviewResources").innerHTML=state.resources.map((r,i)=>resourceLine(["◉","▦","✚"][i%3],r.resource_name,r.available_quantity,r.total_quantity,["cyan","violet","green"][i%3])).join("");
 const search=($("overviewSearch")?.value||"").toLowerCase();
 const rows=state.appointments.filter(a=>(patientName(a.patient_id)+" "+doctorName(a.doctor_id)).toLowerCase().includes(search)).slice(0,5);
 $("overviewAppointments").innerHTML=rows.length?rows.map((a,i)=>`<tr><td><b>#APT-${String(a.appointment_id).padStart(3,"0")}</b></td><td>${personCell(patientName(a.patient_id),`Patient ID ${a.patient_id}`,["cyan","violet","green"][i%3])}</td><td>${escapeHtml(doctorName(a.doctor_id))}</td><td>${escapeHtml(dateTime(a.appointment_date,a.appointment_time))}</td><td>${statusTag(a.appointment_status)}</td></tr>`).join(""):`<tr><td colspan="5" class="empty-state">No appointments match this search.</td></tr>`;
 if(lastSchedule){$("overviewAlgorithm").textContent=lastSchedule.algorithm;$("overviewWait").innerHTML=`${lastSchedule.avgWait.toFixed(1)} <small>units</small>`;}
 else {$("overviewAlgorithm").textContent="Priority";$("overviewWait").innerHTML='— <small>units</small>';}
}
function go(page){
 if(!pages[page])page="overview";state.page=page;const cfg=pages[page];
 navButtons.forEach(b=>b.classList.toggle("active",b.dataset.page===page));
 $("crumb").textContent=cfg.crumb;if(page==="overview"){updateGreeting();}else{$("pageTitle").textContent=cfg.title;}$("pageSubtitle").textContent=cfg.subtitle;
 $("primaryAction").innerHTML=`＋ <span>${escapeHtml(cfg.action)}</span>`;
 $("overviewView").classList.toggle("hidden",page!=="overview");$("moduleView").classList.toggle("hidden",page==="overview");
 $("sidebar").classList.remove("open");
 if(page!=="overview")renderModule(page);refreshOverview();
}
function table(headers,rows,empty="No records yet."){return `<div class="data-table-wrap"><table><thead><tr>${headers.map(h=>`<th>${h}</th>`).join("")}</tr></thead><tbody>${rows.length?rows.join(""):`<tr><td colspan="${headers.length}" class="empty-state">${empty}</td></tr>`}</tbody></table></div>`;}
function intro(kicker,title,desc,actionLabel="",action=""){return `<div class="module-intro"><div><div class="section-kicker">${kicker}</div><h2>${title}</h2><p>${desc}</p></div>${actionLabel?`<button class="button button-primary" data-action="${action}">＋ ${actionLabel}</button>`:""}</div>`;}
function card(title,desc,body){return `<article class="module-card"><h3>${title}</h3><p class="muted">${desc}</p>${body}</article>`;}
function field(label,name,type="text",placeholder="",extra=""){return `<div class="field"><label for="f-${name}">${label}</label><input id="f-${name}" name="${name}" type="${type}" placeholder="${placeholder}" ${extra}></div>`;}
function selectField(label,name,options){return `<div class="field"><label for="f-${name}">${label}</label><select id="f-${name}" name="${name}">${options.map(o=>`<option value="${escapeHtml(o.value)}">${escapeHtml(o.label)}</option>`).join("")}</select></div>`;}
function renderModule(page){
 const host=$("moduleContent");let html="";
 if(page==="patients"){
  html=intro("DBMS · PATIENTS","Patient registry","Maps to `patients`: patient_id, patient_name, age, gender, phone and address.","Register patient","new-patient");
  html+=card("Patient records","Search the local preview records before connecting the patient CRUD API.",`<div class="filter-row"><div class="search-wrap"><span>⌕</span><input id="patientSearch" placeholder="Search name, phone or address" aria-label="Search patients"></div><span class="table-meta" id="patientTableCount"></span></div><div id="patientsTable"></div><div class="inline-note"><b>DBMS mapping</b><span>Phone is unique in Rohan’s schema. The final Java API should return generated patient_id values from MySQL.</span></div>`);
 }else if(page==="doctors"){
  html=intro("DBMS · DOCTORS","Doctor directory","Maps to `doctors`: doctor_id, doctor_name, specialization and unique phone.","Add doctor","new-doctor");
  html+=card("Registered doctors","Profiles currently mirror the SQL sample data.",`<div class="filter-row"><div class="search-wrap"><span>⌕</span><input id="doctorSearch" placeholder="Search doctor or specialization"></div></div><div id="doctorsTable"></div>`);
 }else if(page==="appointments"){
  html=intro("DBMS · APPOINTMENTS","Appointment management","Appointment rows join patient and doctor records using foreign keys.","New appointment","new-appointment");
  html+=card("Appointment list","The final view should use the appointment JOIN query from the database.",`<div class="filter-row"><div class="search-wrap"><span>⌕</span><input id="appointmentSearch" placeholder="Search patient, doctor or status"></div>${selectField("Status","appointmentFilter",[{value:"All",label:"All statuses"},{value:"Scheduled",label:"Scheduled"},{value:"Completed",label:"Completed"},{value:"Cancelled",label:"Cancelled"}])}</div><div id="appointmentsTable"></div>`);
 }else if(page==="resources"){
  html=intro("DBMS · RESOURCE INVENTORY","Beds & resources","Separate views for bed status and available/total quantities. Banker allocation uses its own process-resource matrix.","Add resource","new-resource");
  html+=`<div class="metric-grid os-resource-grid">${osInventory.map((r,i)=>`<article class="metric-card"><div class="metric-head"><span>${r.name}</span><span class="metric-icon ${["green","violet","cyan"][i]}">${["▤","⚕","◉"][i]}</span></div><div class="metric-number" id="os-available-${r.key}">${r.available} <small>/ ${r.total}</small></div><div class="metric-foot"><span class="tiny-dot ${r.available?"green-dot":"amber-dot"}"></span><span>${r.available} currently available</span></div><div class="resource-bar"><i style="width:${r.available/r.total*100}%"></i></div></article>`).join("")}</div>
  <div class="module-grid equal">${card("Allocate or release OS resources","Select a resource and quantity. Allocation and release are tracked in this browser preview only.",`<form id="osResourceForm"><div class="field-grid">${selectField("Resource","osResource",osInventory.map(r=>({value:r.key,label:r.name})))}${field("Quantity","osQuantity","number","Enter quantity",'min="1" step="1" value="1" required')}</div><div class="form-actions"><button type="button" class="button button-light" data-action="release-os-resource">Release resource</button><button type="submit" class="button button-primary">Allocate resource</button></div></form><div id="osResourceMessage" class="inline-note"><b>Resource rules</b><span>Quantity must be a positive whole number. You cannot allocate more than is available or release more than has been allocated.</span></div><div id="osResourceLedger"></div>`)}${card("Hospital inventory · DBMS","Existing database-backed resource views are preserved separately from the OS simulation.",`<div class="mini-stat-grid"><div class="mini-stat"><span>Available beds (DBMS)</span><b>${availableBeds()}</b></div><div class="mini-stat"><span>Occupied beds</span><b>${state.beds.filter(b=>b.bed_status==="Occupied").length}</b></div><div class="mini-stat"><span>Maintenance</span><b>${state.beds.filter(b=>b.bed_status==="In Maintenance").length}</b></div></div><div id="bedsTable"></div><div id="resourcesTable"></div><div class="inline-note"><b>Note</b><span>OS inventory values (10 beds, 5 doctors, 8 monitoring devices) are the requested simulation defaults; they are not loaded from MySQL.</span></div>`)}</div>`;
 }else if(page==="treatments"){
  html=intro("OS INPUT · TREATMENT PROCESSES","Treatment process queue","Maps to `treatment_processes`: patient/appointment, process name, arrival time, burst time, priority and status.","Add process","new-process");
  html+=`<div class="module-grid"><div>${card("Process queue","These are scheduling inputs. Arrival time, burst time and priority are sent to the OS module.",`<div class="filter-row"><div class="search-wrap"><span>⌕</span><input id="processSearch" placeholder="Search process or patient"></div></div><div id="processTable"></div>`)}</div><div>${card("Input guide","Use consistent units for all process times.",`<div class="algorithm-explainer"><div class="explain-row"><span class="explain-num">1</span><div><b>Arrival time</b><p>When the process enters the treatment queue.</p></div></div><div class="explain-row"><span class="explain-num">2</span><div><b>Burst time</b><p>Estimated processing duration; must be greater than zero.</p></div></div><div class="explain-row"><span class="explain-num">3</span><div><b>Priority</b><p>Agree with Vansh whether lower or higher numbers mean higher priority.</p></div></div></div><div class="inline-note"><b>Important</b><span>Priority direction must match the Java scheduling implementation; the UI should not silently assume it.</span></div>`)}</div></div>`;
 }else if(page==="scheduler"){
  html=intro("OS MODULE · TREATMENT SCHEDULING","Treatment scheduling simulator","Select an OS scheduling algorithm for queued patient treatment processes and review the resulting metrics.");
  html+=`<div class="module-grid"><div>${card("Run configuration","Uses the patient treatment process inputs from the Treatment processes module.",`<div class="algorithm-tabs" id="algorithmTabs">${["FCFS","SJF","Priority","Round Robin"].map(a=>`<button data-algorithm="${a}" class="${a===selectedAlgorithm?"active":""}">${a}</button>`).join("")}</div><div class="field-grid" id="quantumField" style="${selectedAlgorithm==="Round Robin"?"":"display:none"}">${field("Time quantum","quantum","number","e.g. 2",'min="1" value="2"')}</div><div class="inline-note"><b>Integration boundary</b><span>This preview uses client-side calculations for demonstration. Replace the run handler with Vansh’s confirmed Java API and render its returned scheduling_runs and scheduling_results.</span></div><div class="form-actions"><button class="button button-primary" data-action="run-scheduler">▶ Run scheduling</button></div>`)}</div><div>${card("Scheduling definitions","Metrics shown below are computed from the selected preview algorithm.",`<div class="algorithm-explainer"><div class="explain-row"><span class="explain-num">CT</span><div><b>Completion time</b><p>Clock time when a process finishes.</p></div></div><div class="explain-row"><span class="explain-num">TAT</span><div><b>Turnaround time</b><p>Completion time − arrival time.</p></div></div><div class="explain-row"><span class="explain-num">WT</span><div><b>Waiting time</b><p>Turnaround time − burst time.</p></div></div></div>`)}</div></div><div id="scheduleOutput">${renderScheduleOutput()}</div>`;
 }else if(page==="allocation"){
  html=intro("OS MODULE · DEADLOCK AVOIDANCE","Banker’s algorithm","Safety checks use allocated, maximum and available resource vectors for a consistent snapshot.","Calculate safety state","check-banker-input");
  html+=`<div class="module-grid"><div>${card("Banker’s Algorithm input","Edit Allocation, Maximum Demand and Available vectors. Need is calculated as Maximum − Allocation.",`<div class="banker-inputs"><div class="banker-available"><b>Available resources</b>${bankerDefaults.resources.map((r,i)=>`<div class="field"><label for="bank-av-${i}">${r}</label><input id="bank-av-${i}" type="number" min="0" step="1" value="${bankerDefaults.available[i]}"></div>`).join("")}</div><div class="data-table-wrap"><table class="banker-table"><thead><tr><th>Patient</th><th>Allocation (B,D,M)</th><th>Maximum (B,D,M)</th></tr></thead><tbody>${bankerDefaults.patients.map((p,i)=>`<tr><td><b>${p.name}</b></td><td>${p.allocation.map((v,j)=>`<label class="sr-only" for="bank-a-${i}-${j}">${p.name} allocation ${bankerDefaults.resources[j]}</label><input id="bank-a-${i}-${j}" aria-label="${p.name} allocation ${bankerDefaults.resources[j]}" type="number" min="0" step="1" value="${v}">`).join("")}</td><td>${p.maximum.map((v,j)=>`<label class="sr-only" for="bank-m-${i}-${j}">${p.name} maximum ${bankerDefaults.resources[j]}</label><input id="bank-m-${i}-${j}" aria-label="${p.name} maximum ${bankerDefaults.resources[j]}" type="number" min="0" step="1" value="${v}">`).join("")}</td></tr>`).join("")}</tbody></table></div></div><div class="form-actions"><button class="button button-primary" data-action="check-banker-input">Calculate safety state</button></div><div class="inline-note"><b>Resource order</b><span>B = Beds · D = Doctors · M = Monitoring Devices. These are editable example values for the UI preview, not values retrieved from Java.</span></div>`)}</div><div>${card("Safety result","The safety state is calculated from the matrix currently entered above.",`<div id="bankerInputResult"><div class="safe-banner safe"><span class="big-check">?</span><div><b>Not checked yet</b><span>Enter the snapshot and calculate to show SAFE or UNSAFE.</span></div></div></div><div class="algorithm-explainer"><div class="explain-row"><span class="explain-num">1</span><div><b>Need = Maximum − Allocation</b><p>Remaining resources each patient may still request.</p></div></div><div class="explain-row"><span class="explain-num">2</span><div><b>Find a safe sequence</b><p>Find an unfinished patient whose Need can be met by Available, then release its allocation back into Work.</p></div></div></div>`)}</div></div><div class="module-grid equal"><div>${card("Existing allocation records","Current sample allocation rows from the local DBMS preview.",`<div id="allocationMatrix"></div>`)}</div><div>${card("OS concepts for viva","Short explanations for the evaluation.",`<div class="algorithm-explainer"><div class="explain-row"><span class="explain-num">FCFS</span><div><b>First Come, First Served</b><p>Treatment tasks execute in arrival order. It is simple but a long task can make others wait.</p></div></div><div class="explain-row"><span class="explain-num">P</span><div><b>Priority Scheduling</b><p>The scheduler chooses the highest-priority task. In this project prompt, priority 1 means highest priority.</p></div></div><div class="explain-row"><span class="explain-num">RA</span><div><b>Resource allocation</b><p>Assigns limited beds, doctors and monitoring devices to treatment tasks and tracks release.</p></div></div><div class="explain-row"><span class="explain-num">BA</span><div><b>Banker’s Algorithm</b><p>Checks whether resources can be allocated in an order that lets every process finish safely.</p></div></div></div>`)}</div></div>`;
 }else if(page==="history"){
  html=intro("OS OUTPUT · AUDIT TRAIL","Run history","Scheduling run metadata and process-level results should be persisted and loaded from the Java API.");
  html+=`<div class="module-grid equal">${card("Scheduling runs","Maps to `scheduling_runs`.",`<div id="runsTable"></div>`)}${card("Latest result details","Maps to `scheduling_results`; a preemptive Gantt chart may require multiple execution-segment rows per process.",`<div id="historyResults"></div>`)}</div><div class="inline-note"><b>Schema note</b><span>If scheduling_results stores only one row per process, it can store CT/WT/TAT but cannot accurately represent every segment of a preemptive schedule. Add a scheduling_segments table or equivalent if needed.</span></div>`;
 }
 host.innerHTML=html;wireModule(page);
}
function renderPatients(){
 const q=($("patientSearch")?.value||"").toLowerCase();const rows=state.patients.filter(p=>[p.patient_name,p.phone,p.address,String(p.patient_id)].join(" ").toLowerCase().includes(q));
 if($("patientTableCount"))$("patientTableCount").textContent=`${rows.length} record${rows.length===1?"":"s"}`;
 $("patientsTable").innerHTML=table(["PATIENT","AGE","GENDER","PHONE","ADDRESS"],rows.map(p=>`<tr><td>${personCell(p.patient_name,`Patient ID ${p.patient_id}`)}</td><td>${p.age}</td><td>${escapeHtml(p.gender||"—")}</td><td>${escapeHtml(p.phone||"—")}</td><td>${escapeHtml(p.address||"—")}</td></tr>`));
}
function renderDoctors(){
 const q=($("doctorSearch")?.value||"").toLowerCase();const rows=state.doctors.filter(d=>(d.doctor_name+" "+d.specialization+" "+d.phone).toLowerCase().includes(q));
 $("doctorsTable").innerHTML=table(["DOCTOR","SPECIALIZATION","PHONE","STATUS"],rows.map(d=>`<tr><td>${personCell(d.doctor_name,`Doctor ID ${d.doctor_id}`,"violet")}</td><td>${escapeHtml(d.specialization)}</td><td>${escapeHtml(d.phone||"—")}</td><td>${statusTag("Listed")}</td></tr>`));
}
function renderAppointments(){
 const q=($("appointmentSearch")?.value||"").toLowerCase(),filter=$("appointmentFilter")?.value||"All";
 const rows=state.appointments.filter(a=>(filter==="All"||a.appointment_status===filter)&&(patientName(a.patient_id)+" "+doctorName(a.doctor_id)+" "+a.appointment_status).toLowerCase().includes(q));
 $("appointmentsTable").innerHTML=table(["APPOINTMENT","PATIENT","DOCTOR","DATE & TIME","STATUS"],rows.map((a,i)=>`<tr><td><b>#APT-${String(a.appointment_id).padStart(3,"0")}</b></td><td>${personCell(patientName(a.patient_id),`Patient ID ${a.patient_id}`,["cyan","violet","green"][i%3])}</td><td>${escapeHtml(doctorName(a.doctor_id))}</td><td>${escapeHtml(dateTime(a.appointment_date,a.appointment_time))}</td><td>${statusTag(a.appointment_status)}</td></tr>`));
}
function renderBeds(){
 $("bedsTable").innerHTML=table(["BED","WARD","STATUS"],state.beds.map(b=>`<tr><td><b>${escapeHtml(b.bed_number)}</b><small class="person-id">Bed ID ${b.bed_id}</small></td><td>${escapeHtml(b.ward)}</td><td>${statusTag(b.bed_status)}</td></tr>`));
 $("resourcesTable").innerHTML=table(["RESOURCE","AVAILABLE","TOTAL","STOCK"],state.resources.map(r=>`<tr><td><b>${escapeHtml(r.resource_name)}</b><small class="person-id">Resource ID ${r.resource_id}</small></td><td>${r.available_quantity}</td><td>${r.total_quantity}</td><td>${statusTag(r.available_quantity>0?"Available":"Unavailable")}</td></tr>`));
}
function renderProcesses(){
 const q=($("processSearch")?.value||"").toLowerCase();const rows=state.treatment_processes.filter(p=>(p.process_name+" "+patientName(p.patient_id)+" "+p.process_id).toLowerCase().includes(q));
 $("processTable").innerHTML=table(["PROCESS","PATIENT","ARRIVAL","BURST","PRIORITY","STATUS"],rows.map(p=>`<tr><td><b>${escapeHtml(p.process_name)}</b><small class="person-id">P-${p.process_id}</small></td><td>${escapeHtml(patientName(p.patient_id))}</td><td>${p.arrival_time}</td><td>${p.burst_time}</td><td><b>${p.priority}</b></td><td>${statusTag(p.process_status)}</td></tr>`));
}
function calcSchedule(algorithm,quantum=2){
 const pending=state.treatment_processes.filter(p=>p.process_status!=="Completed").map(p=>({...p,remaining:Number(p.burst_time),start:null}));
 if(!pending.length)return {algorithm,rows:[],segments:[],avgWait:0,avgTat:0};
 const rows=[],segments=[];let time=0,done=0,guard=0;
 if(algorithm==="FCFS"||algorithm==="SJF"||algorithm==="Priority"){
  let available=[];
  while(done<pending.length&&guard++<10000){
   available=pending.filter(p=>!p._done&&p.arrival_time<=time);
   if(!available.length){time=Math.min(...pending.filter(p=>!p._done).map(p=>Number(p.arrival_time)));continue;}
   available.sort((a,b)=>algorithm==="FCFS"?(a.arrival_time-b.arrival_time||a.process_id-b.process_id):algorithm==="SJF"?(a.burst_time-b.burst_time||a.arrival_time-b.arrival_time||a.process_id-b.process_id):(a.priority-b.priority||a.arrival_time-b.arrival_time||a.process_id-b.process_id));
   const p=available[0],start=time;time+=Number(p.burst_time);p._done=true;done++;segments.push({process_id:p.process_id,start,end:time});rows.push({process_id:p.process_id,process_name:p.process_name,arrival_time:Number(p.arrival_time),burst_time:Number(p.burst_time),priority:Number(p.priority),start_time:start,completion_time:time,turnaround_time:time-Number(p.arrival_time),waiting_time:time-Number(p.arrival_time)-Number(p.burst_time)});
  }
 }else{
  const queue=[];let added=new Set();
  while(done<pending.length&&guard++<10000){
   pending.filter(p=>!added.has(p.process_id)&&Number(p.arrival_time)<=time).sort((a,b)=>a.arrival_time-b.arrival_time||a.process_id-b.process_id).forEach(p=>{queue.push(p);added.add(p.process_id);});
   if(!queue.length){const next=Math.min(...pending.filter(p=>p.remaining>0).map(p=>Number(p.arrival_time)));time=Math.max(time,next);continue;}
   const p=queue.shift(),start=time,run=Math.min(Number(quantum),p.remaining);time+=run;p.remaining-=run;segments.push({process_id:p.process_id,start,end:time});
   pending.filter(x=>!added.has(x.process_id)&&Number(x.arrival_time)<=time).sort((a,b)=>a.arrival_time-b.arrival_time||a.process_id-b.process_id).forEach(x=>{queue.push(x);added.add(x.process_id);});
   if(p.remaining>0)queue.push(p);else{done++;rows.push({process_id:p.process_id,process_name:p.process_name,arrival_time:Number(p.arrival_time),burst_time:Number(p.burst_time),priority:Number(p.priority),start_time:null,completion_time:time,turnaround_time:time-Number(p.arrival_time),waiting_time:time-Number(p.arrival_time)-Number(p.burst_time)});}
  }
 }
 rows.sort((a,b)=>a.process_id-b.process_id);
 const avgWait=rows.reduce((s,r)=>s+r.waiting_time,0)/(rows.length||1),avgTat=rows.reduce((s,r)=>s+r.turnaround_time,0)/(rows.length||1);
 return {algorithm,rows,segments,avgWait,avgTat,quantum:algorithm==="Round Robin"?Number(quantum):null,run_id:state.scheduling_runs.length+1};
}
function renderScheduleOutput(){
 if(!lastSchedule)return `<article class="module-card"><h3>Scheduling output</h3><p class="muted">Run an algorithm to calculate a preview of completion time, waiting time and turnaround time.</p><div class="inline-note"><b>No run yet</b><span>Results will appear here. This is not yet connected to the OS Java module.</span></div></article>`;
 const s=lastSchedule,max=Math.max(1,...s.segments.map(x=>x.end));
 const gantt=`<div class="gantt-result"><div class="gantt-labels"><span>Process</span><span>Execution timeline <small>(preview units)</small></span></div>${[...new Set(s.segments.map(x=>x.process_id))].map(pid=>`<div class="gantt-row"><span class="process-label">P-${pid}</span><div class="gantt-track">${s.segments.filter(seg=>seg.process_id===pid).map((seg,i)=>`<i class="gantt-segment ${["seg-blue","seg-lilac","seg-green","seg-amber"][pid%4]}" style="left:${seg.start/max*100}%;width:${Math.max(1,(seg.end-seg.start)/max*100)}%" title="${seg.start}–${seg.end}">${seg.start}–${seg.end}</i>`).join("")}</div></div>`).join("")}<div class="gantt-ticks"><span>0</span><span>${(max*.25).toFixed(1)}</span><span>${(max*.5).toFixed(1)}</span><span>${(max*.75).toFixed(1)}</span><span>${max}</span></div></div>`;
 return `<article class="module-card"><div class="panel-title-row"><div><h3>Run #${s.run_id} · ${escapeHtml(s.algorithm)}</h3><p class="muted">Client-side preview; not saved to scheduling_runs.</p></div>${statusTag("Preview")}</div><div class="result-cards"><div class="result-card"><span>Average waiting time</span><b>${s.avgWait.toFixed(2)}</b></div><div class="result-card"><span>Average turnaround</span><b>${s.avgTat.toFixed(2)}</b></div><div class="result-card"><span>Processes completed</span><b>${s.rows.length}</b></div></div>${gantt}<div style="margin-top:19px">${table(["PROCESS","ARRIVAL","BURST","PRIORITY","COMPLETION (CT)","TURNAROUND (TAT)","WAITING (WT)"],s.rows.map(r=>`<tr><td><b>P-${r.process_id}</b><small class="person-id">${escapeHtml(r.process_name)}</small></td><td>${r.arrival_time}</td><td>${r.burst_time}</td><td>${r.priority}</td><td>${r.completion_time}</td><td>${r.turnaround_time}</td><td>${r.waiting_time}</td></tr>`))}</div></article>`;
}
function allocationMatrixHtml(){
 const rs=state.resources;const rows=state.resource_allocations;
 return table(["PROCESS","PATIENT","RESOURCE","ALLOCATED","MAXIMUM","NEED"],rows.map(a=>`<tr><td><b>P-${a.process_id}</b></td><td>${escapeHtml(patientName(state.treatment_processes.find(p=>p.process_id===a.process_id)?.patient_id||0))}</td><td>${escapeHtml(rs.find(r=>r.resource_id===a.resource_id)?.resource_name||"Resource")}</td><td>${a.allocated_quantity}</td><td>${a.maximum_quantity}</td><td>${Math.max(0,a.maximum_quantity-a.allocated_quantity)}</td></tr>`));
}
function runSafetyPreview(){
 const available=state.resources.map(r=>Number(r.available_quantity));
 const allocations=state.resource_allocations;
 const processIds=[...new Set(allocations.map(a=>a.process_id))];
 // Demo model: every process's remaining need is calculated from allocation rows.
 // Since the database currently stores quantities per resource row, absent resource rows are treated as zero only for this preview.
 const work=[...available],finish=Object.fromEntries(processIds.map(id=>[id,false])),sequence=[];
 const needByProcess={};const allocByProcess={};
 processIds.forEach(pid=>{needByProcess[pid]=state.resources.map(r=>{const a=allocations.find(x=>x.process_id===pid&&x.resource_id===r.resource_id);return a?Math.max(0,a.maximum_quantity-a.allocated_quantity):0;});allocByProcess[pid]=state.resources.map(r=>allocations.find(x=>x.process_id===pid&&x.resource_id===r.resource_id)?.allocated_quantity||0);});
 let changed=true,loops=0;
 while(changed&&loops++<processIds.length+2){changed=false;for(const pid of processIds){if(finish[pid])continue;const need=needByProcess[pid];if(need.every((n,i)=>n<=work[i])){work.forEach((v,i)=>work[i]+=allocByProcess[pid][i]);finish[pid]=true;sequence.push(pid);changed=true;}}}
 lastSafety={safe:sequence.length===processIds.length,sequence,work,processIds};
 return lastSafety;
}
function renderSafetyResult(){
 if(!lastSafety)return `<div class="safe-banner safe"><span class="big-check">?</span><div><b>Not checked yet</b><span>Run the safety check to show a preview result.</span></div></div><p class="muted">This uses the current demo resource quantities. The official result must come from Vansh’s OS module.</p>`;
 if(lastSafety.safe)return `<div class="safe-banner safe"><span class="big-check">✓</span><div><b>Safe state in preview</b><span>All included processes could finish under the current simplified snapshot.</span></div></div><b style="font-size:10px;color:#58796d">Safe sequence</b><div class="safe-sequence">${lastSafety.sequence.map((p,i)=>`${i?'<i>→</i>':""}<span>P-${p}</span>`).join("")}</div><div class="inline-note"><b>Not authoritative</b><span>Confirm complete Allocation, Maximum and Available vectors with the backend before using this as a project result.</span></div>`;
 return `<div class="safe-banner unsafe"><span class="big-check">!</span><div><b>Safe sequence not found</b><span>The simplified preview could not complete every process.</span></div></div><p class="muted">Inspect Available, Allocation and Need values, then rerun with a consistent resource snapshot.</p>`;
}
function changeOSResource(allocate){
 const key=$("f-osResource")?.value, qty=Number($("f-osQuantity")?.value), r=osInventory.find(x=>x.key===key);
 if(!r||!Number.isInteger(qty)||qty<=0){toast("Enter a positive whole-number quantity.");return;}
 const allocated=osResourceLedger.filter(x=>x.key===key).reduce((sum,x)=>sum+x.quantity,0);
 if(allocate){if(qty>r.available){toast(`Cannot allocate ${qty}; only ${r.available} ${r.name.toLowerCase()} available.`);return;}r.available-=qty;osResourceLedger.push({key,name:r.name,quantity:qty,at:new Date().toLocaleTimeString("en-IN",{hour:"2-digit",minute:"2-digit"})});}
 else {if(qty>allocated){toast(`Cannot release ${qty}; only ${allocated} ${r.name.toLowerCase()} allocated in this preview.`);return;}let left=qty;for(let i=osResourceLedger.length-1;i>=0&&left>0;i--){const x=osResourceLedger[i];if(x.key!==key)continue;const take=Math.min(left,x.quantity);x.quantity-=take;left-=take;if(x.quantity===0)osResourceLedger.splice(i,1);}r.available=Math.min(r.total,r.available+qty);}
 osInventory.forEach(x=>{const el=$("os-available-"+x.key);if(el)el.innerHTML=`${x.available} <small>/ ${x.total}</small>`;});
 renderOSResourceLedger();const msg=$("osResourceMessage");if(msg)msg.innerHTML=`<b>${allocate?"Allocation successful":"Resource released"}</b><span>${qty} ${r.name.toLowerCase()} ${allocate?"allocated":"released"}. ${r.available} currently available. Local preview only; not sent to Java/MySQL.</span>`;
 toast(`${r.name}: ${allocate?"allocated":"released"} ${qty}. Local preview only.`);
}
function renderOSResourceLedger(){const host=$("osResourceLedger");if(!host)return;host.innerHTML=table(["RESOURCE","ALLOCATED","ACTION"],osResourceLedger.map(x=>`<tr><td><b>${escapeHtml(x.name)}</b></td><td>${x.quantity}</td><td>${escapeHtml(x.at)}</td></tr>`),"No OS resources allocated yet.");}
function checkBankerInput(){
 const n=bankerDefaults.patients.length,m=bankerDefaults.resources.length;
 const read=id=>{const el=$(id),v=Number(el?.value);if(!el||!Number.isInteger(v)||v<0)throw new Error("All matrix values must be non-negative whole numbers.");return v;};
 try{
  const available=bankerDefaults.resources.map((_,j)=>read(`bank-av-${j}`));
  const alloc=bankerDefaults.patients.map((_,i)=>bankerDefaults.resources.map((_,j)=>read(`bank-a-${i}-${j}`)));
  const max=bankerDefaults.patients.map((_,i)=>bankerDefaults.resources.map((_,j)=>read(`bank-m-${i}-${j}`)));
  for(let i=0;i<n;i++)for(let j=0;j<m;j++)if(alloc[i][j]>max[i][j])throw new Error(`${bankerDefaults.patients[i].name}: Allocation cannot exceed Maximum Demand for ${bankerDefaults.resources[j]}.`);
  let work=[...available],finish=Array(n).fill(false),seq=[];let progress=true;
  while(progress){progress=false;for(let i=0;i<n;i++){if(finish[i])continue;const need=max[i].map((v,j)=>v-alloc[i][j]);if(need.every((v,j)=>v<=work[j])){work=work.map((v,j)=>v+alloc[i][j]);finish[i]=true;seq.push(i);progress=true;}}}
  const safe=finish.every(Boolean),host=$("bankerInputResult");
  if(safe)host.innerHTML=`<div class="safe-banner safe"><span class="big-check">✓</span><div><b>SAFE state</b><span>A safe sequence was found for the values entered.</span></div></div><b style="font-size:10px;color:#58796d">Safe sequence</b><div class="safe-sequence">${seq.map((i,k)=>`${k?'<i>→</i>':""}<span>${bankerDefaults.patients[i].name}</span>`).join("")}</div>`;
  else host.innerHTML=`<div class="safe-banner unsafe"><span class="big-check">!</span><div><b>UNSAFE state</b><span>No safe sequence was found for the entered Allocation, Maximum Demand and Available values.</span></div></div>`;
  toast(safe?"Banker preview: SAFE state.":"Banker preview: UNSAFE state.");
 }catch(e){toast(e.message);}
}
function renderHistory(){
 $("runsTable").innerHTML=table(["RUN","ALGORITHM","QUANTUM","EXECUTED AT"],state.scheduling_runs.map(r=>`<tr><td><b>#RUN-${r.run_id}</b></td><td>${escapeHtml(r.algorithm)}</td><td>${r.time_quantum??"—"}</td><td>${escapeHtml(r.executed_at)}</td></tr>`));
 $("historyResults").innerHTML=lastSchedule?table(["PROCESS","CT","TAT","WT"],lastSchedule.rows.map(r=>`<tr><td>P-${r.process_id}</td><td>${r.completion_time}</td><td>${r.turnaround_time}</td><td>${r.waiting_time}</td></tr>`)):`<div class="inline-note"><b>No scheduling output saved</b><span>Local preview runs are not persisted. The final screen should load persisted results from the backend.</span></div>`;
}
function wireModule(page){
 if(page==="patients"){renderPatients();$("patientSearch").addEventListener("input",renderPatients);}
 if(page==="doctors"){renderDoctors();$("doctorSearch").addEventListener("input",renderDoctors);}
 if(page==="appointments"){renderAppointments();$("appointmentSearch").addEventListener("input",renderAppointments);$("appointmentFilter").addEventListener("change",renderAppointments);}
 if(page==="resources"){
  renderBeds();
  const f=$("osResourceForm"); if(f) f.addEventListener("submit",e=>{e.preventDefault();changeOSResource(true);});
  renderOSResourceLedger();
 }
 if(page==="treatments"){renderProcesses();$("processSearch").addEventListener("input",renderProcesses);}
 if(page==="scheduler"){document.querySelectorAll("[data-algorithm]").forEach(b=>b.addEventListener("click",()=>{selectedAlgorithm=b.dataset.algorithm;renderModule("scheduler");}));}
 if(page==="allocation"){$("allocationMatrix").innerHTML=allocationMatrixHtml();}
 if(page==="history")renderHistory();
}
function openModal(kind){
 const dialog=$("recordDialog"),fields=$("modalFields");let title="",subtitle="",html="";
 const patientOptions=state.patients.map(p=>({value:p.patient_id,label:`${p.patient_name} · #${p.patient_id}`}));
 const doctorOptions=state.doctors.map(d=>({value:d.doctor_id,label:`${d.doctor_name} · ${d.specialization}`}));
 const appointmentOptions=state.appointments.map(a=>({value:a.appointment_id,label:`#${a.appointment_id} · ${patientName(a.patient_id)}`}));
 if(kind==="new-patient"){title="Register patient";subtitle="Create a patient row in this local preview.";html=`<div class="field-grid">${field("Full name","patient_name","text","e.g. Priya Sharma","required")}${field("Age","age","number","Age",'min="0" max="120" required')}${selectField("Gender","gender",[{value:"Male",label:"Male"},{value:"Female",label:"Female"},{value:"Other",label:"Other"}])}${field("Phone","phone","tel","10-digit contact")}${field("Address","address","text","City / address")}</div>`;}
 if(kind==="new-doctor"){title="Add doctor";subtitle="Create a doctor row in this local preview.";html=`<div class="field-grid">${field("Doctor name","doctor_name","text","e.g. Dr. Priya Sharma","required")}${field("Specialization","specialization","text","e.g. Cardiologist","required")}${field("Phone","phone","tel","Contact number")}</div>`;}
 if(kind==="new-appointment"){title="New appointment";subtitle="Select existing patient and doctor foreign keys.";html=`<div class="field-grid">${selectField("Patient","patient_id",patientOptions)}${selectField("Doctor","doctor_id",doctorOptions)}${field("Appointment date","appointment_date","date","","required")}${field("Appointment time","appointment_time","time","","required")}${selectField("Status","appointment_status",[{value:"Scheduled",label:"Scheduled"},{value:"Completed",label:"Completed"},{value:"Cancelled",label:"Cancelled"}])}</div>`;}
 if(kind==="new-resource"){title="Add resource";subtitle="Add a resource inventory row to the local preview.";html=`<div class="field-grid">${field("Resource name","resource_name","text","e.g. Oxygen Cylinder","required")}${field("Available quantity","available_quantity","number","0",'min="0" value="0" required')}${field("Total quantity","total_quantity","number","0",'min="0" value="0" required')}</div>`;}
 if(kind==="new-process"){title="Add treatment process";subtitle="Process inputs used by CPU scheduling.";html=`<div class="field-grid">${selectField("Patient","patient_id",patientOptions)}${selectField("Appointment (optional)","appointment_id",[{value:"",label:"No appointment"},...appointmentOptions])}${field("Process name","process_name","text","e.g. Diagnostic scan","required")}${field("Arrival time","arrival_time","number","0",'min="0" value="0" required')}${field("Burst time","burst_time","number","1",'min="1" value="1" required')}${field("Priority","priority","number","1",'min="1" value="1" required')}</div>`;}
 $("modalKicker").textContent=kind==="new-process"?"OS INPUT": "DBMS · NEW RECORD";$("modalTitle").textContent=title;$("modalSubtitle").textContent=subtitle;fields.innerHTML=html;$("saveModal").textContent=kind==="new-process"?"Add process":"Add record";$("recordForm").dataset.kind=kind;dialog.showModal();
}
function submitModal(e){
 e.preventDefault();const kind=e.currentTarget.dataset.kind,fd=new FormData(e.currentTarget),v=Object.fromEntries(fd.entries());
 const num=x=>Number(v[x]);
 if(kind==="new-patient"){
  if(!v.patient_name?.trim()||v.age===""||num("age")<0)return toast("Enter a valid patient name and age.");
  if(v.phone&&state.patients.some(p=>p.phone===v.phone))return toast("That phone number already exists in the preview.");
  state.patients.push({patient_id:state.nextIds.patients++,patient_name:v.patient_name.trim(),age:num("age"),gender:v.gender,phone:v.phone,address:v.address});
 }else if(kind==="new-doctor"){
  if(!v.doctor_name?.trim()||!v.specialization?.trim())return toast("Doctor name and specialization are required.");
  if(v.phone&&state.doctors.some(d=>d.phone===v.phone))return toast("That phone number already exists in the preview.");
  state.doctors.push({doctor_id:state.nextIds.doctors++,doctor_name:v.doctor_name.trim(),specialization:v.specialization.trim(),phone:v.phone});
 }else if(kind==="new-appointment"){
  if(!v.appointment_date||!v.appointment_time)return toast("Select appointment date and time.");
  state.appointments.push({appointment_id:state.nextIds.appointments++,patient_id:num("patient_id"),doctor_id:num("doctor_id"),appointment_date:v.appointment_date,appointment_time:v.appointment_time,appointment_status:v.appointment_status});
 }else if(kind==="new-resource"){
  if(!v.resource_name?.trim()||num("available_quantity")<0||num("total_quantity")<0||num("available_quantity")>num("total_quantity"))return toast("Quantities must be non-negative and available cannot exceed total.");
  state.resources.push({resource_id:state.nextIds.resources++,resource_name:v.resource_name.trim(),available_quantity:num("available_quantity"),total_quantity:num("total_quantity")});
 }else if(kind==="new-process"){
  if(!v.process_name?.trim()||num("arrival_time")<0||num("burst_time")<=0||num("priority")<1)return toast("Arrival must be ≥ 0, burst > 0 and priority ≥ 1.");
  state.treatment_processes.push({process_id:state.nextIds.treatment_processes++,patient_id:num("patient_id"),appointment_id:v.appointment_id?num("appointment_id"):null,process_name:v.process_name.trim(),arrival_time:num("arrival_time"),burst_time:num("burst_time"),priority:num("priority"),process_status:"Queued"});
 }
 $("recordDialog").close();e.currentTarget.reset();refreshOverview();renderModule(state.page);toast("Added to local preview only — not saved to MySQL.");
}
function runScheduler(){
 const quantum=Math.max(1,Number($("f-quantum")?.value||2));
 if(!state.treatment_processes.length)return toast("Add at least one treatment process first.");
 lastSchedule=calcSchedule(selectedAlgorithm,quantum);
 state.scheduling_runs.push({run_id:state.scheduling_runs.length+1,algorithm:selectedAlgorithm,time_quantum:selectedAlgorithm==="Round Robin"?quantum:null,executed_at:"Local preview"});
 // Local-only results for display. Not written into scheduling_results.
 $("scheduleOutput").innerHTML=renderScheduleOutput();refreshOverview();
 toast(`${selectedAlgorithm} preview calculated. Results have not been sent to the Java backend.`);
}
function checkSafety(){
 lastSafety=runSafetyPreview();$("safetyResult").innerHTML=renderSafetyResult();toast("Safety preview calculated; confirm with the OS module before reporting the result.");
}
function action(kind){
 if(kind.startsWith("new-"))return openModal(kind);
 if(kind==="run-scheduler")return runScheduler();
 if(kind==="check-safety")return checkSafety();
 if(kind==="release-os-resource")return changeOSResource(false);
 if(kind==="check-banker-input")return checkBankerInput();
}
document.addEventListener("click",e=>{
 const nav=e.target.closest("[data-page]");if(nav){go(nav.dataset.page);return;}
 const goto=e.target.closest("[data-goto]");if(goto){go(goto.dataset.goto);return;}
 const act=e.target.closest("[data-action]");if(act){action(act.dataset.action);return;}
 const alg=e.target.closest("[data-algorithm]");if(alg){selectedAlgorithm=alg.dataset.algorithm;renderModule("scheduler");}
});
$("primaryAction").addEventListener("click",()=>{const p=state.page;if(p==="overview"||p==="patients")openModal("new-patient");else if(p==="doctors")openModal("new-doctor");else if(p==="appointments")openModal("new-appointment");else if(p==="resources")openModal("new-resource");else if(p==="treatments")openModal("new-process");else if(p==="scheduler")runScheduler();else if(p==="allocation")checkSafety();else if(p==="history"){renderModule("history");toast("History refreshed from the local preview.");}});
$("recordForm").addEventListener("submit",submitModal);$("closeModal").addEventListener("click",()=>$("recordDialog").close());$("cancelModal").addEventListener("click",()=>$("recordDialog").close());
$("mobileMenu").addEventListener("click",()=>$("sidebar").classList.toggle("open"));
$("refreshButton").addEventListener("click",()=>{refreshOverview();if(state.page!=="overview")renderModule(state.page);toast("Local preview refreshed.");});
$("overviewSearch").addEventListener("input",refreshOverview);
$("dismissNotice").addEventListener("click",()=>$("prototypeNotice").classList.add("hidden"));
$("helpButton").addEventListener("click",()=>toast("Integration pending: agree on Java API routes and JSON payloads with Rohan and Vansh before enabling network requests."));
$("todayLabel").textContent=new Intl.DateTimeFormat("en-IN",{day:"2-digit",month:"short",year:"numeric"}).format(new Date()).toUpperCase();
updateGreeting();
refreshOverview();
