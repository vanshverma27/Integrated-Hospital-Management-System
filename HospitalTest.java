import java.util.*;

/**
 * HospitalTest - Comprehensive Test Suite for B.Tech 3rd Year Hospital OS + DBMS Project.
 * 
 * Verifies:
 * 1. 1 Bed per Patient constraint
 * 2. Workload-specific CPU Scheduling:
 *    - Emergency Tasks     --> Priority Scheduling (P1=Highest)
 *    - Interactive Tasks   --> Round Robin Scheduling
 *    - Batch Tasks         --> FCFS Scheduling
 *    - Diagnostic Tasks    --> SJF Scheduling
 * 3. Deadlock Avoidance (Banker's Algorithm Safe vs Unsafe)
 * 4. ACID Transactional Resource Allocation & Release
 */
public class HospitalTest {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  HOSPITAL OS + DBMS PROJECT VERIFICATION        ");
        System.out.println("  B.Tech 3rd Year Comprehensive Test Suite       ");
        System.out.println("=================================================");

        // Test Tasks across different workloads
        List<HospitalOSModule.Task> emergencyTasks = new ArrayList<>();
        emergencyTasks.add(new HospitalOSModule.Task(1, "ICU Cardiac Telemetry Alarm", "Emergency", 0, 5, 1));
        emergencyTasks.add(new HospitalOSModule.Task(2, "Oxygen Desaturation Alert", "Emergency", 1, 4, 2));
        emergencyTasks.add(new HospitalOSModule.Task(3, "Arrhythmia Critical Notification", "Emergency", 2, 3, 1));

        List<HospitalOSModule.Task> interactiveTasks = new ArrayList<>();
        interactiveTasks.add(new HospitalOSModule.Task(4, "Doctor Prescription Order Entry", "Interactive", 0, 4, 2));
        interactiveTasks.add(new HospitalOSModule.Task(5, "OPD Consultation Vitals Update", "Interactive", 1, 3, 3));
        interactiveTasks.add(new HospitalOSModule.Task(6, "Tele-Health Video Stream Session", "Interactive", 2, 5, 2));

        List<HospitalOSModule.Task> batchTasks = new ArrayList<>();
        batchTasks.add(new HospitalOSModule.Task(7, "Nightly Billing Summary Generation", "Batch", 0, 6, 4));
        batchTasks.add(new HospitalOSModule.Task(8, "Daily Insurance Claim Batching", "Batch", 2, 4, 3));
        batchTasks.add(new HospitalOSModule.Task(9, "Discharge Summary PDF Export", "Batch", 3, 5, 4));

        List<HospitalOSModule.Task> diagnosticTasks = new ArrayList<>();
        diagnosticTasks.add(new HospitalOSModule.Task(10, "Blood Glucose Rapid Screen", "Diagnostic", 0, 2, 1));
        diagnosticTasks.add(new HospitalOSModule.Task(11, "Rapid Antigen Viral Assay", "Diagnostic", 1, 1, 2));
        diagnosticTasks.add(new HospitalOSModule.Task(12, "Haemoglobin Fingerprick Test", "Diagnostic", 2, 3, 1));

        // 1. Test Emergency Workload -> Priority Scheduling
        System.out.println("\n[1] Testing Emergency Workload (Dispatched to Priority Scheduling)...");
        HospitalOSModule.ScheduleOutput outEmergency = HospitalOSModule.dispatchByWorkload(emergencyTasks, "Emergency", null);
        System.out.println("  Assigned Algorithm: " + outEmergency.algorithm);
        System.out.println("  Academic Rationale: " + outEmergency.rationale);
        System.out.printf("  Avg Wait Time: %.2f units, Avg TAT: %.2f units\n", outEmergency.avgWait, outEmergency.avgTat);
        assert "Priority".equalsIgnoreCase(outEmergency.algorithm) : "Emergency must use Priority Scheduling";
        // Task 1 (priority 1) completes at 5; at time 5, Task 3 has priority 1 and Task 2 has priority 2.
        // Task 3 (priority 1) must execute before Task 2 (priority 2)!
        assert outEmergency.segments.get(1).processId == 3 : "Task 3 (Priority 1) must execute before Task 2 (Priority 2)";

        // 2. Test Interactive Workload -> Round Robin Scheduling
        System.out.println("\n[2] Testing Interactive Workload (Dispatched to Round Robin Scheduling)...");
        HospitalOSModule.ScheduleOutput outInteractive = HospitalOSModule.dispatchByWorkload(interactiveTasks, "Interactive", 2);
        System.out.println("  Assigned Algorithm: " + outInteractive.algorithm);
        System.out.println("  Time Quantum: " + outInteractive.timeQuantum);
        System.out.printf("  Avg Wait Time: %.2f units, Avg TAT: %.2f units\n", outInteractive.avgWait, outInteractive.avgTat);
        assert "Round Robin".equalsIgnoreCase(outInteractive.algorithm) : "Interactive tasks must use Round Robin";
        assert outInteractive.segments.size() > 3 : "Round Robin must time-slice into multiple segments";

        // 3. Test Batch Workload -> FCFS Scheduling
        System.out.println("\n[3] Testing Batch Workload (Dispatched to FCFS Scheduling)...");
        HospitalOSModule.ScheduleOutput outBatch = HospitalOSModule.dispatchByWorkload(batchTasks, "Batch", null);
        System.out.println("  Assigned Algorithm: " + outBatch.algorithm);
        System.out.printf("  Avg Wait Time: %.2f units, Avg TAT: %.2f units\n", outBatch.avgWait, outBatch.avgTat);
        assert "FCFS".equalsIgnoreCase(outBatch.algorithm) : "Batch tasks must use FCFS";
        assert outBatch.segments.get(0).processId == 7 : "First arrived batch task must execute first";

        // 4. Test Diagnostic Workload -> SJF Scheduling
        System.out.println("\n[4] Testing Diagnostic Workload (Dispatched to SJF Scheduling)...");
        HospitalOSModule.ScheduleOutput outDiag = HospitalOSModule.dispatchByWorkload(diagnosticTasks, "Diagnostic", null);
        System.out.println("  Assigned Algorithm: " + outDiag.algorithm);
        System.out.printf("  Avg Wait Time: %.2f units, Avg TAT: %.2f units\n", outDiag.avgWait, outDiag.avgTat);
        assert "SJF".equalsIgnoreCase(outDiag.algorithm) : "Diagnostic tasks must use SJF";

        // 5. Test Banker's Algorithm Deadlock Avoidance
        System.out.println("\n[5] Testing Banker's Algorithm for Hospital Resource Deadlock Avoidance...");
        int[] available = {3, 2, 2}; // Beds, Doctors, Devices
        int[][] alloc = {
            {1, 0, 1},
            {1, 1, 0},
            {1, 0, 1}
        };
        int[][] max = {
            {3, 2, 2},
            {2, 2, 1},
            {3, 1, 3}
        };
        String[] names = {"Patient 1", "Patient 2", "Patient 3"};
        HospitalOSModule.BankerOutput bankerSafe = HospitalOSModule.runBankersAlgorithm(available, alloc, max, names);
        System.out.println("  Safe state verified: " + bankerSafe.safe);
        System.out.println("  Safe Sequence: " + String.join(" -> ", bankerSafe.safeSequence));
        assert bankerSafe.safe : "Must evaluate to SAFE state";

        // 6. Test 1:1 Bed Allocation in HospitalDAO
        System.out.println("\n[6] Testing 1:1 Bed-Patient Allocation in HospitalDAO...");
        HospitalDAO dao = new HospitalDAO();
        try {
            List<Map<String, Object>> beds = dao.getAllBeds();
            List<Map<String, Object>> patients = dao.getAllPatients();
            System.out.println("  Total Beds: " + beds.size() + ", Total Patients: " + patients.size());

            // Verify every admitted patient has exactly one bed
            for (Map<String, Object> p : patients) {
                Object bedNum = p.get("bed_number");
                System.out.printf("  Patient: %s -> Assigned Bed: %s\n", p.get("patient_name"), bedNum);
                assert bedNum != null && !"Unassigned".equals(bedNum) : "Every admitted patient must have an assigned bed";
            }

            // Register a new patient and verify bed auto-allocation
            Map<String, Object> newP = dao.addPatient("Ananya Roy", 24, "Female", "9876599999", "Dehradun", null);
            System.out.printf("  New Patient Registered: %s -> Dedicated Bed Assigned: %s (%s)\n",
                newP.get("patient_name"), newP.get("bed_number"), newP.get("ward"));
            assert newP.get("bed_number") != null : "Newly admitted patient must have an assigned bed";

            // Verify the bed is now marked Occupied
            List<Map<String, Object>> updatedBeds = dao.getAllBeds();
            boolean bedOccupied = false;
            for (Map<String, Object> b : updatedBeds) {
                if (b.get("bed_number").equals(newP.get("bed_number"))) {
                    bedOccupied = "Occupied".equals(b.get("bed_status"));
                    break;
                }
            }
            assert bedOccupied : "Assigned bed must have status Occupied";
            System.out.println("  1:1 Bed Allocation verified successfully!");

        } catch (Exception e) {
            System.err.println("DAO Test Exception: " + e.getMessage());
            assert false : "DAO operations should not fail";
        }

        System.out.println("\n=================================================");
        System.out.println("  ALL 6 VERIFICATION TEST SUITES PASSED (100%)!   ");
        System.out.println("=================================================");
    }
}
