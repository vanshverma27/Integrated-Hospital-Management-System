import java.util.*;

/**
 * HospitalOSModule - Operating System Scheduling and Resource Management.
 * 
 * B.Tech 3rd Year OS + DBMS Integrated Project
 * 
 * Core OS Concepts Demonstrated:
 * 1. Workload-Specific CPU Scheduling:
 *    - Critical Emergency Tasks     --> Priority Scheduling (Priority 1 = Highest)
 *    - Interactive OPD/Doctor Tasks  --> Round Robin (Fair CPU Time Sharing)
 *    - Batch Administration/Reports --> First-Come, First-Served (FCFS)
 *    - Rapid Lab Screenings         --> Shortest Job First (SJF - Minimum Average Wait)
 * 2. Deadlock Avoidance:
 *    - Banker's Safety Algorithm on finite hospital resources (Beds, Doctors, Devices)
 * 3. 1:1 Resource Mapping:
 *    - Dedicated Bed Allocation per Patient (No two patients share a bed)
 * 
 * Academic Note: These scheduling algorithms simulate hospital software and diagnostic tasks
 * (telemetry analysis, billing batches, doctor order entry). They are not a clinical triage substitute.
 */
public class HospitalOSModule {

    static Scanner sc = new Scanner(System.in);

    // Default OS Simulation Resource Types
    public static final String[] RESOURCE_NAMES = {
        "Beds", "Doctors", "Monitoring Devices"
    };

    // In-memory fallback tracking for CLI mode
    static int[] totalResources = {6, 5, 8};
    static int[] availableResources = {3, 5, 8};
    static int[] allocatedResources = {3, 0, 0};

    // =============================================================
    // DATA TRANSFER OBJECTS FOR OS CALCULATIONS
    // =============================================================

    public static class Task {
        public int processId;
        public String processName;
        public String workCategory; // Emergency, Interactive, Batch, Diagnostic
        public int arrivalTime;
        public int burstTime;
        public int priority;

        public Task(int processId, String processName, String workCategory, int arrivalTime, int burstTime, int priority) {
            this.processId = processId;
            this.processName = processName;
            this.workCategory = (workCategory != null && !workCategory.trim().isEmpty()) ? workCategory : "Emergency";
            this.arrivalTime = arrivalTime;
            this.burstTime = burstTime;
            this.priority = priority;
        }

        public Task(int processId, String processName, int arrivalTime, int burstTime, int priority) {
            this(processId, processName, "Emergency", arrivalTime, burstTime, priority);
        }
    }

    public static class GanttSegment {
        public int processId;
        public String processName;
        public int start;
        public int end;

        public GanttSegment(int processId, String processName, int start, int end) {
            this.processId = processId;
            this.processName = processName;
            this.start = start;
            this.end = end;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("process_id", processId);
            map.put("process_name", processName);
            map.put("start", start);
            map.put("end", end);
            return map;
        }
    }

    public static class ProcessMetric {
        public int processId;
        public String processName;
        public String workCategory;
        public int arrivalTime;
        public int burstTime;
        public int priority;
        public int startTime;
        public int completionTime;
        public int turnaroundTime;
        public int waitingTime;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("process_id", processId);
            map.put("process_name", processName);
            map.put("work_category", workCategory);
            map.put("arrival_time", arrivalTime);
            map.put("burst_time", burstTime);
            map.put("priority", priority);
            map.put("start_time", startTime);
            map.put("completion_time", completionTime);
            map.put("turnaround_time", turnaroundTime);
            map.put("waiting_time", waitingTime);
            return map;
        }
    }

    public static class ScheduleOutput {
        public String algorithm;
        public String workCategory = "All Tasks";
        public String rationale = "";
        public Integer timeQuantum;
        public List<ProcessMetric> rows = new ArrayList<>();
        public List<GanttSegment> segments = new ArrayList<>();
        public double avgWait;
        public double avgTat;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("algorithm", algorithm);
            map.put("work_category", workCategory);
            map.put("rationale", rationale);
            map.put("time_quantum", timeQuantum);
            List<Map<String, Object>> rowsList = new ArrayList<>();
            for (ProcessMetric pm : rows) rowsList.add(pm.toMap());
            map.put("rows", rowsList);
            List<Map<String, Object>> segList = new ArrayList<>();
            for (GanttSegment gs : segments) segList.add(gs.toMap());
            map.put("segments", segList);
            map.put("avgWait", avgWait);
            map.put("avgTat", avgTat);
            return map;
        }
    }

    public static class BankerOutput {
        public boolean safe;
        public List<String> safeSequence = new ArrayList<>();
        public int[] available;
        public int[][] allocation;
        public int[][] max;
        public int[][] need;
        public String message;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("safe", safe);
            map.put("safeSequence", safeSequence);
            map.put("available", available);
            map.put("allocation", allocation);
            map.put("max", max);
            map.put("need", need);
            map.put("message", message);
            return map;
        }
    }

    // =============================================================
    // WORKLOAD-BASED SCHEDULER DISPATCHER
    // =============================================================

    /**
     * Determines the optimal OS scheduling algorithm based on the hospital workload category.
     * Workloads supported:
     * A. EMERGENCY   --> Priority Scheduling (Priority 1 = Highest Alert)
     * B. INTERACTIVE --> Round Robin (Fair CPU Time Slicing)
     * C. OPD         --> Round Robin (Time-shared clinical consultation)
     * D. DIAGNOSTIC  --> Shortest Job First (SJF - Minimum Average Wait)
     * E. BATCH       --> First-Come, First-Served (FCFS - Minimal Context Switching)
     */
    public static String getRecommendedAlgorithm(String workCategory) {
        if (workCategory == null) return "Priority";
        switch (workCategory.trim().toLowerCase()) {
            case "emergency":
            case "critical":
                return "Priority";
            case "interactive":
            case "doctor":
                return "Round Robin";
            case "opd":
            case "outpatient":
            case "clinical":
                return "Round Robin";
            case "batch":
            case "administrative":
            case "reports":
            case "billing":
                return "FCFS";
            case "diagnostic":
            case "lab":
            case "screening":
            case "imaging":
                return "SJF";
            default:
                return "Priority";
        }
    }

    /**
     * Returns the 3rd-year academic justification for using the chosen algorithm for that workload.
     */
    public static String getWorkloadRationale(String workCategory, String algorithm) {
        if ("Emergency".equalsIgnoreCase(workCategory)) {
            return "Critical ICU and telemetry alerts require Non-Preemptive Priority Scheduling (Priority 1 = Highest Emergency Alert). Immediate CPU dispatch prevents patient telemetry analysis latency.";
        } else if ("Interactive".equalsIgnoreCase(workCategory)) {
            return "Doctor prescription order entry and real-time consultation updates require Round Robin (Time-shared). Slicing execution with a time quantum ensures fair responsiveness across clinicians without starvation.";
        } else if ("OPD".equalsIgnoreCase(workCategory)) {
            return "Outpatient registration, queue ticketing, and consultation slips use Round Robin time sharing to prevent long registrations from blocking incoming outpatient requests.";
        } else if ("Batch".equalsIgnoreCase(workCategory)) {
            return "Nightly hospital billing, insurance claim batches, and statistical summaries use FCFS. Bulk background tasks have no real-time deadlines and benefit from zero context-switching overhead.";
        } else if ("Diagnostic".equalsIgnoreCase(workCategory)) {
            return "Rapid lab screenings (glucose, antigen, CBC, radiograph checks) use Shortest Job First (SJF), which is mathematically provable to minimize the average waiting time across all quick tests.";
        }
        return "Custom algorithm execution for comparison and viva evaluation.";
    }

    /**
     * Intelligently dispatches tasks to the appropriate scheduling algorithm based on workload category.
     */
    public static ScheduleOutput dispatchByWorkload(List<Task> tasks, String workCategory, Integer quantum) {
        String algo = getRecommendedAlgorithm(workCategory);
        int q = (quantum != null && quantum > 0) ? quantum : 2;

        ScheduleOutput out;
        if ("Round Robin".equalsIgnoreCase(algo)) {
            out = scheduleRoundRobin(tasks, q);
        } else {
            out = scheduleNonPreemptive(tasks, algo);
        }

        out.workCategory = workCategory != null ? workCategory : "All Tasks";
        out.rationale = getWorkloadRationale(workCategory, algo);
        return out;
    }

    // =============================================================
    // CORE SCHEDULING ALGORITHMS
    // =============================================================

    /**
     * Executes non-preemptive scheduling (FCFS, SJF, or Priority).
     * Priority convention: Priority 1 represents the highest priority.
     */
    public static ScheduleOutput scheduleNonPreemptive(List<Task> tasks, String algorithm) {
        ScheduleOutput out = new ScheduleOutput();
        out.algorithm = algorithm;
        out.timeQuantum = null;

        if (tasks == null || tasks.isEmpty()) {
            return out;
        }

        List<Task> pending = new ArrayList<>(tasks);
        int time = 0;
        int completed = 0;
        int n = pending.size();
        boolean[] isDone = new boolean[n];

        while (completed < n) {
            List<Integer> readyIndices = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!isDone[i] && pending.get(i).arrivalTime <= time) {
                    readyIndices.add(i);
                }
            }

            if (readyIndices.isEmpty()) {
                int minArr = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (!isDone[i]) {
                        minArr = Math.min(minArr, pending.get(i).arrivalTime);
                    }
                }
                time = minArr;
                continue;
            }

            int bestIdx = readyIndices.get(0);
            for (int i = 1; i < readyIndices.size(); i++) {
                int currIdx = readyIndices.get(i);
                Task best = pending.get(bestIdx);
                Task curr = pending.get(currIdx);

                if ("FCFS".equalsIgnoreCase(algorithm)) {
                    if (curr.arrivalTime < best.arrivalTime || 
                       (curr.arrivalTime == best.arrivalTime && curr.processId < best.processId)) {
                        bestIdx = currIdx;
                    }
                } else if ("SJF".equalsIgnoreCase(algorithm)) {
                    if (curr.burstTime < best.burstTime ||
                       (curr.burstTime == best.burstTime && curr.arrivalTime < best.arrivalTime)) {
                        bestIdx = currIdx;
                    }
                } else if ("Priority".equalsIgnoreCase(algorithm)) {
                    // Priority 1 is highest priority
                    if (curr.priority < best.priority ||
                       (curr.priority == best.priority && curr.arrivalTime < best.arrivalTime)) {
                        bestIdx = currIdx;
                    }
                }
            }

            Task chosen = pending.get(bestIdx);
            int start = time;
            time += chosen.burstTime;
            int finish = time;
            isDone[bestIdx] = true;
            completed++;

            out.segments.add(new GanttSegment(chosen.processId, chosen.processName, start, finish));

            ProcessMetric pm = new ProcessMetric();
            pm.processId = chosen.processId;
            pm.processName = chosen.processName;
            pm.workCategory = chosen.workCategory;
            pm.arrivalTime = chosen.arrivalTime;
            pm.burstTime = chosen.burstTime;
            pm.priority = chosen.priority;
            pm.startTime = start;
            pm.completionTime = finish;
            pm.turnaroundTime = finish - chosen.arrivalTime;
            pm.waitingTime = pm.turnaroundTime - chosen.burstTime;
            out.rows.add(pm);
        }

        out.rows.sort(Comparator.comparingInt(a -> a.processId));

        double totalWait = 0;
        double totalTat = 0;
        for (ProcessMetric r : out.rows) {
            totalWait += r.waitingTime;
            totalTat += r.turnaroundTime;
        }
        out.avgWait = totalWait / out.rows.size();
        out.avgTat = totalTat / out.rows.size();

        return out;
    }

    /**
     * Executes Round Robin scheduling with time quantum.
     * Demonstrates fair CPU allocation among hospital software tasks.
     */
    public static ScheduleOutput scheduleRoundRobin(List<Task> tasks, int quantum) {
        ScheduleOutput out = new ScheduleOutput();
        out.algorithm = "Round Robin";
        out.timeQuantum = Math.max(1, quantum);

        if (tasks == null || tasks.isEmpty()) {
            return out;
        }

        int n = tasks.size();
        int[] remaining = new int[n];
        int[] firstStart = new int[n];
        Arrays.fill(firstStart, -1);
        int[] completion = new int[n];

        for (int i = 0; i < n; i++) {
            remaining[i] = tasks.get(i).burstTime;
        }

        Queue<Integer> queue = new LinkedList<>();
        boolean[] inQueue = new boolean[n];

        int time = 0;
        int completed = 0;

        for (int i = 0; i < n; i++) {
            if (tasks.get(i).arrivalTime <= time && !inQueue[i]) {
                queue.add(i);
                inQueue[i] = true;
            }
        }

        while (completed < n) {
            if (queue.isEmpty()) {
                int nextTime = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (remaining[i] > 0 && tasks.get(i).arrivalTime > time) {
                        nextTime = Math.min(nextTime, tasks.get(i).arrivalTime);
                    }
                }
                time = (nextTime != Integer.MAX_VALUE) ? nextTime : time + 1;
                for (int i = 0; i < n; i++) {
                    if (tasks.get(i).arrivalTime <= time && remaining[i] > 0 && !inQueue[i]) {
                        queue.add(i);
                        inQueue[i] = true;
                    }
                }
                continue;
            }

            int idx = queue.poll();
            inQueue[idx] = false;

            if (firstStart[idx] == -1) {
                firstStart[idx] = time;
            }

            int runTime = Math.min(out.timeQuantum, remaining[idx]);
            int segStart = time;
            time += runTime;
            remaining[idx] -= runTime;

            out.segments.add(new GanttSegment(tasks.get(idx).processId, tasks.get(idx).processName, segStart, time));

            for (int i = 0; i < n; i++) {
                if (i != idx && tasks.get(i).arrivalTime <= time && remaining[i] > 0 && !inQueue[i] && !queue.contains(i)) {
                    queue.add(i);
                    inQueue[i] = true;
                }
            }

            if (remaining[idx] > 0) {
                queue.add(idx);
                inQueue[idx] = true;
            } else {
                completion[idx] = time;
                completed++;
            }
        }

        for (int i = 0; i < n; i++) {
            Task t = tasks.get(i);
            ProcessMetric pm = new ProcessMetric();
            pm.processId = t.processId;
            pm.processName = t.processName;
            pm.workCategory = t.workCategory;
            pm.arrivalTime = t.arrivalTime;
            pm.burstTime = t.burstTime;
            pm.priority = t.priority;
            pm.startTime = firstStart[i];
            pm.completionTime = completion[i];
            pm.turnaroundTime = completion[i] - t.arrivalTime;
            pm.waitingTime = pm.turnaroundTime - t.burstTime;
            out.rows.add(pm);
        }

        out.rows.sort(Comparator.comparingInt(a -> a.processId));

        double totalWait = 0;
        double totalTat = 0;
        for (ProcessMetric r : out.rows) {
            totalWait += r.waitingTime;
            totalTat += r.turnaroundTime;
        }
        out.avgWait = totalWait / out.rows.size();
        out.avgTat = totalTat / out.rows.size();

        return out;
    }

    /**
     * Executes Banker's Algorithm Safety Check.
     * Calculates Need matrix = Maximum - Allocation.
     * Determines whether hospital system is in SAFE or UNSAFE state.
     */
    public static BankerOutput runBankersAlgorithm(int[] available, int[][] allocation, int[][] max, String[] processNames) {
        BankerOutput out = new BankerOutput();
        int n = allocation.length;
        if (n == 0) {
            out.safe = true;
            out.message = "No processes to schedule.";
            return out;
        }
        int m = available.length;

        out.available = available.clone();
        out.allocation = new int[n][m];
        out.max = new int[n][m];
        out.need = new int[n][m];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                out.allocation[i][j] = allocation[i][j];
                out.max[i][j] = max[i][j];

                if (out.allocation[i][j] < 0 || out.max[i][j] < 0) {
                    out.safe = false;
                    out.message = "Matrix values cannot be negative.";
                    return out;
                }

                if (out.allocation[i][j] > out.max[i][j]) {
                    out.safe = false;
                    String pName = (processNames != null && i < processNames.length) ? processNames[i] : "Process " + (i + 1);
                    out.message = "Validation Error: " + pName + " allocated resources exceed maximum demand.";
                    return out;
                }

                out.need[i][j] = out.max[i][j] - out.allocation[i][j];
            }
        }

        for (int j = 0; j < m; j++) {
            if (available[j] < 0) {
                out.safe = false;
                out.message = "Available resources cannot be negative.";
                return out;
            }
        }

        int[] work = available.clone();
        boolean[] finish = new boolean[n];
        List<String> seq = new ArrayList<>();
        int count = 0;

        while (count < n) {
            boolean found = false;

            for (int i = 0; i < n; i++) {
                if (!finish[i]) {
                    boolean canFinish = true;
                    for (int j = 0; j < m; j++) {
                        if (out.need[i][j] > work[j]) {
                            canFinish = false;
                            break;
                        }
                    }

                    if (canFinish) {
                        for (int j = 0; j < m; j++) {
                            work[j] += out.allocation[i][j];
                        }
                        finish[i] = true;
                        String pLabel = (processNames != null && i < processNames.length) ? processNames[i] : "Patient " + (i + 1);
                        seq.add(pLabel);
                        count++;
                        found = true;
                    }
                }
            }

            if (!found) {
                break;
            }
        }

        if (count == n) {
            out.safe = true;
            out.safeSequence = seq;
            out.message = "Hospital system is in a SAFE state. Deadlock avoided.";
        } else {
            out.safe = false;
            out.safeSequence = Collections.emptyList();
            out.message = "Hospital system is in an UNSAFE state. No safe sequence could be found.";
        }

        return out;
    }

    // =============================================================
    // CLI HELPER METHODS FOR INTERACTIVE TERMINAL USE
    // =============================================================

    static int readInt(String message) {
        while (true) {
            System.out.print(message);
            if (sc.hasNextInt()) {
                return sc.nextInt();
            }
            System.out.println("Please enter a valid integer.");
            sc.next();
        }
    }

    static int readPositiveInt(String message) {
        int value;
        do {
            value = readInt(message);
            if (value <= 0) {
                System.out.println("Enter a number greater than zero.");
            }
        } while (value <= 0);
        return value;
    }

    static String readTaskName(int number) {
        System.out.print("Enter name of software task " + number + ": ");
        return sc.next();
    }

    static void displayScheduleOutput(ScheduleOutput res) {
        System.out.println("\n--- " + res.algorithm + " Scheduling Results ---");
        if (res.rationale != null && !res.rationale.isEmpty()) {
            System.out.println("Academic Rationale: " + res.rationale);
        }
        System.out.println("Task\tBurst\tWaiting\tTurnaround\tCompletion");
        for (ProcessMetric r : res.rows) {
            System.out.printf("%s\t%d\t%d\t%d\t\t%d\n",
                r.processName, r.burstTime, r.waitingTime, r.turnaroundTime, r.completionTime);
        }
        System.out.printf("\nAverage Waiting Time: %.2f units\n", res.avgWait);
        System.out.printf("Average Turnaround Time: %.2f units\n", res.avgTat);

        System.out.println("\nGantt Chart Execution Timeline:");
        for (GanttSegment seg : res.segments) {
            System.out.printf("[%d - %s - %d] ", seg.start, seg.processName, seg.end);
        }
        System.out.println();
    }

    static void cliFCFS() {
        System.out.println("\n--- FCFS Scheduling (Batch Workload) ---");
        int n = readPositiveInt("Enter number of batch tasks: ");
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String name = readTaskName(i + 1);
            int burst = readPositiveInt("Enter burst time: ");
            tasks.add(new Task(i + 1, name, "Batch", 0, burst, 1));
        }
        ScheduleOutput out = scheduleNonPreemptive(tasks, "FCFS");
        displayScheduleOutput(out);
    }

    static void cliSJF() {
        System.out.println("\n--- SJF Scheduling (Rapid Diagnostic Tests) ---");
        int n = readPositiveInt("Enter number of diagnostic tasks: ");
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String name = readTaskName(i + 1);
            int burst = readPositiveInt("Enter burst time: ");
            tasks.add(new Task(i + 1, name, "Diagnostic", 0, burst, 1));
        }
        ScheduleOutput out = scheduleNonPreemptive(tasks, "SJF");
        displayScheduleOutput(out);
    }

    static void cliPriority() {
        System.out.println("\n--- Priority Scheduling (Critical Emergency Workload) ---");
        System.out.println("Note: Priority 1 is the highest emergency priority.");
        int n = readPositiveInt("Enter number of emergency alert tasks: ");
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String name = readTaskName(i + 1);
            int burst = readPositiveInt("Enter burst time: ");
            int priority = readPositiveInt("Enter priority (1 is highest): ");
            tasks.add(new Task(i + 1, name, "Emergency", 0, burst, priority));
        }
        ScheduleOutput out = scheduleNonPreemptive(tasks, "Priority");
        displayScheduleOutput(out);
    }

    static void cliRoundRobin() {
        System.out.println("\n--- Round Robin Scheduling (Interactive Doctor Orders) ---");
        int n = readPositiveInt("Enter number of interactive doctor tasks: ");
        int quantum = readPositiveInt("Enter time quantum (e.g. 2): ");
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String name = readTaskName(i + 1);
            int burst = readPositiveInt("Enter burst time: ");
            tasks.add(new Task(i + 1, name, "Interactive", 0, burst, 1));
        }
        ScheduleOutput out = scheduleRoundRobin(tasks, quantum);
        displayScheduleOutput(out);
    }

    static void cliBankers() {
        System.out.println("\n--- Banker's Algorithm (Hospital Resource Deadlock Avoidance) ---");
        int n = readPositiveInt("Enter number of patients/processes: ");
        int m = RESOURCE_NAMES.length;

        int[][] allocation = new int[n][m];
        int[][] max = new int[n][m];
        int[] available = new int[m];
        String[] names = new String[n];

        for (int i = 0; i < n; i++) {
            names[i] = "Patient " + (i + 1);
        }

        System.out.println("\nEnter Allocation Matrix:");
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                allocation[i][j] = readInt(names[i] + ", " + RESOURCE_NAMES[j] + " allocated: ");
            }
        }

        System.out.println("\nEnter Maximum Demand Matrix:");
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                max[i][j] = readInt(names[i] + ", " + RESOURCE_NAMES[j] + " maximum needed: ");
            }
        }

        System.out.println("\nEnter Available Resources:");
        for (int j = 0; j < m; j++) {
            available[j] = readInt("Available " + RESOURCE_NAMES[j] + ": ");
        }

        BankerOutput result = runBankersAlgorithm(available, allocation, max, names);
        System.out.println("\nResult: " + result.message);
        if (result.safe) {
            System.out.println("Safe Sequence: " + String.join(" -> ", result.safeSequence));
        }
    }

    // Interactive CLI Main Menu
    public static void main(String[] args) {
        int choice;
        do {
            System.out.println("\n========================================================");
            System.out.println("  SMART HOSPITAL RESOURCE & TREATMENT MANAGEMENT SYSTEM ");
            System.out.println("  B.Tech 3rd Year Operating Systems Module (CLI Mode)   ");
            System.out.println("========================================================");
            System.out.println("1. Emergency Tasks       --> Priority Scheduling (P1=Highest)");
            System.out.println("2. Interactive OPD Tasks --> Round Robin Scheduling (Time-shared)");
            System.out.println("3. Batch Reports Tasks   --> FCFS Scheduling (Batch Queue)");
            System.out.println("4. Diagnostic Screenings --> SJF Scheduling (Minimum Avg Wait)");
            System.out.println("5. Deadlock Avoidance    --> Banker's Safety Algorithm");
            System.out.println("0. Exit");

            choice = readInt("Enter your choice (0-5): ");
            switch (choice) {
                case 1: cliPriority(); break;
                case 2: cliRoundRobin(); break;
                case 3: cliFCFS(); break;
                case 4: cliSJF(); break;
                case 5: cliBankers(); break;
                case 0: System.out.println("Exiting Hospital OS Module."); break;
                default: System.out.println("Invalid choice. Try again.");
            }
        } while (choice != 0);

        sc.close();
    }
}
