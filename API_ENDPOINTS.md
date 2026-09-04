# BondCircle API Endpoints

## Base Configuration

| Variable | Description | Example |
|---|---|---|
| `API_BASE_URL` | REST API base URL (same-origin for integrated frontend) | `/api/v1` |
| `WS_BASE_URL` | WebSocket base URL | `ws://localhost:8080/ws` or `wss://bondcircle.example.com/ws` |

## REST API Endpoints

### Health Check

| METHOD | URL | AUTH | DESCRIPTION |
|---|---|---|---|
| GET | `/api/v1/health` | No | Service health status check |

### Authentication (Development Test)

| METHOD | URL | AUTH | REQUEST BODY | RESPONSE | DESCRIPTION |
|---|---|---|---|---|---|
| POST | `/test/auth/token` | No | `{ "userId": Long, "roles": ["ROLE_USER"] }` | `{ "token": String, "tokenType": "Bearer" }` | Generate JWT token for development/testing |

### Conversations

| METHOD | URL | AUTH | REQUEST BODY | QUERY PARAMS | RESPONSE | ERROR RESPONSES | PURPOSE |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/chats` | Yes | - | - | `{ success: true, data: List<ConversationSummaryResponse>, requestId: String }` | 401, 403 | List user's active conversations |
| GET | `/api/v1/chats/{conversationId}` | Yes | - | - | `{ success: true, data: ConversationDetailResponse, requestId: String }` | 401, 403, 404 | Get conversation details and participants |
| POST | `/api/v1/chats` | Yes | `{ "participantId": Long }` | - | `{ success: true, data: ConversationDetailResponse, message: "Conversation ready", requestId: String }` | 400, 401, 403, 409 | Create or get direct conversation with participant |
| DELETE | `/api/v1/chats/{conversationId}` | Yes | - | - | `{ success: true, data: null, message: "Conversation archived successfully", requestId: String }` | 401, 403 | Leave/archive conversation |
| POST | `/api/v1/chats/{conversationId}/read` | Yes | `{ "messageId": String }` | - | `{ success: true, data: null, message: "Messages marked as read successfully", requestId: String }` | 400, 401, 403 | Mark messages as read (REST fallback) |

### Messages

| METHOD | URL | AUTH | REQUEST BODY | QUERY PARAMS | RESPONSE | ERROR RESPONSES | PURPOSE |
|---|---|---|---|---|---|---|---|
| GET | `/api/v1/chats/{conversationId}/messages` | Yes | - | `cursor`: String, `limit`: int (default 30) | `{ success: true, data: MessageCursorPage, requestId: String }` | 401, 403, 404 | Get cursor-paginated message history |
| POST | `/api/v1/chats/{conversationId}/messages` | Yes | `{ "content": String, "clientMessageId": String, "replyToMessageId": String, "type": MessageType }` | - | `{ success: true, data: MessageResponse, message: "Message sent successfully", requestId: String }` | 400, 401, 403, 409, 429 | Send message (REST fallback, idempotent) |
| PATCH | `/api/v1/chats/{conversationId}/messages/{messageId}` | Yes | `{ "content": String }` | - | `{ success: true, data: MessageResponse, message: "Message updated successfully", requestId: String }` | 400, 401, 403, 404, 409 | Edit sent message (original sender only) |
| DELETE | `/api/v1/chats/{conversationId}/messages/{messageId}` | Yes | - | - | `{ success: true, data: MessageResponse, message: "Message deleted successfully", requestId: String }` | 401, 403, 404, 409 | Soft-delete message (original sender only) |

### Presence

| METHOD | URL | AUTH | REQUEST BODY | RESPONSE | ERROR RESPONSES | PURPOSE |
|---|---|---|---|---|---|---|
| GET | `/api/v1/presence/{userId}` | Yes | - | `{ success: true, data: UserPresenceResponse, requestId: String }` | 401, 403 | Get user online status and last seen |

### Reports

| METHOD | URL | AUTH | REQUEST BODY | RESPONSE | ERROR RESPONSES | PURPOSE |
|---|---|---|---|---|---|---|
| POST | `/api/v1/chats/{conversationId}/reports` | Yes | Report details | `{ success: true, data: ReportResponse, requestId: String }` | 400, 401, 403 | Submit moderation report |

## Error Response Format

All error responses follow the unified `ApiError` envelope:

```json
{
  "success": false,
  "message": "Human-readable error description",
  "errorCode": "MACHINE_READABLE_CODE",
  "timestamp": "2026-08-26T20:16:00.000Z",
  "requestId": "unique-correlation-id"
}
```

### Common Error Codes

| Code | HTTP | Meaning |
|---|---|---|
| `UNAUTHORIZED` | 401 | Authentication credentials missing or invalid |
| `TOKEN_EXPIRED` | 401 | JWT token has expired |
| `TOKEN_INVALID` | 401 | JWT signature-invalid or malformed |
| `FORBIDDEN` | 403 | You do not have permission to perform this action |
| `CHAT_ACCESS_DENIED` | 403 | You are not a participant in this conversation |
| `BAD_REQUEST` | 400 | Malformed or invalid request |
| `CONVERSATION_NOT_FOUND` | 404 | The requested conversation was not found |
| `MESSAGE_NOT_FOUND` | 404 | The requested message was not found |
| `RATE_LIMIT_EXCEEDED` | 429 | Rate limit exceeded |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected server error |

### Error Display Messages

- **401**: "Your session has expired. Please log in again."
- **403**: "You don't have permission to access this resource." or "You are not a participant in this conversation."
- **404**: "Conversation not found." or "Message not found."
- **500**: "Something went wrong on the server. Reference ID: {requestId}"

Each error includes `requestId` in developer console logs for debugging.

## WebSocket Endpoints (Supplementary)

See `WEBSOCKET_PROTOCOL.md` for detailed WebSocket configuration.

## Test Users

| Label | User ID | Roles |
|---|---|---|
| User A | 101 | ROLE_USER |
| User B | 202 | ROLE_USER |
| User C | 303 | ROLE_USER |

Conversation 2a00f17b-bea2-4d3c-a5ec-a8bbe83fabd9 participates: 101, 202

User 303 is NOT a participant → accessing conversation 2a00f17b... yields 403 CHAT_ACCESS_DENIED.