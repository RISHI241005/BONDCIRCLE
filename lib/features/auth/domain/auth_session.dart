import 'auth_user.dart';

/// Manages client-side authentication session and stored token.
class AuthSession {
  static final AuthSession instance = AuthSession._internal();

  AuthSession._internal();

  String? _token;
  AuthUser? _currentUser;

  String? get token => _token;
  AuthUser? get currentUser => _currentUser;
  bool get isAuthenticated => _token != null && _token!.isNotEmpty;

  void saveSession({required String token, required AuthUser user}) {
    _token = token;
    _currentUser = user;
  }

  void clearSession() {
    _token = null;
    _currentUser = null;
  }
}
