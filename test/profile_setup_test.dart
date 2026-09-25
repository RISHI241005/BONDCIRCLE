import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:bondcircle/core/config/api_config.dart';
import 'package:bondcircle/features/auth/domain/auth_session.dart';
import 'package:bondcircle/features/auth/domain/auth_user.dart';
import 'package:bondcircle/features/circles/presentation/interest_circles_screen.dart';
import 'package:bondcircle/features/profile/data/profile_api_service.dart';
import 'package:bondcircle/features/profile/presentation/profile_setup_screen.dart';

void main() {
  setUp(() {
    AuthSession.instance.clearSession();
    ApiConfig.reset();
  });

  tearDown(() {
    AuthSession.instance.clearSession();
    ApiConfig.reset();
  });

  group('ProfileApiService tests', () {
    test('saveProfile sends all 5 categories with Authorization header', () async {
      late http.Request capturedRequest;
      final mockClient = MockClient((request) async {
        if (request.url.path == '/api/profile/me' && request.method == 'PUT') {
          capturedRequest = request;
          return http.Response(
            jsonEncode({
              'id': 1,
              'gender': 'Woman',
              'orientation': 'Bisexual',
              'connectionIntention': 'Long-term relationship',
              'relationshipStyle': 'Monogamy',
              'interests': ['Coffee', 'Books', 'Travel'],
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }
        return http.Response('Not Found', 404);
      });

      final service = ProfileApiService(client: mockClient);
      const profile = ProfileData(
        gender: 'Woman',
        orientation: 'Bisexual',
        connectionIntention: 'Long-term relationship',
        relationshipStyle: 'Monogamy',
        interests: ['Coffee', 'Books', 'Travel'],
      );

      final result = await service.saveProfile(profile, token: 'test-token-xyz');

      expect(result, isNotNull);
      expect(result!.gender, 'Woman');
      expect(result.orientation, 'Bisexual');
      expect(result.connectionIntention, 'Long-term relationship');
      expect(result.relationshipStyle, 'Monogamy');
      expect(result.interests, ['Coffee', 'Books', 'Travel']);

      expect(capturedRequest.headers['authorization'], 'Bearer test-token-xyz');
      final decodedBody = jsonDecode(capturedRequest.body) as Map<String, dynamic>;
      expect(decodedBody['gender'], 'Woman');
      expect(decodedBody['orientation'], 'Bisexual');
      expect(decodedBody['connectionIntention'], 'Long-term relationship');
      expect(decodedBody['relationshipStyle'], 'Monogamy');
      expect(decodedBody['interests'], ['Coffee', 'Books', 'Travel']);
    });

    test('getProfile retrieves all 5 categories with Authorization header', () async {
      final mockClient = MockClient((request) async {
        if (request.url.path == '/api/profile/me' && request.method == 'GET') {
          expect(request.headers['authorization'], 'Bearer test-token-123');
          return http.Response(
            jsonEncode({
              'id': 42,
              'gender': 'Man',
              'orientation': 'Straight',
              'connectionIntention': 'Life partner',
              'relationshipStyle': 'Monogamy',
              'interests': ['Fitness', 'Startups', 'Photography'],
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }
        return http.Response('Not Found', 404);
      });

      final service = ProfileApiService(client: mockClient);
      final profile = await service.getProfile(token: 'test-token-123');

      expect(profile, isNotNull);
      expect(profile!.gender, 'Man');
      expect(profile.orientation, 'Straight');
      expect(profile.connectionIntention, 'Life partner');
      expect(profile.relationshipStyle, 'Monogamy');
      expect(profile.interests, ['Fitness', 'Startups', 'Photography']);
    });
  });

  group('ProfileSetupScreen persistence and retrieval widgets', () {
    testWidgets('ProfileSetupScreen auto-loads existing saved 5 categories on init', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      AuthSession.instance.saveSession(
        token: 'auth-jwt-token',
        user: const AuthUser(id: 1, name: 'Sagar', email: 'sagar@example.com'),
      );

      final mockClient = MockClient((request) async {
        if (request.url.path == '/api/profile/me' && request.method == 'GET') {
          return http.Response(
            jsonEncode({
              'id': 1,
              'gender': 'Nonbinary',
              'orientation': 'Queer',
              'connectionIntention': 'Figuring out my goals',
              'relationshipStyle': 'Non-monogamy',
              'interests': ['Books', 'Music', 'Hiking'],
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }
        return http.Response('Not Found', 404);
      });

      final service = ProfileApiService(client: mockClient);

      await tester.pumpWidget(
        MaterialApp(
          home: ProfileSetupScreen(
            initialName: 'Sagar',
            profileApiService: service,
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Step 0: Fill details and continue
      await tester.enterText(find.byKey(const Key('profileAgeField')), '25');
      await tester.enterText(find.byKey(const Key('profileCityField')), 'Kolkata');
      await tester.enterText(
        find.byKey(const Key('profileBioField')),
        'Exploring authentic connections and creative hobbies.',
      );
      await tester.tap(find.byKey(const Key('profileContinueButton')));
      await tester.pumpAndSettle();

      // Step 1: Interests step - should have Books, Music, and custom Hiking pre-selected
      expect(find.byKey(const Key('interestBooks')), findsOneWidget);
      expect(find.byKey(const Key('interestMusic')), findsOneWidget);
      expect(find.byKey(const Key('interestHiking')), findsOneWidget);

      final booksChip = tester.widget<FilterChip>(find.byKey(const Key('interestBooks')));
      expect(booksChip.selected, isTrue);

      final musicChip = tester.widget<FilterChip>(find.byKey(const Key('interestMusic')));
      expect(musicChip.selected, isTrue);

      final hikingChip = tester.widget<FilterChip>(find.byKey(const Key('interestHiking')));
      expect(hikingChip.selected, isTrue);

      // Continue to Step 2 (Identity: Gender & Orientation)
      await tester.tap(find.byKey(const Key('profileContinueButton')));
      await tester.pumpAndSettle();

      // Gender 'Nonbinary' and Orientation 'Queer' should be pre-selected
      expect(find.text('Nonbinary'), findsOneWidget);
      expect(find.text('Queer'), findsOneWidget);

      // Select dating preference to proceed
      await tester.ensureVisible(find.text('Men'));
      await tester.tap(find.text('Men'));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('profileContinueButton')));
      await tester.pumpAndSettle();

      // Step 3: Intentions & Relationship Style
      expect(find.text('Figuring out my goals'), findsOneWidget);
      expect(find.text('Non-monogamy'), findsOneWidget);
    });

    testWidgets('ProfilePreviewScreen save button calls saveProfile with all 5 categories', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      AuthSession.instance.saveSession(
        token: 'active-session-token',
        user: const AuthUser(id: 1, name: 'Sagar', email: 'sagar@example.com'),
      );

      Map<String, dynamic>? savedData;
      final mockClient = MockClient((request) async {
        if (request.url.path == '/api/profile/me' && request.method == 'PUT') {
          savedData = jsonDecode(request.body) as Map<String, dynamic>;
          return http.Response(
            jsonEncode({
              'id': 10,
              'gender': 'Woman',
              'orientation': 'Lesbian',
              'connectionIntention': 'Long-term relationship',
              'relationshipStyle': 'Monogamy',
              'interests': ['Coffee', 'Books', 'Music'],
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }
        return http.Response('Not Found', 404);
      });

      final service = ProfileApiService(client: mockClient);

      await tester.pumpWidget(
        MaterialApp(
          home: ProfilePreviewScreen(
            name: 'Sagar',
            age: '24',
            city: 'Kolkata',
            bio: 'Thoughtful discussions and good coffee every weekend.',
            interests: const ['Coffee', 'Books', 'Music'],
            gender: 'Woman',
            datingIntention: 'Long-term relationship',
            relationshipStyle: 'Monogamy',
            datingPreferences: const ['Women'],
            orientation: 'Lesbian',
            profileApiService: service,
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Tap Save and choose circles
      await tester.ensureVisible(find.byKey(const Key('saveProfileButton')));
      await tester.tap(find.byKey(const Key('saveProfileButton')));
      await tester.pumpAndSettle();

      // Verify the 5 categories were saved to the backend
      expect(savedData, isNotNull);
      expect(savedData!['gender'], 'Woman');
      expect(savedData!['orientation'], 'Lesbian');
      expect(savedData!['connectionIntention'], 'Long-term relationship');
      expect(savedData!['relationshipStyle'], 'Monogamy');
      expect(savedData!['interests'], ['Coffee', 'Books', 'Music']);

      // Verifies navigation proceeded to InterestCirclesScreen
      expect(find.byType(InterestCirclesScreen), findsOneWidget);
    });
  });
}
