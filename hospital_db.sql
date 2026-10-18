
create database model_db;
USE model_db;
show tables;

desc patients;
select*from patients;

desc doctors;
select*from doctors;

DESC beds;
select*from beds;

DESC resources;
select*from resources;

DESC appointments;
select*from appointments;

-- Show appointments with patient and doctor names
SELECT
    a.Appointment_id,
    p.patient_name,
    d.doctor_name,
    d.specialization,
    a.Appointment_date,
    a.Appointment_time,
    a.Appointment_status
FROM appointments a
JOIN patients p ON a.Patient_id = p.patient_id
JOIN doctors d ON a.Doctor_id = d.doctor_id;

-- =====================================================
-- OS INTEGRATION: PRIORITY SCHEDULING
-- =====================================================

DESC treatment_processes;
SELECT * FROM treatment_processes;

DESC scheduling_runs;
SELECT * FROM scheduling_runs;

DESC scheduling_results;
SELECT * FROM scheduling_results;

-- Show Priority Scheduling output clearly
SELECT
    sr.process_order,
    tp.process_name,
    tp.arrival_time,
    tp.burst_time,
    tp.priority,
    sr.start_time,
    sr.completion_time,
    sr.waiting_time,
    sr.turnaround_time
FROM scheduling_results sr
JOIN treatment_processes tp ON sr.process_id = tp.process_id
WHERE sr.run_id = 1
ORDER BY sr.process_order;

-- =====================================================
-- BANKER'S ALGORITHM: RESOURCE ALLOCATION TABLE.
-- =====================================================

CREATE TABLE IF NOT EXISTS resource_allocations (
    allocation_id INT AUTO_INCREMENT PRIMARY KEY,

    process_id INT NOT NULL,
    resource_id INT NOT NULL,

    allocated_quantity INT NOT NULL DEFAULT 0,
    maximum_required INT NOT NULL,

    FOREIGN KEY (process_id) REFERENCES treatment_processes(process_id),
    FOREIGN KEY (resource_id) REFERENCES resources(resource_id),

    CONSTRAINT chk_allocated_quantity
    CHECK (allocated_quantity >= 0),

    CONSTRAINT chk_maximum_required
    CHECK (maximum_required >= 0),

    CONSTRAINT chk_allocation_limit
    CHECK (allocated_quantity <= maximum_required),

    UNIQUE (process_id, resource_id)
);

DESC resource_allocations;

-- Run these INSERT statements only once
INSERT INTO resource_allocations
(process_id, resource_id, allocated_quantity, maximum_required)
VALUES
(1, 1, 1, 3),
(1, 2, 2, 5),
(2, 1, 1, 2),
(3, 3, 1, 2);

SELECT * FROM resource_allocations;

-- Show resource-allocation data for Banker's Algorithm
SELECT
    tp.process_name,
    r.resource_name,
    r.available_quantity,
    ra.allocated_quantity,
    ra.maximum_required
FROM resource_allocations ra
JOIN treatment_processes tp ON ra.process_id = tp.process_id
JOIN resources r ON ra.resource_id = r.resource_id;
