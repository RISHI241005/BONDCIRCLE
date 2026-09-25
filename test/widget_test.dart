import 'package:bondcircle/app.dart';
import 'package:bondcircle/features/auth/data/auth_api_service.dart';
import 'package:bondcircle/features/auth/domain/auth_user.dart';
import 'package:bondcircle/features/profile/presentation/profile_setup_screen.dart';
import 'package:bondcircle/features/circles/presentation/interest_circles_screen.dart';
import 'package:bondcircle/features/discover/presentation/discover_screen.dart';
import 'package:bondcircle/features/vibe_check/presentation/vibe_check_screen.dart';
import 'package:bondcircle/features/chat/presentation/chat_screen.dart';
import 'package:bondcircle/features/meetup/presentation/meetup_planner_screen.dart';
import 'package:bondcircle/features/venues/presentation/venue_suggestions_screen.dart';
import 'package:bondcircle/features/safety/presentation/safety_check_in_screen.dart';
import 'package:bondcircle/features/blind_bond/presentation/blind_bond_screen.dart';
import 'package:bondcircle/features/connections/presentation/connections_screen.dart';
import 'package:bondcircle/features/profile/presentation/profile_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('switches between login and signup', (tester) async {
    await tester.pumpWidget(const BondCircleApp());
    expect(find.text('Welcome back.\nYour circle awaits.'), findsOneWidget);
    expect(find.byKey(const Key('nameField')), findsNothing);

    await tester.tap(find.byKey(const Key('signupTab')));
    await tester.pumpAndSettle();
    expect(find.text('Create a genuine\nconnection.'), findsOneWidget);
    expect(find.byKey(const Key('nameField')), findsOneWidget);
  });

  testWidgets('validates empty login form', (tester) async {
    await tester.pumpWidget(const BondCircleApp());
    await tester.ensureVisible(find.byKey(const Key('continueButton')));
    await tester.tap(find.byKey(const Key('continueButton')));
    await tester.pump();
    expect(find.text('Enter a valid email address'), findsOneWidget);
    expect(find.text('Password must have at least 8 characters'), findsOneWidget);
    expect(find.byKey(const Key('passwordField')), findsOneWidget);
  });

  testWidgets('sign in and sign up open different flows', (tester) async {
    final authService = MockAuthApiService();
    await tester.pumpWidget(BondCircleApp(authService: authService));
    await tester.enterText(
      find.byKey(const Key('emailField')),
      'sagar@example.com',
    );
    await tester.enterText(
      find.byKey(const Key('passwordField')),
      'password123',
    );
    await tester.ensureVisible(find.byKey(const Key('continueButton')));
    await tester.tap(find.byKey(const Key('continueButton')));
    await tester.pumpAndSettle();
    expect(find.text('People in your circles'), findsOneWidget);
    expect(find.text('Create profile'), findsNothing);
    expect(find.byKey(const Key('authCodeField')), findsNothing);

    await tester.pumpWidget(BondCircleApp(key: UniqueKey(), authService: authService));
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
      'different123',
    );
    await tester.ensureVisible(find.byKey(const Key('verifyEmailButton')));
    await tester.tap(find.byKey(const Key('verifyEmailButton')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('verificationCodeField')), '123456');
    await tester.ensureVisible(find.byKey(const Key('verifyCodeButton')));
    await tester.tap(find.byKey(const Key('verifyCodeButton')));
    await tester.pumpAndSettle();
    expect(find.text('Verified ✓'), findsOneWidget);

    await tester.ensureVisible(find.byKey(const Key('continueButton')));
    await tester.tap(find.byKey(const Key('continueButton')));
    await tester.pumpAndSettle();
    expect(find.text('Passwords do not match'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('confirmPasswordField')),
      'password123',
    );
    await tester.ensureVisible(find.byKey(const Key('continueButton')));
    await tester.tap(find.byKey(const Key('continueButton')));
    await tester.pumpAndSettle();
    expect(find.text('Account created successfully'), findsOneWidget);
    expect(find.byKey(const Key('continueToProfileButton')), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('continueToProfileButton')));
    await tester.tap(find.byKey(const Key('continueToProfileButton')));
    await tester.pumpAndSettle();
    expect(find.text('Create profile'), findsOneWidget);
  });

  testWidgets('profile setup validates required details', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: ProfileSetupScreen(initialName: 'Sagar')),
    );

    await tester.tap(find.byKey(const Key('profileContinueButton')));
    await tester.pump();

    expect(find.text('Enter age 18–99'), findsOneWidget);
    expect(find.text('Enter your city'), findsOneWidget);
    expect(find.text('Write at least 20 characters'), findsOneWidget);
  });

  testWidgets('profile setup accepts a custom interest', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: ProfileSetupScreen(initialName: 'Sagar')),
    );

    await tester.enterText(find.byKey(const Key('profileAgeField')), '22');
    await tester.enterText(
      find.byKey(const Key('profileCityField')),
      'Kolkata',
    );
    await tester.enterText(
      find.byKey(const Key('profileBioField')),
      'I enjoy thoughtful conversations and creative weekend plans.',
    );
    await tester.tap(find.byKey(const Key('profileContinueButton')));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('addCustomInterestButton')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('customInterestField')),
      'Photography',
    );
    await tester.tap(find.byKey(const Key('confirmCustomInterestButton')));
    await tester.pumpAndSettle();

    expect(find.text('Photography'), findsOneWidget);
  });

  testWidgets('interest circles can be filtered and joined', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: InterestCirclesScreen(displayName: 'Sagar')),
    );

    await tester.tap(find.byKey(const Key('joinCoffee Explorers')));
    await tester.pump();
    expect(find.text('1 joined'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('circleSearchField')), 'books');
    await tester.pump();
    expect(find.text('Readers & Stories'), findsOneWidget);
    expect(find.text('Coffee Explorers'), findsNothing);
  });

  testWidgets('liking a profile shows a match dialog', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: DiscoverScreen(
          displayName: 'Sagar',
          joinedCircles: ['Coffee Explorers', 'Readers & Stories'],
        ),
      ),
    );

    await tester.tap(find.byKey(const Key('likeProfileButton')));
    await tester.pumpAndSettle();
    expect(find.text('You matched with Aarohi!'), findsOneWidget);
    expect(find.byKey(const Key('startVibeCheckButton')), findsOneWidget);
  });

  testWidgets('vibe check advances after selecting an answer', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: VibeCheckScreen(
          matchName: 'Aarohi',
          sharedCircle: 'Coffee Explorers',
        ),
      ),
    );

    expect(find.text('1/3'), findsOneWidget);
    await tester.tap(find.byKey(const Key('vibeOption0')));
    await tester.tap(find.byKey(const Key('vibeContinueButton')));
    await tester.pumpAndSettle();
    expect(find.text('2/3'), findsOneWidget);
  });

  testWidgets('chat sends a local message', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: ChatScreen(matchName: 'Aarohi', sharedCircle: 'Coffee Explorers'),
      ),
    );

    await tester.enterText(
      find.byKey(const Key('chatMessageField')),
      'Would Saturday work?',
    );
    await tester.tap(find.byKey(const Key('sendChatMessageButton')));
    await tester.pump();
    expect(find.text('Would Saturday work?'), findsOneWidget);
  });

  testWidgets('meetup planner requires and accepts a vibe', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: MeetupPlannerScreen(
          matchName: 'Aarohi',
          sharedCircle: 'Coffee Explorers',
        ),
      ),
    );

    await tester.tap(find.byKey(const Key('meetupContinueButton')));
    await tester.pump();
    expect(find.text('Choose a meetup vibe to continue.'), findsOneWidget);

    await tester.tap(find.byKey(const Key('meetupVibe0')));
    await tester.tap(find.byKey(const Key('meetupContinueButton')));
    await tester.pumpAndSettle();
    expect(find.text('Choose date and time'), findsOneWidget);
  });

  testWidgets('venue suggestions can be filtered and selected', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: VenueSuggestionsScreen(
          matchName: 'Aarohi',
          sharedCircle: 'Coffee Explorers',
          vibe: 'Coffee',
          date: DateTime(2026, 8, 28),
          time: '4:00 PM',
          title: 'Coffee and stories',
          note: '',
        ),
      ),
    );

    expect(find.text('Paper & Bean'), findsOneWidget);
    await tester.tap(find.byKey(const Key('venueFilterActivity')));
    await tester.pump();
    expect(find.text('Pixel Playground'), findsOneWidget);
    expect(find.text('Paper & Bean'), findsNothing);
    await tester.tap(find.byKey(const Key('selectVenuePixel Playground')));
    await tester.pumpAndSettle();
    expect(find.text('Venue selected!'), findsOneWidget);
    expect(find.text('Pixel Playground'), findsOneWidget);
  });

  testWidgets('safety check-in validates and confirms trusted contact', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SafetyCheckInScreen(
          matchName: 'Aarohi',
          planTitle: 'Coffee and stories',
          venueName: 'Paper & Bean',
          venueArea: 'Park Street',
          date: DateTime(2026, 8, 28),
          time: '4:00 PM',
        ),
      ),
    );

    await tester.tap(find.byKey(const Key('activateSafetyCheckInButton')));
    await tester.pump();
    expect(find.text('Enter your trusted contact’s name'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('trustedContactNameField')),
      'Riya',
    );
    await tester.enterText(
      find.byKey(const Key('trustedContactPhoneField')),
      '9876543210',
    );
    await tester.tap(find.byKey(const Key('activateSafetyCheckInButton')));
    await tester.pumpAndSettle();
    expect(find.text('Your safety check-in is ready'), findsOneWidget);
    expect(
      find.textContaining('Riya will be your trusted contact'),
      findsOneWidget,
    );
  });

  testWidgets('blind bond joins circle and opens session', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: BlindBondScreen(
          displayName: 'Sagar',
          joinedCircles: ['Coffee Explorers'],
        ),
      ),
    );
    await tester.scrollUntilVisible(
      find.byKey(const Key('blindJoinGaming')),
      200,
    );
    await tester.tap(find.byKey(const Key('blindJoinGaming')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('enterBlindGaming')));
    await tester.pumpAndSettle();
    expect(find.text('Gaming'), findsOneWidget);
    expect(find.byKey(const Key('findBlindBondButton')), findsOneWidget);
  });

  testWidgets('blind reveal requires mutual consent', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: BlindSessionScreen(circle: 'Coffee Explorers')),
    );
    Future<void> tap(String key) async {
      await tester.ensureVisible(find.byKey(Key(key)));
      await tester.tap(find.byKey(Key(key)));
      await tester.pumpAndSettle();
    }

    await tap('findBlindBondButton');
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();
    await tap('startBlindChatButton');
    expect(find.text('15:00 remaining'), findsOneWidget);
    await tap('endBlindInteraction');
    await tap('requestRevealButton');
    expect(find.textContaining('is Aarohi'), findsNothing);
    expect(find.byKey(const Key('continueRevealedChat')), findsNothing);
    await tap('simulatePartnerConsentButton');
    expect(find.text('It’s mutual!'), findsOneWidget);
    await tap('continueRevealedChat');
    expect(find.byKey(const Key('chatMessageField')), findsOneWidget);
  });

  testWidgets('connections can be searched and opened', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: ConnectionsScreen(
          displayName: 'Sagar',
          joinedCircles: ['Coffee Explorers'],
        ),
      ),
    );
    await tester.enterText(
      find.byKey(const Key('connectionSearchField')),
      'Meera',
    );
    await tester.pump();
    expect(find.byKey(const Key('openChatMeera')), findsOneWidget);
    expect(find.text('Aarohi'), findsNothing);
    await tester.tap(find.byKey(const Key('openChatMeera')));
    await tester.pumpAndSettle();
    expect(find.text('Online'), findsOneWidget);
    expect(find.text('Shared circle: Readers & Stories'), findsOneWidget);
  });

  testWidgets('profile opens privacy settings', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: ProfileScreen(
          displayName: 'Sagar',
          joinedCircles: ['Coffee Explorers'],
        ),
      ),
    );
    expect(find.text('Profile strength'), findsOneWidget);
    await tester.tap(find.byKey(const Key('profileSettingsButton')));
    await tester.pumpAndSettle();
    expect(find.text('Privacy and preferences'), findsOneWidget);
    expect(find.byKey(const Key('discoverableSwitch')), findsOneWidget);
  });

  testWidgets('discover navigation opens profile tab', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: DiscoverScreen(
          displayName: 'Sagar',
          joinedCircles: ['Coffee Explorers'],
        ),
      ),
    );
    await tester.tap(find.text('Profile'));
    await tester.pumpAndSettle();
    expect(find.text('Your profile'), findsOneWidget);
    expect(find.text('Profile strength'), findsOneWidget);
  });
}

Future<void> verifyAuthCode(WidgetTester tester) async {
  expect(find.byKey(const Key('passwordField')), findsNothing);
  await tester.ensureVisible(find.byKey(const Key('continueButton')));
  await tester.tap(find.byKey(const Key('continueButton')));
  await tester.pumpAndSettle();
  expect(find.byKey(const Key('passwordField')), findsNothing);
  final code = tester
      .widget<Text>(find.byKey(const Key('demoAuthCode')))
      .data!
      .split(': ')
      .last;
  await tester.enterText(find.byKey(const Key('authCodeField')), code);
  await tester.ensureVisible(find.byKey(const Key('continueButton')));
  await tester.tap(find.byKey(const Key('continueButton')));
  await tester.pumpAndSettle();
}

class MockAuthApiService extends AuthApiService {
  @override
  Future<AuthResult> signIn({
    required String email,
    required String password,
  }) async {
    if (email == 'sagar@example.com' && password == 'password123') {
      return AuthResult.success(
        token: 'mock_jwt_token',
        user: const AuthUser(id: 1, name: 'Sagar', email: 'sagar@example.com'),
      );
    }
    return AuthResult.failure('Invalid email or password.');
  }

  @override
  Future<AuthResult> sendVerificationCode({required String email}) async {
    return AuthResult.success(message: 'Verification code sent.');
  }

  @override
  Future<AuthResult> verifyCode({
    required String email,
    required String code,
  }) async {
    return AuthResult.success(message: 'Email verified successfully');
  }

  @override
  Future<AuthResult> signUp({
    required String name,
    required String email,
    required String password,
  }) async {
    return AuthResult.success(
      user: AuthUser(id: 2, name: name, email: email),
      message: 'Account created successfully',
    );
  }
}
