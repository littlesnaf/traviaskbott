# Traviask Bot

Spring Boot service that ingests booking emails, stores reservations, and
generates optimized pickup routes for drivers.

## Features
- Gmail IMAP ingestion for GetYourGuide booking notifications.
- Auto-parsing of reservation details (dates, tour, pax, pickup, district).
- PostgreSQL persistence with JPA.
- VRP-based route optimization via Google OR-Tools.
- Google Maps geocoding/directions utilities.
- REST APIs for reservations, drivers, and route optimization.
- Thymeleaf pages for viewing and editing reservations.

## Tech stack
- Java 17, Spring Boot 3.4.x, Maven
- PostgreSQL + Spring Data JPA
- Google Maps API (Geocoding/Directions)
- Google OR-Tools (VRP)

## Requirements
- Java 17+
- Maven (or use `./mvnw`)
- PostgreSQL running locally
- Gmail account with IMAP enabled
- Google Maps API key

## Configuration
Application settings live in `src/main/resources/application.properties`.
Environment variables are expected for secrets:

- `GMAIL_USER`
- `GMAIL_PASSWORD`
- `GOOGLE_MAPS_API_KEY`

Database defaults (update as needed):
- `spring.datasource.url=jdbc:postgresql://localhost:5432/traviask`
- `spring.datasource.username=murattuncel`
- `spring.datasource.password=`

## Run locally
```bash
./mvnw spring-boot:run
```

App runs on `http://localhost:8080`.

## Web pages
- `GET /reservations` - list + edit reservations (Thymeleaf)
- `POST /optimize` - trigger route optimization for the page

## Key REST endpoints
Reservations:
- `GET /api/reservations/trigger` - manually fetch new reservation emails
- `GET /api/reservations/active`
- `GET /api/reservations/confirmed`
- `GET /api/reservations/cancelled`
- `POST /api/reservations` or `/new` or `/create` - create reservations
- `PUT /api/reservations/{id}/update`
- `POST /api/reservations/{id}/cancel`
- `POST /api/reservations/{id}/uncancel`
- `DELETE /api/reservations/{id}`

Routes:
- `GET /api/routes/optimized?after=YYYY-MM-DD`
- `GET /api/routes/optimized/mapsUrls?after=YYYY-MM-DD`
- `GET /api/routes/optimized/schedules?after=YYYY-MM-DD`

Drivers:
- `GET /api/drivers`
- `POST /api/drivers`
- `DELETE /api/drivers/{id}`

## Notes
- A scheduler runs every 30 minutes to ingest unread booking emails.
- Route optimization uses internal driver hub addresses defined in
  `RouteController.DRIVER_ADDRS`.
