import 'package:flutter/material.dart';

import '../../../theme/bondcircle_theme.dart';
import '../../vibe_check/presentation/vibe_check_screen.dart';
import '../../blind_bond/presentation/blind_bond_screen.dart';
import '../../connections/presentation/connections_screen.dart';
import '../../profile/presentation/profile_screen.dart';

class DiscoverScreen extends StatefulWidget {
  static const routeName = '/discover';

  /// Return to the existing Discover page, not the onboarding stack root.
  static void open(
    BuildContext context, {
    required String displayName,
    required List<String> joinedCircles,
  }) {
    final navigator = Navigator.of(context);
    var found = false;
    navigator.popUntil((route) {
      found = route.settings.name == routeName;
      return found || route.isFirst;
    });
    if (!found) {
      navigator.pushReplacement(
        MaterialPageRoute<void>(
          settings: const RouteSettings(name: routeName),
          builder: (_) => DiscoverScreen(
            displayName: displayName,
            joinedCircles: joinedCircles,
          ),
        ),
      );
    }
  }

  const DiscoverScreen({
    super.key,
    required this.displayName,
    required this.joinedCircles,
  });

  final String displayName;
  final List<String> joinedCircles;

  @override
  State<DiscoverScreen> createState() => _DiscoverScreenState();
}

class _DiscoverScreenState extends State<DiscoverScreen> {
  static const _profiles = [
    _Profile(
      name: 'Aarohi',
      age: 22,
      city: 'Kolkata',
      profession: 'Design student',
      bio: 'Coffee walks, thoughtful conversations and finding beautiful corners of the city.',
      match: 91,
      distanceKm: 6,
      verified: true,
      languages: ['English', 'Hindi', 'Bengali'],
      sharedCircle: 'Coffee Explorers',
      interests: ['Coffee', 'Design', 'Indie music'],
      gradient: [Color(0xFFE9A8B9), Color(0xFF9E6DD7)],
      icon: Icons.palette_outlined,
    ),
    _Profile(
      name: 'Meera',
      age: 23,
      city: 'Kolkata',
      profession: 'Content writer',
      bio: 'Usually carrying a novel. Always ready for bookstores, brunch and unplanned stories.',
      match: 87,
      distanceKm: 38,
      verified: true,
      languages: ['English', 'Hindi'],
      sharedCircle: 'Readers & Stories',
      interests: ['Books', 'Brunch', 'Travel'],
      gradient: [Color(0xFFF0B47B), Color(0xFFD76C88)],
      icon: Icons.auto_stories_outlined,
    ),
    _Profile(
      name: 'Ishita',
      age: 24,
      city: 'Howrah',
      profession: 'Software engineer',
      bio: 'Building apps on weekdays and chasing sunrise trails whenever the weekend appears.',
      match: 82,
      distanceKm: 92,
      verified: false,
      languages: ['English', 'Bengali'],
      sharedCircle: 'Weekend Trekkers',
      interests: ['Trekking', 'Tech', 'Photography'],
      gradient: [Color(0xFF63B79A), Color(0xFF5477C7)],
      icon: Icons.landscape_outlined,
    ),
  ];

  int _index = 0;
  var _filters = const _DiscoverFilterSettings();
  int _liked = 0;
  int _passed = 0;

  List<_Profile> get _visibleProfiles {
    final nearby = _profiles.where((profile) =>
        profile.distanceKm <= _filters.distanceKm &&
        profile.age >= _filters.ageRange.start &&
        profile.age <= _filters.ageRange.end &&
        (!_filters.verifiedOnly || profile.verified));
    final strict = nearby.where((profile) =>
        (_filters.interests.isEmpty ||
            profile.interests.any(_filters.interests.contains)) &&
        (_filters.languages.isEmpty ||
            profile.languages.any(_filters.languages.contains))).toList();
    if (strict.isNotEmpty || !_filters.showOthersIfEmpty) return strict;
    return nearby.toList();
  }

  _Profile? get _profile => _visibleProfiles.isEmpty
      ? null
      : _visibleProfiles[_index % _visibleProfiles.length];

  void _advance({required bool liked}) {
    final current = _profile;
    if (current == null) return;
    setState(() {
      liked ? _liked++ : _passed++;
      _index = (_index + 1) % _visibleProfiles.length;
    });
    if (liked) _showMatch(current);
  }

  Future<void> _showMatch(_Profile profile) async {
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(
          Icons.favorite_rounded,
          color: BondCircleColors.primary,
          size: 42,
        ),
        title: Text('You matched with ${profile.name}!'),
        content: Text(
          'You both connected through ${profile.sharedCircle}. Start with a Vibe Check before chatting.',
          textAlign: TextAlign.center,
        ),
        actionsAlignment: MainAxisAlignment.center,
        actions: [
          TextButton(
            key: const Key('keepDiscoveringButton'),
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Keep discovering'),
          ),
          FilledButton(
            key: const Key('startVibeCheckButton'),
            onPressed: () {
              Navigator.of(context).pop();
              Navigator.of(this.context).push(
                MaterialPageRoute<void>(
                  builder: (_) => VibeCheckScreen(
                    matchName: profile.name,
                    sharedCircle: profile.sharedCircle,
                  ),
                ),
              );
            },
            child: const Text('Vibe Check'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final profile = _profile;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Discover'),
        actions: [
          IconButton(
            key: const Key('discoverFilterButton'),
            tooltip: 'Discovery filters',
            onPressed: _openFilters,
            icon: const Icon(Icons.tune_rounded),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 6, 24, 14),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'People in your circles',
                          style: Theme.of(context).textTheme.headlineSmall,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Up to ${_filters.distanceKm.round()} km • ages ${_filters.ageRange.start.round()}–${_filters.ageRange.end.round()}',
                          style: const TextStyle(color: BondCircleColors.muted),
                        ),
                      ],
                    ),
                  ),
                  _CountBadge(icon: Icons.favorite_border, value: _liked),
                  const SizedBox(width: 8),
                  _CountBadge(icon: Icons.close_rounded, value: _passed),
                ],
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: profile == null
                    ? const _NoProfilesCard()
                    : AnimatedSwitcher(
                        duration: const Duration(milliseconds: 250),
                        child: _ProfileCard(
                          key: ValueKey('${profile.name}-${_filters.distanceKm.round()}'),
                          profile: profile,
                        ),
                      ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 22),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _ActionButton(
                    key: const Key('passProfileButton'),
                    icon: Icons.close_rounded,
                    color: BondCircleColors.ink,
                    onPressed: profile == null ? null : () => _advance(liked: false),
                  ),
                  const SizedBox(width: 20),
                  _ActionButton(
                    key: const Key('likeProfileButton'),
                    icon: Icons.favorite_rounded,
                    color: BondCircleColors.primary,
                    prominent: true,
                    onPressed: profile == null ? null : () => _advance(liked: true),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: 0,
        onDestinationSelected: (index) {
          if (index == 1) {
            Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => BlindBondScreen(
                  displayName: widget.displayName,
                  joinedCircles: widget.joinedCircles,
                ),
              ),
            );
          } else if (index == 2) {
            Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => ConnectionsScreen(
                  displayName: widget.displayName,
                  joinedCircles: widget.joinedCircles,
                ),
              ),
            );
          } else if (index == 3) {
            Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => ProfileScreen(
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
            selectedIcon: Icon(Icons.explore),
            label: 'Discover',
          ),
          NavigationDestination(
            icon: Icon(Icons.visibility_off_outlined),
            selectedIcon: Icon(Icons.visibility_off),
            label: 'Blind Bond',
          ),
          NavigationDestination(
            icon: Icon(Icons.chat_bubble_outline_rounded),
            selectedIcon: Icon(Icons.chat_bubble_rounded),
            label: 'Chats',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline_rounded),
            selectedIcon: Icon(Icons.person_rounded),
            label: 'Profile',
          ),
        ],
      ),
    );
  }

  Future<void> _openFilters() async {
    final result = await Navigator.of(context).push<_DiscoverFilterSettings>(
      MaterialPageRoute<_DiscoverFilterSettings>(
        builder: (_) => _DiscoverFiltersScreen(initial: _filters),
      ),
    );
    if (result != null && mounted) {
      setState(() {
        _filters = result;
        _index = 0;
      });
    }
  }
}

class _ProfileCard extends StatelessWidget {
  const _ProfileCard({super.key, required this.profile});
  final _Profile profile;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(30),
        border: Border.all(color: BondCircleColors.border),
        boxShadow: const [
          BoxShadow(
            color: Color(0x14000000),
            blurRadius: 24,
            offset: Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        children: [
          Expanded(
            flex: 5,
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: profile.gradient,
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
              child: Stack(
                children: [
                  Center(
                    child: Icon(profile.icon, color: Colors.white70, size: 130),
                  ),
                  Positioned(
                    left: 18,
                    top: 18,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 8,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(99),
                      ),
                      child: Text(
                        '${profile.match}% match',
                        style: const TextStyle(
                          color: BondCircleColors.purple,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          Expanded(
            flex: 4,
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '${profile.name}, ${profile.age}',
                          style: Theme.of(context).textTheme.headlineSmall,
                        ),
                      ),
                      const Icon(
                        Icons.verified_rounded,
                        color: BondCircleColors.primary,
                      ),
                    ],
                  ),
                  const SizedBox(height: 5),
                  Text(
                    '${profile.profession} • ${profile.city} • ${profile.distanceKm} km away',
                    style: const TextStyle(color: BondCircleColors.muted),
                  ),
                  const SizedBox(height: 13),
                  Text(profile.bio, style: const TextStyle(height: 1.4)),
                  const SizedBox(height: 14),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 11,
                      vertical: 8,
                    ),
                    decoration: BoxDecoration(
                      color: BondCircleColors.lavender,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      'Shared circle: ${profile.sharedCircle}',
                      style: const TextStyle(
                        color: BondCircleColors.purple,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 7,
                    runSpacing: 7,
                    children: profile.interests
                        .map(
                          (item) => Chip(
                            label: Text(item),
                            visualDensity: VisualDensity.compact,
                          ),
                        )
                        .toList(),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DiscoverFilterSettings {
  const _DiscoverFilterSettings({
    this.ageRange = const RangeValues(18, 30),
    this.distanceKm = 100,
    this.interests = const {},
    this.languages = const {},
    this.verifiedOnly = false,
    this.showOthersIfEmpty = true,
  });

  final RangeValues ageRange;
  final double distanceKm;
  final Set<String> interests;
  final Set<String> languages;
  final bool verifiedOnly;
  final bool showOthersIfEmpty;
}

class _DiscoverFiltersScreen extends StatefulWidget {
  const _DiscoverFiltersScreen({required this.initial});
  final _DiscoverFilterSettings initial;

  @override
  State<_DiscoverFiltersScreen> createState() => _DiscoverFiltersScreenState();
}

class _DiscoverFiltersScreenState extends State<_DiscoverFiltersScreen> {
  static const _interests = ['Coffee', 'Books', 'Fitness', 'Gaming', 'Music', 'Travel'];
  static const _languages = ['English', 'Hindi', 'Bengali'];
  late RangeValues _ageRange;
  late double _distanceKm;
  late Set<String> _interestsSelected;
  late Set<String> _languagesSelected;
  late bool _verifiedOnly;
  late bool _showOthers;

  @override
  void initState() {
    super.initState();
    _ageRange = widget.initial.ageRange;
    _distanceKm = widget.initial.distanceKm;
    _interestsSelected = {...widget.initial.interests};
    _languagesSelected = {...widget.initial.languages};
    _verifiedOnly = widget.initial.verifiedOnly;
    _showOthers = widget.initial.showOthersIfEmpty;
  }

  void _toggle(Set<String> target, String value) => setState(() =>
      target.contains(value) ? target.remove(value) : target.add(value));

  void _apply() => Navigator.of(context).pop(_DiscoverFilterSettings(
    ageRange: _ageRange,
    distanceKm: _distanceKm,
    interests: _interestsSelected,
    languages: _languagesSelected,
    verifiedOnly: _verifiedOnly,
    showOthersIfEmpty: _showOthers,
  ));

  Widget _sectionCard({required Widget child}) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: Colors.white,
      border: Border.all(color: BondCircleColors.border),
      borderRadius: BorderRadius.circular(22),
    ),
    child: child,
  );

  @override
  Widget build(BuildContext context) => DefaultTabController(
    length: 2,
    child: Scaffold(
      appBar: AppBar(
        leading: IconButton(
          key: const Key('closeDiscoverFiltersButton'),
          onPressed: () => Navigator.of(context).pop(),
          icon: const Icon(Icons.close_rounded),
        ),
        title: const Text('Narrow your search'),
        bottom: const TabBar(tabs: [Tab(text: 'Basic filters'), Tab(text: 'Advanced filters')]),
      ),
      body: TabBarView(children: [_basic(), _advanced()]),
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.fromLTRB(20, 10, 20, 18),
        child: FilledButton(
          key: const Key('applyDiscoverFiltersButton'),
          onPressed: _apply,
          child: const Text('Apply filters'),
        ),
      ),
    ),
  );

  Widget _basic() => ListView(
    padding: const EdgeInsets.fromLTRB(20, 22, 20, 22),
    children: [
      const Text('How old are they?', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
      const SizedBox(height: 10),
      _sectionCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Between ${_ageRange.start.round()} and ${_ageRange.end.round()}', key: const Key('ageFilterLabel'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        RangeSlider(
          key: const Key('ageFilterSlider'),
          values: _ageRange, min: 18, max: 60, divisions: 42,
          labels: RangeLabels('${_ageRange.start.round()}', '${_ageRange.end.round()}'),
          onChanged: (value) => setState(() => _ageRange = value),
        ),
      ])),
      const SizedBox(height: 24),
      const Text('How far away are they?', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
      const SizedBox(height: 10),
      _sectionCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Up to ${_distanceKm.round()} kilometers away', key: const Key('distanceFilterLabel'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        Slider(key: const Key('distanceFilterSlider'), value: _distanceKm, min: 1, max: 100, divisions: 99, label: '${_distanceKm.round()} km', onChanged: (value) => setState(() => _distanceKm = value)),
        const Text('Choose a distance from 1 km to 100 km.'),
      ])),
      const SizedBox(height: 24),
      const Text('Do they share any of your interests?', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
      const SizedBox(height: 10),
      _sectionCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Filter by your interests', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 12),
        Wrap(spacing: 8, runSpacing: 8, children: _interests.map((interest) => FilterChip(
          key: Key('interestFilter$interest'),
          label: Text(interest), selected: _interestsSelected.contains(interest),
          onSelected: (_) => _toggle(_interestsSelected, interest),
        )).toList()),
        const SizedBox(height: 12),
        const Text('We’ll show people who share any selected interest.'),
        const Divider(height: 28),
        SwitchListTile(
          key: const Key('showOthersIfEmptySwitch'), contentPadding: EdgeInsets.zero,
          title: const Text('Show other people if I run out'),
          value: _showOthers, onChanged: (value) => setState(() => _showOthers = value),
        ),
      ])),
    ],
  );

  Widget _advanced() => ListView(
    padding: const EdgeInsets.fromLTRB(20, 22, 20, 22),
    children: [
      const Text('Have they verified themselves?', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
      const SizedBox(height: 10),
      _sectionCard(child: SwitchListTile(
        key: const Key('verifiedOnlySwitch'), contentPadding: EdgeInsets.zero,
        secondary: const Icon(Icons.verified_rounded, color: BondCircleColors.purple),
        title: const Text('Verified only'), subtitle: const Text('Show profiles marked as verified.'),
        value: _verifiedOnly, onChanged: (value) => setState(() => _verifiedOnly = value),
      )),
      const SizedBox(height: 24),
      const Text('Which languages do they know?', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
      const SizedBox(height: 10),
      _sectionCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Select languages', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 12),
        Wrap(spacing: 8, runSpacing: 8, children: _languages.map((language) => FilterChip(
          key: Key('languageFilter$language'), label: Text(language),
          selected: _languagesSelected.contains(language),
          onSelected: (_) => _toggle(_languagesSelected, language),
        )).toList()),
      ])),
      const SizedBox(height: 24),
      const _FilterDemoNotice(),
    ],
  );
}

class _FilterDemoNotice extends StatelessWidget {
  const _FilterDemoNotice();
  @override
  Widget build(BuildContext context) => const Text(
    'Frontend demo: profile distance, verification, language and interests are sample data. Real filtering needs the future backend.',
    style: TextStyle(color: BondCircleColors.muted),
  );
}

class _NoProfilesCard extends StatelessWidget {
  const _NoProfilesCard();

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(28),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(30),
      border: Border.all(color: BondCircleColors.border),
    ),
    child: const Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(Icons.location_off_outlined, size: 54, color: BondCircleColors.purple),
        SizedBox(height: 16),
        Text('No people in this distance yet', textAlign: TextAlign.center, style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
        SizedBox(height: 8),
        Text('Try increasing your distance up to 100 km to see more people in your circles.', textAlign: TextAlign.center),
      ],
    ),
  );
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    super.key,
    required this.icon,
    required this.color,
    required this.onPressed,
    this.prominent = false,
  });

  final IconData icon;
  final Color color;
  final VoidCallback? onPressed;
  final bool prominent;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: prominent ? 72 : 62,
      height: prominent ? 72 : 62,
      child: IconButton.filled(
        onPressed: onPressed,
        style: IconButton.styleFrom(
          backgroundColor: prominent ? color : Colors.white,
          foregroundColor: prominent ? Colors.white : color,
          side: prominent
              ? null
              : const BorderSide(color: BondCircleColors.border),
          elevation: prominent ? 5 : 1,
          shadowColor: BondCircleColors.primary.withValues(alpha: 0.35),
        ),
        icon: Icon(icon, size: prominent ? 32 : 28),
      ),
    );
  }
}

class _CountBadge extends StatelessWidget {
  const _CountBadge({required this.icon, required this.value});
  final IconData icon;
  final int value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(99),
        border: Border.all(color: BondCircleColors.border),
      ),
      child: Row(
        children: [
          Icon(icon, size: 15),
          const SizedBox(width: 4),
          Text('$value', style: const TextStyle(fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

class _Profile {
  const _Profile({
    required this.name,
    required this.age,
    required this.city,
    required this.profession,
    required this.bio,
    required this.match,
    required this.distanceKm,
    required this.verified,
    required this.languages,
    required this.sharedCircle,
    required this.interests,
    required this.gradient,
    required this.icon,
  });

  final String name;
  final int age;
  final String city;
  final String profession;
  final String bio;
  final int match;
  final int distanceKm;
  final bool verified;
  final List<String> languages;
  final String sharedCircle;
  final List<String> interests;
  final List<Color> gradient;
  final IconData icon;
}
