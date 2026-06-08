# Hostinger Deployment Checklist

## ✅ Completed Locally

### Frontend RH

- [x] **vite.config.ts**: `base: '/rh/'` (was `/Antigone_RH/rh/`)
- [x] **axios.ts**: Uses `/api` relative path (Nginx proxy)
- [x] **main.tsx**: BrowserRouter with basename configured ✓

### Frontend Projects

- [x] **vite.config.ts**: `base: '/projects/'` (was `/Antigone_RH/projects/`)
- [x] **axios.ts**: Uses `/api` relative path (Nginx proxy)
- [x] **main.tsx**: BrowserRouter with basename configured ✓

### Backend

- [x] **application.yml**: Added CORS for 2.24.15.95
- [x] **application-prod.yml**: Created production config
- [x] **Dockerfile**: Updated with health checks
- [x] **pom.xml**: Java 17, PostgreSQL driver ✓

### Docker & Infrastructure

- [x] **docker-compose.yml**: PostgreSQL + Spring Boot orchestration
- [x] **.env.example**: All required variables documented
- [x] **nginx.conf**: Complete reverse proxy configuration

---

## 🚀 Deployment Steps (on Hostinger)

### Phase 1: System Setup (SSH)

1. [ ] SSH into VPS: `ssh root@2.24.15.95`
2. [ ] Create directories: `/var/www/solutions-web/rh-application` + `/var/www/rh` + `/var/www/projects`
3. [ ] Set permissions: `chown -R $(whoami):www-data /var/www/`
4. [ ] Clone repository to `/var/www/solutions-web/rh-application`

### Phase 2: Docker Setup

1. [ ] Install Docker: `sudo apt install -y docker.io docker-compose-v2`
2. [ ] Create `.env` file from `.env.example` with actual secrets
3. [ ] Generate JWT: `openssl rand -base64 64`
4. [ ] Generate DB password: `openssl rand -base64 32`
5. [ ] Secure `.env`: `chmod 600 .env`

### Phase 3: Backend & Database

1. [ ] Start Docker: `docker compose up -d --build`
2. [ ] Wait for PostgreSQL healthy check (~30s)
3. [ ] Wait for Spring Boot to start (~40s)
4. [ ] Verify backend: `curl http://127.0.0.1:8080/actuator/health`

### Phase 4: Frontend Build

1. [ ] Build RH: `cd frontend-rh && npm ci && npm run build`
2. [ ] Deploy RH: `sudo cp -r dist/* /var/www/rh/`
3. [ ] Build Projects: `cd ../frontend-projects && npm ci && npm run build`
4. [ ] Deploy Projects: `sudo cp -r dist/* /var/www/projects/`

### Phase 5: Nginx Configuration

1. [ ] Copy nginx.conf to `/etc/nginx/sites-available/rh-application`
2. [ ] Enable site: `sudo ln -s /etc/nginx/sites-available/rh-application /etc/nginx/sites-enabled/rh-application`
3. [ ] Test config: `sudo nginx -t`
4. [ ] Reload Nginx: `sudo systemctl reload nginx`

### Phase 6: Testing

1. [ ] Visit http://2.24.15.95/rh/ → Should show RH login
2. [ ] Visit http://2.24.15.95/projects/ → Should show Projects login
3. [ ] Test API: `curl http://2.24.15.95/api/auth/health`
4. [ ] Check browser console for errors (F12)

### Phase 7: Security (Optional)

1. [ ] Install Certbot: `sudo apt install -y certbot python3-certbot-nginx`
2. [ ] Get SSL cert: `sudo certbot --nginx -d 2.24.15.95`
3. [ ] Auto-renew: `sudo certbot renew --dry-run`

---

## ⚠️ Key Differences from Your Guide

| Aspect          | Your Guide         | Our Implementation                  |
| --------------- | ------------------ | ----------------------------------- |
| **Vite Base**   | `/Antigone_RH/rh/` | `/rh/` ✓                            |
| **API URL**     | Hardcoded Render   | Relative `/api` ✓                   |
| **CORS**        | localhost only     | 2.24.15.95 + localhost ✓            |
| **Dockerfile**  | Custom entrypoint  | Direct Java launch + health check ✓ |
| **Nginx Paths** | Root config needed | Complete standalone config ✓        |
| **Env Vars**    | Inline in compose  | Centralized `.env` ✓                |

---

## 🎯 Architecture Confirmation

Your architecture is **EXCELLENT** for Hostinger:

✅ **Centralized code**: `/var/www/solutions-web/rh-application`
✅ **Separated builds**: `/var/www/rh` & `/var/www/projects`
✅ **Docker isolation**: PostgreSQL + Java in containers
✅ **Nginx proxy**: Single entry point, no CORS issues
✅ **Scalability**: Easy to add more services
✅ **No path conflicts**: `/rh/` and `/projects/` are completely isolated

**Why this works:**

- React SPA routing handled by Vite's `base: '/rh/'`
- API requests go to `/api/` → Nginx proxies → Docker backend
- Nginx serves static assets, doesn't interfere with routes
- Docker containers isolated from host system
- No localhost hardcoding issues

---

## 📝 Next Steps

1. **SSH into Hostinger** and follow Phase 1-5 of deployment steps
2. **Keep this checklist** for reference during deployment
3. **Monitor logs**: `docker compose logs -f` and `sudo tail -f /var/log/nginx/error.log`
4. **Test thoroughly** before going to production

---

**Status**: ✅ Configuration Complete | Ready for Hostinger Deployment
**Files Modified/Created**: 8 files
**API Architecture**: Clean with Nginx reverse proxy
**Frontend Architecture**: Isolated apps with no path conflicts
