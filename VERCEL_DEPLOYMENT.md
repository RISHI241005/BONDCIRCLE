# BondCircle on Vercel

Vercel detects `Dockerfile.vercel` and packages the Java 21 server. The same host serves the frontend, REST API, and STOMP WebSockets. Container and WebSocket support are Vercel beta features.

Connect the free Neon `bondcircle-db` database using Vercel Storage. The integration supplies `POSTGRES_HOST`, `POSTGRES_DATABASE`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`. Set `PORT=8080`. The image selects `prod,vercel` profiles. The Vercel profile generates a shared high-entropy JWT signing key in the private database during migration; it never enters source or logs.

The PostgreSQL profile uses TLS and separate Flyway migrations under `db/postgres`. Existing local MySQL data is not transferred; public users register separately.

Each instance writes events to a PostgreSQL outbox. Active instances poll committed events every 750 ms and forward them to their authenticated sockets. Presence uses shared database leases, expiring after 60 seconds without renewal. Events expire after ten minutes, while message history persists for reconnection. Free database compute quotas apply to polling; this setup targets small initial usage.

Compilation: `mvn -DskipTests compile`. Verify PostgreSQL migration, signup/login, two-account messaging, reconnect, and cross-instance delivery before release. The container context excludes secrets and unrelated workspace files.

References:
- https://vercel.com/docs/functions/container-images
- https://vercel.com/docs/functions/websockets
