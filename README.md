# 🎬 TripSlice — AI-Powered Vacation Video Montages  

![Java](https://img.shields.io/badge/Java-21+-red?logo=java&logoColor=white)  
![Node.js](https://img.shields.io/badge/Node.js-18+-green?logo=node.js&logoColor=white)  
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql&logoColor=white)  
![FFmpeg](https://img.shields.io/badge/FFmpeg-Latest-lightgrey?logo=ffmpeg&logoColor=white)  
![License](https://img.shields.io/badge/license-MIT-yellow)  

---

Tired of scrubbing through hours of shaky vacation footage just to find those few golden moments?  
**TripSlice** does the heavy lifting for you.  

Upload your long, cluttered vacation videos → pick a topic (like *Eiffel Tower* 🌍 or *ocean views* 🌊) → get back a perfectly curated montage of just those moments.  

I built TripSlice after coming back from a family trip to Paris, when I wanted *just* the Eiffel Tower clips without digging through endless video files. TripSlice made it effortless.  

Under the hood:  
- 🧠 Uses [TwelveLabs](https://twelvelabs.io) AI video understanding API to find timestamped segments.  
- ✂️ Uses FFmpeg to trim and stitch clips into your montage.  
- 🗄️ Uses PostgreSQL for storing metadata.  
- ⚡ Powered by a hybrid backend: Spring Boot.  

---

## 🚀 Prerequisites  

Before running TripSlice, make sure you’ve got these installed:  

- **Node.js & npm**  
- **Java 21+**  
- **PostgreSQL**  
  - Mac: `brew install postgresql@16`  
  - Windows: [Download here](https://www.postgresql.org/download/)  
- **FFmpeg**  
  - Mac: `brew install ffmpeg`  
  - Windows: [Download builds](https://www.gyan.dev/ffmpeg/builds/) → extract → add `bin` to your PATH  
- **TwelveLabs API key + Index ID**  
  - Go to [TwelveLabs Playground](https://twelvelabs.io) → sign up / log in  
  - Create an API key under **Overview**  
  - Create an index under **Indexes**, then grab the **Index ID** from the menu  

---

## 🛠️ Setup & Usage  

### 🎨 Frontend  
```bash
git clone <this-repo>
cd frontend
npm install
npm run dev
```

### 🗄️ PostgreSQL Server  

Create and start a Postgres instance (example with custom port `5433` and username "umerqureshi"):  

```bash
initdb -D ./pgdata -U umerqureshi
pg_ctl -D ./pgdata -o "-p 5433" -l logfile start
```

👉 Default password is empty, but you can set one later. 


### ⚙️ Backend (Spring Boot)  

1. Go to `src/main/resources`.  
2. Add your API key + Index ID in `application-secrets.properties`.  
3. Update `application.properties` with your database details.  
4. Run the backend:  

```bash
mvn spring-boot:run




---

## 🧪 Tests

Both test suites run fully offline. S3 and TwelveLabs are mocked, so tests never spend credits or touch AWS. The backend uses a throwaway embedded Postgres, never your dev database.

```bash
# Backend (needs ffmpeg on PATH). Fails the build under 90% line coverage
mvn verify            # report: target/site/jacoco/index.html

# AI service. Fails under 90% coverage
cd ai-service && pip install -r requirements-dev.txt && pytest

# Frontend (Vitest + Testing Library). Fails under 90% line coverage
cd frontend && npm run coverage
```

## 🚂 Deploying on Railway

Production runs on Railway (project `tripslice`): Postgres, **backend** (root directory `/`), and **ai-service** (root directory `/ai-service`). Both app services deploy automatically from `main`. Their build and health-check settings live in Railway itself, since `railway.toml` is deprecated:

| Service | Dockerfile | Health check | Replicas |
|---|---|---|---|
| backend | `Dockerfile` | `/actuator/health` | 1 (the in-memory rate limits need a single instance) |
| ai-service | `ai-service/Dockerfile` | `/health` | 1 |

Only the backend has a public domain; the AI service is reachable only over Railway's private network. The frontend stays on Vercel (project `tidier`) and deploys from `main`.

| Service | Variable | Value |
|---|---|---|
| backend | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| backend | `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGUSER}}` / `${{Postgres.PGPASSWORD}}` |
| backend | `JWT_SECRET` | `openssl rand -base64 48` |
| backend | `AI_SERVICE_KEY` | same as the ai-service `SERVICE_KEY` (32+ chars) |
| backend | `AI_SERVICE_URL` | `http://${{ai-service.RAILWAY_PRIVATE_DOMAIN}}:8000` |
| backend | `FRONTEND_HOST` | your exact Vercel origin, e.g. `https://tripslice.vercel.app` |
| backend | `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_REGION` / `AWS_S3_BUCKET` | least-privilege IAM user for the bucket |
| backend | `CLIENT_IP_HEADER` | `X-Real-IP` |
| backend | `TURNSTILE_SECRET` | optional, enables the sign-up bot check |
| backend | `JPA_DDL_AUTO` | `validate` once the schema has been migrated |
| ai-service | `TWELVELABS_API_KEY`, `TWELVELABS_INDEX_ID`, `SERVICE_KEY` | |
| ai-service | `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` |
| frontend (Vercel) | `VITE_BACKEND_URL`, `VITE_TURNSTILE_SITE_KEY` | |

Per-user daily caps on paid AI calls can be tuned with `LIMIT_DAILY_ANALYZE_CALLS`, `LIMIT_DAILY_SEARCHES` and `LIMIT_DAILY_MONTAGES`.
