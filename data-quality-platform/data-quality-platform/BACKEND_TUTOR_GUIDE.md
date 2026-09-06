# Data Quality Platform — Spring Boot Backend Tutor Guide

This guide explains the actual backend package-by-package for interview preparation. No generic layers are assumed beyond what exists in this project.

## How the backend starts

The application starts from `backend/src/main/java/com/dqplatform/DataQualityApplication.java`:

```java
@SpringBootApplication
public class DataQualityApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataQualityApplication.class, args);
    }
}
```

`SpringApplication.run(...)` starts Spring Boot and does the following:

```text
1. Starts Spring Boot.
2. Scans com.dqplatform and all subpackages.
3. Finds @Configuration, @Service, @Repository, @RestController, and @Entity classes.
4. Creates Spring-managed objects (beans).
5. Reads application.yml.
6. Configures MySQL, JPA/Hibernate, Spring Security, and the JWT filter.
7. Starts embedded Tomcat on port 8080.
8. Runs DataInitializer to seed demo users.
```

Because the main class is in `com.dqplatform`, component scanning includes:

```text
com.dqplatform.config
com.dqplatform.controller
com.dqplatform.dto
com.dqplatform.entity
com.dqplatform.exception
com.dqplatform.repository
com.dqplatform.security
com.dqplatform.service
```

`backend/src/main/resources/application.yml` supplies port 8080, MySQL settings, JPA `ddl-auto: update`, JWT configuration, upload limits, and health endpoint settings.

## Entity package

### Why it exists

The `entity` package defines Java classes that Hibernate maps to MySQL tables. They represent persisted application data.

### Files and responsibilities

| File | Database table / purpose |
|---|---|
| `AppUser.java` | `app_users`: login username, hashed password, role, active status. |
| `MasterRecord.java` | `master_records`: business data, validation results, workflow state, metadata. |
| `AuditLog.java` | `audit_logs`: record of important system actions. |
| `Role.java` | User roles: ADMIN, DATA_ENTRY, APPROVER, VIEWER. |
| `RecordStatus.java` | Workflow states: DRAFT, PENDING_APPROVAL, APPROVED, REJECTED. |

### `AppUser.java`

Maps to `app_users`. It stores `id`, unique `username`, BCrypt-hashed `password`, `role`, and `active`. It is used during login and JWT authentication.

### `MasterRecord.java`

Maps to `master_records`. It stores master-data values:

```text
recordCode, name, email, phone, category, statusValue
```

It also stores process fields:

```text
status, validationErrors, createdBy, updatedBy, createdAt, updatedAt
```

The table has indexes on `record_code` and `status`.

### `AuditLog.java`

Maps to `audit_logs`. Every log stores username, action, entity type, entity ID, details, and timestamp.

### How this package participates in requests

```text
Service creates or changes an entity
-> repository saves it
-> Hibernate translates entity mapping into SQL
-> MySQL table is updated
```

## Repository package

### Why it exists

Repositories isolate database queries from business logic. They extend Spring Data JPA's `JpaRepository`, so Spring creates their implementation at runtime.

### Files and responsibilities

| File | Responsibility |
|---|---|
| `AppUserRepository.java` | Finds users by username. |
| `MasterRecordRepository.java` | CRUD, pagination, search, duplicate checks, status lookup. |
| `AuditLogRepository.java` | Saves and retrieves audit history. |

### `AppUserRepository.java`

Provides `findByUsername(String username)`. It is used by `CustomUserDetailsService`, `AuthController`, and `DataInitializer`.

### `MasterRecordRepository.java`

Provides custom derived query methods:

```java
existsByRecordCodeIgnoreCase(...)
existsByRecordCodeIgnoreCaseAndIdNot(...)
findByStatus(...)
findByRecordCodeContainingIgnoreCaseOrNameContainingIgnoreCase(...)
```

It is used by `MasterRecordService` and `ValidationService`.

### `AuditLogRepository.java`

Used by `AuditService` to save logs and `AuditController` to retrieve them.

### How this package participates in requests

```text
Service
-> repository method
-> Spring Data JPA/Hibernate
-> SQL execution against MySQL
-> entity/result returned to service
```

## Service package

### Why it exists

Services contain business logic. Controllers handle HTTP communication; services decide what the business operation does.

### Files and responsibilities

| File | Responsibility |
|---|---|
| `MasterRecordService.java` | Record CRUD, validation integration, uploads, and approval workflow. |
| `ValidationService.java` | Master-data quality/business rules. |
| `AuditService.java` | Creates audit entries. |

### `MasterRecordService.java`

This is the central business service. It depends on `MasterRecordRepository`, `ValidationService`, and `AuditService`.

| Method | Responsibility |
|---|---|
| `search()` | Returns paginated records; optionally searches record code/name. |
| `get()` | Returns a record or throws not-found exception. |
| `create()` | Creates DRAFT, validates, saves, audits CREATE. |
| `update()` | Updates fields, validates, saves, audits UPDATE. |
| `delete()` | Deletes a record, audits DELETE. |
| `submit()` | Revalidates record and moves valid record to pending approval. |
| `approve()` | Moves a pending record to APPROVED. |
| `reject()` | Moves a pending record to REJECTED. |
| `upload()` | Imports CSV/XLSX rows as DRAFT records. |

Invalid records are intentionally retained as `DRAFT` records with a `validationErrors` value. They cannot be submitted until fixed.

### `ValidationService.java`

Checks:

- record code is required and unique;
- name is required;
- category is required;
- status value is ACTIVE, INACTIVE, or BLOCKED;
- supplied email has valid format;
- supplied phone has allowed format.

It returns `List<String>`. `MasterRecordService` combines those messages and saves them in `MasterRecord.validationErrors`.

### `AuditService.java`

Provides `log(username, action, entityType, entityId, details)`. It creates an `AuditLog` and persists it with `AuditLogRepository`, keeping audit code separate from controllers.

### How this package participates in requests

```text
Controller calls service
-> service applies business rule / transaction
-> service calls validation and/or audit services
-> service calls repository
-> service returns entity/result to controller
```

## Controller package

### Why it exists

Controllers expose REST endpoints. They receive HTTP requests from React, validate request data, obtain authenticated user information, check roles, and delegate to services.

### Files and responsibilities

| File | Base endpoint | Responsibility |
|---|---|---|
| `AuthController.java` | `/api/auth` | Login and JWT response. |
| `MasterRecordController.java` | `/api/records` | Search, CRUD, upload, submit, approve, reject. |
| `AuditController.java` | `/api/audit` | Admin-only audit retrieval. |

### `AuthController.java`

Exposes:

```text
POST /api/auth/login
```

Flow:

```text
React sends LoginRequest
-> AuthenticationManager verifies credentials
-> AppUserRepository gets the AppUser
-> JwtService generates token
-> LoginResponse returns token, username, role
```

### `MasterRecordController.java`

Exposes:

```text
GET    /api/records
GET    /api/records/{id}
POST   /api/records
PUT    /api/records/{id}
DELETE /api/records/{id}
POST   /api/records/upload
POST   /api/records/{id}/submit
POST   /api/records/{id}/approve
POST   /api/records/{id}/reject
```

The controller receives the authenticated username from `Authentication auth` and passes it to `MasterRecordService` so changes and audit logs identify the acting user.

`ADMIN` or `DATA_ENTRY` can create, edit, delete, upload, and submit. `ADMIN` or `APPROVER` can approve/reject.

### `AuditController.java`

Exposes `GET /api/audit`. `@PreAuthorize("hasRole('ADMIN')")` limits it to admins. It fetches audit rows sorted newest first.

### How this package participates in requests

```text
HTTP request
-> controller mapping selected by Spring MVC
-> @Valid validates DTO where applicable
-> @PreAuthorize checks role
-> controller calls service/repository
-> Spring serializes returned Java data as JSON
```

## DTO package

### Why it exists

DTOs (Data Transfer Objects) define API request/response contracts, rather than exposing database entities as request bodies.

### Files and responsibilities

| File | Responsibility |
|---|---|
| `LoginRequest.java` | Login JSON with username and password. |
| `LoginResponse.java` | Login JSON response with token, username, role. |
| `MasterRecordRequest.java` | Create/update JSON with basic validation annotations. |

### `LoginRequest.java`

Expected JSON:

```json
{"username":"admin","password":"Admin@123"}
```

Both fields use `@NotBlank`.

### `LoginResponse.java`

Returns:

```json
{"token":"eyJ...","username":"admin","role":"ADMIN"}
```

### `MasterRecordRequest.java`

Contains record code, name, email, phone, category, status value. It uses `@NotBlank`, `@Size`, and `@Email` for basic request validation. `ValidationService` then runs deeper business validation.

### How this package participates in requests

```text
React JSON
-> DTO receives data in controller
-> @Valid performs basic checks
-> controller passes DTO to service
-> service maps it to entity fields
```

## Security package

### Why it exists

The security package handles password verification, JWT creation, JWT verification, CORS, and role authorization.

### Files and responsibilities

| File | Responsibility |
|---|---|
| `SecurityConfig.java` | Security rules, filter chain, CORS, BCrypt, method-security setup. |
| `CustomUserDetailsService.java` | Loads AppUser and role from MySQL for Spring Security. |
| `JwtService.java` | Creates, parses, validates JWT tokens. |
| `JwtAuthenticationFilter.java` | Authenticates bearer-token requests. |

### `SecurityConfig.java`

It allows public access to:

```text
/api/auth/**
/actuator/health/**
```

All remaining routes require authentication. It disables CSRF, uses stateless sessions, allows CORS from `http://localhost:5173`, registers the JWT filter, creates BCrypt encoder, and enables `@PreAuthorize` checks.

### `CustomUserDetailsService.java`

Flow:

```text
username
-> AppUserRepository.findByUsername()
-> AppUser
-> Spring Security UserDetails with role authority
```

### `JwtService.java`

Generates a signed JWT with username as the subject, issue time, expiry time, and configured secret. It verifies signature, expiry, and username matching.

### `JwtAuthenticationFilter.java`

Runs once per request:

```text
Authorization: Bearer <token>
-> extract token
-> read username through JwtService
-> load current user via CustomUserDetailsService
-> validate token
-> set authenticated user in SecurityContext
-> continue to controller
```

## Config package

### Why it exists

The config package contains Spring application configuration that does not belong to business logic.

### File and responsibility

`DataInitializer.java` creates a `CommandLineRunner` bean. After application startup it checks for, then creates when missing, demo users:

```text
admin    / Admin@123     / ADMIN
entry    / Entry@123     / DATA_ENTRY
approver / Approver@123  / APPROVER
viewer   / Viewer@123    / VIEWER
```

It uses `AppUserRepository` to query/save users and `PasswordEncoder` to store BCrypt password hashes.

## Exception package

### Why it exists

The exception package provides consistent JSON error responses for all controllers.

### File and responsibility

`GlobalExceptionHandler.java` uses `@RestControllerAdvice` and handles:

| Error | HTTP status |
|---|---:|
| `NoSuchElementException` | 404 Not Found |
| `IllegalArgumentException` | 400 Bad Request |
| `IllegalStateException` | 400 Bad Request |
| `MethodArgumentNotValidException` | 400 Bad Request |

Examples include a missing record, unsupported upload file, invalid workflow transition, or invalid request body.

## Full request journey — submit a record

```text
1. React sends POST /api/records/10/submit with Bearer JWT.
2. JwtAuthenticationFilter reads and validates the JWT.
3. CustomUserDetailsService loads the user and role from app_users.
4. SecurityConfig requires an authenticated request.
5. MasterRecordController.submit() allows only ADMIN/DATA_ENTRY.
6. Controller calls MasterRecordService.submit(id, username).
7. Service loads record through MasterRecordRepository.findById().
8. Service calls ValidationService.validate().
9. If errors exist, IllegalStateException is thrown.
10. GlobalExceptionHandler returns HTTP 400 JSON.
11. If valid, service changes DRAFT to PENDING_APPROVAL.
12. MasterRecordRepository.save() persists change to MySQL.
13. AuditService.log() creates SUBMIT audit event.
14. AuditLogRepository.save() stores it in audit_logs.
15. Controller returns updated MasterRecord JSON to React.
```

## Interview questions specific to this backend

1. Why is `DataQualityApplication` located in the `com.dqplatform` root package, and how does that affect component scanning?
2. What is the difference between `statusValue` and `RecordStatus` in `MasterRecord`?
3. Why are invalid master records saved as `DRAFT` instead of rejected immediately?
4. How does `ValidationService` check duplicates differently during create and update?
5. Explain the full flow from `POST /api/auth/login` to JWT generation.
6. What does `JwtAuthenticationFilter` do before a request reaches `MasterRecordController`?
7. Why are frontend role checks not sufficient without `@PreAuthorize` on the backend?
8. Why are create, update, submit, approve, reject, and upload methods marked `@Transactional`?
9. What design benefit does `AuditService` provide over audit code in each controller?
10. What production improvements would you make to JWT secrets, database credentials, CORS, CSV parsing, and Hibernate schema migrations?
