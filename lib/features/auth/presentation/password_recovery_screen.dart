import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../theme/bondcircle_theme.dart';

/// UI demonstration only. No credentials are persisted or sent anywhere.
class PasswordRecoveryScreen extends StatefulWidget {
  const PasswordRecoveryScreen({super.key, this.initialEmail = ''});
  final String initialEmail;

  @override
  State<PasswordRecoveryScreen> createState() => _PasswordRecoveryScreenState();
}

class _PasswordRecoveryScreenState extends State<PasswordRecoveryScreen> {
  final _form = GlobalKey<FormState>();
  late final TextEditingController _email;
  final _code = TextEditingController();
  final _password = TextEditingController();
  final _confirm = TextEditingController();
  int _step = 0;
  bool _hide = true;

  @override
  void initState() {
    super.initState();
    _email = TextEditingController(text: widget.initialEmail);
  }

  @override
  void dispose() {
    for (final controller in [_email, _code, _password, _confirm]) {
      controller.dispose();
    }
    super.dispose();
  }

  void _next() {
    if (!_form.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    if (_step == 2) {
      _password.clear();
      _confirm.clear();
    }
    setState(() => _step++);
  }

  @override
  Widget build(BuildContext context) {
    const titles = [
      'Forgot your password?',
      'Check your email',
      'Choose a new password',
      'You’re ready to sign in',
    ];
    const icons = [
      Icons.lock_reset_rounded,
      Icons.mark_email_read_outlined,
      Icons.password_rounded,
      Icons.check_circle_outline_rounded,
    ];
    return Scaffold(
      appBar: AppBar(title: const Text('Account recovery')),
      body: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              LinearProgressIndicator(
                value: (_step + 1) / 4,
                borderRadius: BorderRadius.circular(20),
              ),
              const SizedBox(height: 32),
              Align(
                alignment: Alignment.centerLeft,
                child: Container(
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(
                    color: BondCircleColors.lavender,
                    borderRadius: BorderRadius.circular(22),
                  ),
                  child: Icon(
                    icons[_step],
                    size: 36,
                    color: BondCircleColors.purple,
                  ),
                ),
              ),
              const SizedBox(height: 24),
              Text(
                titles[_step],
                style: Theme.of(context).textTheme.displaySmall,
              ),
              const SizedBox(height: 12),
              Text(switch (_step) {
                0 => 'Enter the email you use for BondCircle.',
                1 =>
                  'For a registered account, a recovery code would be sent to ${_email.text.trim()}.',
                2 =>
                  'Use at least 8 characters. Confirm your new password below.',
                _ => 'Recovery preview complete. Return to Sign In to continue exploring.',
              }, style: Theme.of(context).textTheme.bodyLarge),
              const SizedBox(height: 24),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: BondCircleColors.lavender,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Text(
                  _step == 1
                      ? 'Frontend demo • No email was sent. Use demo code 123456.'
                      : 'Frontend demo • No email is sent and no account password is changed.',
                ),
              ),
              const SizedBox(height: 24),
              Form(
                key: _form,
                child: Column(
                  children: [
                    if (_step == 0)
                      TextFormField(
                        key: const Key('recoveryEmail'),
                        controller: _email,
                        keyboardType: TextInputType.emailAddress,
                        decoration: const InputDecoration(
                          labelText: 'Email address',
                          prefixIcon: Icon(Icons.alternate_email),
                        ),
                        validator: (value) =>
                            RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$')
                                .hasMatch((value ?? '').trim())
                            ? null
                            : 'Enter a valid email address',
                      ),
                    if (_step == 1) ...[
                      TextFormField(
                        key: const Key('recoveryCode'),
                        controller: _code,
                        keyboardType: TextInputType.number,
                        inputFormatters: [
                          FilteringTextInputFormatter.digitsOnly,
                          LengthLimitingTextInputFormatter(6),
                        ],
                        decoration: const InputDecoration(
                          labelText: '6-digit recovery code',
                        ),
                        validator: (value) =>
                            value == '123456' ? null : 'Use demo code 123456',
                      ),
                      TextButton(
                        onPressed: () {
                          _code.clear();
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text(
                                'Demo only: no email sent. Code: 123456',
                              ),
                            ),
                          );
                        },
                        child: const Text('Resend code'),
                      ),
                      TextButton(
                        onPressed: () {
                          _code.clear();
                          setState(() => _step = 0);
                        },
                        child: const Text('Change email address'),
                      ),
                    ],
                    if (_step == 2) ...[
                      TextFormField(
                        key: const Key('recoveryPassword'),
                        controller: _password,
                        obscureText: _hide,
                        enableSuggestions: false,
                        autocorrect: false,
                        decoration: InputDecoration(
                          labelText: 'New password',
                          suffixIcon: IconButton(
                            tooltip: _hide
                                ? 'Show passwords'
                                : 'Hide passwords',
                            onPressed: () => setState(() => _hide = !_hide),
                            icon: Icon(
                              _hide
                                  ? Icons.visibility_outlined
                                  : Icons.visibility_off_outlined,
                            ),
                          ),
                        ),
                        validator: (value) => (value ?? '').trim().length >= 8
                            ? null
                            : 'Use at least 8 characters',
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        key: const Key('recoveryConfirm'),
                        controller: _confirm,
                        obscureText: _hide,
                        enableSuggestions: false,
                        autocorrect: false,
                        decoration: const InputDecoration(
                          labelText: 'Confirm new password',
                        ),
                        validator: (value) =>
                            value == _password.text && (value ?? '').isNotEmpty
                            ? null
                            : 'Passwords do not match',
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 28),
              FilledButton(
                key: const Key('recoveryContinue'),
                onPressed: _step == 3 ? () => Navigator.pop(context) : _next,
                child: Text(
                  [
                    'Continue',
                    'Verify demo code',
                    'Preview password reset',
                    'Back to Sign In',
                  ][_step],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
