# CarePoint — Integrated Hospital Management System UI

Responsive HTML/CSS/JavaScript frontend for the OS + DBMS PBL.

## Run locally
1. Extract this ZIP.
2. Open `index.html` in a modern browser, or use VS Code Live Server.
3. No build step or package installation is required.

## OS UI updates included
- OS resource cards for Beds (10), Doctors (5), and Monitoring Devices (8).
- Browser-local allocate/release controls with positive-integer validation, availability checks, and prevention of over-release.
- Existing FCFS, SJF, Priority, and Round Robin scheduling preview with waiting/turnaround/completion metrics and a Gantt chart.
- Priority input uses the requested convention in the viva explanation: priority 1 is highest. Confirm that Vansh's Java implementation uses the same convention before integration.
- Editable Banker’s Algorithm Available, Allocation, and Maximum Demand values for three patient processes and three hospital resource types.
- Need is calculated as Maximum − Allocation, with SAFE/UNSAFE output and patient-labelled safe sequence.
- Short OS concept explanations for viva.
- Existing patient, doctor, appointment, bed/resource, treatment queue and history screens are retained.

## Important integration boundary
This ZIP contains only the frontend (`index.html`, `styles.css`, `app.js`, and this README). It does **not** include `HospitalOSModule.java`, a Java server, or confirmed API routes. OS scheduling, resource controls, and Banker's Algorithm are browser-side previews; inventory changes are not persisted and no result is verified against Vansh's Java code or MySQL. Do not present them as backend-connected results.

Before wiring to the real backend, obtain the Java module and confirm the API routes, request/response JSON, priority convention, process identifiers, and resource snapshot model. Then replace the local preview handlers with calls to the confirmed Java API.
