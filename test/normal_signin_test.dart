import 'package:bondcircle/app.dart';
import 'package:bondcircle/features/auth/data/auth_api_service.dart';
import 'package:bondcircle/features/auth/domain/auth_session.dart';
import 'package:bondcircle/features/auth/domain/auth_user.dart';
import 'package:bondcircle/features/discover/presentation/discover_screen.dart';
import 'package:bondcircle/features/profile/presentation/profile_setup_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class MockAuthApiService extends AuthApiService {
  final Set<String> verifiedEmails = {};

  @override
  Future<AuthResult> sendVerificationCode({required String email}) async {
    if (email == 'existing@example.com') {
      return AuthResult.failure('An account with this email already exists.');
    }
    return AuthResult.success(message: 'Verification code sent to your email.');
  }

  @override
  Future<AuthResult> verifyCode({
    required String email,
    required String code,
  }) async {
    if (code == '123456') {
      verifiedEmails.add(email);
      return AuthResult.success(message: 'Email verified successfully');
    }
    return AuthResult.failure('Invalid verification code.');
  }

  @override
  Future<AuthResult> signIn({
    required String email,
    required String password,
  }) async {
    if (email == 'user@example.com' && password == 'securePassword123') {
      const user = AuthUser(id: 1, name: 'Sagar', email: 'user@example.com');
      AuthSession.instance.saveSession(token: 'mock_jwt_token', user: user);
      return AuthResult.success(token: 'mock_jwt_token', user: user);
    }
    return AuthResult.failure('Invalid email or password.');
  }

  @override
  Future<AuthResult> signUp({
    required String name,
    required String email,
    required String password,
  }) async {
    if (!verifiedEmails.contains(email)) {
      return AuthResult.failure('Email address has not been verified.');
    }
    if (email == 'existing@example.com') {
      return AuthResult.failure('An account with this email already exists.');
    }
    final user = AuthUser(id: 2, name: name, email: email);
    return AuthResult.success(
      user: user,
      message: 'Account created successfully',
    );
  }
}

void main() {
  group('Normal Sign-In & Sign-Up Flow', () {
    testWidgets(
      'email and password directly log in to Discover without OTP or email verification',
      (tester) async {
        await tester.pumpWidget(
          BondCircleApp(authService: MockAuthApiService()),
        );

        // Verify initial state: Email and Password fields are both visible immediately
        expect(find.byKey(const Key('emailField')), findsOneWidget);
        expect(find.byKey(const Key('passwordField')), findsOneWidget);
        expect(find.byKey(const Key('forgotPasswordButton')), findsOneWidget);

        // Verify NO OTP or verification code elements exist during sign in
        expect(find.byKey(const Key('demoAuthCode')), findsNothing);
        expect(find.byKey(const Key('authCodeField')), findsNothing);
        expect(find.byKey(const Key('verificationCodeSection')), findsNothing);
        expect(find.byKey(const Key('verifyEmailButton')), findsNothing);

        // Button label should say "Sign in"
        expect(find.widgetWithText(FilledButton, 'Sign in'), findsOneWidget);

        // Enter valid credentials
        await tester.enterText(
          find.byKey(const Key('emailField')),
          'user@example.com',
        );
        await tester.enterText(
          find.byKey(const Key('passwordField')),
          'securePassword123',
        );

        // Tap Sign in
        await tester.ensureVisible(find.byKey(const Key('continueButton')));
        await tester.tap(find.byKey(const Key('continueButton')));
        await tester.pumpAndSettle();

        // Must directly land on DiscoverScreen without any intermediate verification screen
        expect(find.byType(DiscoverScreen), findsOneWidget);
        expect(find.text('People in your circles'), findsOneWidget);
      },
    );

    testWidgets(
      'shows existing error for incorrect email format and invalid password',
      (tester) async {
        await tester.pumpWidget(
          BondCircleApp(authService: MockAuthApiService()),
        );

        // Test with invalid email format
        await tester.enterText(
          find.byKey(const Key('emailField')),
          'notanemail',
        );
        await tester.enterText(
          find.byKey(const Key('passwordField')),
          'short',
        );

        await tester.ensureVisible(find.byKey(const Key('continueButton')));
        await tester.tap(find.byKey(const Key('continueButton')));
        await tester.pump();

        expect(find.text('Enter a valid email address'), findsOneWidget);
        expect(
          find.text('Password must have at least 8 characters'),
          findsOneWidget,
        );
        expect(find.byType(DiscoverScreen), findsNothing);
      },
    );

    testWidgets(
      'shows error message when backend rejects invalid credentials',
      (tester) async {
        await tester.pumpWidget(
          BondCircleApp(authService: MockAuthApiService()),
        );

        await tester.enterText(
          find.byKey(const Key('emailField')),
          'user@example.com',
        );
        await tester.enterText(
          find.byKey(const Key('passwordField')),
          'wrongPassword123',
        );

        await tester.ensureVisible(find.byKey(const Key('continueButton')));
        await tester.tap(find.byKey(const Key('continueButton')));
        await tester.pumpAndSettle();

        expect(find.text('Invalid email or password.'), findsOneWidget);
        expect(find.byType(DiscoverScreen), findsNothing);
      },
    );

    testWidgets(
      'complete signup flow: verify email inline -> create account -> continue to profile setup',
      (tester) async {
        await tester.pumpWidget(
          BondCircleApp(authService: MockAuthApiService()),
        );

        // 1. Switch to Sign up tab
        await tester.tap(find.byKey(const Key('signupTab')));
        await tester.pumpAndSettle();

        // Verify fields on existing Sign Up screen
        expect(find.byKey(const Key('nameField')), findsOneWidget);
        expect(find.byKey(const Key('emailField')), findsOneWidget);
        expect(find.byKey(const Key('passwordField')), findsOneWidget);
        expect(find.byKey(const Key('confirmPasswordField')), findsOneWidget);

        // Verify button beside Email Address field
        expect(find.byKey(const Key('verifyEmailButton')), findsOneWidget);

        // Create Account button is initially disabled because email is not verified
        final createBtn = tester.widget<FilledButton>(
          find.byKey(const Key('continueButton')),
        );
        expect(createBtn.onPressed, isNull);

        // Fill in name and email
        await tester.enterText(find.byKey(const Key('nameField')), 'New User');
        await tester.enterText(
          find.byKey(const Key('emailField')),
          'new@example.com',
        );

        // 2. Click Verify beside Email Address field
        await tester.tap(find.byKey(const Key('verifyEmailButton')));
        await tester.pumpAndSettle();

        // Verification code input area appears directly on the SAME screen
        expect(find.byKey(const Key('verificationCodeSection')), findsOneWidget);
        expect(find.byKey(const Key('verificationCodeField')), findsOneWidget);
        expect(find.byKey(const Key('verifyCodeButton')), findsOneWidget);

        // Try wrong code
        await tester.enterText(
          find.byKey(const Key('verificationCodeField')),
          '999999',
        );
        await tester.ensureVisible(find.byKey(const Key('verifyCodeButton')));
        await tester.tap(find.byKey(const Key('verifyCodeButton')));
        await tester.pumpAndSettle();

        expect(find.text('Invalid verification code.'), findsOneWidget);
        expect(find.byKey(const Key('emailVerifiedBadge')), findsNothing);

        // Enter correct 6-digit code
        await tester.enterText(
          find.byKey(const Key('verificationCodeField')),
          '123456',
        );
        await tester.ensureVisible(find.byKey(const Key('verifyCodeButton')));
        await tester.tap(find.byKey(const Key('verifyCodeButton')));
        await tester.pumpAndSettle();

        // Mark email as verified and show success state
        expect(find.text('Email verified successfully'), findsOneWidget);
        expect(find.byKey(const Key('emailVerifiedBadge')), findsOneWidget);
        expect(find.text('Verified ✓'), findsOneWidget);

        // Fill passwords
        await tester.enterText(
          find.byKey(const Key('passwordField')),
          'password123',
        );
        await tester.enterText(
          find.byKey(const Key('confirmPasswordField')),
          'password123',
        );

        // Create Account button can now be used
        final enabledCreateBtn = tester.widget<FilledButton>(
          find.byKey(const Key('continueButton')),
        );
        expect(enabledCreateBtn.onPressed, isNotNull);

        // 3. Click Create Account
        await tester.ensureVisible(find.byKey(const Key('continueButton')));
        await tester.tap(find.byKey(const Key('continueButton')));
        await tester.pumpAndSettle();

        // Shows "Account created successfully" and [ Continue ]
        expect(find.text('Account created successfully'), findsOneWidget);
        expect(find.byKey(const Key('continueToProfileButton')), findsOneWidget);

        // 4. Click Continue -> opens ProfileSetupScreen
        await tester.tap(find.byKey(const Key('continueToProfileButton')));
        await tester.pumpAndSettle();

        expect(find.byType(ProfileSetupScreen), findsOneWidget);
        expect(find.text('Create profile'), findsOneWidget);
        expect(find.text('Let’s start with you'), findsOneWidget);
      },
    );

    testWidgets(
      'signup verify email shows duplicate email error',
      (tester) async {
        await tester.pumpWidget(
          BondCircleApp(authService: MockAuthApiService()),
        );

        await tester.tap(find.byKey(const Key('signupTab')));
        await tester.pumpAndSettle();

        await tester.enterText(find.byKey(const Key('nameField')), 'Existing User');
        await tester.enterText(
          find.byKey(const Key('emailField')),
          'existing@example.com',
        );

        // Tap Verify
        await tester.tap(find.byKey(const Key('verifyEmailButton')));
        await tester.pumpAndSettle();

        expect(
          find.text('An account with this email already exists.'),
          findsOneWidget,
        );
        // Does not show verification code area
        expect(find.byKey(const Key('verificationCodeSection')), findsNothing);
      },
    );

    testWidgets(
      'forgot password remains a separate flow accessible from sign-in',
      (tester) async {
        await tester.pumpWidget(
          BondCircleApp(authService: MockAuthApiService()),
        );

        // Pre-fill email
        await tester.enterText(
          find.byKey(const Key('emailField')),
          'forgotten@example.com',
        );

        // Tap "Forgot password?"
        await tester.ensureVisible(find.byKey(const Key('forgotPasswordButton')));
        await tester.tap(find.byKey(const Key('forgotPasswordButton')));
        await tester.pumpAndSettle();

        // Recovery screen appears as a separate screen
        expect(find.text('Account recovery'), findsOneWidget);
        expect(find.text('Forgot your password?'), findsOneWidget);
        expect(find.byKey(const Key('recoveryEmail')), findsOneWidget);

        // Pre-filled email from login should be present
        final emailField = tester.widget<TextFormField>(
          find.byKey(const Key('recoveryEmail')),
        );
        expect(emailField.controller?.text, 'forgotten@example.com');
      },
    );
  });
}
