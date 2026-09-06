# Data Quality Platform — Architecture Study Guide

This document explains the repository as a Java + React full-stack interview project.

## High-level architecture

```text
                         Browser
                           |
                           | React UI / Axios
                           v
+--------------------------------------------------+
| Frontend: React 19 + Vite                        |
| main.jsx -> App.jsx -> api.js                    |
| localStorage stores JWT, role, username          |
+--------------------------------------------------+
                           |
                           | HTTP JSON + Bearer JWT
                           v
+--------------------------------------------------+
| Backend: Spring Boot 3.5 / Java 21               |
| JWT authentication filter                         |
|        v                                         |
| Controllers: Auth / MasterRecord / Audit          |
|        v                                         |
| Services: MasterRecord / Validation / Audit       |
|        v                                         |
| Repositories: Spring Data JPA                     |
|        v                                         |
| Entities: AppUser / MasterRecord / AuditLog       |
+--------------------------------------------------+
                           |
                           v
                       MySQL 8
         app_users | master_records | audit_logs

Docker Compose: frontend + backend + MySQL
Kubernetes: frontend + backend + MySQL deployments/services
GitHub Actions: CI build/test + ECR image push
AWS target: ECR -> EKS, with RDS MySQL for production
```

## Request flow

```text
React UI
  -> Axios API client
  -> JWT security filter
  -> REST controller
  -> service/business logic
  -> repository
  -> JPA/Hibernate
  -> MySQL
  -> JSON response back to React
```

Example — create a master record:

```text
App.jsx
-> POST /api/records
-> JwtAuthenticationFilter verifies the token
-> MasterRecordController.create()
-> MasterRecordService.create()
-> ValidationService.validate()
-> MasterRecordRepository.save()
-> MySQL master_records
-> AuditService.log()
-> AuditLogRepository.save()
-> MySQL audit_logs
-> JSON response to App.jsx
```

## 1. Frontend

Technology: React 19, Vite 7, Axios, React Router, and Nginx.

| File | Responsibility | Communicates with |
|---|---|---|
| `frontend/package.json` | Defines frontend dependencies and `dev`, `build`, and `lint` scripts. | Node/npm |
| `frontend/src/main.jsx` | Browser entry point; mounts React and renders `App`. | `App.jsx` |
| `frontend/src/App.jsx` | Current UI: login, search, record create/edit, upload, submission, approval, rejection. | `api.js`, localStorage, REST API |
| `frontend/src/api.js` | Axios client; reads JWT from localStorage and adds `Authorization: Bearer <token>`. | Backend API |
| `frontend/src/styles.css` | UI styling. | React components |
| `frontend/index.html` | HTML shell with the React root element. | `main.jsx` |
| `frontend/vite.config.js` | Enables Vite React integration. | Vite |
| `frontend/nginx.conf` | Serves the SPA and falls back to `index.html`. | Nginx |
| `frontend/Dockerfile` | Builds the React assets with Node, then serves them with Nginx. | Docker |

Frontend flow:

```text
main.jsx -> App.jsx -> api.js -> Spring Boot REST APIs
```

The UI stores `token`, `username`, and `role` in browser localStorage after login. UI role checks only hide/show controls; backend role checks provide actual protection.

## 2. Backend

Technology: Java 21, Spring Boot 3.5, Spring MVC, Spring Data JPA/Hibernate, Spring Security/JWT, Bean Validation, Apache POI, Maven, and Lombok.

`backend/pom.xml` contains the dependencies. The main package layout is:

```text
com.dqplatform
├── DataQualityApplication.java    application entry point
├── config                         startup configuration
├── controller                     HTTP REST endpoints
├── dto                            request/response data contracts
├── entity                         database table mappings
├── exception                      centralized error responses
├── repository                     database access interfaces
├── security                       JWT authentication/security setup
└── service                        business logic
```

### Application entry point

`backend/src/main/java/com/dqplatform/DataQualityApplication.java`

`SpringApplication.run(DataQualityApplication.class, args)` starts Spring Boot. `@SpringBootApplication` scans the `com.dqplatform` package tree, creates beans, configures JPA and security, connects to MySQL, and starts the HTTP server.

### Backend configuration

`backend/src/main/resources/application.yml` configures:

- backend port `8080`
- MySQL connection defaults
- `ddl-auto: update` so Hibernate updates tables from entities
- JWT secret and token expiration
- 10 MB upload limits
- Actuator health endpoints

`backend/src/main/java/com/dqplatform/config/DataInitializer.java` uses `CommandLineRunner` to seed demo users when they do not exist: `admin`, `entry`, `approver`, and `viewer`.

## 3. Database

The application uses MySQL 8.4. Hibernate maps Java entities to these tables:

| Entity | Table | Responsibility |
|---|---|---|
| `AppUser.java` | `app_users` | Username, BCrypt password hash, role, active flag. |
| `MasterRecord.java` | `master_records` | Business fields, workflow status, validation errors, timestamps, ownership details. |
| `AuditLog.java` | `audit_logs` | Activity history: actor, action, affected target, details, timestamp. |

Enums:

- `Role.java`: `ADMIN`, `DATA_ENTRY`, `APPROVER`, `VIEWER`
- `RecordStatus.java`: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`

Repositories use JPA to query these tables:

| Repository | Responsibility |
|---|---|
| `AppUserRepository.java` | Finds users by username. |
| `MasterRecordRepository.java` | CRUD, paging, search, duplicate-code checks, status queries. |
| `AuditLogRepository.java` | Saves and retrieves audit logs. |

## 4. Authentication and authorization

| File | Responsibility |
|---|---|
| `SecurityConfig.java` | Configures stateless security, CORS, public routes, BCrypt, JWT filter, and method security. |
| `CustomUserDetailsService.java` | Loads an `AppUser` from MySQL and creates Spring Security `UserDetails`. |
| `JwtService.java` | Generates, parses, and validates JWTs. |
| `JwtAuthenticationFilter.java` | Reads bearer tokens and places authenticated users in Spring's security context. |
| `AuthController.java` | Login endpoint that returns a JWT. |

Login:

```text
POST /api/auth/login
-> AuthenticationManager verifies credentials
-> CustomUserDetailsService loads user from MySQL
-> BCrypt checks password
-> JwtService generates signed token
-> LoginResponse returns token, username, role
-> React stores values in localStorage
```

Later requests:

```text
Axios adds Bearer token
-> JwtAuthenticationFilter extracts it
-> JwtService verifies it
-> CustomUserDetailsService loads current user and authorities
-> Spring Security allows or rejects controller access
```

Role permissions:

- `ADMIN`: manages records, approves/rejects, and reads audit logs.
- `DATA_ENTRY`: creates, edits, deletes, uploads, and submits records.
- `APPROVER`: approves/rejects pending records.
- `VIEWER`: reads authenticated record APIs.

## 5. REST APIs

| Controller | Endpoint(s) | Responsibility |
|---|---|---|
| `AuthController.java` | `POST /api/auth/login` | Authenticates user and returns JWT. |
| `MasterRecordController.java` | `/api/records/**` | Search, CRUD, upload, submit, approve, reject. |
| `AuditController.java` | `GET /api/audit` | Returns audit log, restricted to `ADMIN`. |

`MasterRecordController` API summary:

| Endpoint | Access | Meaning |
|---|---|---|
| `GET /api/records` | Any authenticated user | Paginated list/search. |
| `GET /api/records/{id}` | Any authenticated user | One record. |
| `POST /api/records` | ADMIN, DATA_ENTRY | Create draft. |
| `PUT /api/records/{id}` | ADMIN, DATA_ENTRY | Update record. |
| `DELETE /api/records/{id}` | ADMIN, DATA_ENTRY | Delete record. |
| `POST /api/records/{id}/submit` | ADMIN, DATA_ENTRY | Submit valid record. |
| `POST /api/records/{id}/approve` | ADMIN, APPROVER | Approve pending record. |
| `POST /api/records/{id}/reject` | ADMIN, APPROVER | Reject pending record. |
| `POST /api/records/upload` | ADMIN, DATA_ENTRY | Upload CSV/XLSX. |

DTOs:

- `LoginRequest.java`: login username/password.
- `LoginResponse.java`: token, username, role.
- `MasterRecordRequest.java`: create/update data and basic constraints.

`GlobalExceptionHandler.java` converts errors to JSON responses: not found is `404`; invalid data, invalid files, and invalid workflow transitions are `400`.

## 6. Master-data flow

`MasterRecordService.java` contains the core business rules.

| Method | Responsibility |
|---|---|
| `search()` | Paged list and code/name search. |
| `get()` | Finds one record or throws not found. |
| `create()` | Creates a draft, validates it, saves it, audits CREATE. |
| `update()` | Updates/revalidates/saves, audits UPDATE. |
| `delete()` | Deletes and audits DELETE. |
| `submit()` | Allows only valid records to become pending. |
| `approve()` | Makes a pending record approved. |
| `reject()` | Makes a pending record rejected. |
| `upload()` | Imports CSV/XLSX into drafts. |

Business lifecycle:

```text
Manual form or file upload
-> DRAFT record
-> validation errors saved with record when present
-> data-entry user corrects issues
-> submit valid record
-> approver approves or rejects it
```

## 7. Validation flow

There are two validation layers.

1. DTO/request validation in `MasterRecordRequest.java` using `@NotBlank`, `@Size`, and `@Email`. The controller uses `@Valid`; failed requests return HTTP 400 before service logic runs.
2. Data-quality/business validation in `ValidationService.java`.

`ValidationService.java` checks:

- record code is mandatory and unique
- name is mandatory
- category is mandatory
- status value is `ACTIVE`, `INACTIVE`, or `BLOCKED`
- supplied email is valid
- supplied phone is valid

Flow:

```text
MasterRecordService.create/update/upload/submit
-> ValidationService.validate(record, updateFlag)
-> list of error messages
-> messages saved into MasterRecord.validationErrors
```

An invalid record can remain saved as `DRAFT`, but cannot be submitted until errors are resolved.

## 8. CSV and Excel upload flow

```text
React file picker
-> FormData multipart request
-> POST /api/records/upload
-> MasterRecordController.upload()
-> MasterRecordService.upload()
-> check .csv or .xlsx extension
-> parse CSV with BufferedReader or XLSX with Apache POI
-> skip header row
-> create + validate + save each DRAFT record
-> save BULK_UPLOAD audit log
-> return upload count
```

The sample file is `sample-data/master-data.csv`.

Column order:

```text
recordCode, name, email, phone, category, statusValue
```

Note: the CSV reader uses a simple comma split, so quoted commas inside CSV fields are not handled.

## 9. Approval workflow

```text
Create or upload
      |
      v
    DRAFT
      |
      | Submit only when no validation errors
      v
PENDING_APPROVAL
      |
      +-- Approve --> APPROVED
      |
      +-- Reject  --> REJECTED
```

Rules in `MasterRecordService`:

- `ADMIN` or `DATA_ENTRY` submits a record.
- Submission fails if validation errors exist.
- `ADMIN` or `APPROVER` approves/rejects.
- Approval/rejection is allowed only from `PENDING_APPROVAL`.

## 10. Audit logging

`AuditService.java` saves audit entries through `AuditLogRepository`.

Audited actions:

```text
CREATE, UPDATE, DELETE, SUBMIT, APPROVE, REJECT, BULK_UPLOAD
```

Each entry records username, action, entity type, entity ID, details, and time. `AuditController.java` returns the logs newest first to admins at `GET /api/audit`. The current React UI does not yet include an audit-log screen.

## 11. Docker

`docker-compose.yml` defines three services:

```text
mysql -> backend -> frontend
```

- `mysql`: MySQL 8.4 on host port 3306, with persistent `mysql_data` Docker volume.
- `backend`: built with `backend/Dockerfile`, exposed on port 8080, waits for healthy MySQL.
- `frontend`: built with `frontend/Dockerfile`, exposed as host port 5173 to Nginx port 80.

`backend/Dockerfile` uses Maven/JDK 21 to build a JAR and a JRE 21 image to run it. `frontend/Dockerfile` uses Node 20 to build the Vite files and Nginx to serve them.

## 12. Kubernetes

Kubernetes files are in `k8s/`.

| File | Responsibility |
|---|---|
| `namespace.yaml` | Creates `dq-platform` namespace. |
| `mysql.yaml` | One MySQL deployment plus internal service. |
| `backend.yaml` | Two backend replicas, internal service, readiness/liveness probes. |
| `frontend.yaml` | Two frontend replicas and public LoadBalancer service. |
| `configmap.yaml` | Defines DB URL and frontend API URL values. |

Runtime request path:

```text
Internet
-> frontend LoadBalancer service
-> frontend pods
-> backend service
-> backend pods
-> mysql service
-> mysql pod
```

Important production observations:

- MySQL manifest has no persistent volume, so pod replacement can lose data.
- Database and JWT values should be Kubernetes Secrets rather than inline configuration.
- The current ConfigMap is defined but not referenced by the deployments.

## 13. CI/CD

`.github/workflows/ci.yml` runs on `main` pushes and pull requests:

```text
Backend: Java 21 -> mvn test -> mvn package -DskipTests
Frontend: Node 20 -> npm install -> npm run build
```

`.github/workflows/cd.yml` runs when a tag beginning with `v` is pushed:

```text
Git tag (example: v1.0.0)
-> authenticate to AWS using GitHub secrets
-> login to Amazon ECR
-> build and push dq-backend:<tag>
-> build and push dq-frontend:<tag>
```

Required GitHub secrets:

```text
AWS_REGION
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
ECR_REGISTRY
```

The CD workflow pushes images to ECR but does not yet deploy them to EKS.

## 14. AWS deployment preparation

Target architecture:

```text
GitHub Actions
-> Amazon ECR (Docker images)
-> Amazon EKS (Kubernetes workloads)
-> Amazon RDS MySQL (production database)
```

Existing preparation:

- Dockerfiles for backend and frontend
- GitHub Actions ECR push workflow
- Kubernetes manifests with ECR-style image locations
- Environment-variable-based backend configuration
- Health endpoints for Kubernetes probes

Required production completion:

- Replace AWS account/region image placeholders.
- Provision ECR, EKS, and RDS.
- Use RDS JDBC URL instead of the in-cluster MySQL service.
- Put secrets in AWS Secrets Manager and/or Kubernetes Secrets.
- Configure HTTPS, DNS, Ingress/ALB, and production CORS.
- Add persistent storage for any non-production in-cluster MySQL.
- Add a deployment step after image push, such as Helm, Argo CD, or `kubectl set image`.

## Interview summary

> This is a layered React and Spring Boot data-governance application. React makes JWT-authenticated REST calls. Spring Security validates the token and role. Controllers receive HTTP requests, services apply validation and approval business rules, repositories use JPA/Hibernate to persist data in MySQL, and audit logs record every important record state change. Docker supports local multi-container execution; Kubernetes, GitHub Actions, AWS ECR, EKS, and RDS are the intended production deployment path.
