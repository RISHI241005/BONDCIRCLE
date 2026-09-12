import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:bondcircle/features/blind_bond/presentation/blind_bond_screen.dart';

Future<void> tap(WidgetTester tester, String key) async {
  await tester.ensureVisible(find.byKey(Key(key)));
  await tester.tap(find.byKey(Key(key)));
  await tester.pumpAndSettle();
}

Future<void> found(
  WidgetTester tester, {
  Duration chat = const Duration(minutes: 15),
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: BlindSessionScreen(circle: 'Coffee Explorers', chatDuration: chat),
    ),
  );
  await tap(tester, 'findBlindBondButton');
  expect(find.text('Finding someone in your circle…'), findsOneWidget);
  await tester.pump(const Duration(seconds: 2));
  await tester.pumpAndSettle();
  expect(find.text('Partner found!'), findsOneWidget);
  expect(find.textContaining('Aarohi'), findsNothing);
}

void main() {
  setUp(() {
    BlindBondDemo.matched.clear();
    BlindBondDemo.blocked.clear();
    BlindBondDemo.joined.clear();
  });

  test('pairing excludes matched and blocked demo members', () {
    BlindBondDemo.matched.add('CoffeeSoul27');
    BlindBondDemo.blocked.add('CoffeeSoul28');
    expect(BlindBondDemo.eligible('Coffee Explorers'), ['CoffeeSoul29']);
  });

  testWidgets('own decline ends session without profile', (tester) async {
    await found(tester);
    await tap(tester, 'startBlindChatButton');
    await tap(tester, 'endBlindInteraction');
    expect(find.byKey(const Key('blindChatField')), findsNothing);
    await tap(tester, 'declineBlindBond');
    expect(
      find.text('No connection made. Both identities stayed private.'),
      findsOneWidget,
    );
    expect(find.byKey(const Key('continueRevealedChat')), findsNothing);
    expect(BlindBondDemo.matched, isEmpty);
  });

  testWidgets('partner decline cannot reveal profiles', (tester) async {
    await found(tester);
    await tap(tester, 'startBlindChatButton');
    await tap(tester, 'demoExpire');
    await tap(tester, 'requestRevealButton');
    await tap(tester, 'simulatePartnerDeclineButton');
    expect(
      find.text(
        'No mutual connection this time. Both identities stayed private.',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('Aarohi'), findsNothing);
    expect(BlindBondDemo.matched, isEmpty);
  });

  testWidgets('voice demo and withdrawal preserve privacy', (tester) async {
    await found(tester);
    await tap(tester, 'startBlindVoiceButton');
    expect(find.text('10:00 remaining'), findsOneWidget);
    expect(find.text('Simulated call • No audio transmitted'), findsOneWidget);
    await tester.tap(find.text('Mute (demo)'));
    await tester.pump();
    expect(find.text('Muted (demo)'), findsOneWidget);
    await tap(tester, 'endBlindInteraction');
    await tap(tester, 'requestRevealButton');
    await tap(tester, 'withdrawReveal');
    expect(
      find.text('You withdrew your choice. Your profile was not revealed.'),
      findsOneWidget,
    );
  });

  testWidgets('timer expiry removes composer and opens decision', (
    tester,
  ) async {
    await found(tester, chat: Duration.zero);
    await tap(tester, 'startBlindChatButton');
    await tester.pump(const Duration(seconds: 1));
    await tester.pumpAndSettle();
    expect(find.text('Conversation finished'), findsOneWidget);
    expect(find.byKey(const Key('blindChatField')), findsNothing);
    expect(find.byKey(const Key('continueRevealedChat')), findsNothing);
  });

  testWidgets('empty pool is recoverable', (tester) async {
    BlindBondDemo.matched.addAll(BlindBondDemo.eligible('Coffee Explorers'));
    await tester.pumpWidget(
      const MaterialApp(home: BlindSessionScreen(circle: 'Coffee Explorers')),
    );
    await tap(tester, 'findBlindBondButton');
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();
    expect(find.text('No new partners right now'), findsOneWidget);
    expect(find.byKey(const Key('returnToCircles')), findsOneWidget);
  });

  testWidgets('mobile chat supports local messages without overflow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await found(tester);
    await tap(tester, 'startBlindChatButton');
    await tester.enterText(
      find.byKey(const Key('blindChatField')),
      'A good book!',
    );
    await tap(tester, 'sendBlindMessageButton');
    await tester.ensureVisible(find.text('A good book!'));
    expect(find.text('A good book!'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tap(tester, 'endBlindInteraction');
  });
}
