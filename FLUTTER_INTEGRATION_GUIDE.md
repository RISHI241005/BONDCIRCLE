# Flutter Integration Guide: Chat Backend

This guide outlines how Flutter mobile applications (targeting Android and iOS) integrate with the Spring Boot Chat Backend over **HTTPS REST** and **WSS STOMP WebSocket**.

---

## 1. Flutter Project Architecture

Recommended directory layout for Flutter:

```
lib/
├── core/
│   ├── network/
│   │   ├── api_client.dart            // Dio / HTTP client with Bearer auth interceptor
│   │   ├── api_endpoints.dart         // /api/v1/chats, /messages, /read constants
│   │   └── websocket_client.dart      // stomp_dart_client wrapper with auto-reconnect
│   └── errors/
│       └── api_exception.dart         // Maps { success: false, errorCode: "USER_BLOCKED" }
│
└── features/
    └── chat/
        ├── models/
        │   ├── chat_message.dart      // Message entity DTO
        │   ├── conversation_item.dart // Conversation summary DTO
        │   └── ws_event.dart          // Inbound WebSocket polymorphic event
        ├── repositories/
        │   └── chat_repository.dart   // Abstract repository (REST + Cache + WS)
        ├── services/
        │   └── chat_service.dart      // Business logic, optimistic UI, offline queue
        └── presentation/
            ├── providers/             // Riverpod / Bloc state management
            └── screens/               // ChatListScreen, ChatConversationScreen
```

---

## 2. Dart Data Models

### Chat Message Model (`chat_message.dart`)
```dart
enum MessageType { TEXT, SYSTEM, IMAGE, VIDEO, AUDIO, FILE }
enum MessageStatus { SENT, DELIVERED, READ, EDITED, DELETED, FAILED }

class ChatMessage {
  final String id;
  final String conversationId;
  final int senderId;
  final String? clientMessageId;
  final String content;
  final MessageType type;
  final MessageStatus status;
  final ReplySnippet? replyTo;
  final DateTime createdAt;
  final DateTime updatedAt;
  final bool isDeleted;

  ChatMessage({
    required this.id,
    required this.conversationId,
    required this.senderId,
    this.clientMessageId,
    required this.content,
    required this.type,
    required this.status,
    this.replyTo,
    required this.createdAt,
    required this.updatedAt,
    required this.isDeleted,
  });

  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    return ChatMessage(
      id: json['id'] as String,
      conversationId: json['conversationId'] as String,
      senderId: json['senderId'] as int,
      clientMessageId: json['clientMessageId'] as String?,
      content: json['content'] as String,
      type: MessageType.values.byName(json['type'] as String),
      status: MessageStatus.values.byName(json['status'] as String),
      replyTo: json['replyTo'] != null ? ReplySnippet.fromJson(json['replyTo']) : null,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
      isDeleted: json['deleted'] as bool? ?? false,
    );
  }
}

class ReplySnippet {
  final String id;
  final int senderId;
  final String content;
  final MessageType type;

  ReplySnippet({
    required this.id,
    required this.senderId,
    required this.content,
    required this.type,
  });

  factory ReplySnippet.fromJson(Map<String, dynamic> json) {
    return ReplySnippet(
      id: json['id'] as String,
      senderId: json['senderId'] as int,
      content: json['content'] as String,
      type: MessageType.values.byName(json['type'] as String),
    );
  }
}
```

---

## 3. WebSocket / STOMP Connection Setup

Using `stomp_dart_client`:

```dart
import 'dart:convert';
import 'package:stomp_dart_client/stomp_dart_client.dart';

class ChatWebSocketClient {
  late StompClient _client;
  final String jwtToken;
  final Function(dynamic) onMessageReceived;
  final Function(dynamic) onTypingReceived;

  ChatWebSocketClient({
    required this.jwtToken,
    required this.onMessageReceived,
    required this.onTypingReceived,
  }) {
    _client = StompClient(
      config: StompConfig(
        url: 'ws://your-backend-host:8080/ws',
        onConnect: _onConnectCallback,
        stompConnectHeaders: {
          'Authorization': 'Bearer $jwtToken',
        },
        webSocketConnectHeaders: {
          'Authorization': 'Bearer $jwtToken',
        },
        reconnectDelay: const Duration(seconds: 4),
        heartbeatOutgoing: const Duration(seconds: 10),
        heartbeatIncoming: const Duration(seconds: 10),
      ),
    );
    _client.activate();
  }

  void _onConnectCallback(StompFrame frame) {
    // 1. Subscribe to personal incoming messages and status changes
    _client.subscribe(
      destination: '/user/queue/messages',
      callback: (frame) {
        if (frame.body != null) {
          final data = jsonDecode(frame.body!);
          onMessageReceived(data);
        }
      },
    );

    // 2. Subscribe to partner typing indicators
    _client.subscribe(
      destination: '/user/queue/typing',
      callback: (frame) {
        if (frame.body != null) {
          final data = jsonDecode(frame.body!);
          onTypingReceived(data);
        }
      },
    );
  }

  /// Sends realtime chat message
  void sendMessage({
    required String conversationId,
    required String content,
    required String clientMessageId,
    String? replyToMessageId,
  }) {
    _client.send(
      destination: '/app/chat.send',
      body: jsonEncode({
        'conversationId': conversationId,
        'clientMessageId': clientMessageId,
        'content': content,
        'replyToMessageId': replyToMessageId,
        'type': 'TEXT',
      }),
    );
  }

  /// Emits ephemeral typing indicator
  void sendTyping({required String conversationId, required bool isTyping}) {
    _client.send(
      destination: '/app/chat.typing',
      body: jsonEncode({
        'conversationId': conversationId,
        'typing': isTyping,
      }),
    );
  }

  /// Acknowledges message delivery upon receiving payload
  void sendDeliveryAck({required String conversationId, required String messageId}) {
    _client.send(
      destination: '/app/chat.delivered',
      body: jsonEncode({
        'conversationId': conversationId,
        'messageId': messageId,
      }),
    );
  }

  /// Acknowledges message read upon opening conversation
  void sendReadReceipt({required String conversationId, required String messageId}) {
    _client.send(
      destination: '/app/chat.read',
      body: jsonEncode({
        'conversationId': conversationId,
        'messageId': messageId,
      }),
    );
  }

  void disconnect() {
    _client.deactivate();
  }
}
```

---

## 4. Message Ingestion & Pagination Workflow

```
1. App Launch / Inbox Open:
   - Call REST: GET /api/v1/chats
   - Connect WebSocket to /ws

2. Open Conversation:
   - Call REST: GET /api/v1/chats/{conversationId}/messages?limit=30
   - Render initial 30 messages in reverse ListView.builder.
   - Send Read Receipt for newest message: /app/chat.read

3. Scrolling Up (Loading Older Messages):
   - When scroll threshold reached and hasMore == true:
   - Call REST: GET /api/v1/chats/{conversationId}/messages?limit=30&cursor={nextCursor}
   - Append received older messages to bottom of state list.

4. Sending a Message:
   - Generate local UUID clientMessageId = uuid.v4()
   - Optimistically render message in Flutter UI with status = MessageStatus.SENT
   - Send STOMP frame to /app/chat.send
   - On offline/network retry: Re-send with same clientMessageId (backend prevents duplicates)
```

---

## 5. BondCircle AI Reply Coach Integration

The AI Reply Coach provides an intelligent companion beside the user in chat. It suggests up to 3 context-aware replies that the user can tap to populate into the text composer without auto-sending.

### 5.1 Dart Models (`reply_coach_models.dart`)

```dart
class ReplySuggestionItem {
  final String id;
  final String text;
  final String? topic;
  final String? tone;

  ReplySuggestionItem({
    required this.id,
    required this.text,
    this.topic,
    this.tone,
  });

  factory ReplySuggestionItem.fromJson(Map<String, dynamic> json) {
    return ReplySuggestionItem(
      id: json['id'] as String? ?? '',
      text: json['text'] as String? ?? '',
      topic: json['topic'] as String?,
      tone: json['tone'] as String?,
    );
  }
}

class ConversationStateInfo {
  final String topic;
  final String tone;
  final String engagement;
  final String language;
  final bool dry;

  ConversationStateInfo({
    required this.topic,
    required this.tone,
    required this.engagement,
    required this.language,
    required this.dry,
  });

  factory ConversationStateInfo.fromJson(Map<String, dynamic> json) {
    return ConversationStateInfo(
      topic: json['topic'] as String? ?? 'General',
      tone: json['tone'] as String? ?? 'Friendly',
      engagement: json['engagement'] as String? ?? 'BALANCED',
      language: json['language'] as String? ?? 'ENGLISH',
      dry: json['dry'] as bool? ?? false,
    );
  }
}

class ReplySuggestionResponse {
  final String conversationId;
  final List<ReplySuggestionItem> suggestions;
  final ConversationStateInfo? conversationState;

  ReplySuggestionResponse({
    required this.conversationId,
    required this.suggestions,
    this.conversationState,
  });

  factory ReplySuggestionResponse.fromJson(Map<String, dynamic> json) {
    final data = json['data'] != null ? json['data'] as Map<String, dynamic> : json;
    final list = (data['suggestions'] as List<dynamic>? ?? [])
        .map((e) => ReplySuggestionItem.fromJson(e as Map<String, dynamic>))
        .toList();
    return ReplySuggestionResponse(
      conversationId: data['conversationId'] as String? ?? '',
      suggestions: list,
      conversationState: data['conversationState'] != null
          ? ConversationStateInfo.fromJson(data['conversationState'] as Map<String, dynamic>)
          : null,
    );
  }
}

enum ReplyFeedbackAction { SHOWN, USED, REJECTED, COPIED, EDITED, SENT }
```

### 5.2 API Service (`reply_coach_service.dart`)

```dart
import 'dart:convert';
import 'package:http/http.dart' as http;

class ReplyCoachService {
  final String baseUrl;
  final String Function() getBearerToken;

  ReplyCoachService({required this.baseUrl, required this.getBearerToken});

  Map<String, String> get _headers => {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ${getBearerToken()}',
  };

  /// Request up to 3 AI reply suggestions for current chat
  Future<ReplySuggestionResponse> getReplySuggestions({
    required String conversationId,
    int limit = 3,
  }) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/ai/reply-suggestions'),
      headers: _headers,
      body: jsonEncode({
        'conversationId': conversationId,
        'limit': limit,
      }),
    );
    if (response.statusCode == 200) {
      return ReplySuggestionResponse.fromJson(jsonDecode(response.body));
    }
    throw Exception('Failed to generate suggestions: ${response.statusCode}');
  }

  /// Request up to 3 fresh suggestions avoiding rejected ideas
  Future<ReplySuggestionResponse> regenerateSuggestions({
    required String conversationId,
    required List<String> rejectedSuggestionIds,
    List<String> rejectedTexts = const [],
    int limit = 3,
  }) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/ai/reply-suggestions/regenerate'),
      headers: _headers,
      body: jsonEncode({
        'conversationId': conversationId,
        'rejectedSuggestionIds': rejectedSuggestionIds,
        'rejectedTexts': rejectedTexts,
        'limit': limit,
      }),
    );
    if (response.statusCode == 200) {
      return ReplySuggestionResponse.fromJson(jsonDecode(response.body));
    }
    throw Exception('Failed to regenerate suggestions: ${response.statusCode}');
  }

  /// Track user feedback (USED, REJECTED, etc.) for personalized learning
  Future<void> sendFeedback({
    required String suggestionId,
    required String conversationId,
    required ReplyFeedbackAction action,
    String? suggestionText,
  }) async {
    try {
      await http.post(
        Uri.parse('$baseUrl/api/ai/reply-suggestions/feedback'),
        headers: _headers,
        body: jsonEncode({
          'suggestionId': suggestionId,
          'conversationId': conversationId,
          'action': action.name,
          'suggestionText': suggestionText ?? '',
        }),
      );
    } catch (_) {
      // Best-effort background tracking
    }
  }
}
```

### 5.3 Flutter UI Integration Flow

```
Chat Screen
    ↓
Tap "✨ AI Reply" button beside composer
    ↓
Bottom Sheet or Inline Drawer appears with 3 suggestion cards
    ↓
User taps a suggestion card:
    → Populates: textEditingController.text = suggestion.text
    → Dismisses suggestions panel
    → Records: sendFeedback(action: ReplyFeedbackAction.USED)
    → Focuses text field: focusNode.requestFocus()
    → DOES NOT auto-send: User can edit, add text, or send normally
    ↓
If user taps "🔄 More suggestions":
    → Gathers current suggestion IDs into rejectedList
    → Calls: regenerateSuggestions(rejectedSuggestionIds: rejectedList)
    → Animates 3 new suggestions into view
```

