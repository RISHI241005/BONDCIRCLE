import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../../../core/config/api_config.dart';
import '../domain/auth_session.dart';
import '../domain/auth_user.dart';

class AuthApiService {
  final http.Client _client;

  AuthApiService({http.Client? client}) : _client = client ?? http.Client();

  /// Sign up a new user with Spring Boot backend.
  Future<AuthResult> signUp({
    required String name,
    required String email,
    required String password,
  }) async {
    try {
      final response = await _client
          .post(
            ApiConfig.signupUri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'name': name.trim(),
              'email': email.trim(),
              'password': password,
            }),
          )
          .timeout(const Duration(seconds: 15));

      final data = _decodeBody(response.body);

      if (response.statusCode == 201 || response.statusCode == 200) {
        final userData = data['user'] as Map<String, dynamic>?;
        final user = userData != null ? AuthUser.fromJson(userData) : null;
        final message = data['message'] as String? ?? 'Account created successfully';
        return AuthResult.success(user: user, message: message);
      }

      if (response.statusCode == 409) {
        final message = data['message'] as String? ??
            'An account with this email already exists.';
        return AuthResult.failure(message);
      }

      final message = data['message'] as String? ??
          'Sign up failed. Please check your details and try again.';
      return AuthResult.failure(message);
    } on SocketException {
      return AuthResult.failure('Unable to connect to the server. Please try again.');
    } on TimeoutException {
      return AuthResult.failure('Unable to connect to the server. Please try again.');
    } on http.ClientException {
      return AuthResult.failure('Unable to connect to the server. Please try again.');
    } catch (e) {
      return AuthResult.failure('Unable to connect to the server. Please try again.');
    }
  }

  /// Sign in an existing user with Spring Boot backend.
  Future<AuthResult> signIn({
    required String email,
    required String password,
  }) async {
    try {
      final response = await _client
          .post(
            ApiConfig.loginUri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'email': email.trim(),
              'password': password,
            }),
          )
          .timeout(const Duration(seconds: 15));

      final data = _decodeBody(response.body);

      if (response.statusCode == 200) {
        final token = data['token'] as String?;
        final userData = data['user'] as Map<String, dynamic>?;
        final user = userData != null ? AuthUser.fromJson(userData) : null;

        if (token != null && user != null) {
          AuthSession.instance.saveSession(token: token, user: user);
        }

        return AuthResult.success(token: token, user: user);
      }

      if (response.statusCode == 401) {
        final message = data['message'] as String? ?? 'Invalid email or password.';
        return AuthResult.failure(message);
      }

      final message = data['message'] as String? ??
          'Invalid email or password.';
      return AuthResult.failure(message);
    } on SocketException {
      return AuthResult.failure('Unable to connect to the server. Please try again.');
    } on TimeoutException {
      return AuthResult.failure('Unable to connect to the server. Please try again.');
    } on http.ClientException {
      return AuthResult.failure('Unable to connect to the server. Please try again.');
    } catch (e) {
      return AuthResult.failure('Unable to connect to the server. Please try again.');
    }
  }

  /// Fetch authenticated user details from /api/auth/me.
  Future<AuthUser?> getMe() async {
    final token = AuthSession.instance.token;
    if (token == null) return null;

    try {
      final response = await _client.get(
        ApiConfig.meUri,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $token',
        },
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final data = _decodeBody(response.body);
        final userData = data['user'] as Map<String, dynamic>?;
        if (userData != null) {
          return AuthUser.fromJson(userData);
        }
      }
    } catch (_) {
      // Ignored for optional session restoration
    }
    return null;
  }

  Map<String, dynamic> _decodeBody(String body) {
    try {
      if (body.isEmpty) return {};
      final decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic>) return decoded;
    } catch (_) {}
    return {};
  }
}
