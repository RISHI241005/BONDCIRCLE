# BondCircle Realtime Chat

A phone-number-first chat website built with **Java 21**, **Spring Boot 3.3.3**, **MySQL 8+**, **Flyway**, and **STOMP over WebSocket**. The responsive browser client and API ship together in one executable JAR.

---

## Features

- **Realtime Messaging (STOMP WebSocket)**: Sub-second message delivery to individual user queues (`/user/queue/messages`).
- **REST Fallback APIs**: Complete `/api/v1` REST interface for conversation management, message history, editing, soft deletion, and read acknowledgments.
- **Cursor-Based Pagination**: Backward pagination for chat history (`limit` and opaque `cursor`) optimized with composite database indexes `(conversation_id, created_at DESC, id DESC)`.
- **Zero-Trust JWT Security**: Senders and participants are securely derived from JWT claims; client-provided sender IDs are never trusted.
- **Network Retry Idempotency**: `clientMessageId` deduplication prevents duplicate message creation upon client reconnections.
- **Delivery & Read Receipts**: Realtime status transitions (`SENT` → `DELIVERED` → `READ`) with watermarked unread counters.
- **Ephemeral Typing Indicators**: Broadcasts partner typing state without database overhead (`/app/chat.typing` → `/user/queue/typing`).
- **Multi-Device Presence Tracking**: Tracks simultaneous device sessions per user (e.g. Android phone + iOS tablet) and maintains accurate `lastSeenAt` timestamps.
- **Content Moderation & Safety**: Bi-directional blocking (`BlockService`) and conversation/message reporting (`ReportService`).
- **Database Migrations (Flyway)**: Versioned schema evolution with zero reliance on `hibernate.ddl-auto=create`.
- **OpenAPI 3.0 / Swagger UI**: Interactive API documentation at `/swagger-ui.html`.

---

## Technology Stack

- **Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 3.3.3
- **Data Persistence**: Spring Data JPA / Hibernate 6.5
- **Database**: MySQL 8.0+ / 8.4 LTS
- **Schema Versioning**: Flyway
- **Realtime Broker**: Spring WebSocket + STOMP (with SockJS fallback)
- **Security**: Spring Security 6 + JJWT 0.12.6
- **Documentation**: Springdoc OpenAPI 2.6.0
- **Testing**: JUnit 5, Mockito, Spring Security Test, Testcontainers

---

## Architecture Overview

```
Flutter (Android / iOS)
   │
   ├── HTTPS / REST (/api/v1) ──┐
   │                            │
   └── WSS / STOMP (/ws) ───────┤
                                ▼
                       Spring Boot Chat Service
                                │
                  ┌─────────────┴─────────────┐
                  ▼                           ▼
         REST Controllers            WebSocket Controller
        (Conversation, Message,     (/app/chat.send, .typing,
         Presence, Report)           .delivered, .read)
                  │                           │
                  └─────────────┬─────────────┘
                                ▼
                          Service Layer
                   (Conversation, Message, Receipt,
                    Presence, Block, Report)
                                │
                                ▼
                         Repository Layer
                                │
                                ▼
                          MySQL Database
```

---

## Quick Start (Local Setup)

After startup, open `http://localhost:8080/`. Create two accounts with different phone numbers, then use one account's phone search to start a realtime conversation with the other.

### 1. Prerequisites
- **Java 21**
- **Maven 3.9+**
- **Docker & Docker Compose** (optional for local containerized MySQL)

### 2. Configure Environment
Copy `.env.example` to `.env` or set environment variables:
```bash
cp .env.example .env
```

Default local development values:
```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=dating_chat_db
DB_USERNAME=chat_user
DB_PASSWORD=your_secure_password_here
DB_ROOT_PASSWORD=rootpassword

JWT_SECRET=replace_with_at_least_32_random_characters
JWT_ISSUER=dating-app-auth-service
JWT_EXPIRATION=86400000

SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev
```

### 3. Start MySQL Container
```bash
docker compose up -d
```

### 4. Build and Run Application
```bash
mvn clean package
mvn spring-boot:run
```

---

## API Documentation (REST /api/v1)

Interactive Swagger UI is accessible at:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`
- **Health Check**: `http://localhost:8080/api/v1/health`

### REST Endpoint Summary

| Method | Endpoint | Description |
|:---|:---|:---|
| `GET` | `/api/v1/chats` | List user's active conversations with unread counts |
| `POST` | `/api/v1/chats` | Create or get direct conversation with matched user |
| `GET` | `/api/v1/chats/{id}` | Get conversation metadata and participant details |
| `DELETE` | `/api/v1/chats/{id}` | Archive / leave conversation |
| `GET` | `/api/v1/chats/{id}/messages` | Cursor-paginated message history (`limit`, `cursor`) |
| `POST` | `/api/v1/chats/{id}/messages` | Send message (REST fallback with `clientMessageId`) |
| `POST` | `/api/v1/chats/{id}/read` | Acknowledge read receipt up to specified message |
| `PATCH` | `/api/v1/chats/{id}/messages/{msgId}` | Edit sender's message content |
| `DELETE` | `/api/v1/chats/{id}/messages/{msgId}` | Soft-delete sender's message |
| `POST` | `/api/v1/chats/{id}/reports` | Submit a moderation report |
| `GET` | `/api/v1/presence/{userId}` | Query user online status and last seen timestamp |

---

## STOMP WebSocket Protocol

- **Connection URL**: `ws://<host>:8080/ws`
- **Connect Header**:
  ```stomp
  CONNECT
  accept-version:1.2,1.1,1.0
  Authorization:Bearer <JWT_TOKEN>
  heart-beat:10000,10000
  ^@
  ```

### Inbound Actions (`/app/*`)
- `/app/chat.send`: Send text message
- `/app/chat.typing`: Emit typing status (`{ "conversationId": "uuid", "typing": true }`)
- `/app/chat.delivered`: Acknowledge message delivery
- `/app/chat.read`: Acknowledge message read

### Subscriptions (`/user/queue/*`)
- `/user/queue/messages`: Receive new messages and status transitions (`SENT`, `DELIVERED`, `READ`, `EDITED`, `DELETED`)
- `/user/queue/typing`: Receive real-time typing indicators from conversation partners
- `/user/queue/errors`: Receive asynchronous error payloads

---

## Automated Tests

Run all unit, slice, and integration tests:
```bash
mvn clean test
```

---

## Production Deployment (US-18)

### 1. Local Setup

```bash
# Clone and install dependencies
mvn clean package -DskipTests

# Run locally with Docker MySQL
docker compose up -d

# Or use local MySQL with .env configuration
cp .env.example .env
# Edit .env with your credentials

# Start the application (the project automatically loads the gitignored .env file)
mvn spring-boot:run
# Or run the packaged JAR
java -jar target/chat-service-1.0.0-SNAPSHOT.jar
```

### 2. MySQL Setup

- Use MySQL 8+ with `utf8mb4` encoding
- Ensure `max_allowed_packet` >= 16MB (default, fine for chat)
- Create database: `CREATE DATABASE dating_chat_db;`
- Flyway will baseline/migrate on first startup
- Do NOT set `spring.jpa.hibernate.ddl-auto=create` in production

### 3. Environment Variables

**Never commit actual passwords to Git.** Use environment variables:

| Variable | Description | Required |
|---|---|---|
| `DB_HOST` | MySQL host address | Yes |
| `DB_PORT` | MySQL port (3306 default) | Yes |
| `DB_NAME` | Database name | Yes |
| `DB_USERNAME` | Database user | Yes |
| `DB_PASSWORD` | Database password | Yes (env var only) |
| `JWT_SECRET` | JWT signing secret | Yes |
| `JWT_ISSUER` | JWT issuer claim | Yes |
| `JWT_EXPIRATION` | Token expiration in ms | Yes |
| `SPRING_PROFILES_ACTIVE` | Spring profile (`dev`/`local`/`prod`) | Yes |
| `BOND_CIRCLE_TEST_AUTH_ENABLED` | Enable test auth banner | No |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | Yes (prod) |
| `API_BASE_URL` | Frontend API base URL | Yes |
| `WS_BASE_URL` | WebSocket URL | Yes |

**Local .env example** (gitignored):
```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=dating_chat_db
DB_USERNAME=chat_user
DB_PASSWORD=your_secure_password_here
JWT_SECRET=replace_with_at_least_32_random_characters
JWT_ISSUER=dating-app-auth-service
JWT_EXPIRATION=86400000
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev
```

### 4. Backend Deployment

Build and deploy the executable JAR to a Java-capable host:

```bash
mvn clean package -DskipTests
# Deploy target/chat-service-1.0.0-SNAPSHOT.jar to:
# - Render, Railway, Fly.io, VPS, or equivalent
# Set environment variables on your hosting provider
```

The JAR contains both the backend REST/WebSocket API and the static frontend files.

### 5. Frontend Deployment

The frontend is **embedded** in the Spring Boot JAR (`src/main/resources/static/`). 

- **Same-host deployment**: Frontend and backend on same domain - no CORS needed beyond same-origin
- **Vercel-limited**: For Vercel-only hosting, set these env vars:
  - `API_BASE_URL`: `https://your-backend-domain.com/api/v1`
  - `WS_BASE_URL`: `wss://your-backend-domain.com/ws`
- The `config.js` module reads these vars (with local defaults for development)

### 6. WebSocket URL

- **Local development**: `ws://localhost:8080/ws` (configurable via `WS_BASE_URL`)
- **Production**: `wss://<your-domain>/ws` (configured via `WS_BASE_URL` env var)
- The `websocket.js` module uses `CONFIG.WS_BASE_URL` for connection
- WSS (WebSocket Secure) requires SSL/TLS on your domain

### 7. Production Database Configuration

- Set `spring.profiles.active=prod`
- JDBC URL uses `useSSL=true&requireSSL=true` (from `application-prod.yml`)
- Set `CORS_ALLOWED_ORIGINS` env var with your frontend domain
- Set `WS_BASE_URL` env var with `wss://<your-domain>/ws`
- MySQL config from `application-prod.yml`:
  - `useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8`
  - HikariCP connection pool tuned for production (max 50, min idle 10)

### 8. CORS Configuration

**Critical**: Do not use wildcard origin (`*`) with `allowCredentials(true)` - violates browser security policy.

The `WebConfig` class configures CORS profile-awarely:
- **Development**: Permissive origin policy for `localhost` testing
- **Production**: Specific origins from `CORS_ALLOWED_ORIGINS` env var

Example production configuration:
```bash
CORS_ALLOWED_ORIGINS=https://bondcircle.example.com
```

### 9. Testing Two Users from Different Browsers

1. Start the application locally or on your production domain
2. Open Browser A, login as User A (ID: 101 or phone+password)
3. Open Browser B (or incognito mode), login as User B (ID: 202 or different phone+password)
4. User A starts a conversation with User B
5. User B receives real-time messages via WebSocket (WSS if HTTPS)
6. Test message status transitions: SENT → DELIVERED → READ
7. Test 403 access control: User C should get CHAT_ACCESS_DENIED when trying to access User A's conversation
8. Test logout from one browser doesn't affect the other
9. Test WebSocket reconnection and offline message delivery

### Production Readiness Checklist

- [ ] MySQL 8+ database running with `utf8mb4` encoding
- [ ] `JWT_SECRET`, `JWT_ISSUER`, `JWT_EXPIRATION` set (env vars, not in source)
- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `CORS_ALLOWED_ORIGINS` set to frontend domain (no `*` with credentials)
- [ ] `API_BASE_URL` set to `https://<domain>/api/v1`
- [ ] `WS_BASE_URL` set to `wss://<domain>/ws`
- [ ] SSL/TLS configured for HTTPS/WSS
- [ ] `mvn clean package` builds successfully
- [ ] `java -jar target/*.jar` starts without errors
- [ ] All 21 core tests pass: `mvn clean test -Dtest="ChatWebSocketControllerTest,WebSocketBroadcastServiceTest,MessageServiceTest,MessageControllerIntegrationTest,MessageLifecycleIntegrationTest,ReceiptServiceTest,PresenceTest"`
- [ ] Frontend can connect to WebSocket via WSS
- [ ] Two-browser test: User A + User B can chat with message status updates
