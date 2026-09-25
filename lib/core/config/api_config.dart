import 'dart:io' show Platform;
import 'package:flutter/foundation.dart';

/// Central configuration for BondCircle API endpoints.
class ApiConfig {
  ApiConfig._();

  static String? _customBaseUrl;

  /// Central base URL for backend API requests.
  /// Resolves to:
  /// 1. Explicit override via [baseUrl] setter
  /// 2. Compile-time environment variable (--dart-define=API_BASE_URL=...)
  /// 3. http://10.0.2.2:8080 for Android emulator
  /// 4. http://localhost:8080 for desktop, web, iOS
  static String get baseUrl {
    if (_customBaseUrl != null) return _customBaseUrl!;

    const envUrl = String.fromEnvironment('API_BASE_URL');
    if (envUrl.isNotEmpty) return envUrl;

    if (!kIsWeb && Platform.isAndroid) {
      return 'http://10.0.2.2:8080';
    }
    return 'http://localhost:8080';
  }

  static set baseUrl(String url) {
    _customBaseUrl = url;
  }

  static void reset() {
    _customBaseUrl = null;
  }

  static Uri get signupUri => Uri.parse('$baseUrl/api/auth/signup');
  static Uri get loginUri => Uri.parse('$baseUrl/api/auth/login');
  static Uri get meUri => Uri.parse('$baseUrl/api/auth/me');
  static Uri get sendVerificationUri => Uri.parse('$baseUrl/api/auth/send-verification');
  static Uri get verifyCodeUri => Uri.parse('$baseUrl/api/auth/verify-code');
  static Uri get forgotPasswordSendCodeUri => Uri.parse('$baseUrl/api/auth/forgot-password/send-code');
  static Uri get forgotPasswordVerifyCodeUri => Uri.parse('$baseUrl/api/auth/forgot-password/verify-code');
  static Uri get forgotPasswordResetUri => Uri.parse('$baseUrl/api/auth/forgot-password/reset');
  static Uri get profileMeUri => Uri.parse('$baseUrl/api/profile/me');

  // Chat & Messaging URIs
  static Uri get chatsUri => Uri.parse('$baseUrl/api/v1/chats');
  static Uri chatMessagesUri(String conversationId) =>
      Uri.parse('$baseUrl/api/v1/chats/$conversationId/messages');
  static Uri chatMessageStatusUri(String conversationId, String messageId) =>
      Uri.parse('$baseUrl/api/v1/chats/$conversationId/messages/$messageId/status');
  static Uri chatTypingUri(String conversationId) =>
      Uri.parse('$baseUrl/api/v1/chats/$conversationId/typing');

  // AI Reply Coach & Icebreakers
  static Uri get replySuggestionsUri =>
      Uri.parse('$baseUrl/api/ai/reply-suggestions');
  static Uri get replyRegenerateUri =>
      Uri.parse('$baseUrl/api/ai/reply-suggestions/regenerate');
  static Uri get replyFeedbackUri =>
      Uri.parse('$baseUrl/api/ai/reply-suggestions/feedback');
  static Uri get icebreakerSuggestionsUri =>
      Uri.parse('$baseUrl/api/v1/icebreakers/suggestions');

  // Trust & Safety
  static Uri get blocksUri => Uri.parse('$baseUrl/api/v1/blocks');
  static Uri get reportsUri => Uri.parse('$baseUrl/api/v1/reports');

  // WebSocket Endpoint
  static String get webSocketUrl {
    final wsBase = baseUrl.startsWith('https://')
        ? baseUrl.replaceFirst('https://', 'wss://')
        : baseUrl.replaceFirst('http://', 'ws://');
    return '$wsBase/ws';
  }
}
