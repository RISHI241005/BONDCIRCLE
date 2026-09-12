import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';

import '../../../theme/bondcircle_theme.dart';
import '../../chat/presentation/chat_screen.dart';

/// Session-only demo data. No accounts, messages or consent leave the device.
class BlindBondDemo {
  static final matched = <String>{};
  static final blocked = <String>{};
  static final joined = <String>{};
  static const circles = [
    'Coffee Explorers',
    'Gaming',
    'Readers & Stories',
    'Fitness',
  ];
  static String alias(String circle, int index) =>
      '${circle.split(' ').first}Soul${27 + index}';
  static List<String> eligible(String circle) => List.generate(
    3,
    (i) => alias(circle, i),
  ).where((id) => !matched.contains(id) && !blocked.contains(id)).toList();
}

class BlindBondScreen extends StatefulWidget {
  const BlindBondScreen({
    super.key,
    required this.displayName,
    required this.joinedCircles,
  });
  final String displayName;
  final List<String> joinedCircles;
  @override
  State<BlindBondScreen> createState() => _BlindBondScreenState();
}

class _BlindBondScreenState extends State<BlindBondScreen> {
  @override
  void initState() {
    super.initState();
    BlindBondDemo.joined.addAll(widget.joinedCircles);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Blind Bond')),
    body: ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const _Hero(
          title: 'A shared interest.\nA fresh connection.',
          subtitle: 'Join a circle. Meet anonymously. Reveal only together.',
          icon: Icons.diversity_1_outlined,
        ),
        const SizedBox(height: 20),
        const Text(
          '01 JOIN  →  02 TALK  →  03 DECIDE',
          style: TextStyle(
            color: BondCircleColors.purple,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 12),
        const _DemoNotice(),
        const SizedBox(height: 20),
        Text(
          'Choose your circle',
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const Text(
          'Coffee, books, gaming or fitness — start with something you love.',
        ),
        const SizedBox(height: 12),
        for (final circle in {
          ...BlindBondDemo.circles,
          ...widget.joinedCircles,
        })
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    circle,
                    style: const TextStyle(
                      fontSize: 19,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    BlindBondDemo.joined.contains(circle)
                        ? 'Joined • Your circle is ready'
                        : 'Meet people who share this interest',
                  ),
                  const SizedBox(height: 12),
                  if (!BlindBondDemo.joined.contains(circle))
                    OutlinedButton.icon(
                      key: Key('blindJoin$circle'),
                      icon: const Icon(Icons.add),
                      label: const Text('Join circle'),
                      onPressed: () =>
                          setState(() => BlindBondDemo.joined.add(circle)),
                    )
                  else
                    FilledButton.icon(
                      key: Key('enterBlind$circle'),
                      icon: const Icon(Icons.visibility_off_outlined),
                      label: const Text('Enter Blind Bond'),
                      onPressed: () async {
                        await Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => BlindSessionScreen(circle: circle),
                          ),
                        );
                        if (mounted) setState(() {});
                      },
                    ),
                ],
              ),
            ),
          ),
      ],
    ),
  );
}

enum _Stage {
  ready,
  searching,
  found,
  chat,
  voice,
  decision,
  waiting,
  revealed,
  ended,
  empty,
}

class BlindSessionScreen extends StatefulWidget {
  const BlindSessionScreen({
    super.key,
    required this.circle,
    this.chatDuration = const Duration(minutes: 15),
    this.voiceDuration = const Duration(minutes: 10),
  });
  final String circle;
  final Duration chatDuration, voiceDuration;
  @override
  State<BlindSessionScreen> createState() => _BlindSessionScreenState();
}

class _BlindSessionScreenState extends State<BlindSessionScreen>
    with WidgetsBindingObserver {
  _Stage _stage = _Stage.ready;
  Timer? _timer, _search;
  DateTime? _deadline;
  int _remaining = 0;
  String _alias = '', _ending = '';
  bool _muted = false, _speaker = false;
  final _input = TextEditingController();
  final _messages = <String>[];
  bool get _active => _stage == _Stage.chat || _stage == _Stage.voice;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _timer?.cancel();
    _search?.cancel();
    _input.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _active) _tick();
  }

  void _find() {
    setState(() => _stage = _Stage.searching);
    _search = Timer(const Duration(seconds: 2), () {
      if (!mounted) return;
      final pool = BlindBondDemo.eligible(widget.circle);
      setState(() {
        if (pool.isEmpty) {
          _stage = _Stage.empty;
          return;
        }
        _alias = pool[Random().nextInt(pool.length)];
        _stage = _Stage.found;
      });
    });
  }

  void _start(bool voice) {
    final duration = voice ? widget.voiceDuration : widget.chatDuration;
    _deadline = DateTime.now().add(duration);
    setState(() {
      _remaining = duration.inSeconds;
      _stage = voice ? _Stage.voice : _Stage.chat;
    });
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
  }

  void _tick() {
    if (!_active || !mounted) return;
    final seconds =
        (_deadline!.difference(DateTime.now()).inMilliseconds / 1000).ceil();
    if (seconds <= 0) {
      _decision();
    } else {
      setState(() => _remaining = seconds);
    }
  }

  void _decision() {
    _timer?.cancel();
    _input.clear();
    FocusScope.of(context).unfocus();
    setState(() => _stage = _Stage.decision);
  }

  void _end(String message) {
    _timer?.cancel();
    _search?.cancel();
    _input.clear();
    _messages.clear();
    setState(() {
      _ending = message;
      _stage = _Stage.ended;
    });
  }

  void _send() {
    _tick();
    if (!_active || _input.text.trim().isEmpty) return;
    final value = _input.text.trim();
    // Basic demo guard only; not a comprehensive identity/content filter.
    if (RegExp(r'@|https?://|www\.|\d{7,}').hasMatch(value)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Keep contact details and links out of this anonymous chat.',
          ),
        ),
      );
      return;
    }
    setState(() => _messages.add(value));
    _input.clear();
  }

  Future<void> _leave() async {
    if (_stage == _Stage.ready ||
        _stage == _Stage.ended ||
        _stage == _Stage.empty ||
        _stage == _Stage.revealed) {
      Navigator.pop(context);
      return;
    }
    final leave = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Leave this Blind Bond?'),
        content: const Text(
          'This interaction will end without revealing either profile.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Stay'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Leave'),
          ),
        ],
      ),
    );
    if (leave == true && mounted) {
      _end('You left the conversation. Both identities stayed private.');
    }
  }

  Widget _button(String key, String label, VoidCallback action) => Padding(
    padding: const EdgeInsets.only(top: 12),
    child: FilledButton(key: Key(key), onPressed: action, child: Text(label)),
  );
  Widget _title(String text) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 18),
    child: Text(text, style: Theme.of(context).textTheme.headlineSmall),
  );
  String get _clock =>
      '${(_remaining ~/ 60).toString().padLeft(2, '0')}:${(_remaining % 60).toString().padLeft(2, '0')} remaining';

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: [
      _Stage.ready,
      _Stage.ended,
      _Stage.empty,
      _Stage.revealed,
    ].contains(_stage),
    onPopInvokedWithResult: (didPop, _) {
      if (!didPop) _leave();
    },
    child: Scaffold(
      appBar: AppBar(
        leading: IconButton(
          onPressed: _leave,
          icon: const Icon(Icons.arrow_back),
        ),
        title: const Text('Blind Bond'),
        actions: [
          if (_active || _stage == _Stage.found || _stage == _Stage.waiting)
            PopupMenuButton<String>(
              onSelected: (value) {
                BlindBondDemo.blocked.add(_alias);
                _end(
                  value == 'report'
                      ? 'Demo report recorded locally. Partner blocked for this app session; no report was sent.'
                      : 'Partner blocked for this app session. Your identity stayed private.',
                );
              },
              itemBuilder: (_) => const [
                PopupMenuItem(value: 'block', child: Text('Block and leave')),
                PopupMenuItem(
                  value: 'report',
                  child: Text('Report and leave (demo)'),
                ),
              ],
            ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Row(
                children: [
                  const Icon(
                    Icons.diversity_1_outlined,
                    size: 18,
                    color: BondCircleColors.purple,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      widget.circle,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ),
                  if (_active)
                    Text(
                      _clock,
                      key: const Key('blindCountdown'),
                      style: const TextStyle(
                        color: BondCircleColors.purple,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                ],
              ),
            ),
            const Padding(padding: EdgeInsets.all(12), child: _DemoNotice()),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: _content(),
                ),
              ),
            ),
            if (_stage == _Stage.chat)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                child: Row(
                  children: [
                    Expanded(
                      child: TextField(
                        key: const Key('blindChatField'),
                        controller: _input,
                        maxLength: 300,
                        decoration: const InputDecoration(
                          hintText: 'Message anonymously…',
                          counterText: '',
                        ),
                        onSubmitted: (_) => _send(),
                      ),
                    ),
                    IconButton.filled(
                      key: const Key('sendBlindMessageButton'),
                      onPressed: _send,
                      icon: const Icon(Icons.send),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    ),
  );

  List<Widget> _content() {
    switch (_stage) {
      case _Stage.ready:
        return [
          const _Hero(
            title: 'Your circle.\nSomeone new.',
            subtitle: 'A little curiosity can start something meaningful.',
            icon: Icons.local_cafe_outlined,
          ),
          _title('How your Blind Bond works'),
          const Text(
            '1. Find an unmatched member of this circle.\n\n2. Talk anonymously: 15-min chat or 10-min voice demo.\n\n3. Decide independently. Only two yeses unlock profiles.',
          ),
          const SizedBox(height: 18),
          const _Privacy(),
          _button('findBlindBondButton', 'Find an anonymous partner', _find),
        ];
      case _Stage.searching:
        return [
          _title('Finding someone in your circle…'),
          const Center(
            child: Icon(Icons.radar, size: 80, color: BondCircleColors.purple),
          ),
          const SizedBox(height: 24),
          const Text(
            'Selecting randomly from eligible demo members. Existing demo matches and blocked partners are excluded.',
          ),
          TextButton(
            onPressed: () {
              _search?.cancel();
              setState(() => _stage = _Stage.ready);
            },
            child: const Text('Cancel search'),
          ),
        ];
      case _Stage.empty:
        return [
          _title('No new partners right now'),
          const Text(
            'All demo partners in this circle are matched or blocked. Try another circle.',
          ),
          _button(
            'returnToCircles',
            'Back to circles',
            () => Navigator.pop(context),
          ),
        ];
      case _Stage.found:
        return [
          _Hero(
            title: 'Partner found!',
            subtitle: 'Say hello to $_alias.',
            icon: Icons.auto_awesome,
          ),
          _title('You have something in common'),
          Wrap(
            spacing: 8,
            children: [
              widget.circle,
              'Travel',
              'Music',
            ].map((s) => Chip(label: Text(s))).toList(),
          ),
          const Text('Shared interests shown here are sample data.'),
          const SizedBox(height: 18),
          const _Privacy(),
          _button(
            'startBlindChatButton',
            'Start chat • 15 minutes',
            () => _start(false),
          ),
          _button(
            'startBlindVoiceButton',
            'Start voice demo • 10 minutes',
            () => _start(true),
          ),
          const SizedBox(height: 10),
          const Text(
            'Voice is a UI simulation. No microphone access, recording or live call.',
          ),
        ];
      case _Stage.chat:
      case _Stage.voice:
        return [
          _title(_alias),
          const _Privacy(),
          const SizedBox(height: 16),
          if (_stage == _Stage.voice) ...[
            const _Hero(
              title: 'Anonymous voice',
              subtitle: 'Simulated call • No audio transmitted',
              icon: Icons.graphic_eq,
            ),
            Wrap(
              alignment: WrapAlignment.center,
              spacing: 12,
              children: [
                FilterChip(
                  label: Text(_muted ? 'Muted (demo)' : 'Mute (demo)'),
                  selected: _muted,
                  onSelected: (v) => setState(() => _muted = v),
                ),
                FilterChip(
                  label: Text(
                    _speaker ? 'Speaker on (demo)' : 'Speaker (demo)',
                  ),
                  selected: _speaker,
                  onSelected: (v) => setState(() => _speaker = v),
                ),
              ],
            ),
          ] else ...[
            const Card(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                  'Icebreaker: What would your perfect slow Sunday look like?',
                ),
              ),
            ),
            const Text(
              'Sample partner: A new café, a good book and no alarms. What about you?',
            ),
            for (final message in _messages)
              Align(
                alignment: Alignment.centerRight,
                child: Card(
                  color: BondCircleColors.lavender,
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Text(message),
                  ),
                ),
              ),
          ],
          _button(
            'endBlindInteraction',
            'End conversation & decide',
            _decision,
          ),
          TextButton(
            key: const Key('demoExpire'),
            onPressed: _decision,
            child: const Text('Demo: jump to time limit'),
          ),
        ];
      case _Stage.decision:
        return [
          const _Hero(
            title: 'Did you feel\na connection?',
            subtitle: 'Your choice is yours. There is no pressure to reveal.',
            icon: Icons.favorite_outline,
          ),
          _title('Conversation finished'),
          const Text(
            'The anonymous session has ended. Your profile remains hidden unless both of you choose to connect.',
          ),
          _button(
            'requestRevealButton',
            'Reveal my profile / Connect',
            () => setState(() => _stage = _Stage.waiting),
          ),
          TextButton(
            key: const Key('declineBlindBond'),
            onPressed: () =>
                _end('No connection made. Both identities stayed private.'),
            child: const Text('Stay anonymous / Not interested'),
          ),
        ];
      case _Stage.waiting:
        return [
          const _Hero(
            title: 'Your profile\nis still private.',
            subtitle: 'Waiting for the other person’s independent decision.',
            icon: Icons.lock_outline,
          ),
          _title('You chose to connect'),
          const Text(
            'One yes is not enough. You can withdraw before a mutual reveal.',
          ),
          const SizedBox(height: 20),
          const Text(
            'PRESENTATION CONTROLS • Simulate the other person',
            style: TextStyle(
              color: BondCircleColors.purple,
              fontWeight: FontWeight.bold,
            ),
          ),
          _button(
            'simulatePartnerConsentButton',
            'Demo: partner chooses Reveal',
            () {
              BlindBondDemo.matched.add(_alias);
              setState(() => _stage = _Stage.revealed);
            },
          ),
          _button(
            'simulatePartnerDeclineButton',
            'Demo: partner declines',
            () => _end(
              'No mutual connection this time. Both identities stayed private.',
            ),
          ),
          TextButton(
            key: const Key('withdrawReveal'),
            onPressed: () => _end(
              'You withdrew your choice. Your profile was not revealed.',
            ),
            child: const Text('Withdraw & stay anonymous'),
          ),
        ];
      case _Stage.revealed:
        return [
          const _Hero(
            title: 'It’s mutual!',
            subtitle:
                'You both chose to connect. Your profiles are now unlocked.',
            icon: Icons.celebration_outlined,
          ),
          _title('$_alias is Aarohi'),
          const Card(
            child: Padding(
              padding: EdgeInsets.all(20),
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 38,
                    child: Icon(Icons.person_outline, size: 40),
                  ),
                  SizedBox(height: 12),
                  Text(
                    'Aarohi, 24',
                    style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                  ),
                  Text(
                    'Demo profile • Coffee lover, reader and weekend explorer.',
                  ),
                ],
              ),
            ),
          ),
          _button(
            'continueRevealedChat',
            'Match & open normal chat',
            () => Navigator.of(context).pushReplacement(
              MaterialPageRoute<void>(
                builder: (_) => ChatScreen(
                  matchName: 'Aarohi',
                  sharedCircle: widget.circle,
                ),
              ),
            ),
          ),
        ];
      case _Stage.ended:
        return [
          _Hero(
            title: 'Private,\njust as promised.',
            subtitle: _ending,
            icon: Icons.shield_outlined,
          ),
          _button(
            'returnToCircles',
            'Back to circles',
            () => Navigator.pop(context),
          ),
        ];
    }
  }
}

class _DemoNotice extends StatelessWidget {
  const _DemoNotice();
  @override
  Widget build(BuildContext context) => const Text(
    'FRONTEND DEMO • Sample partners. No live chat or calls. Data resets when the app restarts.',
    style: TextStyle(fontSize: 12, color: BondCircleColors.muted),
  );
}

class _Privacy extends StatelessWidget {
  const _Privacy();
  @override
  Widget build(BuildContext context) => const Card(
    color: BondCircleColors.lavender,
    child: Padding(
      padding: EdgeInsets.all(14),
      child: Text(
        'Identity hidden • No names, photos or profiles are shown. Please do not share your name, contact details, location or other private information.',
      ),
    ),
  );
}

class _Hero extends StatelessWidget {
  const _Hero({
    required this.title,
    required this.subtitle,
    required this.icon,
  });
  final String title, subtitle;
  final IconData icon;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(24),
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(28),
      gradient: const LinearGradient(
        colors: [Color(0xFF2D1B69), Color(0xFF7547B9)],
      ),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 38, color: const Color(0xFFE6D6FF)),
        const SizedBox(height: 20),
        Text(
          title,
          style: const TextStyle(
            fontSize: 30,
            height: 1.15,
            color: Colors.white,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 12),
        Text(
          subtitle,
          style: const TextStyle(color: Colors.white, height: 1.5),
        ),
      ],
    ),
  );
}
