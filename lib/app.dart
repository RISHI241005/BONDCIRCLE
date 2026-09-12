import 'package:flutter/material.dart';

import 'features/auth/data/auth_api_service.dart';
import 'features/auth/presentation/auth_screen.dart';
import 'theme/bondcircle_theme.dart';

class BondCircleApp extends StatelessWidget {
  const BondCircleApp({super.key, this.authService});

  final AuthApiService? authService;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'BondCircle',
      debugShowCheckedModeBanner: false,
      theme: BondCircleTheme.light,
      home: AuthScreen(authService: authService),
    );
  }
}
