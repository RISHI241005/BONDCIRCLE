import 'package:flutter/material.dart';

import '../../../theme/bondcircle_theme.dart';
import '../../discover/presentation/discover_screen.dart';
import '../../blind_bond/presentation/blind_bond_screen.dart';

class InterestCirclesScreen extends StatefulWidget {
  const InterestCirclesScreen({super.key, required this.displayName});

  final String displayName;

  @override
  State<InterestCirclesScreen> createState() => _InterestCirclesScreenState();
}

class _InterestCirclesScreenState extends State<InterestCirclesScreen> {
  static const _categories = [
    'All',
    'Lifestyle',
    'Creative',
    'Active',
    'Social',
  ];
  static const _circles = [
    _Circle(
      name: 'Coffee Explorers',
      description:
          'Find hidden cafés, try new brews and plan relaxed coffee dates.',
      category: 'Lifestyle',
      members: 284,
      icon: Icons.local_cafe_rounded,
      colors: [Color(0xFF7A4B36), Color(0xFFD89A6A)],
    ),
    _Circle(
      name: 'Gaming',
      description:
          'Co-op adventures, favourite games and friendly competition.',
      category: 'Social',
      members: 120,
      icon: Icons.sports_esports_outlined,
      colors: [Color(0xFF4056A1), Color(0xFF7D91DC)],
    ),
    _Circle(
      name: 'Fitness',
      description: 'Share your favourite workouts and active routines.',
      category: 'Active',
      members: 180,
      icon: Icons.fitness_center,
      colors: [Color(0xFF2E7257), Color(0xFF6EBE8D)],
    ),
    _Circle(
      name: 'Readers & Stories',
      description:
          'Books, bookstores and conversations that go beyond small talk.',
      category: 'Creative',
      members: 196,
      icon: Icons.auto_stories_rounded,
      colors: [Color(0xFF6750A4), Color(0xFFAA8BE8)],
    ),
    _Circle(
      name: 'Weekend Trekkers',
      description:
          'Easy trails, sunrise walks and active weekends with good company.',
      category: 'Active',
      members: 342,
      icon: Icons.landscape_rounded,
      colors: [Color(0xFF2E7257), Color(0xFF6EBE8D)],
    ),
    _Circle(
      name: 'Indie Music Club',
      description:
          'Share playlists, discover artists and meet at small live gigs.',
      category: 'Creative',
      members: 228,
      icon: Icons.graphic_eq_rounded,
      colors: [Color(0xFFB43C67), Color(0xFFF087A8)],
    ),
    _Circle(
      name: 'Food Trail',
      description:
          'Street food, brunch spots and honest recommendations around town.',
      category: 'Lifestyle',
      members: 415,
      icon: Icons.restaurant_rounded,
      colors: [Color(0xFFB45C27), Color(0xFFF2A55F)],
    ),
    _Circle(
      name: 'Builders & Dreamers',
      description: 'Startups, side projects and ambitious conversations without pitching.',
      category: 'Social',
      members: 174,
      icon: Icons.rocket_launch_rounded,
      colors: [Color(0xFF4056A1), Color(0xFF7D91DC)],
    ),
  ];

  final _searchController = TextEditingController();
  final Set<String> _joined = {};
  String _category = 'All';
  String _query = '';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  List<_Circle> get _visibleCircles {
    return _circles.where((circle) {
      final categoryMatches =
          _category == 'All' || circle.category == _category;
      final queryMatches =
          _query.isEmpty ||
          circle.name.toLowerCase().contains(_query.toLowerCase()) ||
          circle.description.toLowerCase().contains(_query.toLowerCase());
      return categoryMatches && queryMatches;
    }).toList();
  }

  void _continue() {
    if (_joined.length < 2) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Join at least 2 circles to continue.')),
      );
      return;
    }
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => _CirclesCompleteScreen(
          displayName: widget.displayName,
          circles: _joined.toList(),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final visible = _visibleCircles;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Interest circles'),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 18),
            child: Center(
              child: Text(
                '${_joined.length} joined',
                key: const Key('joinedCount'),
                style: const TextStyle(
                  color: BondCircleColors.primary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            Expanded(
              child: CustomScrollView(
                slivers: [
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(24, 8, 24, 20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Find your people.',
                            style: Theme.of(context).textTheme.displaySmall,
                          ),
                          const SizedBox(height: 10),
                          Text(
                            'Join circles that feel like you. They will shape your matches and meetup ideas.',
                            style: Theme.of(context).textTheme.bodyLarge,
                          ),
                          const SizedBox(height: 22),
                          TextField(
                            key: const Key('circleSearchField'),
                            controller: _searchController,
                            onChanged: (value) =>
                                setState(() => _query = value.trim()),
                            decoration: const InputDecoration(
                              hintText: 'Search circles',
                              prefixIcon: Icon(Icons.search_rounded),
                            ),
                          ),
                          const SizedBox(height: 16),
                          SizedBox(
                            height: 42,
                            child: ListView.separated(
                              scrollDirection: Axis.horizontal,
                              itemCount: _categories.length,
                              separatorBuilder: (_, _) =>
                                  const SizedBox(width: 8),
                              itemBuilder: (context, index) {
                                final item = _categories[index];
                                return ChoiceChip(
                                  key: Key('category$item'),
                                  selected: item == _category,
                                  label: Text(item),
                                  onSelected: (_) =>
                                      setState(() => _category = item),
                                );
                              },
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  if (visible.isEmpty)
                    const SliverFillRemaining(
                      hasScrollBody: false,
                      child: Center(
                        child: Text('No circles match your search.'),
                      ),
                    )
                  else
                    SliverPadding(
                      padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
                      sliver: SliverList.separated(
                        itemCount: visible.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 14),
                        itemBuilder: (context, index) {
                          final circle = visible[index];
                          return _CircleCard(
                            circle: circle,
                            joined: _joined.contains(circle.name),
                            onEnter: () => Navigator.of(context).push(
                              MaterialPageRoute<void>(
                                builder: (_) =>
                                    BlindSessionScreen(circle: circle.name),
                              ),
                            ),
                            onToggle: () => setState(() {
                              _joined.contains(circle.name)
                                  ? _joined.remove(circle.name)
                                  : _joined.add(circle.name);
                              if (_joined.contains(circle.name)) {
                                BlindBondDemo.joined.add(circle.name);
                              } else {
                                BlindBondDemo.joined.remove(circle.name);
                              }
                            }),
                          );
                        },
                      ),
                    ),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.fromLTRB(24, 12, 24, 20),
              decoration: const BoxDecoration(
                color: BondCircleColors.background,
                border: Border(top: BorderSide(color: BondCircleColors.border)),
              ),
              child: FilledButton(
                key: const Key('continueFromCirclesButton'),
                onPressed: _continue,
                child: Text(
                  _joined.isEmpty
                      ? 'Choose at least 2 circles'
                      : 'Continue with ${_joined.length} circles',
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CircleCard extends StatelessWidget {
  const _CircleCard({
    required this.circle,
    required this.joined,
    required this.onToggle,
    required this.onEnter,
  });

  final _Circle circle;
  final bool joined;
  final VoidCallback onToggle;
  final VoidCallback onEnter;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: joined ? BondCircleColors.primary : BondCircleColors.border,
          width: joined ? 1.5 : 1,
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 58,
            height: 58,
            decoration: BoxDecoration(
              gradient: LinearGradient(colors: circle.colors),
              borderRadius: BorderRadius.circular(18),
            ),
            child: Icon(circle.icon, color: Colors.white, size: 28),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  circle.name,
                  style: const TextStyle(
                    color: BondCircleColors.ink,
                    fontSize: 17,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  circle.description,
                  style: const TextStyle(
                    color: BondCircleColors.muted,
                    height: 1.35,
                  ),
                ),
                const SizedBox(height: 11),
                Text(
                  '${circle.members} members • ${circle.category}',
                  style: const TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                if (joined)
                  TextButton.icon(
                    key: Key('circleBlind${circle.name}'),
                    onPressed: onEnter,
                    icon: const Icon(Icons.visibility_off_outlined),
                    label: const Text('Enter Blind Bond'),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 10),
          IconButton.filledTonal(
            key: Key('join${circle.name}'),
            tooltip: joined ? 'Leave circle' : 'Join circle',
            onPressed: onToggle,
            icon: Icon(joined ? Icons.check_rounded : Icons.add_rounded),
          ),
        ],
      ),
    );
  }
}

class _CirclesCompleteScreen extends StatelessWidget {
  const _CirclesCompleteScreen({
    required this.displayName,
    required this.circles,
  });

  final String displayName;
  final List<String> circles;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 90,
                height: 90,
                decoration: const BoxDecoration(
                  color: BondCircleColors.lavender,
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.diversity_1_rounded,
                  color: BondCircleColors.purple,
                  size: 42,
                ),
              ),
              const SizedBox(height: 28),
              Text(
                'Your circles are ready, $displayName.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.displaySmall,
              ),
              const SizedBox(height: 12),
              Text(
                'We will use ${circles.length} shared-interest communities to make discovery feel more relevant.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge,
              ),
              const SizedBox(height: 24),
              Wrap(
                alignment: WrapAlignment.center,
                spacing: 8,
                runSpacing: 8,
                children: circles
                    .map((item) => Chip(label: Text(item)))
                    .toList(),
              ),
              const SizedBox(height: 34),
              FilledButton(
                key: const Key('goToDiscoverButton'),
                onPressed: () => Navigator.of(context).pushReplacement(
                  MaterialPageRoute<void>(
                    settings: const RouteSettings(
                      name: DiscoverScreen.routeName,
                    ),
                    builder: (_) => DiscoverScreen(
                      displayName: displayName,
                      joinedCircles: circles,
                    ),
                  ),
                ),
                child: const Text('Go to Discover'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Circle {
  const _Circle({
    required this.name,
    required this.description,
    required this.category,
    required this.members,
    required this.icon,
    required this.colors,
  });

  final String name;
  final String description;
  final String category;
  final int members;
  final IconData icon;
  final List<Color> colors;
}
