import java.sql.*;
import java.util.*;

/**
 * HospitalDAO - Data Access Object for MySQL database interactions using JDBC.
 * 
 * B.Tech 3rd Year OS + DBMS Integrated Project:
 * - Full CRUD for Patients & Doctors
 * - Strict 1:1 Bed Allocation per Inpatient (Dedicated bed with ACID guarantees)
 * - Workload Categories: Emergency, Interactive, OPD, Diagnostic, Batch
 * - PreparedStatements for parameterized, injection-safe SQL operations
 * - Explicit transactions (commit/rollback) for ACID data integrity
 * - Auto-migration & safe sample data initialization (prevents duplicate spamming)
 */
public class HospitalDAO {

    public HospitalDAO() {
        // Automatically ensure schema and sample data exist when DAO initializes
        try {
            ensureSchemaAndSeedData();
        } catch (Exception e) {
            System.err.println("[DB] Auto-migration / seed notice: " + e.getMessage());
        }
    }

    // =============================================================
    // SCHEMA MIGRATION & SAFE SAMPLE DATA INITIALIZATION
    // =============================================================

    public synchronized void ensureSchemaAndSeedData() {
        if (!DBConnection.isAvailable()) return;

        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            // 1. Ensure patients table has all required columns
            ensureColumnExists(conn, meta, "patients", "diagnosis", "VARCHAR(255) DEFAULT 'General Consultation'");
            ensureColumnExists(conn, meta, "patients", "department", "VARCHAR(100) DEFAULT 'General Medicine'");
            ensureColumnExists(conn, meta, "patients", "doctor_id", "INT NULL");
            ensureColumnExists(conn, meta, "patients", "priority_level", "INT DEFAULT 2");

            // 2. Ensure doctors table has all required columns
            ensureColumnExists(conn, meta, "doctors", "department", "VARCHAR(100) DEFAULT 'General Medicine'");
            ensureColumnExists(conn, meta, "doctors", "availability_status", "VARCHAR(50) DEFAULT 'Available'");

            // 3. Check and synchronize beds count in resources
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("UPDATE resources SET total_quantity = (SELECT COUNT(*) FROM beds) WHERE LOWER(resource_name) LIKE '%bed%'");
                st.executeUpdate("UPDATE resources SET available_quantity = (SELECT COUNT(*) FROM beds WHERE bed_status = 'Available') WHERE LOWER(resource_name) LIKE '%bed%'");
                st.executeUpdate("UPDATE resources SET total_quantity = (SELECT COUNT(*) FROM doctors), available_quantity = (SELECT COUNT(*) FROM doctors WHERE availability_status = 'Available') WHERE LOWER(resource_name) LIKE '%doctor%'");
            }

        } catch (SQLException e) {
            System.err.println("[DB] Notice during schema verification: " + e.getMessage());
        }
    }

    private void ensureColumnExists(Connection conn, DatabaseMetaData meta, String table, String col, String colDef) throws SQLException {
        try (ResultSet rs = meta.getColumns(null, null, table, col)) {
            if (!rs.next()) {
                String sql = "ALTER TABLE " + table + " ADD COLUMN " + col + " " + colDef;
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sql);
                    System.out.println("[DB] Added column " + col + " to " + table);
                }
            }
        }
    }

    // =============================================================
    // PATIENTS MANAGEMENT (FULL CRUD WITH 1:1 BED ALLOCATION)
    // =============================================================

    /**
     * Retrieves all patients, optionally filtered by keyword and department.
     */
    public List<Map<String, Object>> getAllPatients(String search, String department) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT p.patient_id, p.patient_name, p.age, p.gender, p.phone, p.address, " +
            "COALESCE(p.diagnosis, 'General Consultation') AS diagnosis, " +
            "COALESCE(p.department, 'General Medicine') AS department, " +
            "p.doctor_id, COALESCE(d.doctor_name, 'Unassigned') AS doctor_name, " +
            "p.bed_id, COALESCE(b.bed_number, 'Unassigned') AS bed_number, " +
            "COALESCE(b.ward, '—') AS ward, p.admission_status, " +
            "COALESCE(p.priority_level, 2) AS priority_level, p.created_at " +
            "FROM patients p " +
            "LEFT JOIN beds b ON p.bed_id = b.bed_id " +
            "LEFT JOIN doctors d ON p.doctor_id = d.doctor_id " +
            "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            String q = "%" + search.trim() + "%";
            sql.append("AND (p.patient_name LIKE ? OR CAST(p.patient_id AS CHAR) LIKE ? OR p.phone LIKE ? OR p.diagnosis LIKE ? OR p.department LIKE ?) ");
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }
        if (department != null && !department.trim().isEmpty() && !"All".equalsIgnoreCase(department)) {
            sql.append("AND LOWER(p.department) = LOWER(?) ");
            params.add(department.trim());
        }

        sql.append("ORDER BY p.patient_id ASC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("patient_id", rs.getInt("patient_id"));
                    row.put("patient_name", rs.getString("patient_name"));
                    row.put("age", rs.getInt("age"));
                    row.put("gender", rs.getString("gender"));
                    row.put("phone", rs.getString("phone"));
                    row.put("address", rs.getString("address"));
                    row.put("diagnosis", rs.getString("diagnosis"));
                    row.put("department", rs.getString("department"));
                    row.put("doctor_id", rs.getObject("doctor_id"));
                    row.put("doctor_name", rs.getString("doctor_name"));
                    row.put("bed_id", rs.getObject("bed_id"));
                    row.put("bed_number", rs.getString("bed_number"));
                    row.put("ward", rs.getString("ward"));
                    row.put("admission_status", rs.getString("admission_status"));
                    row.put("priority_level", rs.getInt("priority_level"));
                    row.put("created_at", rs.getString("created_at"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    public List<Map<String, Object>> getAllPatients() throws SQLException {
        return getAllPatients(null, null);
    }

    public Map<String, Object> getPatientById(int patientId) throws SQLException {
        String sql = "SELECT p.patient_id, p.patient_name, p.age, p.gender, p.phone, p.address, " +
                     "COALESCE(p.diagnosis, 'General Consultation') AS diagnosis, " +
                     "COALESCE(p.department, 'General Medicine') AS department, " +
                     "p.doctor_id, COALESCE(d.doctor_name, 'Unassigned') AS doctor_name, " +
                     "p.bed_id, COALESCE(b.bed_number, 'Unassigned') AS bed_number, " +
                     "COALESCE(b.ward, '—') AS ward, p.admission_status, " +
                     "COALESCE(p.priority_level, 2) AS priority_level, p.created_at " +
                     "FROM patients p " +
                     "LEFT JOIN beds b ON p.bed_id = b.bed_id " +
                     "LEFT JOIN doctors d ON p.doctor_id = d.doctor_id " +
                     "WHERE p.patient_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("patient_id", rs.getInt("patient_id"));
                    row.put("patient_name", rs.getString("patient_name"));
                    row.put("age", rs.getInt("age"));
                    row.put("gender", rs.getString("gender"));
                    row.put("phone", rs.getString("phone"));
                    row.put("address", rs.getString("address"));
                    row.put("diagnosis", rs.getString("diagnosis"));
                    row.put("department", rs.getString("department"));
                    row.put("doctor_id", rs.getObject("doctor_id"));
                    row.put("doctor_name", rs.getString("doctor_name"));
                    row.put("bed_id", rs.getObject("bed_id"));
                    row.put("bed_number", rs.getString("bed_number"));
                    row.put("ward", rs.getString("ward"));
                    row.put("admission_status", rs.getString("admission_status"));
                    row.put("priority_level", rs.getInt("priority_level"));
                    row.put("created_at", rs.getString("created_at"));
                    return row;
                }
            }
        }
        return null;
    }

    /**
     * Registers a new patient with strict 1:1 Bed Allocation if admitted.
     */
    public synchronized Map<String, Object> addPatient(
            String name, int age, String gender, String phone, String address,
            String diagnosis, String department, Integer doctorId, Integer preferredBedId,
            String admissionStatus, Integer priorityLevel) throws SQLException {

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // START TRANSACTION

            String status = (admissionStatus != null && !admissionStatus.trim().isEmpty()) ? admissionStatus.trim() : "Admitted";
            int priority = (priorityLevel != null && priorityLevel >= 1 && priorityLevel <= 3) ? priorityLevel : 2;
            String diag = (diagnosis != null && !diagnosis.trim().isEmpty()) ? diagnosis.trim() : "General Consultation";
            String dept = (department != null && !department.trim().isEmpty()) ? department.trim() : "General Medicine";

            Integer allocatedBedId = null;
            String allocatedBedNumber = "Unassigned";
            String allocatedWard = "—";

            // If patient is Admitted, allocate exactly ONE bed (1:1 constraint)
            if ("Admitted".equalsIgnoreCase(status)) {
                String bedQuery;
                if (preferredBedId != null && preferredBedId > 0) {
                    bedQuery = "SELECT bed_id, bed_number, ward FROM beds WHERE bed_id = ? AND bed_status = 'Available' FOR UPDATE";
                } else {
                    bedQuery = "SELECT bed_id, bed_number, ward FROM beds WHERE bed_status = 'Available' ORDER BY bed_id ASC LIMIT 1 FOR UPDATE";
                }

                try (PreparedStatement psBed = conn.prepareStatement(bedQuery)) {
                    if (preferredBedId != null && preferredBedId > 0) {
                        psBed.setInt(1, preferredBedId);
                    }
                    try (ResultSet rsBed = psBed.executeQuery()) {
                        if (rsBed.next()) {
                            allocatedBedId = rsBed.getInt("bed_id");
                            allocatedBedNumber = rsBed.getString("bed_number");
                            allocatedWard = rsBed.getString("ward");
                        } else {
                            conn.rollback();
                            throw new SQLException("Bed allocation failed: No available beds found in MySQL. Exactly one dedicated bed is required for every admitted patient.");
                        }
                    }
                }
            }

            // Insert patient record
            int newPatientId = 0;
            String sqlPatient = "INSERT INTO patients (patient_name, age, gender, phone, address, diagnosis, department, doctor_id, bed_id, admission_status, priority_level) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psPatient = conn.prepareStatement(sqlPatient, Statement.RETURN_GENERATED_KEYS)) {
                psPatient.setString(1, name);
                psPatient.setInt(2, age);
                psPatient.setString(3, gender);
                psPatient.setString(4, phone);
                psPatient.setString(5, address);
                psPatient.setString(6, diag);
                psPatient.setString(7, dept);
                if (doctorId != null && doctorId > 0) {
                    psPatient.setInt(8, doctorId);
                } else {
                    psPatient.setNull(8, Types.INTEGER);
                }
                if (allocatedBedId != null) {
                    psPatient.setInt(9, allocatedBedId);
                } else {
                    psPatient.setNull(9, Types.INTEGER);
                }
                psPatient.setString(10, status);
                psPatient.setInt(11, priority);
                psPatient.executeUpdate();

                try (ResultSet keys = psPatient.getGeneratedKeys()) {
                    if (keys.next()) {
                        newPatientId = keys.getInt(1);
                    }
                }
            }

            // If bed allocated, mark bed as Occupied by this patient
            if (allocatedBedId != null && newPatientId > 0) {
                String updateBed = "UPDATE beds SET bed_status = 'Occupied', patient_id = ? WHERE bed_id = ?";
                try (PreparedStatement psUpdateBed = conn.prepareStatement(updateBed)) {
                    psUpdateBed.setInt(1, newPatientId);
                    psUpdateBed.setInt(2, allocatedBedId);
                    psUpdateBed.executeUpdate();
                }

                // Decrement available beds in resources
                String updateRes = "UPDATE resources SET available_quantity = GREATEST(0, available_quantity - 1) WHERE LOWER(resource_name) LIKE '%bed%'";
                try (PreparedStatement psRes = conn.prepareStatement(updateRes)) {
                    psRes.executeUpdate();
                }
            }

            conn.commit(); // COMMIT TRANSACTION

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("patient_id", newPatientId);
            res.put("patient_name", name);
            res.put("age", age);
            res.put("gender", gender);
            res.put("phone", phone);
            res.put("address", address);
            res.put("diagnosis", diag);
            res.put("department", dept);
            res.put("doctor_id", doctorId);
            res.put("bed_id", allocatedBedId);
            res.put("bed_number", allocatedBedNumber);
            res.put("ward", allocatedWard);
            res.put("admission_status", status);
            res.put("priority_level", priority);
            res.put("message", "Patient registered successfully! " + (allocatedBedId != null ? "Dedicated Bed " + allocatedBedNumber + " allocated." : ""));
            return res;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    public synchronized Map<String, Object> addPatient(String name, int age, String gender, String phone, String address, Integer preferredBedId) throws SQLException {
        return addPatient(name, age, gender, phone, address, "General Consultation", "General Medicine", null, preferredBedId, "Admitted", 2);
    }

    /**
     * Updates an existing patient record, maintaining 1:1 bed allocation integrity.
     */
    public synchronized Map<String, Object> updatePatient(
            int patientId, String name, int age, String gender, String phone, String address,
            String diagnosis, String department, Integer doctorId, Integer newBedId,
            String admissionStatus, Integer priorityLevel) throws SQLException {

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // START TRANSACTION

            // 1. Fetch current patient status and bed
            Integer currentBedId = null;
            String currentStatus = "";
            String selectSql = "SELECT bed_id, admission_status FROM patients WHERE patient_id = ? FOR UPDATE";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentBedId = (Integer) rs.getObject("bed_id");
                        currentStatus = rs.getString("admission_status");
                    } else {
                        conn.rollback();
                        throw new SQLException("Patient not found with ID: " + patientId);
                    }
                }
            }

            String targetStatus = (admissionStatus != null && !admissionStatus.trim().isEmpty()) ? admissionStatus.trim() : currentStatus;
            int priority = (priorityLevel != null && priorityLevel >= 1 && priorityLevel <= 3) ? priorityLevel : 2;
            String diag = (diagnosis != null && !diagnosis.trim().isEmpty()) ? diagnosis.trim() : "General Consultation";
            String dept = (department != null && !department.trim().isEmpty()) ? department.trim() : "General Medicine";

            Integer finalBedId = currentBedId;

            // Scenario A: Patient discharged or changed to Outpatient -> free bed
            if (!"Admitted".equalsIgnoreCase(targetStatus)) {
                if (currentBedId != null) {
                    // Free the bed
                    String freeBed = "UPDATE beds SET bed_status = 'Available', patient_id = NULL WHERE bed_id = ?";
                    try (PreparedStatement psFree = conn.prepareStatement(freeBed)) {
                        psFree.setInt(1, currentBedId);
                        psFree.executeUpdate();
                    }
                    // Increment available beds in resources
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate("UPDATE resources SET available_quantity = LEAST(total_quantity, available_quantity + 1) WHERE LOWER(resource_name) LIKE '%bed%'");
                    }
                    finalBedId = null;
                }
            } else {
                // Scenario B: Patient is Admitted
                if (newBedId != null && !newBedId.equals(currentBedId)) {
                    // Patient requested a specific new bed -> lock and verify availability
                    String checkBed = "SELECT bed_id FROM beds WHERE bed_id = ? AND (bed_status = 'Available' OR patient_id = ?) FOR UPDATE";
                    try (PreparedStatement psChk = conn.prepareStatement(checkBed)) {
                        psChk.setInt(1, newBedId);
                        psChk.setInt(2, patientId);
                        try (ResultSet rsChk = psChk.executeQuery()) {
                            if (!rsChk.next()) {
                                conn.rollback();
                                throw new SQLException("Selected bed #" + newBedId + " is already occupied by another patient!");
                            }
                        }
                    }

                    // Free old bed if any
                    if (currentBedId != null) {
                        try (PreparedStatement psFree = conn.prepareStatement("UPDATE beds SET bed_status = 'Available', patient_id = NULL WHERE bed_id = ?")) {
                            psFree.setInt(1, currentBedId);
                            psFree.executeUpdate();
                        }
                    }

                    // Assign new bed
                    try (PreparedStatement psOccupy = conn.prepareStatement("UPDATE beds SET bed_status = 'Occupied', patient_id = ? WHERE bed_id = ?")) {
                        psOccupy.setInt(1, patientId);
                        psOccupy.setInt(2, newBedId);
                        psOccupy.executeUpdate();
                    }

                    if (currentBedId == null) {
                        try (Statement st = conn.createStatement()) {
                            st.executeUpdate("UPDATE resources SET available_quantity = GREATEST(0, available_quantity - 1) WHERE LOWER(resource_name) LIKE '%bed%'");
                        }
                    }
                    finalBedId = newBedId;

                } else if (currentBedId == null) {
                    // Patient newly marked Admitted without a bed -> auto allocate next available bed
                    int nextBed = 0;
                    try (PreparedStatement psNext = conn.prepareStatement("SELECT bed_id FROM beds WHERE bed_status = 'Available' ORDER BY bed_id ASC LIMIT 1 FOR UPDATE");
                         ResultSet rsNext = psNext.executeQuery()) {
                        if (rsNext.next()) {
                            nextBed = rsNext.getInt("bed_id");
                        } else {
                            conn.rollback();
                            throw new SQLException("Cannot admit patient: No beds available in MySQL.");
                        }
                    }
                    try (PreparedStatement psOccupy = conn.prepareStatement("UPDATE beds SET bed_status = 'Occupied', patient_id = ? WHERE bed_id = ?")) {
                        psOccupy.setInt(1, patientId);
                        psOccupy.setInt(2, nextBed);
                        psOccupy.executeUpdate();
                    }
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate("UPDATE resources SET available_quantity = GREATEST(0, available_quantity - 1) WHERE LOWER(resource_name) LIKE '%bed%'");
                    }
                    finalBedId = nextBed;
                }
            }

            // Update patient record
            String updatePatientSql = "UPDATE patients SET patient_name = ?, age = ?, gender = ?, phone = ?, address = ?, " +
                                      "diagnosis = ?, department = ?, doctor_id = ?, bed_id = ?, admission_status = ?, priority_level = ? " +
                                      "WHERE patient_id = ?";
            try (PreparedStatement psUp = conn.prepareStatement(updatePatientSql)) {
                psUp.setString(1, name);
                psUp.setInt(2, age);
                psUp.setString(3, gender);
                psUp.setString(4, phone);
                psUp.setString(5, address);
                psUp.setString(6, diag);
                psUp.setString(7, dept);
                if (doctorId != null && doctorId > 0) {
                    psUp.setInt(8, doctorId);
                } else {
                    psUp.setNull(8, Types.INTEGER);
                }
                if (finalBedId != null) {
                    psUp.setInt(9, finalBedId);
                } else {
                    psUp.setNull(9, Types.INTEGER);
                }
                psUp.setString(10, targetStatus);
                psUp.setInt(11, priority);
                psUp.setInt(12, patientId);
                psUp.executeUpdate();
            }

            conn.commit(); // COMMIT TRANSACTION

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("patient_id", patientId);
            res.put("patient_name", name);
            res.put("admission_status", targetStatus);
            res.put("bed_id", finalBedId);
            res.put("message", "Patient #" + patientId + " (" + name + ") updated successfully in MySQL.");
            return res;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Deletes a patient, freeing their assigned bed and maintaining transactional consistency.
     */
    public synchronized Map<String, Object> deletePatient(int patientId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // START TRANSACTION

            String patientName = "";
            Integer bedId = null;

            // 1. Check patient and lock row
            String selectSql = "SELECT patient_name, bed_id FROM patients WHERE patient_id = ? FOR UPDATE";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        patientName = rs.getString("patient_name");
                        bedId = (Integer) rs.getObject("bed_id");
                    } else {
                        conn.rollback();
                        throw new SQLException("Patient not found with ID: " + patientId);
                    }
                }
            }

            // 2. Free dedicated bed if occupied
            if (bedId != null) {
                String freeBed = "UPDATE beds SET bed_status = 'Available', patient_id = NULL WHERE bed_id = ?";
                try (PreparedStatement psFree = conn.prepareStatement(freeBed)) {
                    psFree.setInt(1, bedId);
                    psFree.executeUpdate();
                }

                // Increment bed count in resources
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("UPDATE resources SET available_quantity = LEAST(total_quantity, available_quantity + 1) WHERE LOWER(resource_name) LIKE '%bed%'");
                }
            }

            // 3. Delete patient (FK on delete cascade will clear associated appointments and treatment_processes)
            String deleteSql = "DELETE FROM patients WHERE patient_id = ?";
            try (PreparedStatement psDel = conn.prepareStatement(deleteSql)) {
                psDel.setInt(1, patientId);
                psDel.executeUpdate();
            }

            conn.commit(); // COMMIT TRANSACTION

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("patient_id", patientId);
            res.put("patient_name", patientName);
            res.put("message", "Patient #" + patientId + " (" + patientName + ") deleted successfully. Dedicated bed released.");
            return res;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // =============================================================
    // DOCTORS MANAGEMENT (FULL CRUD)
    // =============================================================

    public List<Map<String, Object>> getAllDoctors(String search, String department) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT doctor_id, doctor_name, specialization, " +
            "COALESCE(department, 'General Medicine') AS department, " +
            "phone, COALESCE(availability_status, 'Available') AS availability_status " +
            "FROM doctors WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            String q = "%" + search.trim() + "%";
            sql.append("AND (doctor_name LIKE ? OR specialization LIKE ? OR CAST(doctor_id AS CHAR) LIKE ? OR department LIKE ?) ");
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }
        if (department != null && !department.trim().isEmpty() && !"All".equalsIgnoreCase(department)) {
            sql.append("AND LOWER(department) = LOWER(?) ");
            params.add(department.trim());
        }

        sql.append("ORDER BY doctor_id ASC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("doctor_id", rs.getInt("doctor_id"));
                    row.put("doctor_name", rs.getString("doctor_name"));
                    row.put("specialization", rs.getString("specialization"));
                    row.put("department", rs.getString("department"));
                    row.put("phone", rs.getString("phone"));
                    row.put("availability_status", rs.getString("availability_status"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    public List<Map<String, Object>> getAllDoctors() throws SQLException {
        return getAllDoctors(null, null);
    }

    public Map<String, Object> getDoctorById(int doctorId) throws SQLException {
        String sql = "SELECT doctor_id, doctor_name, specialization, " +
                     "COALESCE(department, 'General Medicine') AS department, " +
                     "phone, COALESCE(availability_status, 'Available') AS availability_status " +
                     "FROM doctors WHERE doctor_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("doctor_id", rs.getInt("doctor_id"));
                    row.put("doctor_name", rs.getString("doctor_name"));
                    row.put("specialization", rs.getString("specialization"));
                    row.put("department", rs.getString("department"));
                    row.put("phone", rs.getString("phone"));
                    row.put("availability_status", rs.getString("availability_status"));
                    return row;
                }
            }
        }
        return null;
    }

    public synchronized Map<String, Object> addDoctor(String name, String specialization, String phone, String department, String availability) throws SQLException {
        String dept = (department != null && !department.trim().isEmpty()) ? department.trim() : "General Medicine";
        String avail = (availability != null && !availability.trim().isEmpty()) ? availability.trim() : "Available";

        String sql = "INSERT INTO doctors (doctor_name, specialization, department, phone, availability_status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, specialization);
            ps.setString(3, dept);
            ps.setString(4, phone);
            ps.setString(5, avail);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("doctor_id", id);
                    res.put("doctor_name", name);
                    res.put("specialization", specialization);
                    res.put("department", dept);
                    res.put("phone", phone);
                    res.put("availability_status", avail);
                    res.put("message", "Doctor " + name + " added successfully.");
                    return res;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated doctor ID");
    }

    public synchronized Map<String, Object> addDoctor(String name, String specialization, String phone) throws SQLException {
        return addDoctor(name, specialization, phone, "General Medicine", "Available");
    }

    public synchronized Map<String, Object> updateDoctor(int doctorId, String name, String specialization, String phone, String department, String availability) throws SQLException {
        String dept = (department != null && !department.trim().isEmpty()) ? department.trim() : "General Medicine";
        String avail = (availability != null && !availability.trim().isEmpty()) ? availability.trim() : "Available";

        String sql = "UPDATE doctors SET doctor_name = ?, specialization = ?, department = ?, phone = ?, availability_status = ? WHERE doctor_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, specialization);
            ps.setString(3, dept);
            ps.setString(4, phone);
            ps.setString(5, avail);
            ps.setInt(6, doctorId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Doctor not found with ID: " + doctorId);
            }

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("doctor_id", doctorId);
            res.put("doctor_name", name);
            res.put("specialization", specialization);
            res.put("department", dept);
            res.put("phone", phone);
            res.put("availability_status", avail);
            res.put("message", "Doctor #" + doctorId + " updated successfully.");
            return res;
        }
    }

    public synchronized Map<String, Object> deleteDoctor(int doctorId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // START TRANSACTION

            String doctorName = "";
            String selectSql = "SELECT doctor_name FROM doctors WHERE doctor_id = ? FOR UPDATE";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, doctorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        doctorName = rs.getString("doctor_name");
                    } else {
                        conn.rollback();
                        throw new SQLException("Doctor not found with ID: " + doctorId);
                    }
                }
            }

            // Unlink patients assigned to this doctor
            try (PreparedStatement psUnlink = conn.prepareStatement("UPDATE patients SET doctor_id = NULL WHERE doctor_id = ?")) {
                psUnlink.setInt(1, doctorId);
                psUnlink.executeUpdate();
            }

            // Delete doctor
            try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM doctors WHERE doctor_id = ?")) {
                psDel.setInt(1, doctorId);
                psDel.executeUpdate();
            }

            conn.commit(); // COMMIT TRANSACTION

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("doctor_id", doctorId);
            res.put("doctor_name", doctorName);
            res.put("message", "Doctor " + doctorName + " deleted successfully.");
            return res;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // =============================================================
    // BEDS & 1:1 PATIENT ASSIGNMENT
    // =============================================================

    public List<Map<String, Object>> getAllBeds() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT b.bed_id, b.bed_number, b.ward, b.bed_status, b.patient_id, " +
                     "COALESCE(p.patient_name, 'None') AS patient_name " +
                     "FROM beds b " +
                     "LEFT JOIN patients p ON b.patient_id = p.patient_id " +
                     "ORDER BY b.bed_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("bed_id", rs.getInt("bed_id"));
                row.put("bed_number", rs.getString("bed_number"));
                row.put("ward", rs.getString("ward"));
                row.put("bed_status", rs.getString("bed_status"));
                row.put("patient_id", rs.getObject("patient_id"));
                row.put("patient_name", rs.getString("patient_name"));
                list.add(row);
            }
        }
        return list;
    }

    public synchronized Map<String, Object> releaseBed(int bedId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // START TRANSACTION

            int patientId = 0;
            String bedNumber = "";
            String selectSql = "SELECT bed_number, patient_id FROM beds WHERE bed_id = ? FOR UPDATE";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, bedId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        bedNumber = rs.getString("bed_number");
                        patientId = rs.getInt("patient_id");
                    } else {
                        conn.rollback();
                        throw new SQLException("Bed not found: " + bedId);
                    }
                }
            }

            // Free bed
            String updateBed = "UPDATE beds SET bed_status = 'Available', patient_id = NULL WHERE bed_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateBed)) {
                ps.setInt(1, bedId);
                ps.executeUpdate();
            }

            // Update patient if assigned
            if (patientId > 0) {
                String updatePatient = "UPDATE patients SET bed_id = NULL, admission_status = 'Discharged' WHERE patient_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updatePatient)) {
                    ps.setInt(1, patientId);
                    ps.executeUpdate();
                }
            }

            // Increment beds in resources
            String updateRes = "UPDATE resources SET available_quantity = LEAST(total_quantity, available_quantity + 1) WHERE LOWER(resource_name) LIKE '%bed%'";
            try (PreparedStatement ps = conn.prepareStatement(updateRes)) {
                ps.executeUpdate();
            }

            conn.commit(); // COMMIT TRANSACTION

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("bed_id", bedId);
            res.put("bed_number", bedNumber);
            res.put("message", "Bed " + bedNumber + " released successfully. Patient discharged.");
            return res;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // =============================================================
    // RESOURCES & ACID ALLOCATION / RELEASE
    // =============================================================

    public List<Map<String, Object>> getAllResources() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT resource_id, resource_name, available_quantity, total_quantity FROM resources ORDER BY resource_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("resource_id", rs.getInt("resource_id"));
                row.put("resource_name", rs.getString("resource_name"));
                row.put("available_quantity", rs.getInt("available_quantity"));
                row.put("total_quantity", rs.getInt("total_quantity"));
                list.add(row);
            }
        }
        return list;
    }

    public synchronized Map<String, Object> addResource(String name, int available, int total) throws SQLException {
        String sql = "INSERT INTO resources (resource_name, available_quantity, total_quantity) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, available);
            ps.setInt(3, total);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("resource_id", id);
                    res.put("resource_name", name);
                    res.put("available_quantity", available);
                    res.put("total_quantity", total);
                    return res;
                }
            }
        }
        throw new SQLException("Failed to add resource");
    }

    public synchronized Map<String, Object> allocateResource(String resourceIdentifier, int quantity) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Allocation quantity must be greater than zero.");
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // START TRANSACTION

            String selectSql;
            boolean isId = resourceIdentifier.matches("\\d+");
            if (isId) {
                selectSql = "SELECT resource_id, resource_name, available_quantity, total_quantity FROM resources WHERE resource_id = ? FOR UPDATE";
            } else {
                selectSql = "SELECT resource_id, resource_name, available_quantity, total_quantity FROM resources WHERE LOWER(resource_name) LIKE LOWER(?) FOR UPDATE";
            }

            int resourceId = 0;
            String resourceName = "";
            int available = 0;
            int total = 0;

            try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                if (isId) {
                    psSelect.setInt(1, Integer.parseInt(resourceIdentifier));
                } else {
                    psSelect.setString(1, "%" + resourceIdentifier.trim() + "%");
                }
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        resourceId = rs.getInt("resource_id");
                        resourceName = rs.getString("resource_name");
                        available = rs.getInt("available_quantity");
                        total = rs.getInt("total_quantity");
                    } else {
                        conn.rollback();
                        throw new SQLException("Resource not found: " + resourceIdentifier);
                    }
                }
            }

            if (quantity > available) {
                conn.rollback();
                throw new SQLException("Allocation failed: Requested " + quantity + " " + resourceName + 
                                      ", but only " + available + " units are available.");
            }

            int newAvailable = available - quantity;
            String updateSql = "UPDATE resources SET available_quantity = ? WHERE resource_id = ?";
            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setInt(1, newAvailable);
                psUpdate.setInt(2, resourceId);
                psUpdate.executeUpdate();
            }

            conn.commit(); // COMMIT TRANSACTION

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("resource_id", resourceId);
            result.put("resource_name", resourceName);
            result.put("allocated_quantity", quantity);
            result.put("available_quantity", newAvailable);
            result.put("total_quantity", total);
            result.put("message", "Allocated " + quantity + " " + resourceName + " successfully.");
            return result;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    public synchronized Map<String, Object> releaseResource(String resourceIdentifier, int quantity) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be greater than zero.");
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // START TRANSACTION

            String selectSql;
            boolean isId = resourceIdentifier.matches("\\d+");
            if (isId) {
                selectSql = "SELECT resource_id, resource_name, available_quantity, total_quantity FROM resources WHERE resource_id = ? FOR UPDATE";
            } else {
                selectSql = "SELECT resource_id, resource_name, available_quantity, total_quantity FROM resources WHERE LOWER(resource_name) LIKE LOWER(?) FOR UPDATE";
            }

            int resourceId = 0;
            String resourceName = "";
            int available = 0;
            int total = 0;

            try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                if (isId) {
                    psSelect.setInt(1, Integer.parseInt(resourceIdentifier));
                } else {
                    psSelect.setString(1, "%" + resourceIdentifier.trim() + "%");
                }
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        resourceId = rs.getInt("resource_id");
                        resourceName = rs.getString("resource_name");
                        available = rs.getInt("available_quantity");
                        total = rs.getInt("total_quantity");
                    } else {
                        conn.rollback();
                        throw new SQLException("Resource not found: " + resourceIdentifier);
                    }
                }
            }

            int currentAllocated = total - available;
            if (quantity > currentAllocated) {
                conn.rollback();
                throw new SQLException("Release failed: Cannot release " + quantity + " " + resourceName + 
                                      ". Only " + currentAllocated + " units are currently allocated.");
            }

            int newAvailable = available + quantity;
            String updateSql = "UPDATE resources SET available_quantity = ? WHERE resource_id = ?";
            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setInt(1, newAvailable);
                psUpdate.setInt(2, resourceId);
                psUpdate.executeUpdate();
            }

            conn.commit(); // COMMIT TRANSACTION

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("resource_id", resourceId);
            result.put("resource_name", resourceName);
            result.put("released_quantity", quantity);
            result.put("available_quantity", newAvailable);
            result.put("total_quantity", total);
            result.put("message", "Released " + quantity + " " + resourceName + " successfully.");
            return result;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // =============================================================
    // APPOINTMENTS
    // =============================================================

    public List<Map<String, Object>> getAllAppointments() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.appointment_id, a.patient_id, a.doctor_id, " +
                     "p.patient_name, d.doctor_name, d.specialization, " +
                     "a.appointment_date, a.appointment_time, a.appointment_status " +
                     "FROM appointments a " +
                     "JOIN patients p ON a.patient_id = p.patient_id " +
                     "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                     "ORDER BY a.appointment_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("appointment_id", rs.getInt("appointment_id"));
                row.put("patient_id", rs.getInt("patient_id"));
                row.put("doctor_id", rs.getInt("doctor_id"));
                row.put("patient_name", rs.getString("patient_name"));
                row.put("doctor_name", rs.getString("doctor_name"));
                row.put("specialization", rs.getString("specialization"));
                row.put("appointment_date", rs.getString("appointment_date"));
                row.put("appointment_time", rs.getString("appointment_time"));
                row.put("appointment_status", rs.getString("appointment_status"));
                list.add(row);
            }
        }
        return list;
    }

    public synchronized Map<String, Object> addAppointment(int patientId, int doctorId, String date, String time, String status) throws SQLException {
        String sql = "INSERT INTO appointments (patient_id, doctor_id, appointment_date, appointment_time, appointment_status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, patientId);
            ps.setInt(2, doctorId);
            ps.setString(3, date);
            ps.setString(4, time);
            ps.setString(5, status == null || status.trim().isEmpty() ? "Scheduled" : status);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("appointment_id", id);
                    res.put("patient_id", patientId);
                    res.put("doctor_id", doctorId);
                    res.put("appointment_date", date);
                    res.put("appointment_time", time);
                    res.put("appointment_status", status);
                    return res;
                }
            }
        }
        throw new SQLException("Failed to add appointment");
    }

    // =============================================================
    // TREATMENT PROCESSES BY WORKLOAD CATEGORY
    // =============================================================

    public List<Map<String, Object>> getAllTreatmentProcesses(String workCategory) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql;
        boolean hasFilter = (workCategory != null && !workCategory.trim().isEmpty() && !"All".equalsIgnoreCase(workCategory) && !"All Tasks".equalsIgnoreCase(workCategory));
        if (hasFilter) {
            sql = "SELECT tp.process_id, tp.patient_id, tp.appointment_id, " +
                  "p.patient_name, tp.process_name, tp.work_category, tp.arrival_time, tp.burst_time, " +
                  "tp.priority, tp.process_status " +
                  "FROM treatment_processes tp " +
                  "JOIN patients p ON tp.patient_id = p.patient_id " +
                  "WHERE LOWER(tp.work_category) = LOWER(?) " +
                  "ORDER BY tp.process_id ASC";
        } else {
            sql = "SELECT tp.process_id, tp.patient_id, tp.appointment_id, " +
                  "p.patient_name, tp.process_name, tp.work_category, tp.arrival_time, tp.burst_time, " +
                  "tp.priority, tp.process_status " +
                  "FROM treatment_processes tp " +
                  "JOIN patients p ON tp.patient_id = p.patient_id " +
                  "ORDER BY tp.process_id ASC";
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (hasFilter) {
                ps.setString(1, workCategory.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("process_id", rs.getInt("process_id"));
                    row.put("patient_id", rs.getInt("patient_id"));
                    row.put("appointment_id", rs.getObject("appointment_id"));
                    row.put("patient_name", rs.getString("patient_name"));
                    row.put("process_name", rs.getString("process_name"));
                    row.put("work_category", rs.getString("work_category"));
                    row.put("arrival_time", rs.getInt("arrival_time"));
                    row.put("burst_time", rs.getInt("burst_time"));
                    row.put("priority", rs.getInt("priority"));
                    row.put("process_status", rs.getString("process_status"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    public synchronized Map<String, Object> addTreatmentProcess(int patientId, Integer appointmentId, String name, String workCategory, int arrival, int burst, int priority) throws SQLException {
        String category = (workCategory != null && !workCategory.trim().isEmpty()) ? workCategory : "Emergency";

        String sql = "INSERT INTO treatment_processes (patient_id, appointment_id, process_name, work_category, arrival_time, burst_time, priority, process_status) VALUES (?, ?, ?, ?, ?, ?, ?, 'Queued')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, patientId);
            if (appointmentId == null || appointmentId <= 0) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, appointmentId);
            }
            ps.setString(3, name);
            ps.setString(4, category);
            ps.setInt(5, arrival);
            ps.setInt(6, burst);
            ps.setInt(7, priority);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("process_id", id);
                    res.put("patient_id", patientId);
                    res.put("appointment_id", appointmentId);
                    res.put("process_name", name);
                    res.put("work_category", category);
                    res.put("arrival_time", arrival);
                    res.put("burst_time", burst);
                    res.put("priority", priority);
                    res.put("process_status", "Queued");
                    return res;
                }
            }
        }
        throw new SQLException("Failed to add treatment process");
    }

    public synchronized Map<String, Object> deleteTreatmentProcess(int processId) throws SQLException {
        String sql = "DELETE FROM treatment_processes WHERE process_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Task not found with ID: " + processId);
            }
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("process_id", processId);
            res.put("message", "Task P-" + processId + " deleted successfully.");
            return res;
        }
    }

    // =============================================================
    // SCHEDULING RUNS & RESULTS PERSISTENCE
    // =============================================================

    public synchronized int saveSchedulingRun(String algorithm, String workCategory, Integer timeQuantum, List<Map<String, Object>> rows) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // START TRANSACTION

            int runId = 0;
            String runSql = "INSERT INTO scheduling_runs (algorithm, work_category, time_quantum) VALUES (?, ?, ?)";
            try (PreparedStatement psRun = conn.prepareStatement(runSql, Statement.RETURN_GENERATED_KEYS)) {
                psRun.setString(1, algorithm);
                psRun.setString(2, workCategory != null ? workCategory : "All Tasks");
                if (timeQuantum == null || timeQuantum <= 0) {
                    psRun.setNull(3, Types.INTEGER);
                } else {
                    psRun.setInt(3, timeQuantum);
                }
                psRun.executeUpdate();
                try (ResultSet keys = psRun.getGeneratedKeys()) {
                    if (keys.next()) {
                        runId = keys.getInt(1);
                    }
                }
            }

            if (runId <= 0) {
                conn.rollback();
                throw new SQLException("Failed to generate scheduling run ID");
            }

            String resultSql = "INSERT INTO scheduling_results " +
                               "(run_id, process_id, process_order, start_time, completion_time, waiting_time, turnaround_time) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psRes = conn.prepareStatement(resultSql)) {
                int order = 1;
                for (Map<String, Object> r : rows) {
                    psRes.setInt(1, runId);
                    psRes.setInt(2, ((Number) r.get("process_id")).intValue());
                    psRes.setInt(3, order++);
                    Number startVal = (Number) r.get("start_time");
                    psRes.setInt(4, startVal != null ? startVal.intValue() : 0);
                    psRes.setInt(5, ((Number) r.get("completion_time")).intValue());
                    psRes.setInt(6, ((Number) r.get("waiting_time")).intValue());
                    psRes.setInt(7, ((Number) r.get("turnaround_time")).intValue());
                    psRes.addBatch();
                }
                psRes.executeBatch();
            }

            conn.commit(); // COMMIT TRANSACTION
            return runId;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    public List<Map<String, Object>> getSchedulingRuns() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT run_id, algorithm, work_category, time_quantum, executed_at FROM scheduling_runs ORDER BY run_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("run_id", rs.getInt("run_id"));
                row.put("algorithm", rs.getString("algorithm"));
                row.put("work_category", rs.getString("work_category"));
                row.put("time_quantum", rs.getObject("time_quantum"));
                row.put("executed_at", rs.getString("executed_at"));
                list.add(row);
            }
        }
        return list;
    }

    public List<Map<String, Object>> getSchedulingResults(int runId) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT sr.result_id, sr.run_id, sr.process_id, sr.process_order, " +
                     "tp.process_name, tp.work_category, tp.arrival_time, tp.burst_time, tp.priority, " +
                     "sr.start_time, sr.completion_time, sr.waiting_time, sr.turnaround_time " +
                     "FROM scheduling_results sr " +
                     "JOIN treatment_processes tp ON sr.process_id = tp.process_id " +
                     "WHERE sr.run_id = ? " +
                     "ORDER BY sr.process_order ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("result_id", rs.getInt("result_id"));
                    row.put("run_id", rs.getInt("run_id"));
                    row.put("process_id", rs.getInt("process_id"));
                    row.put("process_order", rs.getInt("process_order"));
                    row.put("process_name", rs.getString("process_name"));
                    row.put("work_category", rs.getString("work_category"));
                    row.put("arrival_time", rs.getInt("arrival_time"));
                    row.put("burst_time", rs.getInt("burst_time"));
                    row.put("priority", rs.getInt("priority"));
                    row.put("start_time", rs.getInt("start_time"));
                    row.put("completion_time", rs.getInt("completion_time"));
                    row.put("waiting_time", rs.getInt("waiting_time"));
                    row.put("turnaround_time", rs.getInt("turnaround_time"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    // =============================================================
    // RESOURCE ALLOCATIONS (Banker's Algorithm Data)
    // =============================================================

    public List<Map<String, Object>> getResourceAllocations() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT ra.allocation_id, ra.process_id, tp.process_name, p.patient_name, " +
                     "ra.resource_id, r.resource_name, r.available_quantity, " +
                     "ra.allocated_quantity, ra.maximum_required " +
                     "FROM resource_allocations ra " +
                     "JOIN treatment_processes tp ON ra.process_id = tp.process_id " +
                     "JOIN patients p ON tp.patient_id = p.patient_id " +
                     "JOIN resources r ON ra.resource_id = r.resource_id " +
                     "ORDER BY ra.process_id ASC, ra.resource_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("allocation_id", rs.getInt("allocation_id"));
                row.put("process_id", rs.getInt("process_id"));
                row.put("process_name", rs.getString("process_name"));
                row.put("patient_name", rs.getString("patient_name"));
                row.put("resource_id", rs.getInt("resource_id"));
                row.put("resource_name", rs.getString("resource_name"));
                row.put("available_quantity", rs.getInt("available_quantity"));
                row.put("allocated_quantity", rs.getInt("allocated_quantity"));
                row.put("maximum_quantity", rs.getInt("maximum_required"));
                list.add(row);
            }
        }
        return list;
    }
}
