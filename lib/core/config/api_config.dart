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
}
