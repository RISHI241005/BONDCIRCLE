import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:bondcircle/features/circles/presentation/interest_circles_screen.dart';
import 'package:bondcircle/features/discover/presentation/discover_screen.dart';
import 'package:bondcircle/features/profile/presentation/profile_screen.dart';
import 'package:bondcircle/features/connections/presentation/connections_screen.dart';

void main() {
  for (final tab in ['Profile', 'Chats']) {
    testWidgets(
      '$tab returns to Discover, not onboarding, preserving its state',
      (tester) async {
        final navigator = GlobalKey<NavigatorState>();
        await tester.pumpWidget(
          MaterialApp(
            navigatorKey: navigator,
            home: const InterestCirclesScreen(displayName: 'Sagar'),
          ),
        );
        navigator.currentState!.push(
          MaterialPageRoute<void>(
            settings: const RouteSettings(name: DiscoverScreen.routeName),
            builder: (_) => const DiscoverScreen(
              displayName: 'Sagar',
              joinedCircles: ['Coffee Explorers'],
            ),
          ),
        );
        await tester.pumpAndSettle();
        final original = tester.state(find.byType(DiscoverScreen));
        for (var i = 0; i < 2; i++) {
          await tester.tap(
            find.descendant(
              of: find.byType(NavigationBar),
              matching: find.text(tab),
            ),
          );
          await tester.pumpAndSettle();
          expect(
            find.byType(tab == 'Profile' ? ProfileScreen : ConnectionsScreen),
            findsOneWidget,
          );
          await tester.tap(
            find.descendant(
              of: find.byType(NavigationBar),
              matching: find.text('Discover'),
            ),
          );
          await tester.pumpAndSettle();
          expect(find.byType(DiscoverScreen), findsOneWidget);
          expect(find.byType(InterestCirclesScreen), findsNothing);
          expect(
            identical(tester.state(find.byType(DiscoverScreen)), original),
            isTrue,
          );
        }
      },
    );
  }

  testWidgets('legacy stack without named Discover opens correct destination', (
    tester,
  ) async {
    final navigator = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigator,
        home: const InterestCirclesScreen(displayName: 'Sagar'),
      ),
    );
    navigator.currentState!.push(
      MaterialPageRoute<void>(
        builder: (_) => const ProfileScreen(
          displayName: 'Sagar',
          joinedCircles: ['Gaming'],
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.descendant(
        of: find.byType(NavigationBar),
        matching: find.text('Discover'),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(InterestCirclesScreen), findsNothing);
    final discover = tester.widget<DiscoverScreen>(find.byType(DiscoverScreen));
    expect(discover.displayName, 'Sagar');
    expect(discover.joinedCircles, ['Gaming']);
  });
}
