import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../theme/bondcircle_theme.dart';
import '../../discover/presentation/discover_screen.dart';
import '../../profile/presentation/profile_setup_screen.dart';
import '../data/auth_api_service.dart';
import 'password_recovery_screen.dart';

enum AuthMode { login, signup }

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key, this.authService});

  final AuthApiService? authService;

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmController = TextEditingController();
  final _verificationCodeController = TextEditingController();

  late final AuthApiService _authService =
      widget.authService ?? AuthApiService();

  AuthMode _mode = AuthMode.login;
  bool _obscurePassword = true;
  bool _isLoading = false;
  bool _isSendingCode = false;
  bool _isVerifyingCode = false;
  bool _codeSent = false;
  bool _emailVerified = false;
  bool _accountCreated = false;
  String? _createdUserName;
  String? _errorMessage;
  String? _successMessage;

  bool get _isSignup => _mode == AuthMode.signup;

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _confirmController.dispose();
    _verificationCodeController.dispose();
    super.dispose();
  }

  void _switchMode(AuthMode mode) {
    if (_mode == mode) return;
    setState(() {
      _mode = mode;
      _resetForm();
    });
  }

  Future<void> _sendVerificationCode() async {
    final email = _emailController.text.trim();
    if (email.isEmpty) {
      setState(() => _errorMessage = 'Please enter your email address first');
      return;
    }
    if (!RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(email)) {
      setState(() => _errorMessage = 'Enter a valid email address');
      return;
    }

    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _isSendingCode = true;
      _errorMessage = null;
      _successMessage = null;
    });

    final result = await _authService.sendVerificationCode(email: email);

    if (!mounted) return;

    setState(() {
      _isSendingCode = false;
      if (result.success) {
        _codeSent = true;
        _successMessage = result.message ?? 'Verification code sent to your email.';
      } else {
        _errorMessage = result.message ?? 'Failed to send verification code.';
      }
    });
  }

  Future<void> _verifyCode() async {
    final code = _verificationCodeController.text.trim();
    if (code.length != 6 || !RegExp(r'^[0-9]{6}$').hasMatch(code)) {
      setState(() => _errorMessage = 'Please enter a valid 6-digit verification code.');
      return;
    }

    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _isVerifyingCode = true;
      _errorMessage = null;
      _successMessage = null;
    });

    final result = await _authService.verifyCode(
      email: _emailController.text.trim(),
      code: code,
    );

    if (!mounted) return;

    setState(() {
      _isVerifyingCode = false;
      if (result.success) {
        _emailVerified = true;
        _successMessage = 'Email verified successfully';
      } else {
        _errorMessage = result.message ?? 'Invalid verification code.';
      }
    });
  }

  Future<void> _continue() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    FocusManager.instance.primaryFocus?.unfocus();

    if (_isSignup && !_emailVerified) {
      setState(() => _errorMessage = 'Please verify your email address before creating an account.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _successMessage = null;
    });

    if (_isSignup) {
      final name = _nameController.text.trim();
      final result = await _authService.signUp(
        name: name,
        email: _emailController.text.trim(),
        password: _passwordController.text,
      );

      if (!mounted) return;

      setState(() => _isLoading = false);

      if (!result.success) {
        final message = result.message ?? 'Sign up failed.';
        setState(() => _errorMessage = message);
        return;
      }

      setState(() {
        _accountCreated = true;
        _createdUserName = result.user?.name ?? name;
      });
      return;
    }

    // Normal Sign-In: Email + Password directly authenticates against backend
    final result = await _authService.signIn(
      email: _emailController.text.trim(),
      password: _passwordController.text,
    );

    if (!mounted) return;

    setState(() => _isLoading = false);

    if (!result.success) {
      final message = result.message ?? 'Invalid email or password.';
      setState(() => _errorMessage = message);
      return;
    }

    Navigator.of(context).pushReplacement(
      MaterialPageRoute<void>(
        settings: const RouteSettings(
          name: DiscoverScreen.routeName,
        ),
        builder: (_) => DiscoverScreen(
          displayName: result.user?.name ?? 'Sagar',
          joinedCircles: const [
            'Coffee Explorers',
            'Readers & Stories',
            'Weekend Trekkers',
          ],
        ),
      ),
    );
    _passwordController.clear();
    _confirmController.clear();
  }

  void _resetForm() {
    _formKey.currentState?.reset();
    _passwordController.clear();
    _confirmController.clear();
    _verificationCodeController.clear();
    _obscurePassword = true;
    _errorMessage = null;
    _successMessage = null;
    _codeSent = false;
    _emailVerified = false;
    _accountCreated = false;
    _createdUserName = null;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) => SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minHeight: constraints.maxHeight > 56
                    ? constraints.maxHeight - 56
                    : 0,
              ),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const _BrandMark(),
                    const SizedBox(height: 44),
                    Text(
                      _accountCreated
                          ? 'Account created\nsuccessfully.'
                          : (_isSignup
                              ? 'Create a genuine\nconnection.'
                              : 'Welcome back.\nYour circle awaits.'),
                      style: Theme.of(context).textTheme.displaySmall,
                    ),
                    const SizedBox(height: 14),
                    Text(
                      _accountCreated
                          ? 'Your email has been verified. Let\'s set up your profile and circles.'
                          : (_isSignup
                              ? 'Build your profile and meet people through shared interests.'
                              : 'Sign in to continue discovering meaningful matches.'),
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                    const SizedBox(height: 30),
                    if (!_accountCreated) ...[
                      _ModeSelector(mode: _mode, onChanged: _switchMode),
                      const SizedBox(height: 26),
                    ],
                    if (_errorMessage != null) ...[
                      Container(
                        key: const Key('authErrorMessage'),
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
                    if (_successMessage != null && !_accountCreated) ...[
                      Container(
                        key: const Key('authSuccessMessage'),
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
                                _successMessage!,
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
                    if (_accountCreated) ...[
                      Container(
                        key: const Key('accountCreatedView'),
                        width: double.infinity,
                        padding: const EdgeInsets.all(28),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(24),
                          border: Border.all(color: BondCircleColors.border),
                          boxShadow: const [
                            BoxShadow(
                              color: Color(0x0A000000),
                              blurRadius: 16,
                              offset: Offset(0, 6),
                            ),
                          ],
                        ),
                        child: Column(
                          children: [
                            Container(
                              width: 68,
                              height: 68,
                              decoration: const BoxDecoration(
                                color: Color(0xFFDEF7EC),
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(
                                Icons.check_circle_rounded,
                                color: Color(0xFF0E9F6E),
                                size: 44,
                              ),
                            ),
                            const SizedBox(height: 20),
                            const Text(
                              'Account created successfully',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                color: BondCircleColors.ink,
                                fontSize: 22,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                            const SizedBox(height: 10),
                            const Text(
                              'Your email is verified and your BondCircle account is active. Tap Continue to set up your profile.',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                color: BondCircleColors.muted,
                                fontSize: 14,
                              ),
                            ),
                            const SizedBox(height: 28),
                            SizedBox(
                              width: double.infinity,
                              child: FilledButton(
                                key: const Key('continueToProfileButton'),
                                onPressed: () {
                                  Navigator.of(context).pushReplacement(
                                    MaterialPageRoute<void>(
                                      builder: (_) => ProfileSetupScreen(
                                        initialName: _createdUserName ??
                                            _nameController.text.trim(),
                                      ),
                                    ),
                                  );
                                },
                                child: const Text('Continue'),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 24),
                    ] else ...[
                      if (_isSignup) ...[
                        TextFormField(
                          key: const Key('nameField'),
                          controller: _nameController,
                          textCapitalization: TextCapitalization.words,
                          decoration: const InputDecoration(
                            labelText: 'Full name',
                            hintText: 'Enter your name',
                            prefixIcon: Icon(Icons.person_outline_rounded),
                          ),
                          validator: (value) => (value ?? '').trim().length < 2
                              ? 'Please enter your name'
                              : null,
                        ),
                        const SizedBox(height: 16),
                      ],
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(
                            child: TextFormField(
                              key: const Key('emailField'),
                              controller: _emailController,
                              keyboardType: TextInputType.emailAddress,
                              readOnly: _emailVerified,
                              decoration: InputDecoration(
                                labelText: 'Email address',
                                hintText: 'you@example.com',
                                prefixIcon:
                                    const Icon(Icons.alternate_email_rounded),
                                suffixIcon: _emailVerified
                                    ? const Icon(
                                        Icons.check_circle_rounded,
                                        color: Color(0xFF0E9F6E),
                                        size: 20,
                                      )
                                    : null,
                              ),
                              validator: (value) {
                                final email = (value ?? '').trim();
                                return !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$')
                                        .hasMatch(email)
                                    ? 'Enter a valid email address'
                                    : null;
                              },
                              onChanged: (_) {
                                if (_emailVerified || _codeSent) {
                                  setState(() {
                                    _emailVerified = false;
                                    _codeSent = false;
                                    _verificationCodeController.clear();
                                    _successMessage = null;
                                    _errorMessage = null;
                                  });
                                }
                              },
                            ),
                          ),
                          if (_isSignup) ...[
                            const SizedBox(width: 10),
                            SizedBox(
                              height: 56,
                              child: _emailVerified
                                  ? Container(
                                      key: const Key('emailVerifiedBadge'),
                                      padding: const EdgeInsets.symmetric(
                                          horizontal: 14),
                                      decoration: BoxDecoration(
                                        color: const Color(0xFFDEF7EC),
                                        borderRadius:
                                            BorderRadius.circular(16),
                                        border: Border.all(
                                            color: const Color(0xFF31C48D)),
                                      ),
                                      alignment: Alignment.center,
                                      child: const Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          Icon(
                                            Icons.check_circle_rounded,
                                            color: Color(0xFF0E9F6E),
                                            size: 18,
                                          ),
                                          SizedBox(width: 6),
                                          Text(
                                            'Verified ✓',
                                            style: TextStyle(
                                              color: Color(0xFF0E9F6E),
                                              fontWeight: FontWeight.w700,
                                              fontSize: 13,
                                            ),
                                          ),
                                        ],
                                      ),
                                    )
                                  : OutlinedButton(
                                      key: const Key('verifyEmailButton'),
                                      onPressed: _isSendingCode
                                          ? null
                                          : _sendVerificationCode,
                                      style: OutlinedButton.styleFrom(
                                        foregroundColor:
                                            BondCircleColors.purple,
                                        side: const BorderSide(
                                            color: BondCircleColors.border),
                                        padding: const EdgeInsets.symmetric(
                                            horizontal: 16),
                                        shape: RoundedRectangleBorder(
                                          borderRadius:
                                              BorderRadius.circular(16),
                                        ),
                                      ),
                                      child: _isSendingCode
                                          ? const SizedBox(
                                              width: 18,
                                              height: 18,
                                              child: CircularProgressIndicator(
                                                  strokeWidth: 2),
                                            )
                                          : const Text(
                                              'Verify',
                                              style: TextStyle(
                                                  fontWeight: FontWeight.w700),
                                            ),
                                    ),
                            ),
                          ],
                        ],
                      ),
                      if (_isSignup && _codeSent && !_emailVerified) ...[
                        const SizedBox(height: 14),
                        Container(
                          key: const Key('verificationCodeSection'),
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(16),
                            border:
                                Border.all(color: BondCircleColors.border),
                            boxShadow: const [
                              BoxShadow(
                                color: Color(0x0A000000),
                                blurRadius: 10,
                                offset: Offset(0, 4),
                              ),
                            ],
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisAlignment:
                                    MainAxisAlignment.spaceBetween,
                                children: [
                                  const Text(
                                    'Verification Code',
                                    style: TextStyle(
                                      fontWeight: FontWeight.w700,
                                      fontSize: 14,
                                      color: BondCircleColors.ink,
                                    ),
                                  ),
                                  TextButton(
                                    key: const Key('resendCodeButton'),
                                    onPressed: _isSendingCode
                                        ? null
                                        : _sendVerificationCode,
                                    style: TextButton.styleFrom(
                                      padding: EdgeInsets.zero,
                                      minimumSize: Size.zero,
                                      tapTargetSize:
                                          MaterialTapTargetSize.shrinkWrap,
                                    ),
                                    child: const Text(
                                      'Resend',
                                      style: TextStyle(
                                        fontSize: 12.5,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 4),
                              const Text(
                                'Enter the 6-digit code sent to your Gmail',
                                style: TextStyle(
                                    color: BondCircleColors.muted,
                                    fontSize: 12),
                              ),
                              const SizedBox(height: 12),
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Expanded(
                                    child: TextFormField(
                                      key: const Key(
                                          'verificationCodeField'),
                                      controller:
                                          _verificationCodeController,
                                      keyboardType:
                                          TextInputType.number,
                                      inputFormatters: [
                                        LengthLimitingTextInputFormatter(6),
                                        FilteringTextInputFormatter.digitsOnly,
                                      ],
                                      decoration: const InputDecoration(
                                        hintText: '6 digit code',
                                        prefixIcon:
                                            Icon(Icons.pin_outlined),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 10),
                                  SizedBox(
                                    height: 56,
                                    child: FilledButton(
                                      key: const Key('verifyCodeButton'),
                                      onPressed: _isVerifyingCode
                                          ? null
                                          : _verifyCode,
                                      style: FilledButton.styleFrom(
                                        backgroundColor:
                                            BondCircleColors.primary,
                                        minimumSize: const Size(0, 56),
                                        padding: const EdgeInsets.symmetric(
                                            horizontal: 20),
                                        shape: RoundedRectangleBorder(
                                          borderRadius:
                                              BorderRadius.circular(16),
                                        ),
                                      ),
                                      child: _isVerifyingCode
                                          ? const SizedBox(
                                              width: 18,
                                              height: 18,
                                              child:
                                                  CircularProgressIndicator(
                                                strokeWidth: 2,
                                                color: Colors.white,
                                              ),
                                            )
                                          : const Text(
                                              'Verify',
                                              style: TextStyle(
                                                  fontWeight:
                                                      FontWeight.w700),
                                            ),
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                      const SizedBox(height: 16),
                      TextFormField(
                        key: const Key('passwordField'),
                        controller: _passwordController,
                        obscureText: _obscurePassword,
                        decoration: InputDecoration(
                          labelText: _isSignup
                              ? 'Create password'
                              : 'Enter your password',
                          hintText: 'At least 8 characters',
                          prefixIcon: const Icon(Icons.lock_outline_rounded),
                          suffixIcon: IconButton(
                            tooltip: _obscurePassword
                                ? 'Show password'
                                : 'Hide password',
                            onPressed: () => setState(
                              () => _obscurePassword = !_obscurePassword,
                            ),
                            icon: Icon(
                              _obscurePassword
                                  ? Icons.visibility_outlined
                                  : Icons.visibility_off_outlined,
                            ),
                          ),
                        ),
                        validator: (value) => (value ?? '').trim().length < 8
                            ? 'Password must have at least 8 characters'
                            : null,
                      ),
                      if (_isSignup) ...[
                        const SizedBox(height: 16),
                        TextFormField(
                          key: const Key('confirmPasswordField'),
                          controller: _confirmController,
                          obscureText: _obscurePassword,
                          decoration: const InputDecoration(
                            labelText: 'Confirm password',
                          ),
                          validator: (value) =>
                              value == _passwordController.text &&
                                      (value ?? '').isNotEmpty
                                  ? null
                                  : 'Passwords do not match',
                        ),
                      ],
                      if (!_isSignup)
                        Align(
                          alignment: Alignment.centerRight,
                          child: TextButton(
                            key: const Key('forgotPasswordButton'),
                            onPressed: () => Navigator.of(context).push(
                              MaterialPageRoute<void>(
                                builder: (_) => PasswordRecoveryScreen(
                                  initialEmail: _emailController.text.trim(),
                                  authService: _authService,
                                ),
                              ),
                            ),
                            child: const Text('Forgot password?'),
                          ),
                        )
                      else ...[
                        if (!_emailVerified)
                          const Padding(
                            padding: EdgeInsets.only(top: 8, bottom: 4),
                            child: Text(
                              'Click "Verify" beside your email to enable account creation.',
                              style: TextStyle(
                                color: BondCircleColors.muted,
                                fontSize: 12.5,
                              ),
                            ),
                          ),
                        const SizedBox(height: 18),
                      ],
                      FilledButton(
                        key: const Key('continueButton'),
                        onPressed: (_isLoading || (_isSignup && !_emailVerified))
                            ? null
                            : _continue,
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
                                _isSignup ? 'Create account' : 'Sign in',
                              ),
                      ),
                    ],
                    const SizedBox(height: 22),
                    const _TrustNote(),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _BrandMark extends StatelessWidget {
  const _BrandMark();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: BondCircleColors.primary,
            borderRadius: BorderRadius.circular(15),
          ),
          child: const Icon(
            Icons.favorite_rounded,
            color: Colors.white,
            size: 24,
          ),
        ),
        const SizedBox(width: 12),
        const Text(
          'BondCircle',
          style: TextStyle(
            color: BondCircleColors.ink,
            fontSize: 21,
            fontWeight: FontWeight.w800,
            letterSpacing: -0.5,
          ),
        ),
      ],
    );
  }
}

class _ModeSelector extends StatelessWidget {
  const _ModeSelector({required this.mode, required this.onChanged});

  final AuthMode mode;
  final ValueChanged<AuthMode> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(5),
      decoration: BoxDecoration(
        color: BondCircleColors.lavender,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: AuthMode.values.map((item) {
          final selected = item == mode;
          return Expanded(
            child: InkWell(
              key: Key('${item.name}Tab'),
              onTap: () => onChanged(item),
              borderRadius: BorderRadius.circular(14),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                padding: const EdgeInsets.symmetric(vertical: 13),
                decoration: BoxDecoration(
                  color: selected ? Colors.white : Colors.transparent,
                  borderRadius: BorderRadius.circular(14),
                  boxShadow: selected
                      ? const [
                          BoxShadow(
                            color: Color(0x16000000),
                            blurRadius: 12,
                            offset: Offset(0, 4),
                          ),
                        ]
                      : null,
                ),
                child: Text(
                  item == AuthMode.login ? 'Sign in' : 'Sign up',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: selected
                        ? BondCircleColors.ink
                        : BondCircleColors.muted,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}

class _TrustNote extends StatelessWidget {
  const _TrustNote();

  @override
  Widget build(BuildContext context) {
    return const Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(
          Icons.verified_user_outlined,
          size: 18,
          color: BondCircleColors.muted,
        ),
        SizedBox(width: 8),
        Flexible(
          child: Text(
            'Trust-first profiles • Shared interests • Safer meetups',
            textAlign: TextAlign.center,
            style: TextStyle(color: BondCircleColors.muted, fontSize: 12.5),
          ),
        ),
      ],
    );
  }
}
