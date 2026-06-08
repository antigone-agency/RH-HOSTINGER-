# 🏗️ Hostinger Deployment Architecture

## 📋 Overview

This document describes the final architecture for deploying **Antigone RH** on Hostinger VPS (IP: `2.24.15.95`).

### Problem Solved ✅

Your concern about path conflicts when passing from RH to Projects is **completely resolved** through:

1. **Separate base paths**: `/rh/` and `/projects/` in Vite config
2. **Isolated builds**: Static files in separate directories
3. **Nginx reverse proxy**: Single entry point handling all routing
4. **Relative API URLs**: No hardcoded backend URLs

---

## 🗂️ Directory Structure

```
/var/www/
├── solutions-web/
│   └── rh-application/              # 📁 CODE REPOSITORY
│       ├── Backend/                 # Java Spring Boot
│       │   ├── Dockerfile
│       │   ├── pom.xml
│       │   ├── src/
│       │   └── ...
│       ├── frontend-rh/             # React RH App
│       │   ├── vite.config.ts (base: '/rh/')
│       │   ├── package.json
│       │   └── src/
│       ├── frontend-projects/       # React Projects App
│       │   ├── vite.config.ts (base: '/projects/')
│       │   ├── package.json
│       │   └── src/
│       ├── docker-compose.yml       # 🐳 Orchestration
│       ├── .env                     # 🔒 Secrets (not in git)
│       ├── nginx.conf               # 🌐 Reverse proxy
│       └── ...
│
├── rh/                              # 📦 BUILT RH FRONTEND
│   ├── index.html
│   ├── assets/
│   └── ...
│
└── projects/                        # 📦 BUILT PROJECTS FRONTEND
    ├── index.html
    ├── assets/
    └── ...
```

---

## 🔄 Request Flow Diagram

### User visits: `http://2.24.15.95/rh/`

```
Browser (Client)
    ↓
[Nginx] Listen on port 80
    ↓
[Route Match] /rh/ → /var/www/rh/
    ↓
[File Serve] index.html (with base: '/rh/')
    ↓
[React Router] Handles /rh/dashboard, /rh/employees, etc.
    ↓
[API Call] axios → /api/employees (relative)
    ↓
[Nginx Route] /api/ → Proxy to http://127.0.0.1:8080/api/
    ↓
[Spring Boot] Backend on Docker (port 8080, localhost only)
    ↓
[PostgreSQL] Data layer on Docker
```

### User visits: `http://2.24.15.95/projects/`

Same flow as above, but:

- Files served from `/var/www/projects/`
- React Router handles `/projects/*` paths
- API calls proxied to same backend

---

## 🐳 Docker Services

### PostgreSQL Container

- **Image**: `postgres:16-alpine`
- **Port**: `127.0.0.1:5432` (localhost only, not exposed)
- **Data**: Persisted in Docker volume `postgres_data`
- **Health Check**: Every 10s

### Spring Boot Backend Container

- **Build**: Multi-stage from `/Backend/Dockerfile`
- **Java**: Version 17
- **Port**: `127.0.0.1:8080` (localhost only, accessed via Nginx proxy)
- **Config**: Uses `application-prod.yml` with env vars from `.env`
- **Volumes**:
  - `uploads/` → Persistent file uploads
  - `logs/` → Application logs
  - `tokens/` → Google Drive tokens (if used)
- **Health Check**: Every 30s via `/actuator/health`

---

## 🌐 Nginx Configuration

### Server Block

- **Listens on**: Port 80 (HTTP)
- **Handles**: All incoming traffic to 2.24.15.95
- **Gzip**: Enabled for static assets

### Location Rules

| Path         | Action                                | Purpose                 |
| ------------ | ------------------------------------- | ----------------------- |
| `/`          | 302 redirect to `/rh/`                | Default landing         |
| `/rh/`       | Serve from `/var/www/rh/`             | RH Frontend (SPA)       |
| `/projects/` | Serve from `/var/www/projects/`       | Projects Frontend (SPA) |
| `/api/`      | Proxy to `http://127.0.0.1:8080/api/` | Backend API             |
| `/health`    | Return 200 "healthy"                  | Monitoring              |
| `/.`         | Deny all                              | Security                |

### SPA Routing

```nginx
try_files $uri $uri/ /rh/index.html;  # RH app
try_files $uri $uri/ /projects/index.html;  # Projects app
```

This ensures React Router handles all routes (not 404s from Nginx).

### Cache Policy

- **Static assets** (`.js`, `.css`, `.png`, etc.): 30 days cache
- **index.html**: No cache (always fetch fresh)

---

## 🔐 Environment Variables

Located in: `/var/www/solutions-web/rh-application/.env` (not in git)

```bash
# Database
POSTGRES_DB=rh_database
POSTGRES_USER=rh_user
POSTGRES_PASSWORD=<strong-password>

# Spring Boot
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=<long-base64-secret>
FRONTEND_URL=http://2.24.15.95,https://2.24.15.95

# (Optional) Email, Google Drive, etc.
```

---

## ✨ How Path Conflicts Are Avoided

### Before (Your Concern)

```
❌ Both apps trying to use root path /
❌ Routes like /dashboard collide
❌ Hardcoded localhost:3000 in one, localhost:3001 in other
❌ Moving from RH to Projects = page break
```

### After (Our Solution)

```
✅ RH on /rh/, Projects on /projects/ (no collision)
✅ Routes like /rh/dashboard and /projects/dashboard (separate)
✅ Vite `base` config defines URL prefix
✅ React Router basename handles all routing
✅ Nginx proxy routes /api/ to backend
✅ Navigation buttons: window.location.href = '/projects/';
✅ No hardcoded URLs (relative /api path)
```

---

## 🚀 Deployment Flow

### Local Development

```bash
npm run dev  # frontend runs on http://localhost:3001/
# Browser sees: /
# Vite proxy: /api → http://localhost:8080
# axios.ts: Detects localhost, uses http://localhost:8080
```

### Hostinger Production

```bash
npm run build  # Creates dist/ with base: '/rh/'
cp dist/* /var/www/rh/
# Browser sees: http://2.24.15.95/rh/
# axios.ts: Detects non-localhost, uses /api (relative)
# Nginx proxy: /api/ → http://127.0.0.1:8080/api/
```

---

## 📊 Tech Stack

| Component             | Tech                      | Version       |
| --------------------- | ------------------------- | ------------- |
| **Backend**           | Spring Boot               | 3.5.6         |
| **Java**              | OpenJDK                   | 17 LTS        |
| **Database**          | PostgreSQL                | 16 Alpine     |
| **Frontend RH**       | React + Vite + TypeScript | Latest        |
| **Frontend Projects** | React + Vite + TypeScript | Latest        |
| **Web Server**        | Nginx                     | Latest        |
| **Containerization**  | Docker Compose            | v2            |
| **Server**            | Hostinger VPS             | Ubuntu/Debian |

---

## 📈 Scalability Features

This architecture is ready for:

- ✅ **Horizontal scaling**: Add more backend replicas behind a load balancer
- ✅ **Additional services**: Easy to add new microservices in docker-compose.yml
- ✅ **HTTPS**: Simple with Let's Encrypt + Certbot
- ✅ **Monitoring**: Docker health checks + Nginx monitoring
- ✅ **Backups**: PostgreSQL dumps via Docker exec
- ✅ **Updates**: Git pull + rebuild without downtime

---

## 🎯 Key Improvements Made

| Issue                | Solution                       |
| -------------------- | ------------------------------ |
| Path conflicts       | Separate Vite base paths       |
| Localhost hardcoding | Relative API URLs              |
| CORS errors          | Proper Nginx proxy headers     |
| Dev/Prod mismatch    | Unified `application-prod.yml` |
| Secrets exposure     | `.env` file + `.gitignore`     |
| No reverse proxy     | Complete Nginx configuration   |
| SPA 404s             | `try_files` for React Router   |
| API timeouts         | Proper proxy buffering         |
| Database access      | Only local, not exposed        |

---

## 🔗 Quick Links

- **RH App**: `http://2.24.15.95/rh/`
- **Projects App**: `http://2.24.15.95/projects/`
- **API Health**: `http://2.24.15.95/api/actuator/health`
- **Server Health**: `http://2.24.15.95/health`

---

## 📞 Support Files

1. **HOSTINGER_DEPLOYMENT_CHECKLIST.md** - Step-by-step deployment
2. **.env.example** - All required environment variables
3. **docker-compose.yml** - Docker orchestration
4. **nginx.conf** - Web server configuration
5. **Backend/application-prod.yml** - Spring Boot production config
6. **frontend-rh/vite.config.ts** - React RH base path
7. **frontend-projects/vite.config.ts** - React Projects base path

---

**✅ This architecture is production-ready for Hostinger deployment.**
**🚀 All path conflicts are resolved.**
**🔒 Secrets are properly isolated.**
