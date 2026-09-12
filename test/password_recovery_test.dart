import 'package:bondcircle/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('recovery validates inputs and returns to sign in', (
    tester,
  ) async {
    await tester.pumpWidget(const BondCircleApp());
    await tester.enterText(
      find.byKey(const Key('emailField')),
      'demo@example.com',
    );
    await tester.ensureVisible(find.byKey(const Key('forgotPasswordButton')));
    await tester.tap(find.byKey(const Key('forgotPasswordButton')));
    await tester.pumpAndSettle();
    Future<void> next() async {
      await tester.ensureVisible(find.byKey(const Key('recoveryContinue')));
      await tester.tap(find.byKey(const Key('recoveryContinue')));
      await tester.pumpAndSettle();
    }

    await tester.enterText(find.byKey(const Key('recoveryEmail')), '');
    await next();
    expect(find.text('Enter a valid email address'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('recoveryEmail')),
      'demo@example.com',
    );
    await next();
    await tester.enterText(find.byKey(const Key('recoveryCode')), '000000');
    await next();
    expect(find.text('Use demo code 123456'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('recoveryCode')), '123456');
    await next();
    await tester.enterText(
      find.byKey(const Key('recoveryPassword')),
      'example123',
    );
    await tester.enterText(
      find.byKey(const Key('recoveryConfirm')),
      'different123',
    );
    await next();
    expect(find.text('Passwords do not match'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('recoveryConfirm')),
      'example123',
    );
    await next();
    expect(find.text('You’re ready to sign in'), findsOneWidget);
    await next();
    expect(find.byKey(const Key('forgotPasswordButton')), findsOneWidget);
  });
}
