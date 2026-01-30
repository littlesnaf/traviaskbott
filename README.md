# Traviask

Logistics assistant for tourism companies that automatically collects booking
emails from GetYourGuide, creates reservations, and computes optimal driver
routes for daily transportation.

## Why it matters
Tour operators often build pickup routes manually from scattered booking emails.
Traviask removes that manual work by turning emails into structured reservations
and splitting passengers across vehicles with optimized routes.

## What it does
- Auto-ingests new GetYourGuide booking emails on a schedule.
- Parses customer, pickup, date/time, pax, and tour details.
- Stores reservations in PostgreSQL.
- Solves a vehicle routing problem (VRP) to assign customers to drivers.
- Produces optimized pickup sequences and Google Maps links.

## Highlights (portfolio)
- End-to-end automation: email → reservation → optimized route.
- Real-world constraints: vehicle capacity, hub locations, region rules.
- Google Maps geocoding + OR-Tools optimization integration.
- REST APIs plus a simple Thymeleaf admin page.

## Architecture (high level)
1) Gmail IMAP fetch reads unread booking emails.
2) Parser extracts reservation data.
3) Data is persisted with Spring Data JPA.
4) VRP solver assigns stops to drivers and orders pickups.
5) Routes and maps URLs are exposed via REST endpoints.

## Tech stack
- Java 17, Spring Boot 3.4.x, Maven
- PostgreSQL + Spring Data JPA
- Google Maps API (Geocoding/Directions)
- Google OR-Tools (VRP)
- Docker

## Requirements
- Java 17+
- Maven (or use `./mvnw`)
- PostgreSQL running locally
- Gmail account with IMAP enabled
- Google Maps API key

## Configuration
Application settings: `src/main/resources/application.properties`.

Secrets (env vars):
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

## Deployment
- Dockerized for deployment.
- Previously hosted on AWS (currently removed).

## Screenshots
<img width="1419" height="703" alt="Routes map links" src="https://github.com/user-attachments/assets/7736ebfd-9faa-4d00-8625-e1d6a31b11ed" />
<img width="1453" height="800" alt="Route optimization" src="https://github.com/user-attachments/assets/a873a5d5-7feb-41a2-bbfb-12f0444c3b1f" />
<img width="1460" height="573" alt="Reservations" src="https://github.com/user-attachments/assets/c2b18347-6d82-43f6-b7a8-559f7182c67e" />

## Notes
- A scheduler runs every 30 minutes to ingest unread booking emails.
- Route optimization uses internal driver hub addresses defined in
  `RouteController.DRIVER_ADDRS`.
