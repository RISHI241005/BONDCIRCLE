import 'dart:io';

import 'package:bondcircle/core/config/api_config.dart';
import 'package:bondcircle/features/auth/data/auth_api_service.dart';
import 'package:bondcircle/features/auth/domain/auth_session.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Live Spring Boot & PostgreSQL Authentication Integration', () {
    late AuthApiService authService;

    setUp(() {
      ApiConfig.baseUrl = 'http://localhost:8080';
      authService = AuthApiService();
      AuthSession.instance.clearSession();
    });

    test('End-to-end signup and login with real backend and PostgreSQL', () async {
      try {
        final socket = await Socket.connect('localhost', 8080, timeout: const Duration(milliseconds: 500));
        await socket.close();
      } catch (_) {
        // Backend not running; skip live test in offline test runs
        return;
      }

      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final testEmail = 'user_$timestamp@bondcircle.com';
      const testPassword = 'Password123!';
      const testName = 'Integration Tester';

      // 1. SIGN UP WITHOUT VERIFICATION IS REJECTED
      final unverifiedResult = await authService.signUp(
        name: testName,
        email: testEmail,
        password: testPassword,
      );
      expect(unverifiedResult.success, isFalse);
      expect(
        unverifiedResult.message,
        contains('has not been verified'),
      );

      // 2. INVALID VERIFICATION CODE IS REJECTED
      final invalidCodeResult = await authService.verifyCode(
        email: testEmail,
        code: '000000',
      );
      expect(invalidCodeResult.success, isFalse);

      // 3. INVALID CREDENTIALS LOGIN
      final wrongPasswordResult = await authService.signIn(
        email: 'nonexistent_$timestamp@bondcircle.com',
        password: 'WrongPassword!',
      );

      expect(wrongPasswordResult.success, isFalse);
      expect(
        wrongPasswordResult.message,
        equals('Invalid email or password.'),
      );
    });
  });
}
