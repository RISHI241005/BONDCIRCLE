import 'package:flutter/material.dart';

import '../../../theme/bondcircle_theme.dart';
import '../../discover/presentation/discover_screen.dart';
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

  late final AuthApiService _authService =
      widget.authService ?? AuthApiService();

  AuthMode _mode = AuthMode.login;
  bool _obscurePassword = true;
  bool _isLoading = false;
  String? _errorMessage;

  bool get _isSignup => _mode == AuthMode.signup;

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  void _switchMode(AuthMode mode) {
    if (_mode == mode) return;
    setState(() {
      _mode = mode;
      _resetForm();
    });
  }

  Future<void> _continue() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    FocusScope.of(context).unfocus();

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    if (_isSignup) {
      final result = await _authService.signUp(
        name: _nameController.text.trim(),
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

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            result.message ?? 'Account created successfully! Please sign in.',
          ),
          backgroundColor: BondCircleColors.primary,
        ),
      );

      setState(() {
        _mode = AuthMode.login;
        _resetForm();
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
    _obscurePassword = true;
    _errorMessage = null;
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
                      _isSignup
                          ? 'Create a genuine\nconnection.'
                          : 'Welcome back.\nYour circle awaits.',
                      style: Theme.of(context).textTheme.displaySmall,
                    ),
                    const SizedBox(height: 14),
                    Text(
                      _isSignup
                          ? 'Build your profile and meet people through shared interests.'
                          : 'Sign in to continue discovering meaningful matches.',
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                    const SizedBox(height: 30),
                    _ModeSelector(mode: _mode, onChanged: _switchMode),
                    const SizedBox(height: 26),
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
                    TextFormField(
                      key: const Key('emailField'),
                      controller: _emailController,
                      keyboardType: TextInputType.emailAddress,
                      decoration: const InputDecoration(
                        labelText: 'Email address',
                        hintText: 'you@example.com',
                        prefixIcon: Icon(Icons.alternate_email_rounded),
                      ),
                      validator: (value) {
                        final email = (value ?? '').trim();
                        return !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$')
                                .hasMatch(email)
                            ? 'Enter a valid email address'
                            : null;
                      },
                    ),
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
                              ),
                            ),
                          ),
                          child: const Text('Forgot password?'),
                        ),
                      )
                    else
                      const SizedBox(height: 24),
                    FilledButton(
                      key: const Key('continueButton'),
                      onPressed: _isLoading ? null : _continue,
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
