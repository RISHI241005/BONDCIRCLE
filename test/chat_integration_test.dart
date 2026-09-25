import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:bondcircle/features/chat/data/chat_api_service.dart';
import 'package:bondcircle/features/chat/presentation/chat_screen.dart';

class MockChatApiService extends ChatApiService {
  bool sendMessageCalled = false;
  String? lastSentContent;
  bool blockUserCalled = false;
  bool reportUserCalled = false;
  String? lastReportReason;

  @override
  Future<List<ChatMessageModel>> getMessages(String conversationId) async {
    return [
      ChatMessageModel(
        id: 'msg-1',
        conversationId: conversationId,
        senderId: 202,
        content: 'Hey from backend!',
        isMine: false,
        createdAt: DateTime.now(),
      ),
      ChatMessageModel(
        id: 'msg-2',
        conversationId: conversationId,
        senderId: 101,
        content: 'Hi! Glad to connect.',
        isMine: true,
        createdAt: DateTime.now(),
      ),
    ];
  }

  @override
  Future<ChatMessageModel?> sendMessage({
    required String conversationId,
    required String content,
    String? clientMessageId,
  }) async {
    sendMessageCalled = true;
    lastSentContent = content;
    return ChatMessageModel(
      id: 'msg-new',
      conversationId: conversationId,
      senderId: 101,
      content: content,
      isMine: true,
      createdAt: DateTime.now(),
    );
  }

  @override
  Future<ReplyCoachResponse?> getReplySuggestions({
    required String conversationId,
    int limit = 3,
  }) async {
    return const ReplyCoachResponse(
      suggestions: [
        ReplyCoachSuggestion(
          id: 'sug-1',
          text: 'What kind of music are you into?',
          topic: 'Music',
        ),
        ReplyCoachSuggestion(
          id: 'sug-2',
          text: 'Have you been to any fun gigs recently?',
          topic: 'Concerts',
        ),
      ],
    );
  }

  @override
  Future<bool> blockUser({required int blockedUserId}) async {
    blockUserCalled = true;
    return true;
  }

  @override
  Future<bool> reportUser({
    required int reportedUserId,
    required String reason,
    String? details,
  }) async {
    reportUserCalled = true;
    lastReportReason = reason;
    return true;
  }
}

void main() {
  group('BondCircle Chat & AI Reply Coach Integration Tests', () {
    testWidgets('loads messages and AI suggestions from ChatApiService', (tester) async {
      final mockService = MockChatApiService();

      await tester.pumpWidget(
        MaterialApp(
          home: ChatScreen(
            matchName: 'Aarohi',
            sharedCircle: 'Coffee Explorers',
            conversationId: 'test-conv-123',
            partnerId: 202,
            chatService: mockService,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Verify backend messages loaded
      expect(find.text('Hey from backend!'), findsOneWidget);
      expect(find.text('Hi! Glad to connect.'), findsOneWidget);

      // Verify AI suggestions displayed in chips
      expect(find.text('What kind of music are you into?'), findsOneWidget);
      expect(find.text('Have you been to any fun gigs recently?'), findsOneWidget);

      // Tap on an AI suggestion chip to send it
      await tester.tap(find.text('What kind of music are you into?'));
      await tester.pumpAndSettle();

      expect(mockService.sendMessageCalled, isTrue);
      expect(mockService.lastSentContent, 'What kind of music are you into?');
      expect(find.text('What kind of music are you into?'), findsWidgets);
    });

    testWidgets('sends manual message through ChatApiService', (tester) async {
      final mockService = MockChatApiService();

      await tester.pumpWidget(
        MaterialApp(
          home: ChatScreen(
            matchName: 'Aarohi',
            sharedCircle: 'Coffee Explorers',
            conversationId: 'test-conv-123',
            partnerId: 202,
            chatService: mockService,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('chatMessageField')), 'See you tomorrow!');
      await tester.tap(find.byKey(const Key('sendChatMessageButton')));
      await tester.pumpAndSettle();

      expect(mockService.sendMessageCalled, isTrue);
      expect(mockService.lastSentContent, 'See you tomorrow!');
      expect(find.text('See you tomorrow!'), findsOneWidget);
    });

    testWidgets('triggers user report flow via safety options', (tester) async {
      final mockService = MockChatApiService();

      await tester.pumpWidget(
        MaterialApp(
          home: ChatScreen(
            matchName: 'Aarohi',
            sharedCircle: 'Coffee Explorers',
            conversationId: 'test-conv-123',
            partnerId: 202,
            chatService: mockService,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Open safety options
      await tester.tap(find.byKey(const Key('chatSafetyButton')));
      await tester.pumpAndSettle();

      // Tap Report a concern
      await tester.tap(find.text('Report a concern'));
      await tester.pumpAndSettle();

      // Dialog opens
      expect(find.text('Report Aarohi'), findsOneWidget);
      expect(find.text('Submit Report'), findsOneWidget);

      // Submit report
      await tester.tap(find.text('Submit Report'));
      await tester.pumpAndSettle();

      expect(mockService.reportUserCalled, isTrue);
      expect(mockService.lastReportReason, 'HARASSMENT');
      expect(find.text('Report submitted. Thank you for keeping BondCircle safe.'), findsOneWidget);
    });

    testWidgets('triggers block user flow via safety options', (tester) async {
      final mockService = MockChatApiService();

      await tester.pumpWidget(
        MaterialApp(
          home: ChatScreen(
            matchName: 'Aarohi',
            sharedCircle: 'Coffee Explorers',
            conversationId: 'test-conv-123',
            partnerId: 202,
            chatService: mockService,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Open safety options
      await tester.tap(find.byKey(const Key('chatSafetyButton')));
      await tester.pumpAndSettle();

      // Tap Block Aarohi
      await tester.tap(find.text('Block Aarohi'));
      await tester.pumpAndSettle();

      expect(mockService.blockUserCalled, isTrue);
    });
  });
}
