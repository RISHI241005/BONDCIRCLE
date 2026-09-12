import 'package:bondcircle/app.dart';
import 'package:bondcircle/features/auth/data/auth_api_service.dart';
import 'package:bondcircle/features/auth/domain/auth_session.dart';
import 'package:bondcircle/features/auth/domain/auth_user.dart';
import 'package:bondcircle/features/discover/presentation/discover_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class MockAuthApiService extends AuthApiService {
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
    if (email == 'existing@example.com') {
      return AuthResult.failure('An account with this email already exists.');
    }
    final user = AuthUser(id: 2, name: name, email: email);
    return AuthResult.success(
      user: user,
      message: 'Account created successfully! Please sign in.',
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

        // Verify NO OTP or verification code elements exist
        expect(find.byKey(const Key('demoAuthCode')), findsNothing);
        expect(find.byKey(const Key('authCodeField')), findsNothing);
        expect(find.byKey(const Key('resendAuthCodeButton')), findsNothing);
        expect(find.byKey(const Key('changeAuthEmailButton')), findsNothing);
        expect(find.textContaining('Step 1 of 3'), findsNothing);
        expect(find.text('Verify your email before continuing'), findsNothing);

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
      'signup creates user and transitions to sign in with success feedback',
      (tester) async {
        await tester.pumpWidget(
          BondCircleApp(authService: MockAuthApiService()),
        );

        await tester.tap(find.byKey(const Key('signupTab')));
        await tester.pumpAndSettle();

        await tester.enterText(find.byKey(const Key('nameField')), 'New User');
        await tester.enterText(
          find.byKey(const Key('emailField')),
          'new@example.com',
        );
        await tester.enterText(
          find.byKey(const Key('passwordField')),
          'password123',
        );
        await tester.enterText(
          find.byKey(const Key('confirmPasswordField')),
          'password123',
        );

        await tester.ensureVisible(find.byKey(const Key('continueButton')));
        await tester.tap(find.byKey(const Key('continueButton')));
        await tester.pumpAndSettle();

        expect(
          find.text('Account created successfully! Please sign in.'),
          findsOneWidget,
        );
        // Switched to sign in tab
        expect(find.widgetWithText(FilledButton, 'Sign in'), findsOneWidget);
      },
    );

    testWidgets(
      'signup shows duplicate email error message',
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
        await tester.enterText(
          find.byKey(const Key('passwordField')),
          'password123',
        );
        await tester.enterText(
          find.byKey(const Key('confirmPasswordField')),
          'password123',
        );

        await tester.ensureVisible(find.byKey(const Key('continueButton')));
        await tester.tap(find.byKey(const Key('continueButton')));
        await tester.pumpAndSettle();

        expect(
          find.text('An account with this email already exists.'),
          findsOneWidget,
        );
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
