# GiftHunter

GiftHunter is an Android-first giveaway platform prototype. This repository currently contains the backend bootstrap; the Android client and production infrastructure are still to be built.

## Stack
- FastAPI / Python
- PostgreSQL
- SQLAlchemy async
- JWT access tokens and Argon2 password hashes
- Docker Compose for local development

## Run locally
1. Copy `.env.example` to `.env` and replace the secrets.
2. Run `docker compose up --build`.
3. Open `http://localhost:8000/docs` for the interactive API docs and `http://localhost:8000/health` for the health check.

Never use example secrets in a public deployment. Before production, add database migrations, rate limiting, email verification/password recovery, admin provisioning, audit logging, and automated tests. Steam OpenID and the Android client are not implemented yet.
