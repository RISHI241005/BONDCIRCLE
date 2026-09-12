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
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final testEmail = 'user_$timestamp@bondcircle.com';
      const testPassword = 'Password123!';
      const testName = 'Integration Tester';

      // 1. SIGN UP
      final signupResult = await authService.signUp(
        name: testName,
        email: testEmail,
        password: testPassword,
      );

      expect(signupResult.success, isTrue);
      expect(signupResult.message, contains('Account created successfully'));
      expect(signupResult.user, isNotNull);
      expect(signupResult.user?.email, equals(testEmail));
      expect(signupResult.user?.name, equals(testName));

      // 2. DUPLICATE EMAIL SIGNUP
      final duplicateResult = await authService.signUp(
        name: 'Another User',
        email: testEmail,
        password: 'AnotherPassword123!',
      );

      expect(duplicateResult.success, isFalse);
      expect(
        duplicateResult.message,
        equals('An account with this email already exists.'),
      );

      // 3. INVALID CREDENTIALS LOGIN
      final wrongPasswordResult = await authService.signIn(
        email: testEmail,
        password: 'WrongPassword!',
      );

      expect(wrongPasswordResult.success, isFalse);
      expect(
        wrongPasswordResult.message,
        equals('Invalid email or password.'),
      );

      // 4. SUCCESSFUL LOGIN WITH JWT
      final loginResult = await authService.signIn(
        email: testEmail,
        password: testPassword,
      );

      expect(loginResult.success, isTrue);
      expect(loginResult.token, isNotNull);
      expect(loginResult.token!.isNotEmpty, isTrue);
      expect(loginResult.user?.email, equals(testEmail));
      expect(loginResult.user?.name, equals(testName));

      // Token stored in AuthSession
      expect(AuthSession.instance.isAuthenticated, isTrue);
      expect(AuthSession.instance.token, equals(loginResult.token));
      expect(AuthSession.instance.currentUser?.email, equals(testEmail));

      // 5. GET CURRENT USER VIA /api/auth/me
      final meUser = await authService.getMe();
      expect(meUser, isNotNull);
      expect(meUser?.email, equals(testEmail));
      expect(meUser?.name, equals(testName));
    });
  });
}
