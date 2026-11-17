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



