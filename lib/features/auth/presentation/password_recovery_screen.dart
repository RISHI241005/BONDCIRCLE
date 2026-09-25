import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../theme/bondcircle_theme.dart';
import '../data/auth_api_service.dart';

class PasswordRecoveryScreen extends StatefulWidget {
  const PasswordRecoveryScreen({
    super.key,
    this.initialEmail = '',
    this.authService,
  });

  final String initialEmail;
  final AuthApiService? authService;

  @override
  State<PasswordRecoveryScreen> createState() => _PasswordRecoveryScreenState();
}

class _PasswordRecoveryScreenState extends State<PasswordRecoveryScreen> {
  final _form = GlobalKey<FormState>();
  late final TextEditingController _email;
  final _code = TextEditingController();
  final _password = TextEditingController();
  final _confirm = TextEditingController();

  late final AuthApiService _authService =
      widget.authService ?? AuthApiService();

  int _step = 0;
  bool _hide = true;
  bool _isLoading = false;
  String? _resetToken;
  String? _errorMessage;
  String? _statusMessage;

  bool get _hasMinLength => _password.text.length >= 8;
  bool get _hasUppercase => RegExp(r'[A-Z]').hasMatch(_password.text);
  bool get _hasLowercase => RegExp(r'[a-z]').hasMatch(_password.text);
  bool get _hasDigit => RegExp(r'[0-9]').hasMatch(_password.text);
  bool get _hasSpecial => RegExp(r'[!@#$%^&*]').hasMatch(_password.text);

  bool get _isPasswordValid =>
      _hasMinLength && _hasUppercase && _hasLowercase && _hasDigit && _hasSpecial;

  bool get _doPasswordsMatch =>
      _password.text.isNotEmpty && _password.text == _confirm.text;

  bool get _canResetPassword => _isPasswordValid && _doPasswordsMatch;

  @override
  void initState() {
    super.initState();
    _email = TextEditingController(text: widget.initialEmail);
    _password.addListener(_onFieldChanged);
    _confirm.addListener(_onFieldChanged);
  }

  void _onFieldChanged() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    _password.removeListener(_onFieldChanged);
    _confirm.removeListener(_onFieldChanged);
    for (final controller in [_email, _code, _password, _confirm]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _handleContinue() async {
    if (_step == 3) {
      Navigator.pop(context);
      return;
    }

    if (_step == 2) {
      if (!_canResetPassword) return;
      await _resetPassword();
      return;
    }

    if (!_form.currentState!.validate()) return;
    FocusScope.of(context).unfocus();

    if (_step == 0) {
      await _sendCode();
    } else if (_step == 1) {
      await _verifyCode();
    }
  }

  Future<void> _sendCode() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _statusMessage = null;
    });

    final email = _email.text.trim();
    final result = await _authService.sendPasswordResetCode(email: email);

    if (!mounted) return;

    setState(() {
      _isLoading = false;
      if (result.success) {
        _step = 1;
        _statusMessage = result.message ?? 'Verification code sent to your email.';
      } else {
        _errorMessage = result.message ?? 'Failed to send verification code.';
      }
    });
  }

  Future<void> _verifyCode() async {
    final code = _code.text.trim();
    if (code.length != 6) {
      setState(() => _errorMessage = 'Please enter a 6-digit verification code.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _statusMessage = null;
    });

    final email = _email.text.trim();
    final result = await _authService.verifyPasswordResetCode(
      email: email,
      code: code,
    );

    if (!mounted) return;

    setState(() {
      _isLoading = false;
      if (result.success && result.token != null) {
        _resetToken = result.token;
        _step = 2;
        _errorMessage = null;
        _statusMessage = null;
      } else {
        _errorMessage = result.message ?? 'Invalid verification code.';
      }
    });
  }

  Future<void> _resetPassword() async {
    if (_resetToken == null) {
      setState(() {
        _errorMessage = 'Session expired. Please request a new verification code.';
        _step = 0;
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _statusMessage = null;
    });

    final result = await _authService.resetPassword(
      resetToken: _resetToken!,
      newPassword: _password.text,
      confirmPassword: _confirm.text,
    );

    if (!mounted) return;

    setState(() {
      _isLoading = false;
      if (result.success) {
        _password.clear();
        _confirm.clear();
        _resetToken = null;
        _step = 3;
        _errorMessage = null;
        _statusMessage = null;
      } else {
        _errorMessage = result.message ?? 'Failed to reset password.';
      }
    });
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
              Text(
                switch (_step) {
                  0 => 'Enter the email you use for BondCircle.',
                  1 => 'A 6-digit recovery code was sent to ${_email.text.trim()}.',
                  2 => 'Create a strong password that meets all requirements below.',
                  _ => 'Your password has been reset successfully. Return to Sign In to continue exploring.',
                },
                style: Theme.of(context).textTheme.bodyLarge,
              ),
              const SizedBox(height: 24),
              if (_errorMessage != null) ...[
                Container(
                  key: const Key('recoveryErrorMessage'),
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 10,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFDE8E8),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFFF8B4B4)),
                  ),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.error_outline_rounded,
                        color: Color(0xFF9B1C1C),
                        size: 20,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          _errorMessage!,
                          style: const TextStyle(
                            color: Color(0xFF9B1C1C),
                            fontSize: 13.5,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
              ],
              if (_statusMessage != null && _step < 3) ...[
                Container(
                  key: const Key('recoverySuccessMessage'),
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 10,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFFDEF7EC),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFF31C48D)),
                  ),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.check_circle_rounded,
                        color: Color(0xFF0E9F6E),
                        size: 20,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          _statusMessage!,
                          style: const TextStyle(
                            color: Color(0xFF0E9F6E),
                            fontSize: 13.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
              ],
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
                          prefixIcon: Icon(Icons.pin_outlined),
                        ),
                        validator: (value) {
                          final code = (value ?? '').trim();
                          if (code.length != 6 ||
                              !RegExp(r'^[0-9]{6}$').hasMatch(code)) {
                            return 'Enter a valid 6-digit code';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          TextButton(
                            key: const Key('resendCodeButton'),
                            onPressed: _isLoading ? null : _sendCode,
                            child: const Text('Resend code'),
                          ),
                          TextButton(
                            onPressed: _isLoading
                                ? null
                                : () {
                                    setState(() {
                                      _code.clear();
                                      _step = 0;
                                      _errorMessage = null;
                                      _statusMessage = null;
                                    });
                                  },
                            child: const Text('Change email address'),
                          ),
                        ],
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
                          prefixIcon: const Icon(Icons.lock_outline_rounded),
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
                      ),
                      Container(
                        key: const Key('passwordRequirementsContainer'),
                        margin: const EdgeInsets.only(top: 14),
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: const Color(0xFFF9FAFB),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: BondCircleColors.border),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Password Requirements',
                              style: TextStyle(
                                fontWeight: FontWeight.w700,
                                fontSize: 13,
                                color: BondCircleColors.ink,
                              ),
                            ),
                            const SizedBox(height: 10),
                            _RequirementItem(
                              key: const Key('reqMinLength'),
                              label: 'Minimum 8 characters',
                              satisfied: _hasMinLength,
                            ),
                            const SizedBox(height: 6),
                            _RequirementItem(
                              key: const Key('reqUppercase'),
                              label: 'At least 1 uppercase letter (A-Z)',
                              satisfied: _hasUppercase,
                            ),
                            const SizedBox(height: 6),
                            _RequirementItem(
                              key: const Key('reqLowercase'),
                              label: 'At least 1 lowercase letter (a-z)',
                              satisfied: _hasLowercase,
                            ),
                            const SizedBox(height: 6),
                            _RequirementItem(
                              key: const Key('reqNumber'),
                              label: 'At least 1 number (0-9)',
                              satisfied: _hasDigit,
                            ),
                            const SizedBox(height: 6),
                            _RequirementItem(
                              key: const Key('reqSpecial'),
                              label: 'At least 1 special character (!@#\$%^&*)',
                              satisfied: _hasSpecial,
                            ),
                          ],
                        ),
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
                          prefixIcon: Icon(Icons.lock_outline_rounded),
                        ),
                      ),
                      if (_confirm.text.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        Padding(
                          padding: const EdgeInsets.only(left: 4),
                          child: Row(
                            key: const Key('passwordsMatchIndicator'),
                            children: [
                              Icon(
                                _doPasswordsMatch
                                    ? Icons.check_circle_rounded
                                    : Icons.cancel_outlined,
                                size: 16,
                                color: _doPasswordsMatch
                                    ? const Color(0xFF0E9F6E)
                                    : const Color(0xFFE02424),
                              ),
                              const SizedBox(width: 6),
                              Text(
                                _doPasswordsMatch
                                    ? '✓ Passwords match'
                                    : '✗ Passwords do not match',
                                style: TextStyle(
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w600,
                                  color: _doPasswordsMatch
                                      ? const Color(0xFF0E9F6E)
                                      : const Color(0xFFE02424),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 28),
              FilledButton(
                key: const Key('recoveryContinue'),
                onPressed: (_isLoading || (_step == 2 && !_canResetPassword))
                    ? null
                    : _handleContinue,
                child: _isLoading
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : Text(
                        [
                          'Send Code',
                          'Verify Code',
                          'Reset Password',
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

class _RequirementItem extends StatelessWidget {
  const _RequirementItem({
    super.key,
    required this.label,
    required this.satisfied,
  });

  final String label;
  final bool satisfied;

  @override
  Widget build(BuildContext context) {
    final color = satisfied
        ? const Color(0xFF0E9F6E)
        : const Color(0xFF6B7280);
    final icon = satisfied
        ? Icons.check_circle_rounded
        : Icons.cancel_outlined;

    return Row(
      children: [
        Icon(
          icon,
          size: 16,
          color: satisfied
              ? const Color(0xFF0E9F6E)
              : const Color(0xFF9CA3AF),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            satisfied ? '✓ $label' : '✗ $label',
            style: TextStyle(
              fontSize: 12.5,
              fontWeight: satisfied ? FontWeight.w600 : FontWeight.w500,
              color: color,
            ),
          ),
        ),
      ],
    );
  }
}
