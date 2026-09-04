/**
 * BondCircle - Lightweight Centralized Application State
 * 
 * Because this is vanilla JavaScript, we avoid heavy state management
 * libraries and keep state in a single, predictable object.
 */

const AppState = {
    // Authentication state
    user: null,          // UserPrincipal { userId, username, roles }
    token: null,         // JWT token string
    isAuthenticated: false,

    // Conversation state
    conversations: [],   // List of conversation summaries
    activeConversation: null,   // Current conversation detail
    activeConversationId: null, // String UUID

    // Message state
    messages: {},        // Map<conversationId, MessageResponse[]>
    messageCursor: {},   // Map<conversationId, { cursor, hasMore, limit }>

    // WebSocket state
    websocket: null,           // StompClient instance
    connectionStatus: "DISCONNECTED", // CONNECTING | CONNECTED | DISCONNECTED | RECONNECTING | ERROR
    websocketConnectedAt: null,

    // UI state
    isLoading: false,
    loadingOperation: null,    // Description of current loading operation
    unreadCounts: {},          // Map<conversationId, number>

    // Reset state to initial values
    reset: () => {
        Object.assign(AppState, {
            user: null,
            token: null,
            isAuthenticated: false,
            conversations: [],
            activeConversation: null,
            activeConversationId: null,
            messages: {},
            messageCursor: {},
            websocket: null,
            connectionStatus: "DISCONNECTED",
            websocketConnectedAt: null,
            isLoading: false,
            loadingOperation: null,
            unreadCounts: {},
        });
    },

    // Initialize from stored state (e.g., on page load)
    initFromStorage: () => {
        const token = storage.getToken();
        const profile = storage.getProfile();

        if (token && profile) {
            AppState.token = token;
            AppState.user = profile;
            AppState.isAuthenticated = true;
        }
    },

    // Update active conversation and load messages
    setActiveConversation: (conversation) => {
        AppState.activeConversation = conversation;
        AppState.activeConversationId = conversation.conversationId;
        // Clear messages for old conversation, keep for new
        if (AppState.activeConversationId) {
            if (!AppState.messages[AppState.activeConversationId]) {
                AppState.messages[AppState.activeConversationId] = [];
            }
        }
    },

    // Add a message to the conversation message list
    addMessage: (conversationId, message) => {
        if (!AppState.messages[conversationId]) {
            AppState.messages[conversationId] = [];
        }
        // Deduplicate by message ID
        const existing = AppState.messages[conversationId].find(m => m.id === message.id);
        if (existing) return; // Already exists

        AppState.messages[conversationId].unshift(message); // Newest first
        // Keep list manageable (last 200 messages max)
        if (AppState.messages[conversationId].length > 200) {
            AppState.messages[conversationId] = AppState.messages[conversationId].slice(0, 200);
        }
    },

    // Set messages for a conversation (from REST API)
    setMessages: (conversationId, messages) => {
        AppState.messages[conversationId] = messages;
    },

    // Set cursor pagination state
    setCursor: (conversationId, cursor, hasMore, limit) => {
        AppState.messageCursor[conversationId] = { cursor, hasMore, limit };
    },

    // Update unread count for a conversation
    setUnreadCount: (conversationId, count) => {
        AppState.unreadCounts[conversationId] = count;
    },

    // Increment unread count
    incrementUnreadCount: (conversationId) => {
        AppState.unreadCounts[conversationId] = (AppState.unreadCounts[conversationId] || 0) + 1;
    },

    // Decrement unread count (when messages are marked read)
    decrementUnreadCount: (conversationId) => {
        const current = AppState.unreadCounts[conversationId] || 0;
        AppState.unreadCounts[conversationId] = Math.max(0, current - 1);
    },

    // WebSocket connection status updates
    setConnectionStatus: (status) => {
        AppState.connectionStatus = status;
    },

    setWebsocket: (client) => {
        AppState.websocket = client;
    },

    // Set loading state
    setLoading: (isLoading, operation) => {
        AppState.isLoading = isLoading;
        AppState.loadingOperation = operation;
    },

    // Set WebSocket client
    setWebSocketClient: (client) => {
        AppState.websocket = client;
    },

    // Add pending notification
    addPendingNotification: (notification) => {
        // Simple in-memory notification tracking
        if (!AppState.pendingNotifications) {
            AppState.pendingNotifications = [];
        }
        AppState.pendingNotifications.push(notification);
    },

    // Remove pending notification
    removePendingNotification: (index) => {
        if (AppState.pendingNotifications && AppState.pendingNotifications.length > index) {
            AppState.pendingNotifications.splice(index, 1);
        }
    },

    // Clear all pending notifications
    clearPendingNotifications: () => {
        if (AppState.pendingNotifications) {
            AppState.pendingNotifications = [];
        }
    },

    // Get unread count for conversation
    getUnreadCount: (conversationId) => {
        return AppState.unreadCounts[conversationId] || 0;
    },
};

export { AppState };