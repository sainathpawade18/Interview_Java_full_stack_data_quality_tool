# Data Quality Platform

Interview-ready full-stack project built with:
- React 19 + Vite
- Java 21 + Spring Boot 3.5
- Spring Security + JWT + RBAC
- MySQL 8
- REST APIs
- CSV/XLSX bulk upload
- Master-data validation
- Approval workflow
- Audit logging
- Docker / Docker Compose
- Kubernetes manifests
- GitHub Actions CI/CD
- AWS target architecture: ECR + EKS + RDS MySQL

## Business flow

Business users upload or manually maintain master data.
The backend validates records for:
1. Missing mandatory fields
2. Duplicate records
3. Invalid email/phone
4. Invalid status/category values

Valid records can be submitted for approval.
Approvers can approve/reject records.
Every important operation is audit logged.

## Roles

- ADMIN: manage users and all records
- DATA_ENTRY: create/edit/upload records
- APPROVER: approve/reject submitted records
- VIEWER: read-only access

## Local prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+
- Docker Desktop
- Git

## Run backend locally

Create MySQL first, or use Docker Compose.

```bash
docker compose up -d mysql
cd backend
mvn spring-boot:run
```

Backend: http://localhost:8080

## Run frontend locally

```bash
cd frontend
npm install
npm run dev
```

Frontend: http://localhost:5173

## Run everything with Docker

```bash
docker compose up --build
```

Frontend: http://localhost:5173
Backend: http://localhost:8080

## Demo users

The application seeds these users:

- admin / Admin@123
- entry / Entry@123
- approver / Approver@123
- viewer / Viewer@123

Change these credentials before any real deployment.

## Interview story

"I worked on an enterprise data-quality platform where business users upload master data through the React UI or maintain individual records manually. React sends the data to Spring Boot REST APIs. The backend validates mandatory fields, duplicates and business rules before persisting records in MySQL. Records that pass validation can move through an approval workflow. We implemented JWT authentication and RBAC, centralized exception handling, audit logging and Dockerized the application. For deployment, container images are pushed to Amazon ECR and Kubernetes deployments run on Amazon EKS, while production data is stored in Amazon RDS MySQL."

## Suggested implementation order

1. Run MySQL + backend
2. Login
3. CRUD master records
4. Validation
5. CSV/XLSX upload
6. Approval workflow
7. Audit logs
8. Docker
9. Kubernetes locally
10. GitHub Actions
11. AWS ECR
12. AWS EKS + RDS
