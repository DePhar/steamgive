# GiftHunter

GiftHunter is an Android-first giveaway platform MVP in active development.

## Repository contents

- `backend/`: FastAPI API with PostgreSQL/SQLAlchemy, password hashing, JWT auth, user profiles, giveaway listing/creation, paid entries using internal points, and winner drawing after a giveaway closes.
- `android/`: Kotlin + Jetpack Compose app with registration, login, giveaway listing, points display, entry submission, and in-app giveaway creation.
- `docker-compose.yml`: local PostgreSQL and API services.
- `.github/workflows/`: backend test workflow and Android debug APK build workflow.

## Run the backend locally

1. Install Docker Desktop or Docker Engine with the Compose plugin.
2. Copy `.env.example` to `.env`.
3. Replace `POSTGRES_PASSWORD`, update the password in `DATABASE_URL` to match it, and set `JWT_SECRET` to a cryptographically random value of at least 32 characters.
4. Run `docker compose up --build`.
5. Open `http://localhost:8000/docs` for the interactive API docs or `http://localhost:8000/health` for the health check.

The API requires `DATABASE_URL` and a `JWT_SECRET` of at least 32 characters. Never commit `.env` or use example secrets outside local development.

## Android app

Open the `android/` directory in Android Studio and sync Gradle. By default, the app targets the Android emulator's host API at `http://10.0.2.2:8000/`. To use a physical device on the same Wi-Fi network as the development computer, pass the computer's LAN address as a Gradle property, for example `-PapiBaseUrl=http://192.168.1.10:8000/` (replace the IP with the actual address). Ensure the backend listens on the LAN interface and the computer firewall allows the port. HTTP is enabled only in debug builds for local development; the main manifest disables cleartext traffic by default. Use an HTTPS endpoint for release builds. Logged-in users can create giveaways from the app; the current form sets a fixed 24-hour duration.

A successful Android GitHub Actions run publishes `GiftHunter-debug-apk` as a workflow artifact. The APK is not committed to this repository.

## Current limitations

This is an MVP, not yet a production launch. Steam OpenID, password reset, email verification, editing/canceling giveaways, winner/prize fulfilment, moderation/admin provisioning, Alembic migrations, rate limiting, persistent Android login, and production HTTPS/deployment configuration remain to be implemented. The API currently creates tables automatically for development; do not use that in place of migrations for production.

## API overview

- `POST /auth/register`
- `POST /auth/login`
- `GET /users/me`
- `GET /giveaways`
- `POST /giveaways`
- `POST /giveaways/{id}/enter`
- `POST /giveaways/{id}/draw`
- `GET /giveaways/{id}/winners`
