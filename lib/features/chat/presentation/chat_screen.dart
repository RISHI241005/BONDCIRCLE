import 'package:flutter/material.dart';

import '../../../theme/bondcircle_theme.dart';
import '../../meetup/presentation/meetup_planner_screen.dart';
import '../data/chat_api_service.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({
    super.key,
    required this.matchName,
    required this.sharedCircle,
    this.conversationId,
    this.partnerId,
    this.chatService,
  });

  final String matchName;
  final String sharedCircle;
  final String? conversationId;
  final int? partnerId;
  final ChatApiService? chatService;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _messageController = TextEditingController();
  final _scrollController = ScrollController();
  late final List<_ChatMessage> _messages;
  late final ChatApiService _chatService;

  List<String> _starters = [
    'Favourite café?',
    'Weekend plan?',
    'What are you reading?',
  ];
  String? _lastSuggestionId;
  bool _isLoadingSuggestions = false;

  @override
  void initState() {
    super.initState();
    _chatService = widget.chatService ?? ChatApiService();
    _messages = [
      _ChatMessage(
        text: 'You matched through ${widget.sharedCircle}',
        type: _MessageType.system,
      ),
      const _ChatMessage(
        text: 'Your Vibe Check suggests starting with a relaxed, low-pressure plan.',
        type: _MessageType.system,
      ),
      _ChatMessage(
        text: 'Hey! I liked your answer about a quiet coffee meetup ☕',
        type: _MessageType.received,
        time: '5:31 PM',
      ),
      const _ChatMessage(
        text: 'Same here. A good café and an easy conversation sounds perfect.',
        type: _MessageType.sent,
        time: '5:32 PM',
      ),
    ];

    if (widget.conversationId != null) {
      _loadBackendHistory();
      _fetchAiSuggestions();
    }
  }

  Future<void> _loadBackendHistory() async {
    final convId = widget.conversationId;
    if (convId == null) return;

    final backendMsgs = await _chatService.getMessages(convId);
    if (!mounted || backendMsgs.isEmpty) return;

    setState(() {
      _messages.clear();
      _messages.add(
        _ChatMessage(
          text: 'You matched through ${widget.sharedCircle}',
          type: _MessageType.system,
        ),
      );
      for (final m in backendMsgs) {
        _messages.add(
          _ChatMessage(
            text: m.content,
            type: m.isMine ? _MessageType.sent : _MessageType.received,
            time: m.createdAt != null
                ? '${m.createdAt!.hour % 12 == 0 ? 12 : m.createdAt!.hour % 12}:${m.createdAt!.minute.toString().padLeft(2, '0')} ${m.createdAt!.hour >= 12 ? 'PM' : 'AM'}'
                : 'Now',
          ),
        );
      }
    });
    _scrollToBottom();
  }

  Future<void> _fetchAiSuggestions() async {
    final convId = widget.conversationId;
    if (convId == null || _isLoadingSuggestions) return;

    setState(() => _isLoadingSuggestions = true);
    final response = await _chatService.getReplySuggestions(conversationId: convId, limit: 3);
    if (!mounted) return;

    if (response != null && response.suggestions.isNotEmpty) {
      setState(() {
        _starters = response.suggestions.map((s) => s.text).toList();
        _lastSuggestionId = response.suggestions.first.id;
        _isLoadingSuggestions = false;
      });
    } else {
      setState(() => _isLoadingSuggestions = false);
    }
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  void _sendMessage([String? suppliedText]) {
    final text = (suppliedText ?? _messageController.text).trim();
    if (text.isEmpty) return;

    setState(() {
      _messages.add(
        _ChatMessage(text: text, type: _MessageType.sent, time: 'Now'),
      );
      _messageController.clear();
    });
    _scrollToBottom();

    final convId = widget.conversationId;
    if (convId != null) {
      _chatService.sendMessage(conversationId: convId, content: text);
      if (suppliedText != null && _lastSuggestionId != null) {
        _chatService.recordSuggestionFeedback(
          suggestionId: _lastSuggestionId!,
          conversationId: convId,
          action: 'USED',
          finalMessage: text,
        );
      }
      _fetchAiSuggestions();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 0,
        title: Row(
          children: [
            Container(
              width: 42,
              height: 42,
              alignment: Alignment.center,
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [Color(0xFFE9A8B9), Color(0xFF9E6DD7)],
                ),
                shape: BoxShape.circle,
              ),
              child: Text(
                widget.matchName.characters.first,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            const SizedBox(width: 11),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(widget.matchName),
                    const SizedBox(width: 4),
                    const Icon(
                      Icons.verified_rounded,
                      color: BondCircleColors.primary,
                      size: 17,
                    ),
                  ],
                ),
                const Text(
                  'Online',
                  style: TextStyle(
                    color: Color(0xFF3D9B6A),
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ],
        ),
        actions: [
          IconButton(
            key: const Key('chatSafetyButton'),
            tooltip: 'Safety options',
            onPressed: _showSafetyOptions,
            icon: const Icon(Icons.shield_outlined),
          ),
          const SizedBox(width: 6),
        ],
      ),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
              color: BondCircleColors.lavender,
              child: Row(
                children: [
                  const Icon(
                    Icons.diversity_1_outlined,
                    color: BondCircleColors.purple,
                    size: 18,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Shared circle: ${widget.sharedCircle}',
                      style: const TextStyle(
                        color: BondCircleColors.purple,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  TextButton.icon(
                    key: const Key('planMeetupButton'),
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => MeetupPlannerScreen(
                          matchName: widget.matchName,
                          sharedCircle: widget.sharedCircle,
                        ),
                      ),
                    ),
                    icon: const Icon(Icons.calendar_month_outlined, size: 18),
                    label: const Text('Plan'),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView.builder(
                controller: _scrollController,
                padding: const EdgeInsets.fromLTRB(16, 20, 16, 12),
                itemCount: _messages.length,
                itemBuilder: (context, index) =>
                    _MessageBubble(message: _messages[index]),
              ),
            ),
            _ConversationStarters(
              starters: _starters,
              onSelected: _sendMessage,
            ),
            _Composer(controller: _messageController, onSend: _sendMessage),
          ],
        ),
      ),
    );
  }

  Future<void> _showSafetyOptions() async {
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Safety options',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 10),
              ListTile(
                leading: const Icon(Icons.block_rounded, color: Colors.redAccent),
                title: Text('Block ${widget.matchName}'),
                subtitle: const Text(
                  'They will no longer be able to contact you.',
                ),
                onTap: () async {
                  Navigator.of(context).pop();
                  if (widget.partnerId != null) {
                    await _chatService.blockUser(blockedUserId: widget.partnerId!);
                  }
                  if (!mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Blocked ${widget.matchName}')),
                  );
                  Navigator.of(context).pop();
                },
              ),
              ListTile(
                leading: const Icon(Icons.flag_outlined, color: Colors.orangeAccent),
                title: const Text('Report a concern'),
                subtitle: const Text(
                  'Report harassment, spam, or inappropriate behavior.',
                ),
                onTap: () {
                  Navigator.of(context).pop();
                  _showReportDialog();
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showReportDialog() async {
    String selectedReason = 'HARASSMENT';
    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Text('Report ${widget.matchName}'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Please select the reason for reporting this user:'),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: selectedReason,
                items: const [
                  DropdownMenuItem(value: 'HARASSMENT', child: Text('Harassment or bullying')),
                  DropdownMenuItem(value: 'INAPPROPRIATE_CONTENT', child: Text('Inappropriate content')),
                  DropdownMenuItem(value: 'SPAM_OR_SCAM', child: Text('Spam or scam')),
                  DropdownMenuItem(value: 'FAKE_PROFILE', child: Text('Fake profile')),
                ],
                onChanged: (val) {
                  if (val != null) setDialogState(() => selectedReason = val);
                },
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () async {
                Navigator.of(ctx).pop();
                if (widget.partnerId != null) {
                  await _chatService.reportUser(
                    reportedUserId: widget.partnerId!,
                    reason: selectedReason,
                  );
                }
                if (!mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Report submitted. Thank you for keeping BondCircle safe.'),
                  ),
                );
              },
              child: const Text('Submit Report'),
            ),
          ],
        ),
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message});

  final _ChatMessage message;

  @override
  Widget build(BuildContext context) {
    if (message.type == _MessageType.system) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Text(
          message.text,
          textAlign: TextAlign.center,
          style: const TextStyle(color: BondCircleColors.muted, fontSize: 12.5),
        ),
      );
    }

    final sent = message.type == _MessageType.sent;
    return Align(
      alignment: sent ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 290),
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.fromLTRB(15, 11, 15, 8),
        decoration: BoxDecoration(
          color: sent ? BondCircleColors.primary : Colors.white,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(19),
            topRight: const Radius.circular(19),
            bottomLeft: Radius.circular(sent ? 19 : 5),
            bottomRight: Radius.circular(sent ? 5 : 19),
          ),
          border: sent ? null : Border.all(color: BondCircleColors.border),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              message.text,
              style: TextStyle(
                color: sent ? Colors.white : BondCircleColors.ink,
                height: 1.35,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              message.time ?? '',
              style: TextStyle(
                color: sent ? Colors.white70 : BondCircleColors.muted,
                fontSize: 10.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ConversationStarters extends StatelessWidget {
  const _ConversationStarters({
    required this.onSelected,
    required this.starters,
  });

  final ValueChanged<String> onSelected;
  final List<String> starters;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 46,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        scrollDirection: Axis.horizontal,
        itemCount: starters.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (context, index) => ActionChip(
          key: Key('starter$index'),
          avatar: const Icon(Icons.auto_awesome_rounded, size: 16),
          label: Text(
            starters[index].length > 40
                ? '${starters[index].substring(0, 37)}…'
                : starters[index],
          ),
          onPressed: () => onSelected(starters[index]),
        ),
      ),
    );
  }
}

class _Composer extends StatelessWidget {
  const _Composer({required this.controller, required this.onSend});

  final TextEditingController controller;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 9, 14, 14),
      decoration: const BoxDecoration(
        color: BondCircleColors.background,
        border: Border(top: BorderSide(color: BondCircleColors.border)),
      ),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              key: const Key('chatMessageField'),
              controller: controller,
              textCapitalization: TextCapitalization.sentences,
              maxLines: 4,
              minLines: 1,
              onSubmitted: (_) => onSend(),
              decoration: const InputDecoration(
                hintText: 'Write a message…',
                contentPadding: EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 12,
                ),
              ),
            ),
          ),
          const SizedBox(width: 9),
          IconButton.filled(
            key: const Key('sendChatMessageButton'),
            onPressed: onSend,
            icon: const Icon(Icons.send_rounded),
          ),
        ],
      ),
    );
  }
}

enum _MessageType { sent, received, system }

class _ChatMessage {
  const _ChatMessage({required this.text, required this.type, this.time});

  final String text;
  final _MessageType type;
  final String? time;
}
