# Smart Hospital Resource and Treatment Management System
### Integrated Operating Systems (OS) & Database Management Systems (DBMS) B.Tech Project

A college project developed for the **3rd Year B.Tech Computer Science & Engineering** syllabus, integrating:
1. **Operating Systems (OS)**: Process Scheduling (Priority, Round Robin, FCFS, SJF) & Deadlock Avoidance (Banker's Algorithm).
2. **Database Management Systems (DBMS)**: Relational schema in MySQL, 1:1 Entity Cardinality, PreparedStatements & ACID Transactions.
3. **Frontend Dashboard**: Responsive, clean web UI communicating with the Java backend via REST API.

---

## 1. Key 3rd Year Upgrades

### A. Dedicated 1:1 Bed-Patient Allocation
In real hospital informatics, an admitted inpatient is assigned to **exactly one dedicated bed**, and no two patients can occupy the same bed at the same time:
- **Relational Integrity**: Enforced via foreign key constraints with `UNIQUE(bed_id)` and `UNIQUE(patient_id)`.
- **Dynamic Bed Assignment**: When registering an inpatient, the system auto-assigns an available bed (or lets the user choose an available bed).
- **Discharge / Bed Release**: Discharging a patient frees up the bed (`bed_status = 'Available'`, `patient_id = NULL`), immediately allowing new admissions.
- **Over-admission Prevention**: If all beds are occupied, registration alerts the user that capacity has been reached.

### B. Workload-Specific OS Scheduling ("Different Algorithm for Different Work")
Different hospital computational workloads have fundamentally different runtime characteristics. The system intelligently dispatches software tasks to the optimal OS algorithm:

| Workload Category | Example Hospital Tasks | Assigned OS Algorithm | 3rd Year Academic Rationale (Viva Answer) |
|---|---|---|---|
| **🚨 Critical Emergency** | ICU Telemetry Alarm, Oxygen Desaturation Alert | **Priority Scheduling (1 = Highest)** | Critical medical alerts have zero latency tolerance and must preempt or jump ahead of routine tasks. |
| **💬 Interactive OPD** | Doctor Prescription Order, OPD Consultation Update | **Round Robin (RR, $q=2$)** | Time-sharing across active clinicians ensures fair CPU responsiveness without starving any doctor's session. |
| **📊 Batch Reports** | Nightly Billing Generation, Insurance Claim Batch | **FCFS (First-Come, First-Served)** | Non-interactive background bulk tasks have no latency deadlines and run with minimal context-switch overhead. |
| **🧪 Rapid Diagnostics** | Blood Glucose Rapid Test, Viral Antigen Screen | **Shortest Job First (SJF)** | SJF is mathematically provable to minimize the average waiting time ($\sum WT / n$) across quick diagnostic screenings. |
| **🏥 Resource Allocation** | Shared Ventilators, ICU Monitoring Suites | **Banker's Algorithm** | Deadlock avoidance algorithm ensuring that granted resources keep the hospital in a provably SAFE state. |

### C. Effortless One-Click Execution
- **Run without complex setup**: Just double-click `run.bat` or run `run.ps1`.
- **Fault-Tolerant Architecture**: Automatically connects to MySQL if running on port 3306; if MySQL is not yet active, seamlessly runs in an in-memory simulation mode so the project **never crashes** during a college presentation!

---

## 2. System Architecture

```
Frontend (HTML5 / CSS3 / JavaScript)
                 │
                 ▼  REST API (JSON over HTTP)
Java Backend Server (HospitalServer.java via com.sun.net.httpserver)
                 │
        ┌────────┴──────────────────────────┐
        ▼                                   ▼
Workload Dispatcher & OS Algorithms    JDBC Access Layer (HospitalDAO.java)
(Priority, RR, FCFS, SJF, Banker's)    (1:1 Bed Mapping & ACID Transactions)
(HospitalOSModule.java)                     │
                                            ▼
                                MySQL Database (model_db)
```

---

## 3. Quick Run Instructions

### Option 1: Double-Click Runner (Windows)
Double-click `run.bat` in the project folder. It will compile the source code and start the server at:
```
http://localhost:8080/
```

### Option 2: PowerShell
```powershell
Set-Location "C:\Users\HP\.gemini\antigravity\scratch\Integrated-Hospital-Management-System"
.\run.ps1
```

### Option 3: In VS Code (Press F5)
1. Open the project folder in VS Code.
2. Press `F5` (or go to **Run and Debug** and select **"Launch Hospital Server (Web UI + API)"**).
3. Open `http://localhost:8080/` in your browser.

### Option 4: Terminal CLI Mode (For Viva Examination)
If your professor asks to demonstrate OS algorithms in the terminal:
```bash
java -cp "lib/mysql-connector-j-8.4.0.jar;." HospitalOSModule
```

---

## 4. Database Setup (Optional for Permanent MySQL Persistence)

Ensure MySQL is running on port 3306, then execute:
```bash
mysql -u root -p < hospital_db.sql
```
*(Or double-click `setup_db.bat`)*.

---

## 5. Verification Test Suite

To run all 6 automated test suites verifying FCFS, SJF, Priority, Round Robin, Banker's Algorithm, and 1:1 Bed Allocation:
```bash
javac -cp "lib/mysql-connector-j-8.4.0.jar;." -d . HospitalTest.java
java -ea -cp "lib/mysql-connector-j-8.4.0.jar;." HospitalTest
```
All 6 test suites pass with 100% assertions.

---

## 6. Viva Preparation Guide (Common Professor Questions)

1. **Why does Priority 1 represent the highest priority?**
   - In Unix/Linux and real-time operating systems, lower numerical values represent higher urgency (e.g. POSIX scheduling priority levels and nice values).
2. **Why use Round Robin for interactive doctor consultations?**
   - Interactive outpatient services require fair responsiveness. Round Robin provides bounded response time and guarantees that no doctor is starved by a long process.
3. **Why is SJF optimal for rapid diagnostic tests?**
   - Shortest Job First is mathematically proven to give the minimum average waiting time among all non-preemptive scheduling algorithms.
4. **How does Banker's Algorithm prevent deadlock?**
   - It maintains Available, Allocation, and Maximum Demand vectors. Before granting resources, it runs Dijkstra's safety algorithm to simulate whether all remaining processes can still complete without deadlock.
5. **How is 1:1 Bed-Patient allocation enforced in DBMS?**
   - Using foreign key constraints on `beds.patient_id` with a `UNIQUE` constraint, and `patients.bed_id` with a `UNIQUE` constraint. This guarantees at the schema level that no two patients share a bed.
