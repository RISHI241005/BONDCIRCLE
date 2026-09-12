import 'package:flutter/material.dart';

import '../../../theme/bondcircle_theme.dart';
import '../../blind_bond/presentation/blind_bond_screen.dart';
import '../../chat/presentation/chat_screen.dart';
import '../../profile/presentation/profile_screen.dart';
import '../../discover/presentation/discover_screen.dart';

class ConnectionsScreen extends StatefulWidget {
  const ConnectionsScreen({
    super.key,
    required this.displayName,
    required this.joinedCircles,
  });

  final String displayName;
  final List<String> joinedCircles;

  @override
  State<ConnectionsScreen> createState() => _ConnectionsScreenState();
}

class _ConnectionsScreenState extends State<ConnectionsScreen> {
  String _query = '';

  static const _connections = [
    _Connection(
      name: 'Aarohi',
      circle: 'Coffee Explorers',
      preview: 'A good café and an easy conversation sounds perfect.',
      time: '5:32 PM',
      unread: 2,
      color: Color(0xFFE9A8B9),
    ),
    _Connection(
      name: 'Meera',
      circle: 'Readers & Stories',
      preview: 'I just added that book to my list!',
      time: 'Yesterday',
      unread: 0,
      color: Color(0xFFF0B47B),
    ),
    _Connection(
      name: 'Ishita',
      circle: 'Weekend Trekkers',
      preview: 'A sunrise trail could be fun sometime.',
      time: 'Mon',
      unread: 0,
      color: Color(0xFF63B79A),
    ),
  ];

  List<_Connection> get _visible => _connections.where((item) {
    final query = _query.toLowerCase();
    return item.name.toLowerCase().contains(query) ||
        item.circle.toLowerCase().contains(query);
  }).toList();

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Connections')),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
      children: [
        Text(
          'Your conversations',
          style: Theme.of(context).textTheme.displaySmall,
        ),
        const SizedBox(height: 7),
        const Text(
          'Continue with people you connected with through shared circles.',
        ),
        const SizedBox(height: 18),
        TextField(
          key: const Key('connectionSearchField'),
          onChanged: (value) => setState(() => _query = value),
          decoration: const InputDecoration(
            prefixIcon: Icon(Icons.search_rounded),
            hintText: 'Search connections or circles',
          ),
        ),
        const SizedBox(height: 22),
        const Text(
          'Blind Bond',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
        ),
        const SizedBox(height: 10),
        Card(
          color: BondCircleColors.lavender,
          child: ListTile(
            key: const Key('openBlindConversation'),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => BlindBondScreen(
                  displayName: widget.displayName,
                  joinedCircles: widget.joinedCircles,
                ),
              ),
            ),
            leading: const CircleAvatar(
              backgroundColor: BondCircleColors.purple,
              child: Icon(Icons.visibility_off_rounded, color: Colors.white),
            ),
            title: const Text(
              'Meet someone in your circle',
              style: TextStyle(fontWeight: FontWeight.w800),
            ),
            subtitle: const Text(
              'Join a circle • Talk anonymously • Decide together',
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
          ),
        ),
        const SizedBox(height: 22),
        Row(
          children: [
            const Expanded(
              child: Text(
                'Matches',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
              ),
            ),
            Text(
              '${_visible.length} conversations',
              style: const TextStyle(color: BondCircleColors.muted),
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (_visible.isEmpty)
          const Padding(
            padding: EdgeInsets.all(32),
            child: Center(child: Text('No conversations match your search.')),
          )
        else
          ..._visible.map(
            (connection) => _ConnectionTile(
              connection: connection,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => ChatScreen(
                    matchName: connection.name,
                    sharedCircle: connection.circle,
                  ),
                ),
              ),
            ),
          ),
      ],
    ),
    bottomNavigationBar: NavigationBar(
      selectedIndex: 2,
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
        } else if (index == 3) {
          Navigator.of(context).pushReplacement(
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
          label: 'Discover',
        ),
        NavigationDestination(
          icon: Icon(Icons.visibility_off_outlined),
          label: 'Blind Bond',
        ),
        NavigationDestination(
          icon: Icon(Icons.chat_bubble_rounded),
          label: 'Chats',
        ),
        NavigationDestination(
          icon: Icon(Icons.person_outline_rounded),
          label: 'Profile',
        ),
      ],
    ),
  );
}

class _ConnectionTile extends StatelessWidget {
  const _ConnectionTile({required this.connection, required this.onTap});
  final _Connection connection;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(bottom: 10),
    child: ListTile(
      key: Key('openChat${connection.name}'),
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
      leading: CircleAvatar(
        backgroundColor: connection.color,
        child: Text(
          connection.name.characters.first,
          style: const TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.w900,
          ),
        ),
      ),
      title: Row(
        children: [
          Expanded(
            child: Text(
              connection.name,
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
          Text(
            connection.time,
            style: const TextStyle(fontSize: 11, color: BondCircleColors.muted),
          ),
        ],
      ),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            connection.circle,
            style: const TextStyle(
              color: BondCircleColors.purple,
              fontSize: 12,
            ),
          ),
          const SizedBox(height: 3),
          Row(
            children: [
              Expanded(
                child: Text(
                  connection.preview,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (connection.unread > 0)
                Container(
                  margin: const EdgeInsets.only(left: 8),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 7,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: BondCircleColors.primary,
                    borderRadius: BorderRadius.circular(99),
                  ),
                  child: Text(
                    '${connection.unread}',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    ),
  );
}

class _Connection {
  const _Connection({
    required this.name,
    required this.circle,
    required this.preview,
    required this.time,
    required this.unread,
    required this.color,
  });
  final String name, circle, preview, time;
  final int unread;
  final Color color;
}
