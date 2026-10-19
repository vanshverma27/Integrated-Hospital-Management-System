
import java.util.Scanner;

public class HospitalOSModule {

    static Scanner sc = new Scanner(System.in);

    // Hospital resources
    static String[] resourceNames = {
        "Beds", "Doctors", "Monitoring Devices"
    };

    static int[] totalResources = {10, 5, 8};
    static int[] availableResources = {10, 5, 8};
    static int[] allocatedResources = {0, 0, 0};

    // Read integer input
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

    // Read positive integer input
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

    // Read patient name (one word)
    static String readPatientName(int number) {
        System.out.print("Enter name of patient " + number + ": ");
        return sc.next();
    }

    // Display Gantt chart
    static void displayGanttChart(String[] names, int[] burst, int n) {
        System.out.println("\nGantt Chart:");
        int time = 0;
        System.out.print("0");

        for (int i = 0; i < n; i++) {
            time += burst[i];
            System.out.print(" -- " + names[i] + " -- " + time);
        }

        System.out.println();
    }

    // FCFS Scheduling
    static void fcfsScheduling() {
        System.out.println("\n--- FCFS Scheduling ---");

        int n = readPositiveInt("Enter number of patients: ");

        String[] names = new String[n];
        int[] burst = new int[n];
        int[] waiting = new int[n];
        int[] turnaround = new int[n];

        for (int i = 0; i < n; i++) {
            names[i] = readPatientName(i + 1);
            burst[i] = readPositiveInt("Enter burst time: ");
        }

        waiting[0] = 0;

        for (int i = 1; i < n; i++) {
            waiting[i] = waiting[i - 1] + burst[i - 1];
        }

        System.out.println("\nPatient\tBurst\tWaiting\tTurnaround");

        for (int i = 0; i < n; i++) {
            turnaround[i] = waiting[i] + burst[i];

            System.out.println(
                names[i] + "\t" + burst[i] + "\t"
                + waiting[i] + "\t" + turnaround[i]
            );
        }

        System.out.println("\nExecution order:");
        for (int i = 0; i < n; i++) {
            System.out.print(names[i]);
            if (i < n - 1) System.out.print(" -> ");
        }
        System.out.println();

        displayGanttChart(names, burst, n);
    }

    // SJF Scheduling (Non-Preemptive)
    static void sjfScheduling() {
        System.out.println("\n--- SJF Scheduling (Non-Preemptive) ---");

        int n = readPositiveInt("Enter number of patients: ");

        String[] names = new String[n];
        int[] burst = new int[n];
        int[] waiting = new int[n];
        int[] turnaround = new int[n];

        for (int i = 0; i < n; i++) {
            names[i] = readPatientName(i + 1);
            burst[i] = readPositiveInt("Enter burst time: ");
        }

        // Sort by shortest burst time
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                if (burst[j] < burst[i]) {
                    int temp = burst[i];
                    burst[i] = burst[j];
                    burst[j] = temp;

                    String tempName = names[i];
                    names[i] = names[j];
                    names[j] = tempName;
                }
            }
        }

        waiting[0] = 0;

        for (int i = 1; i < n; i++) {
            waiting[i] = waiting[i - 1] + burst[i - 1];
        }

        System.out.println("\nPatient\tBurst\tWaiting\tTurnaround");

        for (int i = 0; i < n; i++) {
            turnaround[i] = waiting[i] + burst[i];

            System.out.println(
                names[i] + "\t" + burst[i] + "\t"
                + waiting[i] + "\t" + turnaround[i]
            );
        }

        System.out.println("\nExecution order:");
        for (int i = 0; i < n; i++) {
            System.out.print(names[i]);
            if (i < n - 1) System.out.print(" -> ");
        }
        System.out.println();

        displayGanttChart(names, burst, n);
    }

    // Priority Scheduling (Non-Preemptive)
    // Priority 1 is the highest
    static void priorityScheduling() {
        System.out.println("\n--- Priority Scheduling ---");

        int n = readPositiveInt("Enter number of patients: ");

        String[] names = new String[n];
        int[] burst = new int[n];
        int[] priority = new int[n];
        int[] waiting = new int[n];
        int[] turnaround = new int[n];

        for (int i = 0; i < n; i++) {
            names[i] = readPatientName(i + 1);
            burst[i] = readPositiveInt("Enter burst time: ");
            priority[i] = readPositiveInt(
                "Enter priority (1 is highest): "
            );
        }

        // Sort by priority
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                if (priority[j] < priority[i]) {
                    int temp = priority[i];
                    priority[i] = priority[j];
                    priority[j] = temp;

                    temp = burst[i];
                    burst[i] = burst[j];
                    burst[j] = temp;

                    String tempName = names[i];
                    names[i] = names[j];
                    names[j] = tempName;
                }
            }
        }

        waiting[0] = 0;

        for (int i = 1; i < n; i++) {
            waiting[i] = waiting[i - 1] + burst[i - 1];
        }

        System.out.println("\nPatient\tPriority\tBurst\tWaiting\tTurnaround");

        for (int i = 0; i < n; i++) {
            turnaround[i] = waiting[i] + burst[i];

            System.out.println(
                names[i] + "\t" + priority[i] + "\t\t"
                + burst[i] + "\t" + waiting[i] + "\t"
                + turnaround[i]
            );
        }

        System.out.println("\nExecution order:");
        for (int i = 0; i < n; i++) {
            System.out.print(names[i]);
            if (i < n - 1) System.out.print(" -> ");
        }
        System.out.println();

        displayGanttChart(names, burst, n);
    }

    // Round Robin Scheduling
    static void roundRobinScheduling() {
        System.out.println("\n--- Round Robin Scheduling ---");

        int n = readPositiveInt("Enter number of patients: ");
        int quantum = readPositiveInt("Enter time quantum: ");

        String[] names = new String[n];
        int[] burst = new int[n];
        int[] remaining = new int[n];
        int[] waiting = new int[n];
        int[] turnaround = new int[n];

        for (int i = 0; i < n; i++) {
            names[i] = readPatientName(i + 1);
            burst[i] = readPositiveInt("Enter burst time: ");
            remaining[i] = burst[i];
        }

        int time = 0;
        int completed = 0;

        System.out.println("\nGantt Chart:");

        while (completed < n) {
            for (int i = 0; i < n; i++) {
                if (remaining[i] > 0) {
                    int start = time;
                    int runTime = Math.min(quantum, remaining[i]);

                    time += runTime;
                    remaining[i] -= runTime;

                    System.out.print(
                        "[" + start + " " + names[i] + " " + time + "] "
                    );

                    if (remaining[i] == 0) {
                        completed++;
                        turnaround[i] = time;
                        waiting[i] = turnaround[i] - burst[i];
                    }
                }
            }
        }

        System.out.println("\n\nPatient\tBurst\tWaiting\tTurnaround");

        for (int i = 0; i < n; i++) {
            System.out.println(
                names[i] + "\t" + burst[i] + "\t"
                + waiting[i] + "\t" + turnaround[i]
            );
        }
    }

    // Display hospital resources
    static void displayResources() {
        System.out.println("\n--- Hospital Resources ---");

        for (int i = 0; i < resourceNames.length; i++) {
            System.out.println(
                resourceNames[i] + ": Available = "
                + availableResources[i] + ", Total = "
                + totalResources[i]
            );
        }
    }

    // Allocate resources
    static void allocateResource() {
        displayResources();

        int choice = readInt(
            "Choose resource (1=Bed, 2=Doctor, 3=Device): "
        );

        if (choice < 1 || choice > 3) {
            System.out.println("Invalid resource choice.");
            return;
        }

        int index = choice - 1;
        int quantity = readPositiveInt("Enter quantity to allocate: ");

        if (quantity <= availableResources[index]) {
            availableResources[index] -= quantity;
            allocatedResources[index] += quantity;

            System.out.println(
                quantity + " " + resourceNames[index]
                + " allocated successfully."
            );
        } else {
            System.out.println(
                "Allocation failed. Not enough "
                + resourceNames[index] + " available."
            );
        }
    }

    // Release resources
    static void releaseResource() {
        displayResources();

        int choice = readInt(
            "Choose resource (1=Bed, 2=Doctor, 3=Device): "
        );

        if (choice < 1 || choice > 3) {
            System.out.println("Invalid resource choice.");
            return;
        }

        int index = choice - 1;
        int quantity = readPositiveInt("Enter quantity to release: ");

        if (quantity <= allocatedResources[index]) {
            allocatedResources[index] -= quantity;
            availableResources[index] += quantity;

            System.out.println(
                quantity + " " + resourceNames[index]
                + " released successfully."
            );
        } else {
            System.out.println(
                "Release failed. Only " + allocatedResources[index]
                + " units are currently allocated."
            );
        }
    }

    // Banker's Algorithm
    static void bankersAlgorithm() {
        System.out.println("\n--- Hospital Banker's Algorithm ---");

        int n = readPositiveInt("Enter number of patients: ");
        int m = 3;

        int[][] allocation = new int[n][m];
        int[][] max = new int[n][m];
        int[][] need = new int[n][m];
        int[] available = new int[m];

        System.out.println("\nEnter Allocation Matrix:");

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                allocation[i][j] = readInt(
                    "Patient " + (i + 1) + ", "
                    + resourceNames[j] + " already allocated: "
                );

                if (allocation[i][j] < 0) {
                    System.out.println("Allocation cannot be negative.");
                    return;
                }
            }
        }

        System.out.println("\nEnter Maximum Demand Matrix:");

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                max[i][j] = readInt(
                    "Patient " + (i + 1) + ", maximum "
                    + resourceNames[j] + " needed: "
                );

                if (max[i][j] < allocation[i][j]) {
                    System.out.println(
                        "Maximum demand cannot be less than allocation."
                    );
                    return;
                }

                need[i][j] = max[i][j] - allocation[i][j];
            }
        }

        System.out.println("\nEnter Available Resources:");

        for (int j = 0; j < m; j++) {
            available[j] = readInt(
                "Available " + resourceNames[j] + ": "
            );

            if (available[j] < 0) {
                System.out.println(
                    "Available resources cannot be negative."
                );
                return;
            }
        }

        int[] work = available.clone();
        boolean[] finished = new boolean[n];
        int[] safeSequence = new int[n];
        int count = 0;

        while (count < n) {
            boolean found = false;

            for (int i = 0; i < n; i++) {
                if (!finished[i]) {
                    boolean canFinish = true;

                    for (int j = 0; j < m; j++) {
                        if (need[i][j] > work[j]) {
                            canFinish = false;
                            break;
                        }
                    }

                    if (canFinish) {
                        for (int j = 0; j < m; j++) {
                            work[j] += allocation[i][j];
                        }

                        safeSequence[count] = i;
                        count++;
                        finished[i] = true;
                        found = true;
                    }
                }
            }

            if (!found) {
                break;
            }
        }

        if (count == n) {
            System.out.println("\nHospital system is in a SAFE state.");
            System.out.print("Safe sequence: ");

            for (int i = 0; i < n; i++) {
                System.out.print("Patient " + (safeSequence[i] + 1));

                if (i < n - 1) {
                    System.out.print(" -> ");
                }
            }

            System.out.println();
        } else {
            System.out.println("\nHospital system is in an UNSAFE state.");
            System.out.println("No safe sequence could be found.");
        }
    }

    // Main menu
    public static void main(String[] args) {
        int choice;

        do {
            System.out.println("\n===== HOSPITAL OS MANAGEMENT =====");
            System.out.println("1. FCFS Scheduling");
            System.out.println("2. SJF Scheduling");
            System.out.println("3. Priority Scheduling");
            System.out.println("4. Display Resources");
            System.out.println("5. Allocate Resources");
            System.out.println("6. Release Resources");
            System.out.println("7. Banker's Algorithm");
            System.out.println("8. Round Robin Scheduling");
            System.out.println("0. Exit");

            choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1:
                    fcfsScheduling();
                    break;
                case 2:
                    sjfScheduling();
                    break;
                case 3:
                    priorityScheduling();
                    break;
                case 4:
                    displayResources();
                    break;
                case 5:
                    allocateResource();
                    break;
                case 6:
                    releaseResource();
                    break;
                case 7:
                    bankersAlgorithm();
                    break;
                case 8:
                    roundRobinScheduling();
                    break;
                case 0:
                    System.out.println("Exiting Hospital OS Management.");
                    break;
                default:
                    System.out.println("Invalid choice. Try again.");
            }

        } while (choice != 0);

        sc.close();
    }
}



