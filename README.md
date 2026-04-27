# FinPlay - Stock Portfolio & Learning Simulator

FinPlay is a learning-driven stock portfolio simulator with an AI mentor.

## 🚀 Deployment Instructions

### 1. Environment Setup
Create a `.env` file in the root directory using `.env.example` as a template:
```bash
cp .env.example .env
# Open .env and fill in your actual API keys and secrets
```

### 2. Run with Docker (Recommended)
Ensure Docker and Docker Compose are installed.
```bash
docker-compose up --build
```
This will start:
- PostgreSQL (Port 5444)
- Redis (Port 6379)
- Spring Boot Backend (Port 8080)

### 3. Manual Backend Run
Requirements: Java 17+, Maven
```bash
# Load env variables or set them in your IDE
mvn spring-boot:run
```

### 4. Frontend Run
Requirements: Node.js 18+
```bash
cd frontend
npm install
# Create a frontend/.env.local file
# VITE_API_URL=http://localhost:8080
npm run dev
```

## 🛠 Tech Stack
- **Backend:** Spring Boot, Spring Security (JWT), Spring Data JPA, Redis (Caching)
- **Frontend:** React, Vite, TailwindCSS, Recharts
- **Database:** PostgreSQL
- **APIs:** Finnhub, NewsAPI, Financial Modeling Prep, Google Gemini (AI Mentor)

## 📁 Structure Highlights
- **Strict Environment Config:** No hardcoded secrets or fallbacks.
- **AI Mentor System:** Contextual learning integrated into the trading flow.
- **Hybrid API System:** Combines multiple market data sources for reliability.
- **Production Ready:** Healthchecks, timeouts, and proper exception handling.
