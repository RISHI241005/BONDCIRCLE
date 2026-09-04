# BondCircle Deployment Guide

## Deployment Architecture

```
LOCAL
   ↓
FRONTEND (Static HTML/CSS/JS, served by Spring Boot)
   ↓
SPRING BOOT JAR (One executable application)
   ↓
MYSQL DATABASE (External)
```

## Local Development

### 1. Start MySQL

Ensure MySQL is running and accessible.

### 2. Configure Environment

Copy `.env.example` to `.env` and customize:

```bash
cp .env.example .env
```

Edit `.env` with your MySQL credentials and settings.

### 3. Run the Application

```bash
# Start Spring Boot with Maven
mvn spring-boot:run
```

or after packaging:

```bash
mvn clean package
java -jar target/chat-service-1.0.0-SNAPSHOT.jar
```

The application will be available at `http://localhost:8080`.

### 4. Access the Application

Open your browser to `http://localhost:8080`.

The landing page (index.html) will load, and the JavaScript application will initialize.

### 5. Development Test Users

Use the development test users to test the application:

| User | ID | Notes |
|------|---|-------|
| User A | 101 | Can access conversation 2a00f17b... |
| User B | 202 | Can access conversation 2a00f17b... |
| User C | 303 | Cannot access conversation 2a00f17... (403 CHAT_ACCESS_DENIED) |

### 6. Login

For development testing, use the login page at `http://localhost:8080/login.html` and enter a User ID (101, 202, or 303).

Or, to enable the development test auth banner, set `BOND_CIRCLE_TEST_AUTH_ENABLED=true` in your `.env` file.

## Production Deployment

### Recommended Architecture

```
                  Internet
                      |
                      v
              Spring Boot Server
               BondCircle JAR
                      |
          ┌───────────┴───────────┐
          |                       |
       Frontend                WebSocket
          |                       |
          └───────────┬───────────┘
                      |
                    MySQL
```

### 1. Build the JAR

```bash
mvn clean package -DskipTests
```

The resulting JAR file contains:
- Frontend HTML, CSS, JavaScript
- Backend Spring Boot classes
- REST APIs
- WebSocket configuration
- MySQL JAR dependencies

### 2. Deploy the JAR

Deploy the JAR to a Java-capable hosting provider:

- **AWS EC2**: `scp target/chat-service-1.0.0-SNAPSHOT.jar ec2-user@your-server:/opt/bondcircle/`
- **Google Cloud Run**: Configure Docker deployment with the JAR
- **Heroku**: `heroku deploy:jar target/chat-service-1.0.0-SNAPSHOT.jar`
- **Azure App Service**: Deploy Java web app
- **Vercel**: Note - Vercel cannot host the Spring Boot WebSocket server. Use for static frontend only if needed.

### 3. Configure Environment Variables

Set the following environment variables on your hosting provider:

| Variable | Description | Example |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |
| `DB_HOST` | MySQL host | `your-mysql-host.com` |
| `DB_PORT` | MySQL port | `3306` |
| `DB_NAME` | Database name | `dating_chat_db` |
| `DB_USERNAME` | Database user | `chat_user` |
| `DB_PASSWORD` | Database password | `(secure value)` |
| `SERVER_PORT` | Server port | `8080` |
| `JWT_SECRET` | JWT signing secret | `(match backend)` |
| `JWT_ISSUER` | JWT issuer | `dating-app-auth-service` |
| `JWT_EXPIRATION` | Token expiration ms | `86400000` |
| `BOND_CIRCLE_TEST_AUTH_ENABLED` | Test auth flag | `false` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `prod` |

### 4. CORS Configuration

**Important**: Do not use wildcard origins (`*`) with `allowCredentials(true)` - this violates browser security policy and will be rejected.

For production, configure specific allowed origins via the `CORS_ALLOWED_ORIGINS` environment variable:

```bash
CORS_ALLOWED_ORIGINS=https://bondcircle.example.com,https://www.bondcircle.example.com
```

The application config reads this variable and configures CORS accordingly. In development (non-prod profile), a permissive origin policy is used for local testing.

**Production CORS Bean** (auto-configured from `CORS_ALLOWED_ORIGINS` env var):

The `WebConfig` class reads `CORS_ALLOWED_ORIGINS` and maps it to CORS allowed origins. Origins are comma-separated. Example:

```
CORS_ALLOWED_ORIGINS=https://bondcircle.example.com
```

### 5. WebSocket Production

Ensure the backend WebSocket config allows the production domain. The `WebSocketConfig` class reads `WS_BASE_URL` and configures allowed origin patterns accordingly.

In production, the WebSocket config uses specific origin patterns from the `WS_BASE_URL` environment variable rather than wildcard `*`.

**Production WebSocket Config** (auto-configured from `WS_BASE_URL` env var):

The application sets `setAllowedOriginPatterns` based on `WS_BASE_URL`. If `WS_BASE_URL` is `wss://bondcircle.example.com/ws`, the allowed origin will be `https://bondcircle.example.com`.

### 6. Health Check

Verify the application is healthy:

```
GET http://your-server:8080/actuator/health
```

Should return `{"status": "UP", "details": {...}}`.

### 7. SSL/TLS

Configure SSL/TLS for your domain to enable:
- HTTPS for REST APIs: `https://bondcircle.example.com/api/v1/...`
- WSS for WebSocket: `wss://bondcircle.example.com/ws`

## Front-Only Deployment (Vercel - Limited)

**IMPORTANT**: Vercel cannot host the Spring Boot WebSocket server. The unified Spring Boot + WebSocket application must run on a Java-capable host.

If you only want to host the frontend on Vercel:

1. The frontend is a static HTML/CSS/JS application
2. Configure API_BASE_URL and WS_BASE_URL environment variables in Vercel to point to your Spring Boot backend
3. The Spring Boot backend must be deployed separately (see above)
4. CORS and WebSocket origin must be configured on the backend

### Vercel Environment Variables

| Variable | Value |
|---|---|
| `API_BASE_URL` | `https://your-backend-domain.com/api/v1` |
| `WS_BASE_URL` | `wss://your-backend-domain.com/ws` |
| `BOND_CIRCLE_TEST_AUTH_ENABLED` | `false` |

**Note**: The frontend `config.js` reads `API_BASE_URL` and `WS_BASE_URL` from environment variables injected by Vercel (or uses local defaults for development). The `CORS_ALLOWED_ORIGINS` variable must also be set on the backend Spring Boot application.

## Database Management

### Flyway Migrations

The project uses Flyway for database migrations. Migration files are in `src/main/resources/db/migration/`.

- **Baseline**: On first run, Flyway will baseline the existing schema
- **New migrations**: Add new `V{N}_description.sql` files to migrate the schema
- **Info**: `mvn flyway:info` shows migration status
- **Migrate**: `mvn flyway:migrate` applies pending migrations

**Do NOT set `spring.jpa.hibernate.ddl-auto=create` in production** - this would drop data. Use Flyway for schema evolution.

### MySQL Considerations

- Use MySQL 8+ for optimal compatibility
- Ensure the MySQL `max_allowed_packet` is sufficient for message sizes (default 16MB, should be fine for chat)
- Set `characterEncoding=UTF-8` and `utf8mb4` for full emoji support
- Set `useSSL=true` and `requireSSL=true` in production JDBC URLs

## Development Workflow

```bash
# 1. Start MySQL
# 2. Copy .env.example to .env and configure
# 3. Run: mvn spring-boot:run
# 4. Open: http://localhost:8080
# 5. Login as User A (ID: 101)
# 6. Login as User B (ID: 202) in another browser
# 7. Test chat functionality
# 8. Test 403 for User C accessing conversation 2a00f17b...
# 9. Test logout
# 10. Test WebSocket reconnection
```

## Rollback

If a deployment causes issues:

1. Stop the application
2. Redeploy a previous version of the JAR
3. Or use your hosting provider's rollback feature

## File Structure Summary

```
BondCircle/
├── pom.xml
├── src/
│   └── main/
│       └── resources/
│           ├── static/              ← Frontend files
│           │   ├── index.html       ← Main landing page
│ │   │   ├── login.html         ← Login page
│ │   │   ├── dashboard.html     ← Dashboard
│ │   │   ├── chat.html          ← Chat page
│ │   │   └── profile.html       ← Profile page
│ │   │   ├── css/               ← CSS stylesheets
│ │   │   ├── js/                ← JavaScript modules
│ │   │   │   ├── config.js
│ │   │   │   ├── api.js
│ │   │   │   ├── auth.js
│ │   │   │   ├── websocket.js
│ │   │   │   ├── chat.js
│ │   │   │   ├── conversations.js
│ │   │   │   ├── profile.js
│ │   │   │   ├── state.js
│ │   │   │   ├── storage.js
│ │   │   │   └── ui.js
│ │   │   └── assets/            ← Images/icons
│ │   ├── application.yml        ← Spring config
│ │   ├── db/migration/          ← Flyway migrations
│ │   └── application-dev.yml    ← Dev profile
│   └── test/                    ← Test sources
├── .env.example                 ← Environment config
├── API_ENDPOINTS.md             ← API documentation
├── WEBSOCKET_PROTOCOL.md        ← WebSocket protocol
├── README.md                    ← Project overview
└── DEPLOYMENT.md                ← Deployment guide
```

## One-Command Startup

```bash
mvn spring-boot:run
```

This single command:
1. Compiles the Java code
2. Packages everything into an executable JAR
3. Starts the Spring Boot application
4. Serves the frontend at `http://localhost:8080`
5. Configures WebSocket at `ws://localhost:8080/ws`
6. Connects to MySQL with the configured credentials

No separate frontend server, npm install, or node build step is required.