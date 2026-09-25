import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../../../core/config/api_config.dart';
import '../../auth/domain/auth_session.dart';

/// Models for Chat, AI Reply Coach, and Moderation.
class ChatMessageModel {
  final String id;
  final String conversationId;
  final int senderId;
  final String content;
  final String messageType;
  final String status;
  final DateTime? createdAt;
  final bool isMine;

  const ChatMessageModel({
    required this.id,
    required this.conversationId,
    required this.senderId,
    required this.content,
    this.messageType = 'TEXT',
    this.status = 'SENT',
    this.createdAt,
    this.isMine = false,
  });

  factory ChatMessageModel.fromJson(Map<String, dynamic> json, {int? currentUserId}) {
    final sender = json['senderId'] is int
        ? json['senderId'] as int
        : int.tryParse(json['senderId']?.toString() ?? '0') ?? 0;
    final isMe = currentUserId != null ? sender == currentUserId : false;

    DateTime? created;
    if (json['createdAt'] != null) {
      created = DateTime.tryParse(json['createdAt'].toString());
    }

    return ChatMessageModel(
      id: json['id']?.toString() ?? json['publicId']?.toString() ?? '',
      conversationId: json['conversationId']?.toString() ?? '',
      senderId: sender,
      content: json['content'] as String? ?? '',
      messageType: json['messageType'] as String? ?? 'TEXT',
      status: json['status'] as String? ?? 'SENT',
      createdAt: created,
      isMine: isMe,
    );
  }
}

class ConversationModel {
  final String id;
  final int partnerId;
  final String partnerName;
  final String? lastMessage;
  final String? lastMessageTime;
  final int unreadCount;

  const ConversationModel({
    required this.id,
    required this.partnerId,
    required this.partnerName,
    this.lastMessage,
    this.lastMessageTime,
    this.unreadCount = 0,
  });

  factory ConversationModel.fromJson(Map<String, dynamic> json, {int? currentUserId}) {
    final convId = json['id']?.toString() ?? json['publicId']?.toString() ?? '';
    int pId = 0;
    String pName = 'Match';

    final participants = json['participants'] as List<dynamic>?;
    if (participants != null) {
      for (final p in participants) {
        final uid = p['userId'] is int ? p['userId'] as int : int.tryParse(p['userId']?.toString() ?? '0') ?? 0;
        if (currentUserId == null || uid != currentUserId) {
          pId = uid;
          pName = p['userName'] as String? ?? p['name'] as String? ?? 'Match';
          break;
        }
      }
    }

    return ConversationModel(
      id: convId,
      partnerId: pId,
      partnerName: pName,
      lastMessage: json['lastMessagePreview'] as String? ?? json['lastMessage'] as String?,
      lastMessageTime: json['lastMessageTime'] as String?,
      unreadCount: json['unreadCount'] as int? ?? 0,
    );
  }
}

class ReplyCoachSuggestion {
  final String id;
  final String text;
  final String topic;
  final String tone;
  final String strategy;

  const ReplyCoachSuggestion({
    required this.id,
    required this.text,
    this.topic = 'General',
    this.tone = 'Conversational',
    this.strategy = 'CONVERSATIONAL',
  });

  factory ReplyCoachSuggestion.fromJson(Map<String, dynamic> json) {
    return ReplyCoachSuggestion(
      id: json['id'] as String? ?? UniqueKey().toString(),
      text: json['text'] as String? ?? '',
      topic: json['topic'] as String? ?? 'General',
      tone: json['tone'] as String? ?? 'Conversational',
      strategy: json['strategy'] as String? ?? 'CONVERSATIONAL',
    );
  }
}

class ConversationStateInfo {
  final String topic;
  final String tone;
  final String engagement;
  final String stage;
  final bool dry;

  const ConversationStateInfo({
    this.topic = 'Chat',
    this.tone = 'Friendly',
    this.engagement = 'BALANCED',
    this.stage = 'CASUAL',
    this.dry = false,
  });

  factory ConversationStateInfo.fromJson(Map<String, dynamic> json) {
    return ConversationStateInfo(
      topic: json['topic'] as String? ?? 'Chat',
      tone: json['tone'] as String? ?? 'Friendly',
      engagement: json['engagement'] as String? ?? 'BALANCED',
      stage: json['stage'] as String? ?? 'CASUAL',
      dry: json['dry'] as bool? ?? false,
    );
  }
}

class ReplyCoachResponse {
  final List<ReplyCoachSuggestion> suggestions;
  final ConversationStateInfo? state;
  final String? generationId;

  const ReplyCoachResponse({
    required this.suggestions,
    this.state,
    this.generationId,
  });
}

/// Central Service for BondCircle Real-Time Chat & Intelligence APIs.
class ChatApiService {
  final http.Client _client;

  ChatApiService({http.Client? client}) : _client = client ?? http.Client();

  Map<String, String> _buildHeaders() {
    final headers = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    final token = AuthSession.instance.token;
    if (token != null && token.isNotEmpty) {
      headers['Authorization'] = token.startsWith('Bearer ') ? token : 'Bearer $token';
    }
    return headers;
  }

  int? get _currentUserId {
    return AuthSession.instance.currentUser?.id;
  }

  /// Fetch user conversations.
  Future<List<ConversationModel>> getConversations() async {
    try {
      final response = await _client
          .get(ApiConfig.chatsUri, headers: _buildHeaders())
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final decoded = jsonDecode(response.body);
        final list = (decoded is Map && decoded['data'] is List)
            ? decoded['data'] as List<dynamic>
            : (decoded is List ? decoded : []);

        return list
            .map((item) => ConversationModel.fromJson(item as Map<String, dynamic>, currentUserId: _currentUserId))
            .toList();
      }
    } catch (e) {
      debugPrint('Error fetching conversations: $e');
    }
    return [];
  }

  /// Fetch message history for a conversation.
  Future<List<ChatMessageModel>> getMessages(String conversationId) async {
    try {
      final response = await _client
          .get(ApiConfig.chatMessagesUri(conversationId), headers: _buildHeaders())
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final decoded = jsonDecode(response.body);
        final list = (decoded is Map && decoded['data'] is List)
            ? decoded['data'] as List<dynamic>
            : (decoded is List ? decoded : []);

        return list
            .map((item) => ChatMessageModel.fromJson(item as Map<String, dynamic>, currentUserId: _currentUserId))
            .toList();
      }
    } catch (e) {
      debugPrint('Error fetching messages: $e');
    }
    return [];
  }

  /// Send a message to a conversation.
  Future<ChatMessageModel?> sendMessage({
    required String conversationId,
    required String content,
    String? clientMessageId,
  }) async {
    try {
      final body = jsonEncode({
        'content': content.trim(),
        'clientMessageId': clientMessageId ?? UniqueKey().toString(),
      });

      final response = await _client
          .post(
            ApiConfig.chatMessagesUri(conversationId),
            headers: _buildHeaders(),
            body: body,
          )
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200 || response.statusCode == 201) {
        final decoded = jsonDecode(response.body);
        final data = decoded is Map && decoded['data'] != null ? decoded['data'] as Map<String, dynamic> : decoded as Map<String, dynamic>;
        return ChatMessageModel.fromJson(data, currentUserId: _currentUserId);
      }
    } catch (e) {
      debugPrint('Error sending message: $e');
    }
    return null;
  }

  /// Request AI Reply Coach suggestions for a conversation.
  Future<ReplyCoachResponse?> getReplySuggestions({
    required String conversationId,
    int limit = 3,
  }) async {
    try {
      final body = jsonEncode({
        'conversationId': conversationId,
        'limit': limit,
      });

      final response = await _client
          .post(
            ApiConfig.replySuggestionsUri,
            headers: _buildHeaders(),
            body: body,
          )
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final decoded = jsonDecode(response.body);
        final data = decoded is Map && decoded['data'] != null ? decoded['data'] as Map<String, dynamic> : decoded as Map<String, dynamic>;

        final suggestionsList = data['suggestions'] as List<dynamic>? ?? [];
        final suggestions = suggestionsList
            .map((item) => ReplyCoachSuggestion.fromJson(item as Map<String, dynamic>))
            .toList();

        ConversationStateInfo? state;
        if (data['conversationState'] is Map) {
          state = ConversationStateInfo.fromJson(data['conversationState'] as Map<String, dynamic>);
        }

        return ReplyCoachResponse(
          suggestions: suggestions,
          state: state,
          generationId: data['generationId'] as String?,
        );
      }
    } catch (e) {
      debugPrint('Error getting reply suggestions: $e');
    }
    return null;
  }

  /// Record user feedback on a suggestion (e.g. USED, LIKED, DISLIKED).
  Future<bool> recordSuggestionFeedback({
    required String suggestionId,
    required String conversationId,
    required String action,
    String? finalMessage,
  }) async {
    try {
      final body = jsonEncode({
        'suggestionId': suggestionId,
        'conversationId': conversationId,
        'action': action,
        if (finalMessage != null) 'finalMessage': finalMessage,
      });

      final response = await _client
          .post(
            ApiConfig.replyFeedbackUri,
            headers: _buildHeaders(),
            body: body,
          )
          .timeout(const Duration(seconds: 5));

      return response.statusCode == 200;
    } catch (e) {
      debugPrint('Error recording suggestion feedback: $e');
      return false;
    }
  }

  /// Fetch interest-based icebreakers.
  Future<List<String>> getIcebreakers({
    String? userInterests,
    String? partnerInterests,
    int count = 3,
  }) async {
    try {
      final queryParams = <String, String>{
        'count': count.toString(),
        if (userInterests != null) 'userInterests': userInterests,
        if (partnerInterests != null) 'partnerInterests': partnerInterests,
      };

      final uri = ApiConfig.icebreakerSuggestionsUri.replace(queryParameters: queryParams);
      final response = await _client
          .get(uri, headers: _buildHeaders())
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final decoded = jsonDecode(response.body);
        final list = (decoded is Map && decoded['data'] is List)
            ? decoded['data'] as List<dynamic>
            : (decoded is List ? decoded : []);

        return list.map((item) => item['text']?.toString() ?? item.toString()).toList();
      }
    } catch (e) {
      debugPrint('Error fetching icebreakers: $e');
    }
    return [];
  }

  /// Block a user.
  Future<bool> blockUser({required int blockedUserId}) async {
    try {
      final body = jsonEncode({'blockedUserId': blockedUserId});
      final response = await _client
          .post(ApiConfig.blocksUri, headers: _buildHeaders(), body: body)
          .timeout(const Duration(seconds: 10));

      return response.statusCode == 200 || response.statusCode == 201;
    } catch (e) {
      debugPrint('Error blocking user: $e');
      return false;
    }
  }

  /// Report a user.
  Future<bool> reportUser({
    required int reportedUserId,
    required String reason,
    String? details,
  }) async {
    try {
      final body = jsonEncode({
        'reportedUserId': reportedUserId,
        'reason': reason,
        if (details != null) 'details': details,
      });

      final response = await _client
          .post(ApiConfig.reportsUri, headers: _buildHeaders(), body: body)
          .timeout(const Duration(seconds: 10));

      return response.statusCode == 200 || response.statusCode == 201;
    } catch (e) {
      debugPrint('Error reporting user: $e');
      return false;
    }
  }
}
