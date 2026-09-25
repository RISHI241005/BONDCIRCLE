import 'package:bondcircle/app.dart';
import 'package:bondcircle/features/auth/data/auth_api_service.dart';
import 'package:bondcircle/features/auth/domain/auth_user.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class MockPasswordRecoveryAuthService extends AuthApiService {
  int sendCount = 0;

  @override
  Future<AuthResult> sendPasswordResetCode({required String email}) async {
    sendCount++;
    if (email == 'cooldown@example.com' && sendCount > 1) {
      return AuthResult.failure('Please wait 60 seconds before requesting another code.');
    }
    return AuthResult.success(message: 'Verification code sent to your email.');
  }

  @override
  Future<AuthResult> verifyPasswordResetCode({
    required String email,
    required String code,
  }) async {
    if (code == '123456') {
      return AuthResult.success(
        token: 'mock-reset-token-12345',
        message: 'Code verified successfully.',
      );
    }
    return AuthResult.failure('Invalid verification code.');
  }

  @override
  Future<AuthResult> resetPassword({
    required String resetToken,
    required String newPassword,
    required String confirmPassword,
  }) async {
    if (resetToken != 'mock-reset-token-12345') {
      return AuthResult.failure('Invalid or expired password reset token.');
    }
    if (newPassword != confirmPassword) {
      return AuthResult.failure('Passwords do not match.');
    }
    // Strict backend complexity check
    if (newPassword.length < 8 ||
        !RegExp(r'[A-Z]').hasMatch(newPassword) ||
        !RegExp(r'[a-z]').hasMatch(newPassword) ||
        !RegExp(r'[0-9]').hasMatch(newPassword) ||
        !RegExp(r'[!@#$%^&*]').hasMatch(newPassword)) {
      return AuthResult.failure('Password does not meet complexity requirements.');
    }
    return AuthResult.success(message: 'Password reset successfully.');
  }
}

void main() {
  group('Forgot Password Flow & Real-Time Password Validation', () {
    testWidgets('real-time password strength validation and button enabling', (
      tester,
    ) async {
      final mockAuth = MockPasswordRecoveryAuthService();
      await tester.pumpWidget(BondCircleApp(authService: mockAuth));

      // Open Forgot Password
      await tester.enterText(
        find.byKey(const Key('emailField')),
        'demo@example.com',
      );
      await tester.ensureVisible(find.byKey(const Key('forgotPasswordButton')));
      await tester.tap(find.byKey(const Key('forgotPasswordButton')));
      await tester.pumpAndSettle();

      // Step 0: Enter email
      await tester.enterText(
        find.byKey(const Key('recoveryEmail')),
        'demo@example.com',
      );
      await tester.tap(find.byKey(const Key('recoveryContinue')));
      await tester.pumpAndSettle();

      // Step 1: Enter code
      expect(find.text('Check your email'), findsOneWidget);
      await tester.enterText(find.byKey(const Key('recoveryCode')), '123456');
      await tester.tap(find.byKey(const Key('recoveryContinue')));
      await tester.pumpAndSettle();

      // Step 2: Choose a new password
      expect(find.text('Choose a new password'), findsOneWidget);
      expect(find.byKey(const Key('passwordRequirementsContainer')), findsOneWidget);

      // Initially all 5 requirements are unsatisfied (✗)
      expect(find.text('✗ Minimum 8 characters'), findsOneWidget);
      expect(find.text('✗ At least 1 uppercase letter (A-Z)'), findsOneWidget);
      expect(find.text('✗ At least 1 lowercase letter (a-z)'), findsOneWidget);
      expect(find.text('✗ At least 1 number (0-9)'), findsOneWidget);
      expect(find.text('✗ At least 1 special character (!@#\$%^&*)'), findsOneWidget);

      // Button is disabled initially
      FilledButton resetBtn = tester.widget<FilledButton>(
        find.byKey(const Key('recoveryContinue')),
      );
      expect(resetBtn.onPressed, isNull);

      // Type partial: only lowercase
      await tester.enterText(find.byKey(const Key('recoveryPassword')), 'pass');
      await tester.pump();
      expect(find.text('✓ At least 1 lowercase letter (a-z)'), findsOneWidget);
      expect(find.text('✗ Minimum 8 characters'), findsOneWidget);
      expect(find.text('✗ At least 1 uppercase letter (A-Z)'), findsOneWidget);

      // Add uppercase and length
      await tester.enterText(find.byKey(const Key('recoveryPassword')), 'Password');
      await tester.pump();
      expect(find.text('✓ Minimum 8 characters'), findsOneWidget);
      expect(find.text('✓ At least 1 uppercase letter (A-Z)'), findsOneWidget);
      expect(find.text('✓ At least 1 lowercase letter (a-z)'), findsOneWidget);
      expect(find.text('✗ At least 1 number (0-9)'), findsOneWidget);
      expect(find.text('✗ At least 1 special character (!@#\$%^&*)'), findsOneWidget);

      // Add number
      await tester.enterText(find.byKey(const Key('recoveryPassword')), 'Password1');
      await tester.pump();
      expect(find.text('✓ At least 1 number (0-9)'), findsOneWidget);
      expect(find.text('✗ At least 1 special character (!@#\$%^&*)'), findsOneWidget);

      // Add special character -> all 5 satisfied!
      await tester.enterText(find.byKey(const Key('recoveryPassword')), 'Password1!');
      await tester.pump();
      expect(find.text('✓ Minimum 8 characters'), findsOneWidget);
      expect(find.text('✓ At least 1 uppercase letter (A-Z)'), findsOneWidget);
      expect(find.text('✓ At least 1 lowercase letter (a-z)'), findsOneWidget);
      expect(find.text('✓ At least 1 number (0-9)'), findsOneWidget);
      expect(find.text('✓ At least 1 special character (!@#\$%^&*)'), findsOneWidget);

      // Button must STILL be disabled because Confirm Password has not matched yet
      resetBtn = tester.widget<FilledButton>(
        find.byKey(const Key('recoveryContinue')),
      );
      expect(resetBtn.onPressed, isNull);

      // Enter mismatched confirm password
      await tester.enterText(find.byKey(const Key('recoveryConfirm')), 'Password2!');
      await tester.pump();
      expect(find.text('✗ Passwords do not match'), findsOneWidget);
      resetBtn = tester.widget<FilledButton>(
        find.byKey(const Key('recoveryContinue')),
      );
      expect(resetBtn.onPressed, isNull);

      // Enter matching confirm password
      await tester.enterText(find.byKey(const Key('recoveryConfirm')), 'Password1!');
      await tester.pump();
      expect(find.text('✓ Passwords match'), findsOneWidget);

      // Reset Password button is NOW enabled!
      resetBtn = tester.widget<FilledButton>(
        find.byKey(const Key('recoveryContinue')),
      );
      expect(resetBtn.onPressed, isNotNull);

      // Tap Reset Password
      await tester.tap(find.byKey(const Key('recoveryContinue')));
      await tester.pumpAndSettle();

      // Step 3: Success & return to Sign In
      expect(find.text('You’re ready to sign in'), findsOneWidget);
      await tester.tap(find.byKey(const Key('recoveryContinue')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('forgotPasswordButton')), findsOneWidget);
    });

    testWidgets('resend code and change email address works', (tester) async {
      final mockAuth = MockPasswordRecoveryAuthService();
      await tester.pumpWidget(BondCircleApp(authService: mockAuth));

      await tester.tap(find.byKey(const Key('forgotPasswordButton')));
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const Key('recoveryEmail')),
        'cooldown@example.com',
      );
      await tester.tap(find.byKey(const Key('recoveryContinue')));
      await tester.pumpAndSettle();

      expect(find.text('Check your email'), findsOneWidget);

      // Tap Resend code -> triggers cooldown
      await tester.tap(find.byKey(const Key('resendCodeButton')));
      await tester.pumpAndSettle();
      expect(
        find.text('Please wait 60 seconds before requesting another code.'),
        findsOneWidget,
      );

      // Tap Change email address -> returns to Step 0
      await tester.tap(find.text('Change email address'));
      await tester.pumpAndSettle();
      expect(find.text('Forgot your password?'), findsOneWidget);
      expect(find.byKey(const Key('recoveryEmail')), findsOneWidget);
    });
  });
}
