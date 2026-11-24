-- =====================================================
-- SMART HOSPITAL RESOURCE AND TREATMENT MANAGEMENT SYSTEM
-- Database: model_db (B.Tech 3rd Year OS + DBMS Integrated Project)
-- =====================================================

CREATE DATABASE IF NOT EXISTS model_db;
USE model_db;

-- -----------------------------------------------------
-- Table: doctors (Medical Specialists & Clinical Staff)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS doctors (
    doctor_id INT AUTO_INCREMENT PRIMARY KEY,
    doctor_name VARCHAR(100) NOT NULL,
    specialization VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL DEFAULT 'General Medicine',
    phone VARCHAR(20) UNIQUE,
    availability_status VARCHAR(50) NOT NULL DEFAULT 'Available'
);

-- -----------------------------------------------------
-- Table: beds (Dedicated 1:1 Bed Allocation per Patient)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS beds (
    bed_id INT AUTO_INCREMENT PRIMARY KEY,
    bed_number VARCHAR(20) NOT NULL UNIQUE,
    ward VARCHAR(50) NOT NULL,
    bed_status VARCHAR(20) NOT NULL DEFAULT 'Available',
    patient_id INT UNIQUE, -- At most one patient per bed (1:1 relationship)
    CONSTRAINT chk_bed_status CHECK (bed_status IN ('Available', 'Occupied', 'In Maintenance'))
);

-- -----------------------------------------------------
-- Table: patients (Inpatient & Outpatient Registry)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS patients (
    patient_id INT AUTO_INCREMENT PRIMARY KEY,
    patient_name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    gender VARCHAR(20),
    phone VARCHAR(20) UNIQUE,
    address VARCHAR(255),
    diagnosis VARCHAR(255) DEFAULT 'General Consultation',
    department VARCHAR(100) DEFAULT 'General Medicine',
    doctor_id INT NULL,
    bed_id INT UNIQUE, -- Exactly one bed assigned to each admitted patient
    admission_status VARCHAR(20) DEFAULT 'Admitted',
    priority_level INT DEFAULT 2, -- 1 = High/Critical, 2 = Moderate, 3 = Low
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bed_id) REFERENCES beds(bed_id) ON DELETE SET NULL,
    FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id) ON DELETE SET NULL
);

-- -----------------------------------------------------
-- Table: resources (OS shared resources: Beds, Doctors, Devices, etc.)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS resources (
    resource_id INT AUTO_INCREMENT PRIMARY KEY,
    resource_name VARCHAR(100) NOT NULL UNIQUE,
    available_quantity INT NOT NULL DEFAULT 0,
    total_quantity INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_resource_quantities CHECK (available_quantity >= 0 AND total_quantity >= 0 AND available_quantity <= total_quantity)
);

-- -----------------------------------------------------
-- Table: appointments
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS appointments (
    appointment_id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    doctor_id INT NOT NULL,
    appointment_date DATE NOT NULL,
    appointment_time TIME NOT NULL,
    appointment_status VARCHAR(20) DEFAULT 'Scheduled',
    FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE,
    FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id) ON DELETE CASCADE
);

-- -----------------------------------------------------
-- Table: treatment_processes (OS Scheduling Tasks by Workload)
-- 5 Distinct Workload Categories:
-- 1. Emergency   --> Priority Scheduling
-- 2. Interactive --> Round Robin Scheduling
-- 3. OPD         --> Round Robin / Time-Shared
-- 4. Diagnostic  --> Shortest Job First (SJF)
-- 5. Batch       --> First-Come, First-Served (FCFS)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS treatment_processes (
    process_id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    appointment_id INT NULL,
    process_name VARCHAR(100) NOT NULL,
    work_category VARCHAR(50) NOT NULL DEFAULT 'Emergency',
    arrival_time INT NOT NULL DEFAULT 0,
    burst_time INT NOT NULL DEFAULT 1,
    priority INT NOT NULL DEFAULT 1, -- Priority 1 = Highest
    process_status VARCHAR(20) DEFAULT 'Queued',
    FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE,
    FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id) ON DELETE SET NULL,
    CONSTRAINT chk_process_times CHECK (arrival_time >= 0 AND burst_time > 0 AND priority >= 1)
);

-- -----------------------------------------------------
-- Table: scheduling_runs
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS scheduling_runs (
    run_id INT AUTO_INCREMENT PRIMARY KEY,
    algorithm VARCHAR(50) NOT NULL,
    work_category VARCHAR(50) DEFAULT 'All Tasks',
    time_quantum INT NULL,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------
-- Table: scheduling_results
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS scheduling_results (
    result_id INT AUTO_INCREMENT PRIMARY KEY,
    run_id INT NOT NULL,
    process_id INT NOT NULL,
    process_order INT NOT NULL,
    start_time INT NOT NULL,
    completion_time INT NOT NULL,
    waiting_time INT NOT NULL,
    turnaround_time INT NOT NULL,
    FOREIGN KEY (run_id) REFERENCES scheduling_runs(run_id) ON DELETE CASCADE,
    FOREIGN KEY (process_id) REFERENCES treatment_processes(process_id) ON DELETE CASCADE
);

-- -----------------------------------------------------
-- Table: resource_allocations (Banker's Algorithm Matrix)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS resource_allocations (
    allocation_id INT AUTO_INCREMENT PRIMARY KEY,
    process_id INT NOT NULL,
    resource_id INT NOT NULL,
    allocated_quantity INT NOT NULL DEFAULT 0,
    maximum_required INT NOT NULL,
    FOREIGN KEY (process_id) REFERENCES treatment_processes(process_id) ON DELETE CASCADE,
    FOREIGN KEY (resource_id) REFERENCES resources(resource_id) ON DELETE CASCADE,
    CONSTRAINT chk_allocated_quantity CHECK (allocated_quantity >= 0),
    CONSTRAINT chk_maximum_required CHECK (maximum_required >= 0),
    CONSTRAINT chk_allocation_limit CHECK (allocated_quantity <= maximum_required),
    UNIQUE (process_id, resource_id)
);

-- =====================================================
-- INITIAL SEED DATA (60 Beds, 10 Doctors, 50 Patients, 25 Workload Tasks)
-- =====================================================

-- 1. Doctors (10 Specialists across key departments)
INSERT IGNORE INTO doctors (doctor_id, doctor_name, specialization, department, phone, availability_status) VALUES
(1, 'Dr. Arun Kumar', 'Cardiologist', 'Cardiology', '9876500001', 'Available'),
(2, 'Dr. Sunita Rao', 'Neurologist', 'Neurology', '9876500002', 'On Duty'),
(3, 'Dr. Rajesh Sharma', 'Orthopedic Surgeon', 'Orthopedics', '9876500003', 'In Surgery'),
(4, 'Dr. Meera Nambiar', 'Pediatrician', 'Pediatrics', '9876500004', 'Available'),
(5, 'Dr. Sanjay Gupta', 'Medical Oncologist', 'Oncology', '9876500005', 'On Duty'),
(6, 'Dr. Ananya Sen', 'General & Laparoscopic Surgeon', 'General Surgery', '9876500006', 'In Surgery'),
(7, 'Dr. Vikram Joshi', 'Pulmonologist & Intensivist', 'Pulmonology', '9876500007', 'Available'),
(8, 'Dr. Neha Kapoor', 'Diagnostic Radiologist', 'Radiology', '9876500008', 'Available'),
(9, 'Dr. Rakesh Verma', 'Emergency Medicine Specialist', 'Emergency', '9876500009', 'On Duty'),
(10, 'Dr. Priya Pillai', 'Clinical Pathologist', 'Pathology', '9876500010', 'Available');

-- 2. Beds (60 Beds: General, Semi-Private, ICU, Emergency)
INSERT IGNORE INTO beds (bed_id, bed_number, ward, bed_status, patient_id) VALUES
(1, 'A-101', 'General Ward', 'Available', NULL),
(2, 'A-102', 'General Ward', 'Available', NULL),
(3, 'A-103', 'General Ward', 'Available', NULL),
(4, 'A-104', 'General Ward', 'Available', NULL),
(5, 'A-105', 'General Ward', 'Available', NULL),
(6, 'A-106', 'General Ward', 'Available', NULL),
(7, 'A-107', 'General Ward', 'Available', NULL),
(8, 'A-108', 'General Ward', 'Available', NULL),
(9, 'A-109', 'General Ward', 'Available', NULL),
(10, 'A-110', 'General Ward', 'Available', NULL),
(11, 'A-111', 'General Ward', 'Available', NULL),
(12, 'A-112', 'General Ward', 'Available', NULL),
(13, 'A-113', 'General Ward', 'Available', NULL),
(14, 'A-114', 'General Ward', 'Available', NULL),
(15, 'A-115', 'General Ward', 'Available', NULL),
(16, 'A-116', 'General Ward', 'Available', NULL),
(17, 'A-117', 'General Ward', 'Available', NULL),
(18, 'A-118', 'General Ward', 'Available', NULL),
(19, 'A-119', 'General Ward', 'Available', NULL),
(20, 'A-120', 'General Ward', 'Available', NULL),
(21, 'A-121', 'General Ward', 'Available', NULL),
(22, 'A-122', 'General Ward', 'Available', NULL),
(23, 'A-123', 'General Ward', 'Available', NULL),
(24, 'A-124', 'General Ward', 'Available', NULL),
(25, 'A-125', 'General Ward', 'Available', NULL),
(26, 'B-101', 'Semi-Private Ward', 'Available', NULL),
(27, 'B-102', 'Semi-Private Ward', 'Available', NULL),
(28, 'B-103', 'Semi-Private Ward', 'Available', NULL),
(29, 'B-104', 'Semi-Private Ward', 'Available', NULL),
(30, 'B-105', 'Semi-Private Ward', 'Available', NULL),
(31, 'B-106', 'Semi-Private Ward', 'Available', NULL),
(32, 'B-107', 'Semi-Private Ward', 'Available', NULL),
(33, 'B-108', 'Semi-Private Ward', 'Available', NULL),
(34, 'B-109', 'Semi-Private Ward', 'Available', NULL),
(35, 'B-110', 'Semi-Private Ward', 'Available', NULL),
(36, 'B-111', 'Semi-Private Ward', 'Available', NULL),
(37, 'B-112', 'Semi-Private Ward', 'Available', NULL),
(38, 'B-113', 'Semi-Private Ward', 'Available', NULL),
(39, 'B-114', 'Semi-Private Ward', 'Available', NULL),
(40, 'B-115', 'Semi-Private Ward', 'Available', NULL),
(41, 'ICU-01', 'ICU', 'Available', NULL),
(42, 'ICU-02', 'ICU', 'Available', NULL),
(43, 'ICU-03', 'ICU', 'Available', NULL),
(44, 'ICU-04', 'ICU', 'Available', NULL),
(45, 'ICU-05', 'ICU', 'Available', NULL),
(46, 'ICU-06', 'ICU', 'Available', NULL),
(47, 'ICU-07', 'ICU', 'Available', NULL),
(48, 'ICU-08', 'ICU', 'Available', NULL),
(49, 'ICU-09', 'ICU', 'Available', NULL),
(50, 'ICU-10', 'ICU', 'Available', NULL),
(51, 'EMG-01', 'Emergency Ward', 'Available', NULL),
(52, 'EMG-02', 'Emergency Ward', 'Available', NULL),
(53, 'EMG-03', 'Emergency Ward', 'Available', NULL),
(54, 'EMG-04', 'Emergency Ward', 'Available', NULL),
(55, 'EMG-05', 'Emergency Ward', 'Available', NULL),
(56, 'EMG-06', 'Emergency Ward', 'Available', NULL),
(57, 'EMG-07', 'Emergency Ward', 'Available', NULL),
(58, 'EMG-08', 'Emergency Ward', 'Available', NULL),
(59, 'EMG-09', 'Emergency Ward', 'Available', NULL),
(60, 'EMG-10', 'Emergency Ward', 'Available', NULL);

-- 3. Patients (50 Realistic Inpatients & Outpatients)
INSERT IGNORE INTO patients (patient_id, patient_name, age, gender, phone, address, diagnosis, department, doctor_id, bed_id, admission_status, priority_level) VALUES
(1, 'Rohit Sharma', 45, 'Male', '9876543001', 'Dehradun', 'Acute Myocardial Infarction', 'Cardiology', 1, 1, 'Admitted', 1),
(2, 'Virat Kohli', 18, 'Male', '9876543002', 'Rishikesh', 'Fractured Right Femur', 'Orthopedics', 3, 2, 'Admitted', 2),
(3, 'MS Dhoni', 42, 'Male', '9876543003', 'Haridwar', 'Severe Sepsis & Dehydration', 'Emergency', 9, 3, 'Admitted', 1),
(4, 'Priya Nair', 29, 'Female', '9876543004', 'Dehradun', 'Viral Pneumonia with Hypoxia', 'Pulmonology', 7, 4, 'Admitted', 2),
(5, 'Aarav Patel', 8, 'Male', '9876543005', 'Roorkee', 'Acute Pediatric Bronchiolitis', 'Pediatrics', 4, 5, 'Admitted', 1),
(6, 'Sunita Deshmukh', 54, 'Female', '9876543006', 'Dehradun', 'Type-2 Diabetes with Ketoacidosis', 'General Medicine', 1, 6, 'Admitted', 2),
(7, 'Rajesh Kothari', 63, 'Male', '9876543007', 'Mussoorie', 'Acute Ischemic Stroke', 'Neurology', 2, 7, 'Admitted', 1),
(8, 'Anita Roy', 36, 'Female', '9876543008', 'Haridwar', 'Acute Appendicitis with Peritonitis', 'General Surgery', 6, 8, 'Admitted', 1),
(9, 'Vikram Chauhan', 50, 'Male', '9876543009', 'Dehradun', 'Chronic Kidney Disease Stage 4', 'General Medicine', 1, 9, 'Admitted', 2),
(10, 'Meenakshi Sundaram', 67, 'Female', '9876543010', 'Rishikesh', 'Hodgkin Lymphoma Stage II', 'Oncology', 5, 10, 'Admitted', 2),
(11, 'Devendra Joshi', 41, 'Male', '9876543011', 'Roorkee', 'Lumbar Disc Herniation L4-L5', 'Orthopedics', 3, 11, 'Admitted', 3),
(12, 'Pooja Bansal', 31, 'Female', '9876543012', 'Dehradun', 'Dengue Fever with Thrombocytopenia', 'Emergency', 9, 12, 'Admitted', 1),
(13, 'Suresh Raina', 38, 'Male', '9876543013', 'Haridwar', 'Bronchial Asthma Exacerbation', 'Pulmonology', 7, 13, 'Admitted', 2),
(14, 'Ananya Chatterjee', 24, 'Female', '9876543014', 'Rishikesh', 'Acute Pyelonephritis', 'General Surgery', 6, 14, 'Admitted', 2),
(15, 'Jasprit Bumrah', 30, 'Male', '9876543015', 'Dehradun', 'Stress Fracture Lumbar Spine', 'Orthopedics', 3, 15, 'Admitted', 3),
(16, 'Kavita Menon', 58, 'Female', '9876543016', 'Roorkee', 'Hypertensive Emergency', 'Cardiology', 1, 16, 'Admitted', 1),
(17, 'Rahul Dravid', 51, 'Male', '9876543017', 'Dehradun', 'Cholelithiasis with Cholecystitis', 'General Surgery', 6, 17, 'Admitted', 2),
(18, 'Divya Agarwal', 12, 'Female', '9876543018', 'Haridwar', 'Juvenile Idiopathic Arthritis', 'Pediatrics', 4, 18, 'Admitted', 2),
(19, 'Hardik Pandya', 31, 'Male', '9876543019', 'Rishikesh', 'Acute Ankle Ligament Tear', 'Orthopedics', 3, 19, 'Admitted', 3),
(20, 'Rekha Saxena', 62, 'Female', '9876543020', 'Dehradun', 'Deep Vein Thrombosis', 'Cardiology', 1, 20, 'Admitted', 1),
(21, 'Manish Malhotra', 47, 'Male', '9876543021', 'Roorkee', 'Bleeding Peptic Ulcer', 'General Surgery', 6, 21, 'Admitted', 1),
(22, 'Sneha Kulkarni', 34, 'Female', '9876543022', 'Dehradun', 'Intractable Migraine with Aura', 'Neurology', 2, 22, 'Admitted', 2),
(23, 'Ravindra Jadeja', 35, 'Male', '9876543023', 'Haridwar', 'Rotator Cuff Tendonitis', 'Orthopedics', 3, 23, 'Admitted', 3),
(24, 'Shalini Varma', 44, 'Female', '9876543024', 'Rishikesh', 'Severe COVID-19 Sequelae', 'Pulmonology', 7, 24, 'Admitted', 2),
(25, 'Amitabh Bakshi', 72, 'Male', '9876543025', 'Dehradun', 'Congestive Heart Failure', 'Cardiology', 1, 25, 'Admitted', 1),
(26, 'Tanvi Shah', 27, 'Female', '9876543026', 'Roorkee', 'Polycystic Kidney Disease', 'General Medicine', 1, 26, 'Admitted', 2),
(27, 'Shubman Gill', 25, 'Male', '9876543027', 'Dehradun', 'Infectious Mononucleosis', 'General Medicine', 1, 27, 'Admitted', 2),
(28, 'Deepa Pillai', 39, 'Female', '9876543028', 'Haridwar', 'Multiple Sclerosis Relapse', 'Neurology', 2, 28, 'Admitted', 2),
(29, 'Mohammed Shami', 34, 'Male', '9876543029', 'Rishikesh', 'Achilles Tendon Rupture', 'Orthopedics', 3, 29, 'Admitted', 2),
(30, 'Ritu Singhal', 52, 'Female', '9876543030', 'Dehradun', 'Breast Carcinoma Stage I', 'Oncology', 5, 30, 'Admitted', 2),
(31, 'Alok Mishra', 60, 'Male', '9876543031', 'Roorkee', 'Chronic Obstructive Pulmonary Disease', 'Pulmonology', 7, 31, 'Admitted', 1),
(32, 'Swati Tiwari', 22, 'Female', '9876543032', 'Dehradun', 'Enteric Typhoid Fever with Shock', 'Emergency', 9, 32, 'Admitted', 1),
(33, 'Rishabh Pant', 27, 'Male', '9876543033', 'Roorkee', 'Knee Cruciate Ligament Reconstruction', 'Orthopedics', 3, 33, 'Admitted', 2),
(34, 'Neelam Rao', 66, 'Female', '9876543034', 'Haridwar', 'Parkinson Disease with Tremors', 'Neurology', 2, 34, 'Admitted', 2),
(35, 'Kuldeep Yadav', 29, 'Male', '9876543035', 'Rishikesh', 'Acute Gastroenteritis with Acidosis', 'Emergency', 9, 35, 'Admitted', 2),
(36, 'Vandana Katariya', 32, 'Female', '9876543036', 'Haridwar', 'Anterior Cruciate Sprain', 'Orthopedics', 3, 36, 'Admitted', 3),
(37, 'Harish Rawat', 68, 'Male', '9876543037', 'Dehradun', 'Coronary Artery Triple Vessel Disease', 'Cardiology', 1, 37, 'Admitted', 1),
(38, 'Garima Negi', 19, 'Female', '9876543038', 'Rishikesh', 'Severe Allergic Anaphylaxis', 'Emergency', 9, 38, 'Admitted', 1),
-- Outpatients (Not occupying beds)
(39, 'Sanjeev Kapoor', 49, 'Male', '9876543039', 'Dehradun', 'Routine Hypertension Screening', 'Cardiology', 1, NULL, 'Outpatient', 3),
(40, 'Pallavi Joshi', 33, 'Female', '9876543040', 'Haridwar', 'Tension Headache & Refraction', 'Neurology', 2, NULL, 'Outpatient', 3),
(41, 'Tarun Bhatt', 28, 'Male', '9876543041', 'Rishikesh', 'Mild Cervical Spondylosis', 'Orthopedics', 3, NULL, 'Outpatient', 3),
(42, 'Bhavna Bisht', 6, 'Female', '9876543042', 'Dehradun', 'Seasonal Allergic Rhinitis', 'Pediatrics', 4, NULL, 'Outpatient', 3),
(43, 'Chetan Chauhan', 55, 'Male', '9876543043', 'Roorkee', 'Pre-Chemotherapy Routine Consultation', 'Oncology', 5, NULL, 'Outpatient', 2),
(44, 'Geeta Goswami', 41, 'Female', '9876543044', 'Dehradun', 'Inguinal Hernia Consultation', 'General Surgery', 6, NULL, 'Outpatient', 3),
(45, 'Nitin Thapa', 37, 'Male', '9876543045', 'Haridwar', 'Chronic Sinusitis & Cough', 'Pulmonology', 7, NULL, 'Outpatient', 3),
(46, 'Archana Semwal', 26, 'Female', '9876543046', 'Rishikesh', 'Routine X-Ray Ankle Review', 'Radiology', 8, NULL, 'Outpatient', 3),
-- Discharged Patients (Beds freed)
(47, 'Manoj Kandpal', 48, 'Male', '9876543047', 'Dehradun', 'Post-Angioplasty Recovery', 'Cardiology', 1, NULL, 'Discharged', 2),
(48, 'Sunita Panwar', 57, 'Female', '9876543048', 'Haridwar', 'Post-Laparoscopic Cholecystectomy', 'General Surgery', 6, NULL, 'Discharged', 2),
(49, 'Vijay Bahuguna', 61, 'Male', '9876543049', 'Rishikesh', 'Resolved Lobar Pneumonia', 'Pulmonology', 7, NULL, 'Discharged', 2),
(50, 'Kiran Khanduri', 35, 'Female', '9876543050', 'Roorkee', 'Managed Dengue Thrombocytopenia', 'Emergency', 9, NULL, 'Discharged', 3);

-- Synchronize beds table with admitted patient IDs (Beds 1 to 38 are Occupied)
UPDATE beds b 
JOIN patients p ON b.bed_id = p.bed_id 
SET b.patient_id = p.patient_id, b.bed_status = 'Occupied' 
WHERE p.admission_status = 'Admitted';

-- 4. Shared Resources
INSERT IGNORE INTO resources (resource_id, resource_name, available_quantity, total_quantity) VALUES
(1, 'Beds', 22, 60), -- 22 available out of 60 total
(2, 'Doctors', 10, 10),
(3, 'Monitoring Devices', 20, 25),
(4, 'Oxygen Cylinder', 30, 40),
(5, 'Ventilator', 12, 15);

-- 5. Appointments (Relational linking between patients and doctors)
INSERT IGNORE INTO appointments (appointment_id, patient_id, doctor_id, appointment_date, appointment_time, appointment_status) VALUES
(1, 1, 1, '2026-11-25', '10:00:00', 'Scheduled'),
(2, 2, 3, '2026-11-25', '11:30:00', 'Scheduled'),
(3, 3, 9, '2026-11-25', '09:00:00', 'Scheduled'),
(4, 4, 7, '2026-11-25', '12:00:00', 'Scheduled'),
(5, 5, 4, '2026-11-25', '14:30:00', 'Scheduled'),
(6, 6, 1, '2026-11-26', '09:30:00', 'Scheduled'),
(7, 7, 2, '2026-11-26', '10:30:00', 'Scheduled'),
(8, 8, 6, '2026-11-26', '11:00:00', 'Scheduled'),
(9, 12, 9, '2026-11-26', '13:00:00', 'Scheduled'),
(10, 16, 1, '2026-11-26', '15:00:00', 'Scheduled'),
(11, 39, 1, '2026-11-27', '09:00:00', 'Scheduled'),
(12, 40, 2, '2026-11-27', '10:00:00', 'Scheduled'),
(13, 41, 3, '2026-11-27', '11:00:00', 'Scheduled'),
(14, 47, 1, '2026-11-24', '16:00:00', 'Completed'),
(15, 48, 6, '2026-11-24', '17:00:00', 'Completed');

-- 6. Software Tasks across 5 Workload Categories
INSERT IGNORE INTO treatment_processes (process_id, patient_id, appointment_id, process_name, work_category, arrival_time, burst_time, priority, process_status) VALUES
-- A. EMERGENCY WORKLOAD (Priority Scheduling, Priority 1 = Highest Alert)
(1, 1, 1, 'ICU Cardiac Telemetry Alarm', 'Emergency', 0, 5, 1, 'Queued'),
(2, 3, 3, 'Oxygen Desaturation Critical Alert', 'Emergency', 1, 4, 1, 'Queued'),
(3, 12, 9, 'Arrhythmia Acute Notification', 'Emergency', 2, 3, 1, 'Queued'),
(4, 7, 7, 'Acute Stroke Code Telemetry', 'Emergency', 3, 4, 1, 'Queued'),
(5, 16, 10, 'Trauma Hemorrhage Vitals Stream', 'Emergency', 4, 6, 2, 'Queued'),

-- B. INTERACTIVE WORKLOAD (Round Robin Scheduling, Time-Shared with Quantum)
(6, 4, 4, 'Doctor Prescription Order Entry', 'Interactive', 0, 4, 2, 'Queued'),
(7, 6, 6, 'OPD Consultation Vitals Update', 'Interactive', 1, 3, 3, 'Queued'),
(8, 9, NULL, 'Telehealth Video Stream Session', 'Interactive', 2, 5, 2, 'Queued'),
(9, 14, NULL, 'E-Prescription Medication Check', 'Interactive', 3, 3, 2, 'Queued'),
(10, 17, NULL, 'Physician Clinical Note Voice Sync', 'Interactive', 4, 4, 3, 'Queued'),

-- C. OPD WORKLOAD (Round Robin / Time-Shared Outpatient Queue)
(11, 39, 11, 'OPD Patient Registration Desk Entry', 'OPD', 0, 3, 3, 'Queued'),
(12, 40, 12, 'Outpatient Consultation Slip Print', 'OPD', 1, 2, 3, 'Queued'),
(13, 41, 13, 'Follow-up Appointment Auto-Scheduler', 'OPD', 2, 4, 4, 'Queued'),
(14, 42, NULL, 'OPD Token Queue Number Broadcast', 'OPD', 3, 2, 3, 'Queued'),
(15, 43, NULL, 'Routine Immunization Vaccine Log', 'OPD', 4, 3, 4, 'Queued'),

-- D. DIAGNOSTIC WORKLOAD (Shortest Job First - SJF Minimizes Average Waiting Time)
(16, 2, 2, 'Blood Glucose Rapid Screen', 'Diagnostic', 0, 2, 2, 'Queued'),
(17, 10, NULL, 'Rapid Antigen Viral Assay', 'Diagnostic', 1, 1, 3, 'Queued'),
(18, 13, NULL, 'Haemoglobin Fingerprick Test', 'Diagnostic', 2, 3, 2, 'Queued'),
(19, 4, 4, 'Chest X-Ray Radiograph Rendering', 'Diagnostic', 3, 5, 2, 'Queued'),
(20, 7, 7, 'Brain MRI Neuro-Scan Processing', 'Diagnostic', 4, 7, 1, 'Queued'),

-- E. BATCH WORKLOAD (First-Come, First-Served - FCFS Minimal Context Switching)
(21, 1, 1, 'Nightly Billing Summary Generation', 'Batch', 0, 6, 4, 'Queued'),
(22, 2, 2, 'Daily Insurance Claim Batching', 'Batch', 2, 5, 4, 'Queued'),
(23, 47, 14, 'Inpatient Discharge Summary PDF Export', 'Batch', 3, 4, 4, 'Queued'),
(24, 8, 8, 'Hospital Bed Occupancy Statistics', 'Batch', 5, 6, 5, 'Queued'),
(25, 17, NULL, 'Pharmacy Stock Level Reconciliation', 'Batch', 6, 5, 5, 'Queued');

-- 7. Resource Allocations for Banker's Algorithm
INSERT IGNORE INTO resource_allocations (allocation_id, process_id, resource_id, allocated_quantity, maximum_required) VALUES
(1, 1, 1, 1, 3),
(2, 1, 2, 0, 2),
(3, 1, 3, 1, 2),
(4, 2, 1, 1, 2),
(5, 2, 2, 1, 2),
(6, 2, 3, 0, 1),
(7, 3, 1, 1, 3),
(8, 3, 2, 0, 1),
(9, 3, 3, 1, 3);
