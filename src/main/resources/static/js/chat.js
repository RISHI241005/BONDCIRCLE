/**
 * BondCircle - Chat Screen Controller
 * 
 * Manages the chat interface including:
 * - Conversation loading and selection
 * - Message sending and receiving via WebSocket
 * - Message status updates (SENT/DELIVERED/READ)
 * - Typing indicators
 * - Connection status display
 * - Auto-scroll with preserve-on-history-load
 */

import { api } from "./api.js";
import { auth } from "./auth.js";
import { AppState } from "./state.js";
import { storage } from "./storage.js";
import { chat as chatModule } from "./chat.js";
import { connectWebSocket, disconnectWebSocket, sendRealtimeMessage, sendTypingIndicator, sendDeliveryAck, sendReadReceipt } from "./websocket.js";

/**
 * Scroll management - preserves position during history loads
 */
const ScrollManager = {
    // Save current scroll position
    save: () => {
        const messagesEl = document.querySelector('#chatMessages');
        if (messagesEl) {
            return messagesEl.scrollTop + messagesEl.clientHeight;
        }
        return null;
    },

    // Restore scroll position
    restore: (position) => {
        const messagesEl = document.querySelector('#chatMessages');
        if (messagesEl && position !== null) {
            messagesEl.scrollTop = position;
        }
    },

    // Scroll to bottom (used after message addition)
    scrollToBottom: () => {
        const messagesEl = document.querySelector('#chatMessages');
        if (messagesEl) {
            messagesEl.scrollTop = messagesEl.scrollHeight;
        }
    }
};

/**
 * Chat module - handles chat screen operations
 */
export const chat = {
    // Initialize chat screen
    init: async (conversationId) => {
        // Check authentication
        if (!auth.isAuthenticated()) {
            window.location.href = "/login.html";
            return;
        }

        // Initialize auth from storage
        auth.init();

        // If conversation ID provided, load it
        if (conversationId) {
            await chat.loadConversation(conversationId);
        } else {
            // Load first conversation or show empty state
            await chat.loadFirstConversation();
        }

        // Set up WebSocket connection
        await connectWebSocket();

        // Set up event listeners
        setupEventListeners();
    },

    // Load a specific conversation
    loadConversation: async (conversationId) => {
        // Save current scroll position before loading
        const savedScroll = ScrollManager.save();

        // Fetch conversation details
        try {
            const convResult = await api.getConversation(conversationId);

            if (convResult.success && convResult.data) {
                AppState.setActiveConversation(convResult.data);
                UI.renderConversationHeader(convResult.data);
                
                // Mark messages as read when opening conversation
                try {
                    await api.markMessagesRead(conversationId, null);
                } catch (e) {
                    // Ignore read receipt errors
                }
            } else {
                console.error("Failed to load conversation:", convResult.error);
                UI.showError(convResult.error);
            }
        } catch (error) {
            console.error("Error loading conversation:", error);
            UI.showError(`Error loading conversation: ${error.message}`);
        }

        // Fetch message history
        try {
            const messagesResult = await api.getMessages(conversationId);

            if (messagesResult.success && messagesResult.data) {
                AppState.setMessages(conversationId, messagesResult.data.messages);
                AppState.setCursor(
                    conversationId,
                    messagesResult.data.nextCursor,
                    messagesResult.data.hasMore,
                    messagesResult.data.limit
                );
                // Restore scroll position after rendering history
                ScrollManager.restore(savedScroll);
                UI.renderMessages(messagesResult.data.messages);
                // Process delivery receipts for SENT messages (recipient now online after reconnect)
                await this.processOfflineDeliveryReceipts(conversationId, messagesResult.data.messages);
                // Scroll to bottom after history load
                ScrollManager.scrollToBottom();
            } else {
                console.error("Failed to load messages:", messagesResult.error);
                UI.showError("Failed to load messages");
            }
        } catch (error) {
            console.error("Error loading messages:", error);
            UI.showError(`Error loading messages: ${error.message}`);
        }
    },

    // Process delivery receipts for messages that are SENT when recipient opens conversation
    // After recipient reconnects and views the conversation, SENT messages transition to DELIVERED
    // if the recipient has an active WebSocket connection.
    processOfflineDeliveryReceipts: async (conversationId, messages) => {
        // Get the current user's ID
        const currentUserId = auth.getCurrentUser()?.userId;
        if (!currentUserId) return;

        // Get the other participant's user ID from the active conversation
        const conversation = AppState.activeConversation;
        if (!conversation) return;

        let otherUserId: number | undefined;
        for (const participant of conversation.getParticipants()) {
            if (participant.getUserId() !== currentUserId) {
                otherUserId = participant.getUserId();
                break;
            }
        }
        if (otherUserId === undefined) return;

        // If WebSocket is connected, the recipient is online
        // Trigger delivery receipt processing for SENT messages
        if (AppState.connectionStatus === "CONNECTED" && AppState.websocket) {
            for (const message of messages) {
                if (message.status === "SENT") {
                    // Send delivery acknowledgment via WebSocket
                    // The backend ReceiptServiceImpl will check presence and transition SENT→DELIVERED
                    sendDeliveryAck(conversationId, message.id);
                }
            }
        }
    },

    // Load the first conversation from the user's list
    loadFirstConversation: async () => {
        try {
            const convResult = await api.getConversations();

            if (convResult.success && convResult.data && convResult.data.length > 0) {
                const firstConv = convResult.data[0];
                await chat.loadConversation(firstConv.conversationId);
            } else {
                UI.showEmptyState("No conversations yet. Start a new conversation!");
            }
        } catch (error) {
            alert(`Error loading conversations: ${error.message}`);
        }
    },

    // Set up event listeners
    setupEventListeners: (conversationId) => {
        // Send message on button click
        const sendBtn = document.getElementById("sendBtn");
        if (sendBtn) {
            sendBtn.addEventListener("click", () => sendMessage());
        }

        // Send message on Enter key
        const messageInput = document.getElementById("messageInput");
        if (messageInput) {
            messageInput.addEventListener("keydown", async (e) => {
                if (e.key === "Enter") {
                    e.preventDefault();
                    // Shift+Enter for newline
                    if (e.shiftKey) {
                        messageInput.value += "\n";
                        return;
                    }
                    sendMessage();
                }
            });
        }

        // Input focus/blur handling - enable/disable send button
        messageInput?.addEventListener("input", () => {
            const sendBtn = document.getElementById("sendBtn");
            if (messageInput.value.trim()) {
                sendBtn?.removeAttribute("disabled");
            } else {
                sendBtn?.disabled = true;
            }
        });
    },

    // Send message via WebSocket or REST
    sendMessage: async () => {
        const messageInput = document.getElementById("messageInput");
        const content = messageInput.value.trim();

        if (!content) return;

        // Clear input
        messageInput.value = "";
        document.getElementById("sendBtn")?.disabled = true;

        // Get active conversation ID
        const conversationId = AppState.activeConversationId;
        if (!conversationId) {
            console.error("No active conversation");
            return;
        }

        // Show typing stopped indicator
        sendTypingIndicator(conversationId, false);

        // Try WebSocket first (real-time)
        if (AppState.websocket && AppState.connectionStatus === "CONNECTED") {
            const wsResult = await sendRealtimeMessage(conversationId, content, {
                clientMessageId: `client-${Date.now()}`
            });

            if (wsResult.success) {
                // Also send via REST for persistence
                try {
                    await api.sendMessage(conversationId, { content });
                } catch (e) {
                    // WebSocket sent, REST failed - still show message as sent
                    console.log("REST send failed, message sent via WebSocket");
                }
            }
        } else {
            // Fallback to REST only
            try {
                await api.sendMessage(conversationId, { content });
            } catch (error) {
                UI.showError(`Failed to send message: ${error.message}`);
                // Reset input
                messageInput.value = content;
                document.getElementById("sendBtn")?.disabled = false;
                return;
            }
        }

        // Clear input after send
        messageInput.value = "";
    },
};

/**
 * Initialize typing indicator timeout - clears typing after user stops typing
 */
export const initTypingTimeout = (conversationId, timeoutMs = 3000) => {
    clearTimeout(AppState.typingTimeouts);
    AppState.typingTimeouts = setTimeout(() => {
        sendTypingIndicator(conversationId, false);
    }, timeoutMs);
};

/**
 * Handle WebSocket connection status changes for UI
 */
export const updateChatUIForConnection = (status) => {
    const sendBtn = document.getElementById("sendBtn");
    if (sendBtn) {
        if (status === "CONNECTED") {
            sendBtn.disabled = false;
        } else {
            sendBtn?.disabled = true;
        }
    }
};

/**
 * Global WebSocket event handling for incoming messages
 * Called from websocket.js handleIncomingMessage
 */
export const handleIncomingMessage = (msg) => {
    const { conversationId, message: receivedMessage } = msg;

    // Add message to state (with deduplication)
    AppState.addMessage(conversationId, receivedMessage);

    // Update unread count if this is not the active conversation
    if (AppState.activeConversationId !== conversationId) {
        AppState.incrementUnreadCount(conversationId);
    }

    // Re-render chat UI if on chat screen
    // UI layer will handle this
    console.log("New message received:", receivedMessage.id, "in conversation:", conversationId);
};

/**
 * Handle typing indicator events
 */
export const handleTypingEvent = (data) => {
    const { conversationId, userId, typing } = data;

    UI.updateTypingIndicator(conversationId, typing);
    console.log(`User ${userId} is typing in conversation ${conversationId}: ${typing}`);
};

/**
 * Handle message status updates from WebSocket
 */
export const handleStatusUpdate = (data) => {
    const { conversationId, messageId, status } = data;

    // Update message status in state
    if (AppState.messages[conversationId]) {
        const message = AppState.messages[conversationId].find(m => m.id === messageId);
        if (message) {
            message.status = status;
        }
    }

    // Re-render message status in UI
    UI.renderMessages(AppState.messages[conversationId] || []);
    console.log(`Message ${messageId} status updated to ${status} in conversation ${conversationId}`);
};

/**
 * Handle read receipt events
 */
export const handleReadReceipt = (data) => {
    const { conversationId, messageId } = data;

    // Update message status to READ in state
    if (AppState.messages[conversationId]) {
        const message = AppState.messages[conversationId].find(m => m.id === messageId);
        if (message) {
            message.status = "READ";
        }
    }

    // Re-render message status in UI
    UI.renderMessages(AppState.messages[conversationId] || []);
    console.log(`Read receipt for message ${messageId} in conversation ${conversationId}`);
};