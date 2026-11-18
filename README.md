<div align="center">

# 🚀 AgileMind — AI-Powered Agile Management Platform

**A professional, full-stack project management tool built for engineering teams.**  
Kanban boards · Sprint analytics · AI-driven risk insights · Automated standup reporting.

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![React](https://img.shields.io/badge/React_19-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-007ACC?style=for-the-badge&logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![Google Gemini](https://img.shields.io/badge/Google_Gemini-4285F4?style=for-the-badge&logo=google&logoColor=white)

</div>

---

## 📖 Overview

**AgileMind** is a capstone project built as a Jira/Trello-style agile platform with an integrated AI intelligence layer. It enables software teams to manage projects, sprints, and task workflows while receiving real-time AI-generated risk assessments and reassignment suggestions powered by **Google Gemini** via **n8n** automation pipelines.

The application is now named AgileMind. Existing database names, Docker storage identifiers, webhook identifiers and browser storage keys retain their previous values for compatibility with existing installations.

### Key Highlights

- 🏗️ **Full Monorepo** — Spring Boot backend + React frontend in a single repository
- 🔐 **JWT-secured REST API** with role-based access control (`OWNER`, `MANAGER`, `DEVELOPER`, `VIEWER`)
- 📊 **Sprint Health Scoring** — composite 0–100 metric from velocity, workload, and timing
- 🤖 **AI Risk Engine** — Google Gemini identifies delivery risks and suggests reassignments
- 🔄 **n8n Automation** — webhook-driven workflow triggers LLM pipelines on every task change
- 🎯 **Design Patterns** — Observer, Strategy, and Composite patterns throughout the backend

---

## 🏛️ Architecture

```
AgileMind/
├── backend/           # Spring Boot 3 REST API (Java 17)
│   └── src/
│       └── main/java/
│           ├── controller/    # REST endpoints
│           ├── service/       # Business logic & design patterns
│           ├── entity/        # JPA entities
│           ├── repository/    # Spring Data repositories
│           └── security/      # JWT filter chain
├── frontend/          # React 19 SPA (TypeScript + Vite)
│   └── src/
│       ├── api/           # Axios interceptors
│       ├── components/    # Reusable UI components
│       ├── pages/         # Route-level views
│       └── types/         # Shared TypeScript interfaces
└── .env.example       # Environment variable template
```

---

## ✨ Features

### 🗂️ Agile Project Management
| Feature | Description |
|---|---|
| **Projects** | Create and manage multiple projects with role-based membership |
| **Sprint Lifecycle** | Start, manage, and complete sprints with full validation |
| **Requirement Backlog** | Hierarchical tree of requirements and subtasks (Composite Pattern) |
| **Kanban Board** | Drag-and-drop task board with `TODO → IN_PROGRESS → DONE` columns |
| **Inline Task Creation** | Create and assign subtasks with story points directly in the backlog |

### 📈 Analytics & Metrics Engine
| Metric | Formula |
|---|---|
| **Sprint Health Score** | Velocity (40%) + Workload balance (30%) + Timing (30%) |
| **Team Capacity Heatmap** | Assigned story points vs. per-member capacity limits |
| **Velocity Trend** | Planned vs. completed story points across historical sprints |
| **Workload Distribution** | Story points grouped by Kanban column |

### 🤖 AI & Automation Layer
- **Observer Pattern** dispatches task events as webhooks to n8n on every task update/creation
- **n8n Workflow Engine** receives events and invokes Google Gemini with full sprint context
- **AI Insights** are persisted back to PostgreSQL and surfaced in the UI as:
  - `RISK_WARNING` — bottleneck and delivery risk alerts
  - `REASSIGNMENT_SUGGESTION` — smart workload rebalancing recommendations

---

## 🛠️ Tech Stack

### Backend
| Technology | Version | Role |
|---|---|---|
| Java | 17 | Core language |
| Spring Boot | 3.x | Application framework |
| Spring Security | 6.x | JWT authentication & RBAC |
| Spring Data JPA | — | ORM & database access |
| PostgreSQL | 15+ | Primary database |
| Maven | 3.x | Build tool |

### Frontend
| Technology | Version | Role |
|---|---|---|
| React | 19 | UI framework |
| TypeScript | 6.x | Type safety |
| Vite | 8.x | Build tool & dev server |
| Tailwind CSS | 4.x | Styling |
| @dnd-kit | 6.x | Drag-and-drop Kanban |
| Recharts | 3.x | Analytics charts |
| Axios | 1.x | HTTP client |
| React Router | 7.x | Client-side routing |
| Lucide React | — | Icon library |

### Infrastructure & AI
| Technology | Role |
|---|---|
| Docker & Docker Compose | Local PostgreSQL database |
| n8n | Automation workflow engine |
| Google Gemini API | AI risk analysis & recommendations |

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+** — [Download](https://adoptium.net/)
- **Node.js 20+** — [Download](https://nodejs.org/)
- **Docker Desktop** — [Download](https://www.docker.com/products/docker-desktop/)
- **Maven 3.x** — included via `./mvnw` wrapper
- **Google Gemini API Key** — [Get one](https://aistudio.google.com/app/apikey)

---

### 1. Clone the Repository

```bash
git clone https://github.com/yassine-dev22/LevelUP.git AgileMind
cd AgileMind
```

---

### 2. Configure Environment Variables

Copy the example environment file and fill in your values:

```bash
cp .env.example .env
```

Edit `.env`:

```env
# Database
DB_URL=jdbc:postgresql://localhost:55432/levelup
DB_USERNAME=levelup
DB_PASSWORD=your_secure_password

# JWT
JWT_SECRET=
JWT_EXPIRATION_SECONDS=86400

# Google Gemini AI
GEMINI_API_KEY=your_gemini_api_key
GEMINI_MODEL=gemini-1.5-flash

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:5173

# Frontend
VITE_API_BASE_URL=http://localhost:8082/api
```

---

### 3. Start the Database

```bash
docker compose up -d
```

This starts PostgreSQL on loopback port `55432`. Development uses Hibernate schema updates; production validates the schema after reviewed SQL changes are applied. Configure `AI_CALLBACK_SECRET` and `N8N_WEBHOOK_SECRET` when using the AI workflow. Production also requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` and `CORS_ALLOWED_ORIGINS`.

---

### 4. Start the Backend

```bash
cd backend
./mvnw spring-boot:run
```

The API will be available at **`http://localhost:8082/api`**.

---

### 5. Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

The app will be available at **`http://localhost:5173`**.

---

### 6. (Optional) Start the n8n AI Workflow

```bash
docker compose -f docker-compose.n8n.yml up -d
```

Access the n8n editor at **`http://localhost:5678`** and import `n8n_workflow.json` to activate the Gemini AI pipeline.

---

## 🔑 API Overview

### Authentication
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register a new user |
| `POST` | `/api/auth/login` | Login and receive a JWT token |

### Projects & Members
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/projects` | List all projects for current user |
| `POST` | `/api/projects` | Create a new project |
| `GET` | `/api/projects/{id}/workspace` | Fetch full project workspace (sprint + tasks) |

### Sprints
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/sprints` | Create a sprint |
| `PUT` | `/api/sprints/{id}/start` | Start a sprint |
| `PUT` | `/api/sprints/{id}/complete` | Complete sprint (validates all tasks done) |
| `GET` | `/api/sprints/{id}/insights` | Fetch AI-generated sprint insights |

### Tasks & Requirements
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/requirements` | Create a requirement |
| `POST` | `/api/tasks` | Create a subtask |
| `PUT` | `/api/tasks/{id}/status` | Update task status (triggers AI webhook) |

> All endpoints (except `/api/auth/**`) require a `Bearer <token>` header.

---

## 🏗️ Design Patterns

| Pattern | Location | Purpose |
|---|---|---|
| **Observer** | `TaskService` → Webhook Dispatcher | Publishes task events to n8n on every mutation |
| **Strategy** | `SprintHealthService` | Pluggable health score calculation strategy |
| **Composite** | `Requirement` ↔ `Task` entities | Hierarchical requirement/subtask tree structure |

---

## 👥 Role Permissions

| Action | OWNER | MANAGER | DEVELOPER | VIEWER |
|---|:---:|:---:|:---:|:---:|
| Create / Delete Project | ✅ | ❌ | ❌ | ❌ |
| Manage Sprints | ✅ | ✅ | ❌ | ❌ |
| Create Requirements & Tasks | ✅ | ✅ | ✅ | ❌ |
| Update Task Status (Kanban) | ✅ | ✅ | ✅ | ❌ |
| View Board & Analytics | ✅ | ✅ | ✅ | ✅ |

---

## 📁 Project Structure (Detailed)

```
backend/src/main/java/
├── controller/
│   ├── AuthController.java
│   ├── ProjectController.java
│   ├── SprintController.java
│   ├── RequirementController.java
│   └── TaskController.java
├── service/
│   ├── AuthService.java
│   ├── ProjectService.java
│   ├── SprintService.java
│   ├── SprintHealthService.java      # Strategy Pattern
│   ├── TaskService.java              # Observer Pattern (webhook dispatch)
│   └── AIInsightService.java
├── entity/
│   ├── User.java
│   ├── Project.java
│   ├── ProjectMember.java
│   ├── Sprint.java
│   ├── Requirement.java              # Composite Pattern root
│   └── Task.java                    # Composite Pattern leaf
└── security/
    ├── JwtFilter.java
    └── SecurityConfig.java

frontend/src/
├── api/
│   └── axios.ts                     # Axios instance with JWT interceptor
├── components/
│   ├── KanbanBoard/                 # dnd-kit drag-and-drop board
│   ├── BacklogTree/                 # Hierarchical requirement tree
│   ├── TeamCapacity/                # Capacity heatmap grid
│   └── Analytics/                   # Recharts widgets
└── pages/
    ├── LoginPage.tsx
    ├── DashboardPage.tsx
    ├── ProjectsPage.tsx
    └── SprintPage.tsx
```

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch: `git checkout -b feat/your-feature`
3. Commit your changes: `git commit -m 'feat: add your feature'`
4. Push to the branch: `git push origin feat/your-feature`
5. Open a Pull Request

---

## 📄 License

This project was developed as a **Capstone Project** for the 2nd year of engineering studies.

---

<div align="center">
  Built with ❤️ by <strong>yassine-dev22/
Saaf-ghost/
Mansour</strong>
</div>

## Session 2: Co-Pilot and sprint diagnostics

JWT authentication and project membership are checked before Spring delegates a question to FastAPI. Persisted project roles control access, and a global VIEWER remains read-only. The Kanban board loads tasks from the workspace API and saves status changes through the task API.

| Method | Endpoint | Request / result |
|---|---|---|
| POST | `/ai/copilot/ask` | `{ "question": "Why was this task blocked?", "project_id": 1 }`; returns `answer` and `citations` |
| POST | `/ai/sprintdiagnostics/trigger` | `{ "sprint_id": 1 }`; queues real metrics when health is below 60 |
| POST | `/ai/sprint-diagnostics/trigger` | Same operation, matching the original proposal spelling |
| GET | `/actuator/health/liveness` | Process health, without internal details |
| GET | `/actuator/health/readiness` | Traffic readiness, including database availability |

The AI routes also support the `/api/ai` prefix used by the existing frontend. The probe routes allow unauthenticated GET requests; other Actuator routes are blocked. A healthy sprint returns `SKIPPED`; a low-health sprint returns `QUEUED` when the webhook is configured or `LOCAL_FALLBACK` when it is not configured. Webhooks run after transaction commit. Delivery failures use local insights. A queued response does not confirm that n8n has completed its work.

### Existing memberships

New projects and invitations write `project_memberships` automatically while keeping the legacy `project_members` join table for compatibility. For existing PostgreSQL data, review and apply `backend/src/main/resources/db/project-memberships-backfill.sql`. It inserts missing owner/member rows and preserves existing explicit roles. Legacy memberships remain readable before the backfill is applied.

### FastAPI connection

Set `RAG_SERVICE_URL` to the separate FastAPI service and configure the same private `RAG_SERVICE_TOKEN` on both services. Spring sends the token as `X-RAG-Service-Token`. The environment example uses `http://127.0.0.1:8000`. Connection and read timeouts use `RAG_CONNECT_TIMEOUT` and `RAG_READ_TIMEOUT`.

The expected FastAPI contract is `POST /ask` with `{ "question": "...", "project_id": 1 }`, returning `{ "answer": "...", "citations": [{ "type": "Task", "source_id": 1, "author": "...", "date": "...", "chunk_index": 0 }] }`. Spring validates questions up to 500 characters, rejects unauthorized projects before making an HTTP call and returns a sanitized HTTP 503 if the service is unavailable. Implementing the Python service is a separate task.

### n8n connection

Set `N8N_WEBHOOK_URL`, `N8N_WEBHOOK_SECRET` and `AI_CALLBACK_SECRET`. Configure n8n Header Auth for `X-LevelUP-Webhook-Token` and use the independent `X-AI-Callback-Token` on callbacks. The payload includes current velocity, workload balance, bottlenecks and remaining days. Import the existing workflow into a running n8n instance. This session adds the backend connection; it does not deploy n8n or configure model credentials.
