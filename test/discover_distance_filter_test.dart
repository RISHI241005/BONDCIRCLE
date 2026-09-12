import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:bondcircle/features/discover/presentation/discover_screen.dart';

void main() {
  testWidgets('distance filter supports up to 100 km and filters profiles', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: DiscoverScreen(displayName: 'Sagar', joinedCircles: ['Coffee Explorers']),
    ));

    expect(find.textContaining('92 km away'), findsNothing);
    await tester.tap(find.byKey(const Key('discoverFilterButton')));
    await tester.pumpAndSettle();
    expect(find.text('High compatibility only'), findsNothing);
    expect(find.text('Up to 100 kilometers away'), findsOneWidget);

    await tester.drag(find.byKey(const Key('distanceFilterSlider')), const Offset(-245, 0));
    await tester.pump();
    expect(find.textContaining('kilometers away'), findsOneWidget);
    await tester.tap(find.byKey(const Key('applyDiscoverFiltersButton')));
    await tester.pumpAndSettle();

    expect(find.textContaining('Showing people up to'), findsOneWidget);
    expect(find.textContaining('km away'), findsOneWidget);
  });

  testWidgets('advanced filters expose verification and language choices', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: DiscoverScreen(displayName: 'Sagar', joinedCircles: ['Coffee Explorers']),
    ));
    await tester.tap(find.byKey(const Key('discoverFilterButton')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Advanced filters'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('verifiedOnlySwitch')), findsOneWidget);
    expect(find.byKey(const Key('languageFilterEnglish')), findsOneWidget);
    await tester.tap(find.byKey(const Key('verifiedOnlySwitch')));
    await tester.tap(find.byKey(const Key('languageFilterBengali')));
    await tester.tap(find.byKey(const Key('applyDiscoverFiltersButton')));
    await tester.pumpAndSettle();

    expect(find.textContaining('6 km away'), findsOneWidget);
  });
}
