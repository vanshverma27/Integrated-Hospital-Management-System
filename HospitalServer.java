import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * HospitalServer - Lightweight HTTP Server for the Integrated Hospital Management System.
 * 
 * B.Tech 3rd Year OS + DBMS Integrated Project
 * Bridges Frontend (HTML/CSS/JS) <-> Java OS Module <-> JDBC <-> MySQL Database.
 * Built using standard com.sun.net.httpserver.HttpServer (no Spring Boot / Maven needed).
 */
public class HospitalServer {

    private static final int DEFAULT_PORT = 8080;
    private static final HospitalDAO dao = new HospitalDAO();
    private static Path frontendDir;

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            try { port = Integer.parseInt(portEnv.trim()); } catch (NumberFormatException ignored) {}
        }

        // Locate frontend directory
        frontendDir = Paths.get("frontend");
        if (!Files.exists(frontendDir)) {
            frontendDir = Paths.get("../frontend");
        }
        if (!Files.exists(frontendDir)) {
            frontendDir = Paths.get(".").toAbsolutePath();
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        // API Endpoints
        server.createContext("/api/status", HospitalServer::handleStatus);
        server.createContext("/api/patients", HospitalServer::handlePatients);
        server.createContext("/api/patients/update", HospitalServer::handlePatientUpdate);
        server.createContext("/api/patients/delete", HospitalServer::handlePatientDelete);
        server.createContext("/api/doctors", HospitalServer::handleDoctors);
        server.createContext("/api/doctors/update", HospitalServer::handleDoctorUpdate);
        server.createContext("/api/doctors/delete", HospitalServer::handleDoctorDelete);
        server.createContext("/api/beds", HospitalServer::handleBeds);
        server.createContext("/api/beds/release", HospitalServer::handleReleaseBed);
        server.createContext("/api/resources", HospitalServer::handleResources);
        server.createContext("/api/resources/allocate", HospitalServer::handleAllocateResource);
        server.createContext("/api/resources/release", HospitalServer::handleReleaseResource);
        server.createContext("/api/appointments", HospitalServer::handleAppointments);
        server.createContext("/api/processes", HospitalServer::handleProcesses);
        server.createContext("/api/processes/delete", HospitalServer::handleProcessDelete);
        server.createContext("/api/schedule", HospitalServer::handleSchedule);
        server.createContext("/api/banker", HospitalServer::handleBanker);
        server.createContext("/api/history", HospitalServer::handleHistory);
        server.createContext("/api/allocations", HospitalServer::handleAllocations);

        // Static Files Handler (serves index.html, styles.css, app.js)
        server.createContext("/", HospitalServer::handleStaticFiles);

        server.start();

        System.out.println("==================================================================");
        System.out.println("  SMART HOSPITAL RESOURCE & TREATMENT MANAGEMENT SYSTEM SERVER   ");
        System.out.println("  B.Tech 3rd Year Operating Systems & Database Project (Active)  ");
        System.out.println("==================================================================");
        System.out.println("  Web Interface : http://localhost:" + port + "/");
        System.out.println("  API Endpoint  : http://localhost:" + port + "/api/status");
        System.out.println("  Database URL  : " + DBConnection.getUrl());
        System.out.println("  Database User : " + DBConnection.getUser());
        System.out.println("  DB Status     : " + (DBConnection.isAvailable() ? "Connected to MySQL (model_db)" : "In-Memory Simulation Mode (MySQL Standby)"));
        System.out.println("==================================================================");
    }

    // =============================================================
    // HTTP HELPERS & CORS
    // =============================================================

    private static void setCorsHeaders(HttpExchange exchange) {
        Headers h = exchange.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
    }

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        setCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        String json = SimpleJson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", true);
        err.put("message", message);
        sendJsonResponse(exchange, statusCode, err);
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=", 2);
            if (entry.length > 1) {
                result.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
                           URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
            } else if (entry.length == 1 && !entry[0].isEmpty()) {
                result.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8), "");
            }
        }
        return result;
    }

    // =============================================================
    // STATIC FILE SERVER
    // =============================================================

    private static void handleStaticFiles(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;

        String pathStr = exchange.getRequestURI().getPath();
        if (pathStr.startsWith("/api/")) {
            sendError(exchange, 404, "Endpoint not found: " + pathStr);
            return;
        }

        if ("/".equals(pathStr) || pathStr.isEmpty()) {
            pathStr = "/index.html";
        }

        Path file = frontendDir.resolve(pathStr.substring(1)).normalize();
        if (!Files.exists(file) || Files.isDirectory(file)) {
            file = Paths.get(".").resolve(pathStr.substring(1)).normalize();
        }

        if (!Files.exists(file) || Files.isDirectory(file)) {
            sendError(exchange, 404, "Static file not found: " + pathStr);
            return;
        }

        String mime = "application/octet-stream";
        if (pathStr.endsWith(".html")) mime = "text/html; charset=UTF-8";
        else if (pathStr.endsWith(".css")) mime = "text/css; charset=UTF-8";
        else if (pathStr.endsWith(".js")) mime = "application/javascript; charset=UTF-8";
        else if (pathStr.endsWith(".json")) mime = "application/json; charset=UTF-8";
        else if (pathStr.endsWith(".svg")) mime = "image/svg+xml";
        else if (pathStr.endsWith(".png")) mime = "image/png";

        byte[] bytes = Files.readAllBytes(file);
        setCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // =============================================================
    // API HANDLERS
    // =============================================================

    private static void handleStatus(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        Map<String, Object> resp = new LinkedHashMap<>();
        boolean dbOk = DBConnection.isAvailable();
        resp.put("status", "online");
        resp.put("db_connected", dbOk);
        resp.put("db_url", DBConnection.getUrl());
        resp.put("db_user", DBConnection.getUser());
        
        try {
            resp.put("total_patients", dao.getAllPatients(null, null).size());
            resp.put("total_doctors", dao.getAllDoctors(null, null).size());
            resp.put("total_beds", dao.getAllBeds().size());
            resp.put("total_tasks", dao.getAllTreatmentProcesses(null).size());
        } catch (Exception ignored) {}

        resp.put("message", dbOk ? "Connected to MySQL database (model_db)." : "Running in In-Memory Mode (Start MySQL service to persist data).");
        sendJsonResponse(exchange, 200, resp);
    }

    // -------------------------------------------------------------
    // PATIENTS (FULL CRUD)
    // -------------------------------------------------------------

    private static void handlePatients(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        String method = exchange.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> qMap = queryToMap(query);
                String search = qMap.get("search");
                String dept = qMap.get("department");
                List<Map<String, Object>> patients = dao.getAllPatients(search, dept);
                sendJsonResponse(exchange, 200, patients);

            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, Object> json = SimpleJson.parseObject(body);
                String name = (String) json.get("patient_name");
                Number ageNum = (Number) json.get("age");
                String gender = (String) json.get("gender");
                String phone = (String) json.get("phone");
                String address = (String) json.get("address");
                String diag = (String) json.get("diagnosis");
                String dept = (String) json.get("department");
                Number docNum = (Number) json.get("doctor_id");
                Number bedNum = (Number) json.get("bed_id");
                String status = (String) json.get("admission_status");
                Number prioNum = (Number) json.get("priority_level");

                if (name == null || name.trim().isEmpty() || ageNum == null) {
                    sendError(exchange, 400, "Patient name and age are required.");
                    return;
                }

                Integer doctorId = docNum != null ? docNum.intValue() : null;
                Integer preferredBedId = bedNum != null ? bedNum.intValue() : null;
                Integer priorityLevel = prioNum != null ? prioNum.intValue() : 2;

                Map<String, Object> res = dao.addPatient(
                    name.trim(), ageNum.intValue(), gender, phone, address,
                    diag, dept, doctorId, preferredBedId, status, priorityLevel
                );
                sendJsonResponse(exchange, 201, res);

            } else if ("PUT".equalsIgnoreCase(method)) {
                executePatientUpdate(exchange);

            } else if ("DELETE".equalsIgnoreCase(method)) {
                executePatientDelete(exchange);

            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (SQLException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private static void handlePatientUpdate(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) || "PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            executePatientUpdate(exchange);
        } else {
            sendError(exchange, 405, "Method not allowed");
        }
    }

    private static void executePatientUpdate(HttpExchange exchange) throws IOException {
        try {
            String body = readRequestBody(exchange);
            Map<String, Object> json = SimpleJson.parseObject(body);
            Number pidNum = (Number) json.get("patient_id");
            String name = (String) json.get("patient_name");
            Number ageNum = (Number) json.get("age");
            String gender = (String) json.get("gender");
            String phone = (String) json.get("phone");
            String address = (String) json.get("address");
            String diag = (String) json.get("diagnosis");
            String dept = (String) json.get("department");
            Number docNum = (Number) json.get("doctor_id");
            Number bedNum = (Number) json.get("bed_id");
            String status = (String) json.get("admission_status");
            Number prioNum = (Number) json.get("priority_level");

            if (pidNum == null || name == null || name.trim().isEmpty() || ageNum == null) {
                sendError(exchange, 400, "Patient ID, name, and age are required for update.");
                return;
            }

            Integer doctorId = docNum != null ? docNum.intValue() : null;
            Integer newBedId = bedNum != null ? bedNum.intValue() : null;
            Integer priorityLevel = prioNum != null ? prioNum.intValue() : 2;

            Map<String, Object> res = dao.updatePatient(
                pidNum.intValue(), name.trim(), ageNum.intValue(), gender, phone, address,
                diag, dept, doctorId, newBedId, status, priorityLevel
            );
            sendJsonResponse(exchange, 200, res);

        } catch (SQLException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private static void handlePatientDelete(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) || "DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            executePatientDelete(exchange);
        } else {
            sendError(exchange, 405, "Method not allowed");
        }
    }

    private static void executePatientDelete(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> qMap = queryToMap(query);
            String idStr = qMap.get("id");
            if (idStr == null || idStr.trim().isEmpty()) {
                idStr = qMap.get("patient_id");
            }

            if (idStr == null || idStr.trim().isEmpty()) {
                String body = readRequestBody(exchange);
                if (body != null && !body.trim().isEmpty()) {
                    Map<String, Object> json = SimpleJson.parseObject(body);
                    Number pidNum = (Number) json.get("patient_id");
                    if (pidNum != null) idStr = String.valueOf(pidNum.intValue());
                }
            }

            if (idStr == null || idStr.trim().isEmpty()) {
                sendError(exchange, 400, "patient_id is required to delete patient.");
                return;
            }

            int pid = Integer.parseInt(idStr.trim());
            Map<String, Object> res = dao.deletePatient(pid);
            sendJsonResponse(exchange, 200, res);

        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid patient ID format.");
        } catch (SQLException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    // -------------------------------------------------------------
    // DOCTORS (FULL CRUD)
    // -------------------------------------------------------------

    private static void handleDoctors(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        String method = exchange.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> qMap = queryToMap(query);
                String search = qMap.get("search");
                String dept = qMap.get("department");
                List<Map<String, Object>> docs = dao.getAllDoctors(search, dept);
                sendJsonResponse(exchange, 200, docs);

            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, Object> json = SimpleJson.parseObject(body);
                String name = (String) json.get("doctor_name");
                String spec = (String) json.get("specialization");
                String dept = (String) json.get("department");
                String phone = (String) json.get("phone");
                String avail = (String) json.get("availability_status");

                if (name == null || spec == null) {
                    sendError(exchange, 400, "Doctor name and specialization are required.");
                    return;
                }

                Map<String, Object> res = dao.addDoctor(name.trim(), spec.trim(), phone, dept, avail);
                sendJsonResponse(exchange, 201, res);

            } else if ("PUT".equalsIgnoreCase(method)) {
                executeDoctorUpdate(exchange);

            } else if ("DELETE".equalsIgnoreCase(method)) {
                executeDoctorDelete(exchange);

            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (SQLException e) {
            sendError(exchange, 500, "Database error: " + e.getMessage());
        }
    }

    private static void handleDoctorUpdate(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) || "PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            executeDoctorUpdate(exchange);
        } else {
            sendError(exchange, 405, "Method not allowed");
        }
    }

    private static void executeDoctorUpdate(HttpExchange exchange) throws IOException {
        try {
            String body = readRequestBody(exchange);
            Map<String, Object> json = SimpleJson.parseObject(body);
            Number didNum = (Number) json.get("doctor_id");
            String name = (String) json.get("doctor_name");
            String spec = (String) json.get("specialization");
            String dept = (String) json.get("department");
            String phone = (String) json.get("phone");
            String avail = (String) json.get("availability_status");

            if (didNum == null || name == null || spec == null) {
                sendError(exchange, 400, "Doctor ID, name, and specialization are required for update.");
                return;
            }

            Map<String, Object> res = dao.updateDoctor(didNum.intValue(), name.trim(), spec.trim(), phone, dept, avail);
            sendJsonResponse(exchange, 200, res);

        } catch (SQLException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private static void handleDoctorDelete(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) || "DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            executeDoctorDelete(exchange);
        } else {
            sendError(exchange, 405, "Method not allowed");
        }
    }

    private static void executeDoctorDelete(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> qMap = queryToMap(query);
            String idStr = qMap.get("id");
            if (idStr == null || idStr.trim().isEmpty()) {
                idStr = qMap.get("doctor_id");
            }

            if (idStr == null || idStr.trim().isEmpty()) {
                String body = readRequestBody(exchange);
                if (body != null && !body.trim().isEmpty()) {
                    Map<String, Object> json = SimpleJson.parseObject(body);
                    Number didNum = (Number) json.get("doctor_id");
                    if (didNum != null) idStr = String.valueOf(didNum.intValue());
                }
            }

            if (idStr == null || idStr.trim().isEmpty()) {
                sendError(exchange, 400, "doctor_id is required to delete doctor.");
                return;
            }

            int did = Integer.parseInt(idStr.trim());
            Map<String, Object> res = dao.deleteDoctor(did);
            sendJsonResponse(exchange, 200, res);

        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid doctor ID format.");
        } catch (SQLException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    // -------------------------------------------------------------
    // BEDS & RESOURCES
    // -------------------------------------------------------------

    private static void handleBeds(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        try {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<Map<String, Object>> beds = dao.getAllBeds();
                sendJsonResponse(exchange, 200, beds);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (SQLException e) {
            sendError(exchange, 500, "Database error: " + e.getMessage());
        }
    }

    private static void handleReleaseBed(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            String body = readRequestBody(exchange);
            Map<String, Object> json = SimpleJson.parseObject(body);
            Number bedIdNum = (Number) json.get("bed_id");
            if (bedIdNum == null) {
                sendError(exchange, 400, "bed_id is required to release bed.");
                return;
            }

            Map<String, Object> res = dao.releaseBed(bedIdNum.intValue());
            sendJsonResponse(exchange, 200, res);
        } catch (SQLException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private static void handleResources(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        String method = exchange.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                List<Map<String, Object>> res = dao.getAllResources();
                sendJsonResponse(exchange, 200, res);
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, Object> json = SimpleJson.parseObject(body);
                String name = (String) json.get("resource_name");
                Number avail = (Number) json.get("available_quantity");
                Number total = (Number) json.get("total_quantity");

                if (name == null || avail == null || total == null) {
                    sendError(exchange, 400, "Name, available quantity, and total quantity are required.");
                    return;
                }

                Map<String, Object> r = dao.addResource(name.trim(), avail.intValue(), total.intValue());
                sendJsonResponse(exchange, 201, r);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (SQLException e) {
            sendError(exchange, 500, "Database error: " + e.getMessage());
        }
    }

    private static void handleAllocateResource(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            String body = readRequestBody(exchange);
            Map<String, Object> json = SimpleJson.parseObject(body);
            Object resKeyObj = json.get("resource");
            if (resKeyObj == null) resKeyObj = json.get("resource_id");
            Number qtyNum = (Number) json.get("quantity");

            if (resKeyObj == null || qtyNum == null) {
                sendError(exchange, 400, "Resource and quantity are required.");
                return;
            }

            int qty = qtyNum.intValue();
            String key = resKeyObj.toString();
            Map<String, Object> result = dao.allocateResource(key, qty);
            sendJsonResponse(exchange, 200, result);
        } catch (IllegalArgumentException | SQLException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private static void handleReleaseResource(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            String body = readRequestBody(exchange);
            Map<String, Object> json = SimpleJson.parseObject(body);
            Object resKeyObj = json.get("resource");
            if (resKeyObj == null) resKeyObj = json.get("resource_id");
            Number qtyNum = (Number) json.get("quantity");

            if (resKeyObj == null || qtyNum == null) {
                sendError(exchange, 400, "Resource and quantity are required.");
                return;
            }

            int qty = qtyNum.intValue();
            String key = resKeyObj.toString();
            Map<String, Object> result = dao.releaseResource(key, qty);
            sendJsonResponse(exchange, 200, result);
        } catch (IllegalArgumentException | SQLException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private static void handleAppointments(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        String method = exchange.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                List<Map<String, Object>> appts = dao.getAllAppointments();
                sendJsonResponse(exchange, 200, appts);
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, Object> json = SimpleJson.parseObject(body);
                Number pid = (Number) json.get("patient_id");
                Number did = (Number) json.get("doctor_id");
                String date = (String) json.get("appointment_date");
                String time = (String) json.get("appointment_time");
                String status = (String) json.get("appointment_status");

                if (pid == null || did == null || date == null || time == null) {
                    sendError(exchange, 400, "Patient ID, Doctor ID, date, and time are required.");
                    return;
                }

                Map<String, Object> res = dao.addAppointment(pid.intValue(), did.intValue(), date, time, status);
                sendJsonResponse(exchange, 201, res);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (SQLException e) {
            sendError(exchange, 500, "Database error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------
    // TREATMENT PROCESSES BY WORKLOAD CATEGORY
    // -------------------------------------------------------------

    private static void handleProcesses(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        String method = exchange.getRequestMethod();
        try {
            if ("GET".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> qMap = queryToMap(query);
                String category = qMap.get("category");
                List<Map<String, Object>> list = dao.getAllTreatmentProcesses(category);
                sendJsonResponse(exchange, 200, list);
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, Object> json = SimpleJson.parseObject(body);
                Number pid = (Number) json.get("patient_id");
                Number apptId = (Number) json.get("appointment_id");
                String name = (String) json.get("process_name");
                String category = (String) json.get("work_category");
                Number arr = (Number) json.get("arrival_time");
                Number burst = (Number) json.get("burst_time");
                Number prio = (Number) json.get("priority");

                if (pid == null || name == null || burst == null) {
                    sendError(exchange, 400, "Patient ID, process name, and burst time are required.");
                    return;
                }

                Map<String, Object> res = dao.addTreatmentProcess(
                    pid.intValue(),
                    apptId != null ? apptId.intValue() : null,
                    name.trim(),
                    category != null ? category.trim() : "Emergency",
                    arr != null ? arr.intValue() : 0,
                    burst.intValue(),
                    prio != null ? prio.intValue() : 1
                );
                sendJsonResponse(exchange, 201, res);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                executeProcessDelete(exchange);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (SQLException e) {
            sendError(exchange, 500, "Database error: " + e.getMessage());
        }
    }

    private static void handleProcessDelete(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) || "DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            executeProcessDelete(exchange);
        } else {
            sendError(exchange, 405, "Method not allowed");
        }
    }

    private static void executeProcessDelete(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> qMap = queryToMap(query);
            String idStr = qMap.get("id");
            if (idStr == null || idStr.trim().isEmpty()) {
                idStr = qMap.get("process_id");
            }
            if (idStr == null || idStr.trim().isEmpty()) {
                String body = readRequestBody(exchange);
                if (body != null && !body.trim().isEmpty()) {
                    Map<String, Object> json = SimpleJson.parseObject(body);
                    Number pnum = (Number) json.get("process_id");
                    if (pnum != null) idStr = String.valueOf(pnum.intValue());
                }
            }
            if (idStr == null || idStr.trim().isEmpty()) {
                sendError(exchange, 400, "process_id is required to delete task.");
                return;
            }
            int procId = Integer.parseInt(idStr.trim());
            Map<String, Object> res = dao.deleteTreatmentProcess(procId);
            sendJsonResponse(exchange, 200, res);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid process ID.");
        } catch (SQLException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    // -------------------------------------------------------------
    // SCHEDULING (WORKLOAD DISPATCHER)
    // -------------------------------------------------------------

    private static void handleSchedule(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            String body = readRequestBody(exchange);
            Map<String, Object> json = SimpleJson.parseObject(body);
            String category = (String) json.get("work_category");
            String requestedAlgo = (String) json.get("algorithm");
            Number qNum = (Number) json.get("quantum");
            int quantum = (qNum != null && qNum.intValue() > 0) ? qNum.intValue() : 2;

            // Load tasks from database based on workload
            List<Map<String, Object>> dbProcesses = dao.getAllTreatmentProcesses(category);
            List<HospitalOSModule.Task> tasks = new ArrayList<>();

            for (Map<String, Object> p : dbProcesses) {
                int pid = ((Number) p.get("process_id")).intValue();
                String name = (String) p.get("process_name");
                String cat = (String) p.get("work_category");
                int arr = ((Number) p.get("arrival_time")).intValue();
                int burst = ((Number) p.get("burst_time")).intValue();
                int prio = ((Number) p.get("priority")).intValue();
                tasks.add(new HospitalOSModule.Task(pid, name, cat, arr, burst, prio));
            }

            if (tasks.isEmpty()) {
                sendError(exchange, 400, "No treatment processes found for category: " + (category != null ? category : "All Tasks"));
                return;
            }

            // Determine algorithm to run
            String algorithmToRun = (requestedAlgo != null && !requestedAlgo.trim().isEmpty()) ? requestedAlgo : HospitalOSModule.getRecommendedAlgorithm(category);

            HospitalOSModule.ScheduleOutput output;
            if ("Round Robin".equalsIgnoreCase(algorithmToRun)) {
                output = HospitalOSModule.scheduleRoundRobin(tasks, quantum);
            } else {
                output = HospitalOSModule.scheduleNonPreemptive(tasks, algorithmToRun);
            }

            output.workCategory = (category != null && !category.trim().isEmpty()) ? category : "All Tasks";
            output.rationale = HospitalOSModule.getWorkloadRationale(category, algorithmToRun);

            // Persist run and results into MySQL
            Map<String, Object> resultMap = output.toMap();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) resultMap.get("rows");
            int runId = dao.saveSchedulingRun(algorithmToRun, output.workCategory, "Round Robin".equalsIgnoreCase(algorithmToRun) ? quantum : null, rows);
            resultMap.put("run_id", runId);

            sendJsonResponse(exchange, 200, resultMap);

        } catch (SQLException e) {
            sendError(exchange, 500, "Database error during scheduling: " + e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "Scheduling execution error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------
    // BANKER'S ALGORITHM
    // -------------------------------------------------------------

    private static void handleBanker(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            String body = readRequestBody(exchange);
            Map<String, Object> json = SimpleJson.parseObject(body);

            Object availObj = json.get("available");
            Object allocObj = json.get("allocation");
            Object maxObj = json.get("max");
            Object patientsObj = json.get("patients");

            int[] available;
            int[][] allocation;
            int[][] max;
            String[] names;

            if (availObj instanceof List && allocObj instanceof List && maxObj instanceof List) {
                @SuppressWarnings("unchecked") List<?> avList = (List<?>) availObj;
                available = new int[avList.size()];
                for (int i = 0; i < avList.size(); i++) available[i] = ((Number) avList.get(i)).intValue();

                @SuppressWarnings("unchecked") List<?> alList = (List<?>) allocObj;
                int n = alList.size();
                allocation = new int[n][available.length];
                for (int i = 0; i < n; i++) {
                    @SuppressWarnings("unchecked") List<?> row = (List<?>) alList.get(i);
                    for (int j = 0; j < row.size(); j++) allocation[i][j] = ((Number) row.get(j)).intValue();
                }

                @SuppressWarnings("unchecked") List<?> mList = (List<?>) maxObj;
                max = new int[n][available.length];
                for (int i = 0; i < n; i++) {
                    @SuppressWarnings("unchecked") List<?> row = (List<?>) mList.get(i);
                    for (int j = 0; j < row.size(); j++) max[i][j] = ((Number) row.get(j)).intValue();
                }

                if (patientsObj instanceof List) {
                    @SuppressWarnings("unchecked") List<?> pList = (List<?>) patientsObj;
                    names = new String[pList.size()];
                    for (int i = 0; i < pList.size(); i++) names[i] = String.valueOf(pList.get(i));
                } else {
                    names = new String[n];
                    for (int i = 0; i < n; i++) names[i] = "P-" + (i + 1);
                }
            } else {
                sendError(exchange, 400, "Banker payload must provide available, allocation, and max matrices.");
                return;
            }

            HospitalOSModule.BankerOutput output = HospitalOSModule.runBankersAlgorithm(available, allocation, max, names);
            sendJsonResponse(exchange, 200, output.toMap());

        } catch (Exception e) {
            sendError(exchange, 500, "Banker evaluation error: " + e.getMessage());
        }
    }

    private static void handleHistory(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        try {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<Map<String, Object>> runs = dao.getSchedulingRuns();
                sendJsonResponse(exchange, 200, runs);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (SQLException e) {
            sendError(exchange, 500, "Database error: " + e.getMessage());
        }
    }

    private static void handleAllocations(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) return;
        try {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<Map<String, Object>> allocs = dao.getResourceAllocations();
                sendJsonResponse(exchange, 200, allocs);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (SQLException e) {
            sendError(exchange, 500, "Database error: " + e.getMessage());
        }
    }
}
