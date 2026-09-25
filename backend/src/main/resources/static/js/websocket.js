/**
 * BondCircle - WebSocket Module
 * 
 * Handles STOMP over WebSocket connection with automatic reconnection,
 * subscription management, and event handling.
 * 
 * Uses the backend's WebSocket configuration:
 * - Endpoint: /ws (SockJS + STOMP)
 * - Authorization: Bearer JWT on CONNECT frame
 * - User destination: /user/queue/messages
 * - App destination: /app/chat.send, /app/chat.typing, etc.
 */

import { AppState } from "./state.js";
import { storage } from "./storage.js";
import { CONFIG } from "./config.js";

let stompClient = null;
let reconnectTimeout = null;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 5;

/**
 * Connect to WebSocket with STOMP.
 * Authenticates using JWT Bearer token.
 * 
 * @returns {Promise<object>} { success, error }
 */
export const connectWebSocket = async () => {
    const token = storage.getToken();

    if (!token) {
        console.error("Cannot connect WebSocket: No JWT token available");
        return { success: false, error: "No authentication token. Please log in." };
    }

    const wsUrl = `${CONFIG.WS_BASE_URL}`;

    try {
        AppState.setConnectionStatus("CONNECTING");

        const socket = new SockJS(wsUrl);
        stompClient = Stomp.over(socket);

        // Configure STOMP settings
        stompClient.debug = (msg) => {
            // Debug logging can be enabled with DEBUG=true
            if (typeof DEBUG !== 'undefined' && DEBUG) {
                console.log(`STOMP: ${msg}`);
            }
        };

        // Connect with JWT in headers
        const connectHeaders = {
            Authorization: `Bearer ${token}`,
            // Additional headers can be added here
        };

        // Wait for connection to complete
        await new Promise((resolve, reject) => {
            stompClient.connect(
                connectHeaders,
                (frame) => {
                    // Connection successful
                    reconnectAttempts = 0; // Reset reconnection counter
                    AppState.setConnectionStatus("CONNECTED");
                    AppState.setWebsocket(stompClient);
                    AppState.setWebsocketConnectedAt(new Date());

                    console.log("WebSocket connected:", frame);

                    // Auto-subscribe to user-specific message queue
                    stompClient.subscribe(
                        "/user/queue/messages",
                        (message) => handleIncomingMessage(message),
                        (error) => {
                            console.error("WebSocket subscription error:", error);
                        }
                    );

                    console.log("Subscribed to /user/queue/messages");
                    resolve();
                },
                (error) => {
                    // Connection failed
                    console.error("WebSocket connection failed:", error);
                    AppState.setConnectionStatus("ERROR");
                    reject(new Error("WebSocket connection failed: " + error.message));
                }
            );
        });

        // Set up reconnect handler
        setupReconnectHandler();

        return { success: true };
    } catch (error) {
        AppState.setConnectionStatus("ERROR");
        console.error("WebSocket connection error:", error);
        return { success: false, error: error.message };
    }
};

/**
 * Disconnect WebSocket.
 */
export const disconnectWebSocket = () => {
    if (stompClient) {
        try {
            stompClient.disconnect();
            console.log("WebSocket disconnected");
        } catch (e) {
            // Ignore
        } finally {
            stompClient = null;
        }
    }
    AppState.setConnectionStatus("DISCONNECTED");
    AppState.setWebsocket(null);
};

/**
 * Send a message via WebSocket.
 * 
 * @param {string} conversationId - UUID of the conversation
 * @param {string} content - Message content
 * @param {object} [options] - Additional options
 * @param {string} [options.clientMessageId] - For idempotency
 * @param {string} [options.replyToMessageId] - Reply to this message
 * @param {string} [options.type] - Message type (default: TEXT)
 * @returns {Promise<object>} { success, error }
 */
export const sendRealtimeMessage = async (conversationId, content, options = {}) => {
    if (!stompClient) {
        return { success: false, error: "WebSocket not connected" };
    }

    const payload = {
        conversationId,
        content,
        ...options,
    };

    try {
        stompClient.send("/app/chat.send", {}, JSON.stringify(payload));
        return { success: true };
    } catch (error) {
        return { success: false, error: error.message };
    }
};

/**
 * Send typing indicator via WebSocket.
 * 
 * @param {string} conversationId - UUID of the conversation
 * @param {boolean} typing - True when typing, false when stopped
 */
export const sendTypingIndicator = (conversationId, typing) => {
    if (!stompClient) return;

    const payload = {
        conversationId,
        typing,
    };

    try {
        stompClient.send("/app/chat.typing", {}, JSON.stringify(payload));
    } catch (error) {
        console.error("Failed to send typing indicator:", error);
    }
};

/**
 * Send delivery receipt acknowledgment.
 * 
 * @param {string} conversationId - UUID of the conversation
 * @param {string} messageId - UUID of the message
 */
export const sendDeliveryAck = (conversationId, messageId) => {
    if (!stompClient) return;

    const payload = {
        conversationId,
        messageId,
    };

    try {
        stompClient.send("/app/chat.delivered", {}, JSON.stringify(payload));
    } catch (error) {
        console.error("Failed to send delivery ack:", error);
    }
};

/**
 * Send read receipt via WebSocket.
 * 
 * @param {string} conversationId - UUID of the conversation
 * @param {string} messageId - UUID of the message
 */
export const sendReadReceipt = (conversationId, messageId) => {
    if (!stompClient) return;

    const payload = {
        conversationId,
        messageId,
    };

    try {
        stompClient.send("/app/chat.read", {}, JSON.stringify(payload));
    } catch (error) {
        console.error("Failed to send read receipt:", error);
    }
};

/**
 * Handle incoming WebSocket messages.
 * 
 * @param {object} message - STOMP message object
 * @param {string} [conversationId] - Optional conversation ID override
 */
const handleIncomingMessage = (message, conversationId) => {
    try {
        const payload = JSON.parse(message.body);
        const eventType = payload.eventType || payload.type;

        // Handle different event types
        switch (eventType) {
            case "NEW_MESSAGE":
                handleNewMessage(payload.data);
                break;
            case "USER_TYPING":
                handleTypingEvent(payload.data);
                break;
            case "MESSAGE_STATUS_UPDATE":
                handleStatusUpdate(payload.data);
                break;
            case "ERROR":
                handleWebSocketError(payload.data);
                break;
            default:
                console.log("Unknown WebSocket event type:", eventType, payload);
        }
    } catch (error) {
        console.error("Failed to parse WebSocket message:", error, message);
    }
};

/**
 * Handle NEW_MESSAGE event.
 * 
 * @param {object} msg - MessageResponse from backend
 */
const handleNewMessage = (msg) => {
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
 * Handle USER_TYPING event.
 * 
 * @param {object} data - { conversationId, userId, typing }
 */
const handleTypingEvent = (data) => {
    const { conversationId, userId, typing } = data;

    // Update UI typing indicator
    // UI layer will handle this
    console.log(`User ${userId} is typing in conversation ${conversationId}: ${typing}`);
};

/**
 * Handle MESSAGE_STATUS_UPDATE event.
 * 
 * @param {object} data - { conversationId, messageId, status, updatedByUserId }
 */
const handleStatusUpdate = (data) => {
    const { conversationId, messageId, status } = data;

    // Update message status in state
    if (AppState.messages[conversationId]) {
        const message = AppState.messages[conversationId].find(m => m.id === messageId);
        if (message) {
            message.status = status;
        }
    }

    // UI layer will re-render status
    console.log(`Message ${messageId} status updated to ${status} in conversation ${conversationId}`);
};

/**
 * Handle ERROR event from WebSocket.
 * 
 * @param {object} data - { errorCode, message }
 */
const handleWebSocketError = (data) => {
    const { errorCode, message } = data;
    console.error(`WebSocket error [${errorCode}]: ${message}`);

    // Show error to user
    // UI layer will handle displaying error messages
};