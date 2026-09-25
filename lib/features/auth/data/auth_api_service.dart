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

  /// Send verification code to email via Spring Boot backend (Brevo).
  Future<AuthResult> sendVerificationCode({required String email}) async {
    try {
      final response = await _client
          .post(
            ApiConfig.sendVerificationUri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'email': email.trim()}),
          )
          .timeout(const Duration(seconds: 15));

      final data = _decodeBody(response.body);

      if (response.statusCode == 200) {
        final message = data['message'] as String? ?? 'Verification code sent.';
        return AuthResult.success(message: message);
      }

      if (response.statusCode == 409) {
        final message = data['message'] as String? ??
            'An account with this email already exists.';
        return AuthResult.failure(message);
      }

      if (response.statusCode == 429) {
        final message = data['message'] as String? ??
            'Please wait before requesting another code.';
        return AuthResult.failure(message);
      }

      final message = data['message'] as String? ??
          'Failed to send verification code. Please try again.';
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

  /// Verify 6-digit verification code with Spring Boot backend.
  Future<AuthResult> verifyCode({
    required String email,
    required String code,
  }) async {
    try {
      final response = await _client
          .post(
            ApiConfig.verifyCodeUri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'email': email.trim(),
              'code': code.trim(),
            }),
          )
          .timeout(const Duration(seconds: 15));

      final data = _decodeBody(response.body);

      if (response.statusCode == 200) {
        final message = data['message'] as String? ?? 'Email verified successfully';
        return AuthResult.success(message: message);
      }

      final message = data['message'] as String? ??
          'Invalid verification code.';
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

  /// Send password reset verification code via Spring Boot backend (Brevo).
  Future<AuthResult> sendPasswordResetCode({required String email}) async {
    try {
      final response = await _client
          .post(
            ApiConfig.forgotPasswordSendCodeUri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'email': email.trim()}),
          )
          .timeout(const Duration(seconds: 15));

      final data = _decodeBody(response.body);

      if (response.statusCode == 200) {
        final message = data['message'] as String? ?? 'Verification code sent.';
        return AuthResult.success(message: message);
      }

      if (response.statusCode == 429) {
        final message = data['message'] as String? ??
            'Please wait before requesting another code.';
        return AuthResult.failure(message);
      }

      final message = data['message'] as String? ??
          'Failed to send reset code. Please try again.';
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

  /// Verify 6-digit password reset code with Spring Boot backend.
  /// On success, returns AuthResult with the secure reset authorization token in [token].
  Future<AuthResult> verifyPasswordResetCode({
    required String email,
    required String code,
  }) async {
    try {
      final response = await _client
          .post(
            ApiConfig.forgotPasswordVerifyCodeUri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'email': email.trim(),
              'code': code.trim(),
            }),
          )
          .timeout(const Duration(seconds: 15));

      final data = _decodeBody(response.body);

      if (response.statusCode == 200) {
        final resetToken = data['resetToken'] as String?;
        final message = data['message'] as String? ?? 'Code verified successfully.';
        return AuthResult.success(token: resetToken, message: message);
      }

      final message = data['message'] as String? ??
          'Invalid verification code.';
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

  /// Submit new password using the verified single-use reset token.
  Future<AuthResult> resetPassword({
    required String resetToken,
    required String newPassword,
    required String confirmPassword,
  }) async {
    try {
      final response = await _client
          .post(
            ApiConfig.forgotPasswordResetUri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'resetToken': resetToken.trim(),
              'newPassword': newPassword,
              'confirmPassword': confirmPassword,
            }),
          )
          .timeout(const Duration(seconds: 15));

      final data = _decodeBody(response.body);

      if (response.statusCode == 200) {
        final message = data['message'] as String? ?? 'Password reset successfully.';
        return AuthResult.success(message: message);
      }

      final message = data['message'] as String? ??
          'Failed to reset password. Please try again.';
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
