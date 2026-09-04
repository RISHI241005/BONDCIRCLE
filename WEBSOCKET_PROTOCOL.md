# BondCircle WebSocket Protocol

## Transport Configuration

| Parameter | Value | Description |
|---|---|---|
| **WebSocket Endpoint** | `/ws` | Native WebSocket endpoint with SockJS fallback |
| **SockJS Fallback** | `/ws` | Fallback for browsers without native WebSocket |
| **STOMP Prefix** | `/app` | Application command prefix |
| **User Destination Prefix** | `/user` | User-specific messaging prefix |
| **Broker Destinations** | `/queue`, `/topic` | Simple in-memory broker |

## WebSocket Connection

### Connection Flow

1. **Client connects** to `WSS_BASE_URL` (e.g., `wss://bondcircle.vercel.app/ws`)
2. **STOMP handshake** with `Authorization: Bearer <JWT>` header
3. **Server validates JWT** via `WebSocketAuthChannelInterceptor`
4. **Connection accepted** if JWT is valid and not expired
5. **Client subscribes** to user-specific destinations

### Connection States

| State | Description |
|---|---|
| `CONNECTING` | Connection in progress |
| `CONNECTED` | Successfully connected and subscribed |
| `DISCONNECTED` | Connection closed |
| `RECONNECTING` | Attempting to reconnect |
| `ERROR` | Connection failed permanently |

### STOMP Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <JWT>` | Yes (on CONNECT frame) |
| `login` | JWT token | Optional (for compatibility) |
| `passcode` | N/A | Optional |

## Outbound STOMP Destinations (Client → Server)

### `/app/chat.send`

| Field | Type | Description |
|---|---|---|
| `conversationId` | String | UUID of the conversation |
| `content` | String | Message text content |
| `clientMessageId` | String | Optional, for idempotency/offline dedup |
| `replyToMessageId` | String | Optional, public UUID of replied message |
| `type` | MessageType | Message type (default: TEXT) |

**Purpose**: Send a message in real time. Validates participant membership, persists to MySQL, broadcasts NEW_MESSAGE event.

### `/app/chat.delivered`

| Field | Type | Description |
|---|---|---|
| `conversationId` | String | UUID of the conversation |
| `messageId` | String | Public UUID of the message |

**Purpose**: Acknowledge message delivery. Client sends after receiving delivery receipt from backend.

### `/app/chat.read`

| Field | Type | Description |
|---|---|---|
| `conversationId` | String | UUID of the conversation |
| `messageId` | String | Public UUID of the message |

**Purpose**: Acknowledge message read status. Client sends after user reads the message.

### `/app/chat.typing`

| Field | Type | Description |
|---|---|---|
| `conversationId` | String | UUID of the conversation |
| `typing` | boolean | True when user is typing, false when stopped |

**Purpose**: Broadcast typing indicator to other conversation partners.

## Inbound Destinations (Server → Client)

### Subscription: `/user/{userId}/queue/messages`

After connecting, client subscribes to this destination to receive all events for the authenticated user.

### Event Types

#### `NEW_MESSAGE`

```json
{
  "conversationId": "UUID",
  "message": {
    "id": "UUID",
    "conversationId": "UUID",
    "senderId": Long,
    "content": "String",
    "type": "TEXT",
    "status": "SENT",
    "createdAt": "ISO timestamp",
    "clientMessageId": "String"
  }
}
```

**Trigger**: When a new message is sent (via WebSocket or REST). Client should add to message list, dedup by message ID.

#### `USER_TYPING`

```json
{
  "conversationId": "UUID",
  "userId": Long,
  "typing": boolean
}
```

**Trigger**: When a participant starts/stops typing. Show typing indicator in chat header.

#### `MESSAGE_STATUS_UPDATE`

```json
{
  "conversationId": "UUID",
  "messageId": "UUID",
  "status": "DELIVERED"|"READ"|"EDITED"|"DELETED",
  "updatedByUserId": Long
}
```

**Trigger**: When message status changes via backend. Update message status in UI.

#### `ERROR`

```json
{
  "errorCode": "STRING",
  "message": "Human-readable error"
}
```

**Trigger**: When WebSocket operation fails (authentication, validation, etc.).

## Message Lifecycle

### Backend-Driven States

The backend is authoritative for message status. The frontend must display the state returned by the backend.

#### Message Status Transitions

```
SENT  →  DELIVERED  →  READ
```

### What the Frontend Must Do

1. **Initial render**: Show message with status from backend (typically `SENT` when just sent)
2. **On `MESSAGE_STATUS_UPDATE` event**: Update the message status display
3. **Do NOT artificially change SENT → DELIVERED → READ** without backend confirmation
4. **Use message IDs for deduplication** - if a message with the same ID already exists, do not add it again

### Status Display

| Status | UI Display | Description |
|---|---|---|
| `SENT` | "Sent" | Message sent, not yet delivered |
| `DELIVERED` | "Delivered" | Message delivered to recipient's device |
| `READ` | "Read" | Recipient has read the message |
| `EDITED` | "Edited" | Message content was modified |
| `DELETED` | (hidden or "This message was deleted") | Message was soft-deleted |

## Presence Events

### `PRESENCE_UPDATE`

May be dispatched to `/user/{userId}/queue/messages` with user status information.

## Reconnection & Reliability

### Automatic Reconnection

- Client should implement exponential backoff reconnection
- On reconnect, resubscribe to `/user/{userId}/queue/messages`
- Do not create duplicate subscriptions

### Message Deduplication

- Use message `id` (public UUID) to prevent duplicate rendering
- Before adding a new message, check if a message with the same ID already exists in the message list
- This is critical since the backend may broadcast the same message via both WebSocket and REST

### Heartbeat / Keepalive

- The backend does not currently implement STOMP heartbeat
- Client should consider connection timeout and manual reconnection logic
- If the WebSocket connection is lost, attempt reconnection after 1-3 seconds, then 5-10 seconds, then 15-30 seconds

## Authentication on WebSocket

### JWT in STOMP CONNECT Frame

The `WebSocketAuthChannelInterceptor` validates the JWT on the CONNECT frame.

**Header format**: `Authorization: Bearer <JWT>`

This header must be included in the STOMP connect headers.

If the token is invalid or expired:
- CONNECT is rejected
- `handleException` sends error to the user
- Client should re-login or refresh the token

## Production URLs

| Environment | API_BASE_URL | WS_BASE_URL |
|---|---|---|
| Local Development | `/api/v1` (same-origin) | `ws://localhost:8080/ws` |
| Production (Vercel) | `https://bondcircle.example.com/api/v1` | `wss://bondcircle.example.com/ws` |

Do not hardcode production URLs in the source. Use environment variables or config file.

## Test Scenario (E2E)

1. User A (101) and User B (202) connect via WebSocket with valid JWTs
2. User A sends message via `/app/chat.send`
3. User B receives `NEW_MESSAGE` event via `/user/queue/messages`
4. User B acknowledges delivery via `/app/chat.delivered`
5. Message status transitions from SENT → DELIVERED in backend
6. User B reads message via `/app/chat.read`
7. Message status transitions from DELIVERED → READ in backend
8. User C (303) attempting to access conversation 2a00f17b... receives 403 CHAT_ACCESS_DENIED