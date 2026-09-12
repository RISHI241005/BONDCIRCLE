import 'package:flutter/material.dart';

import '../../../theme/bondcircle_theme.dart';
import 'profile_setup_screen.dart';
import '../../blind_bond/presentation/blind_bond_screen.dart';
import '../../connections/presentation/connections_screen.dart';
import '../../discover/presentation/discover_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({
    super.key,
    required this.displayName,
    required this.joinedCircles,
  });

  final String displayName;
  final List<String> joinedCircles;

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  bool _discoverable = true;
  bool _showDistance = false;
  bool _readReceipts = true;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Your profile'),
      actions: [
        IconButton(
          key: const Key('profileSettingsButton'),
          tooltip: 'Settings',
          onPressed: _openSettings,
          icon: const Icon(Icons.settings_outlined),
        ),
        const SizedBox(width: 8),
      ],
    ),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        _ProfileHero(name: widget.displayName),
        const SizedBox(height: 18),
        _CompletionCard(onComplete: _editProfile),
        const SizedBox(height: 26),
        _Heading(title: 'About you', action: 'Edit', onTap: _editProfile),
        const SizedBox(height: 10),
        const _InfoCard(),
        const SizedBox(height: 24),
        const _Heading(title: 'Your interests'),
        const SizedBox(height: 10),
        const Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            Chip(
              avatar: Icon(Icons.local_cafe_outlined, size: 17),
              label: Text('Coffee'),
            ),
            Chip(
              avatar: Icon(Icons.menu_book_outlined, size: 17),
              label: Text('Books'),
            ),
            Chip(
              avatar: Icon(Icons.headphones_outlined, size: 17),
              label: Text('Music'),
            ),
            Chip(
              avatar: Icon(Icons.flight_takeoff_rounded, size: 17),
              label: Text('Travel'),
            ),
          ],
        ),
        const SizedBox(height: 24),
        const _Heading(title: 'Connection preferences'),
        const SizedBox(height: 10),
        const _PreferenceCard(),
        const SizedBox(height: 24),
        const _Heading(title: 'Your circles'),
        const SizedBox(height: 10),
        ...widget.joinedCircles.map(
          (circle) => Card(
            margin: const EdgeInsets.only(bottom: 9),
            child: ListTile(
              leading: const CircleAvatar(
                backgroundColor: BondCircleColors.lavender,
                child: Icon(
                  Icons.diversity_2_outlined,
                  color: BondCircleColors.purple,
                ),
              ),
              title: Text(
                circle,
                style: const TextStyle(fontWeight: FontWeight.w800),
              ),
              subtitle: const Text('Active member'),
              trailing: const Icon(Icons.chevron_right_rounded),
            ),
          ),
        ),
        const SizedBox(height: 20),
        OutlinedButton.icon(
          onPressed: () => ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Public profile preview opened.')),
          ),
          icon: const Icon(Icons.visibility_outlined),
          label: const Text('Preview public profile'),
        ),
      ],
    ),
    bottomNavigationBar: NavigationBar(
      selectedIndex: 3,
      onDestinationSelected: (index) {
        if (index == 0) {
          DiscoverScreen.open(
            context,
            displayName: widget.displayName,
            joinedCircles: widget.joinedCircles,
          );
        } else if (index == 1) {
          Navigator.of(context).pushReplacement(
            MaterialPageRoute<void>(
              builder: (_) => BlindBondScreen(
                displayName: widget.displayName,
                joinedCircles: widget.joinedCircles,
              ),
            ),
          );
        } else if (index == 2) {
          Navigator.of(context).pushReplacement(
            MaterialPageRoute<void>(
              builder: (_) => ConnectionsScreen(
                displayName: widget.displayName,
                joinedCircles: widget.joinedCircles,
              ),
            ),
          );
        }
      },
      destinations: const [
        NavigationDestination(
          icon: Icon(Icons.explore_outlined),
          label: 'Discover',
        ),
        NavigationDestination(
          icon: Icon(Icons.visibility_off_outlined),
          label: 'Blind Bond',
        ),
        NavigationDestination(
          icon: Icon(Icons.chat_bubble_outline_rounded),
          label: 'Chats',
        ),
        NavigationDestination(
          icon: Icon(Icons.person_rounded),
          label: 'Profile',
        ),
      ],
    ),
  );

  void _editProfile() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ProfileSetupScreen(initialName: widget.displayName),
      ),
    );
  }

  Future<void> _openSettings() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setSheetState) {
          void update(VoidCallback action) {
            setState(action);
            setSheetState(() {});
          }

          return SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Privacy and preferences',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'These controls are local frontend settings for now.',
                  ),
                  const SizedBox(height: 14),
                  SwitchListTile(
                    key: const Key('discoverableSwitch'),
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Show me in Discover'),
                    subtitle: const Text(
                      'Pause your profile without deleting it.',
                    ),
                    value: _discoverable,
                    onChanged: (value) => update(() => _discoverable = value),
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Show approximate distance'),
                    subtitle: const Text('Never display an exact location.'),
                    value: _showDistance,
                    onChanged: (value) => update(() => _showDistance = value),
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Read receipts'),
                    value: _readReceipts,
                    onChanged: (value) => update(() => _readReceipts = value),
                  ),
                  const Divider(height: 28),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.shield_outlined),
                    title: const Text('Safety centre'),
                    subtitle: const Text(
                      'Blocked accounts, reporting and meetup safety.',
                    ),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: () {},
                  ),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.help_outline_rounded),
                    title: const Text('Help and support'),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: () {},
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _ProfileHero extends StatelessWidget {
  const _ProfileHero({required this.name});
  final String name;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      gradient: const LinearGradient(
        colors: [Color(0xFF6D4DD6), Color(0xFFD94F76)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      ),
      borderRadius: BorderRadius.circular(28),
    ),
    child: Row(
      children: [
        Container(
          width: 86,
          height: 86,
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: .2),
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 2),
          ),
          child: const Icon(
            Icons.person_rounded,
            size: 52,
            color: Colors.white,
          ),
        ),
        const SizedBox(width: 17),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '$name, 22',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 25,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 5),
              const Text(
                'Kolkata • Profile verified',
                style: TextStyle(color: Colors.white70),
              ),
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: Colors.black26,
                  borderRadius: BorderRadius.circular(99),
                ),
                child: const Text(
                  'Looking for meaningful connection',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _CompletionCard extends StatelessWidget {
  const _CompletionCard({required this.onComplete});
  final VoidCallback onComplete;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(17),
    decoration: BoxDecoration(
      color: const Color(0xFFFFF0F4),
      borderRadius: BorderRadius.circular(20),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Row(
          children: [
            Expanded(
              child: Text(
                'Profile strength',
                style: TextStyle(fontWeight: FontWeight.w900),
              ),
            ),
            Text(
              '82%',
              style: TextStyle(
                color: BondCircleColors.primary,
                fontWeight: FontWeight.w900,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        ClipRRect(
          borderRadius: BorderRadius.circular(99),
          child: const LinearProgressIndicator(value: .82, minHeight: 7),
        ),
        const SizedBox(height: 10),
        Row(
          children: [
            const Expanded(child: Text('Add two more photos to stand out.')),
            TextButton(onPressed: onComplete, child: const Text('Complete')),
          ],
        ),
      ],
    ),
  );
}

class _Heading extends StatelessWidget {
  const _Heading({required this.title, this.action, this.onTap});
  final String title;
  final String? action;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) => Row(
    children: [
      Expanded(
        child: Text(
          title,
          style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w900),
        ),
      ),
      if (action != null) TextButton(onPressed: onTap, child: Text(action!)),
    ],
  );
}

class _InfoCard extends StatelessWidget {
  const _InfoCard();
  @override
  Widget build(BuildContext context) => const Card(
    child: Padding(
      padding: EdgeInsets.all(18),
      child: Column(
        children: [
          _ProfileRow(Icons.work_outline_rounded, 'Software student'),
          _ProfileRow(
            Icons.chat_bubble_outline_rounded,
            'Thoughtful conversations, creative weekends and good coffee.',
          ),
          _ProfileRow(
            Icons.visibility_outlined,
            'Gender and lifestyle visible',
            last: true,
          ),
        ],
      ),
    ),
  );
}

class _PreferenceCard extends StatelessWidget {
  const _PreferenceCard();
  @override
  Widget build(BuildContext context) => const Card(
    child: Padding(
      padding: EdgeInsets.all(18),
      child: Column(
        children: [
          _ProfileRow(Icons.favorite_border_rounded, 'Long-term relationship'),
          _ProfileRow(Icons.join_inner_rounded, 'Monogamy'),
          _ProfileRow(Icons.people_alt_outlined, 'Open to women', last: true),
        ],
      ),
    ),
  );
}

class _ProfileRow extends StatelessWidget {
  const _ProfileRow(this.icon, this.text, {this.last = false});
  final IconData icon;
  final String text;
  final bool last;
  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(bottom: last ? 0 : 15),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: BondCircleColors.purple, size: 21),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
        ),
      ],
    ),
  );
}
