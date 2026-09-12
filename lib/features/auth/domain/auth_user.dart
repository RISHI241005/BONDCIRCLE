class AuthUser {
  final int id;
  final String name;
  final String email;

  const AuthUser({
    required this.id,
    required this.name,
    required this.email,
  });

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    return AuthUser(
      id: json['id'] is int ? json['id'] as int : int.parse(json['id'].toString()),
      name: json['name'] as String? ?? '',
      email: json['email'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'email': email,
  };
}

class AuthResult {
  final bool success;
  final String? token;
  final AuthUser? user;
  final String? message;

  const AuthResult({
    required this.success,
    this.token,
    this.user,
    this.message,
  });

  factory AuthResult.success({String? token, AuthUser? user, String? message}) {
    return AuthResult(
      success: true,
      token: token,
      user: user,
      message: message,
    );
  }

  factory AuthResult.failure(String message) {
    return AuthResult(
      success: false,
      message: message,
    );
  }
}
