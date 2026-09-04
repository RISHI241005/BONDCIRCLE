/**
 * BondCircle - Conversations Module
 * 
 * Manages the conversation list including:
 * - Loading conversations from backend
 * - Rendering conversation items
 * - Handling conversation selection
 * - Unread count management
 */

import { api } from "./api.js";
import { auth } from "./auth.js";
import { AppState } from "./state.js";
import { UI } from "./ui.js";

export const conversations = {
    // Load conversations from backend
    loadConversations: async () => {
        try {
            const result = await api.getConversations();

            if (result.success && result.data) {
                AppState.conversations = result.data;
                UI.renderConversations(result.data);
                return true;
            } else {
                UI.showError(result.error || "Failed to load conversations");
                return false;
            }
        } catch (error) {
            UI.showError(`Error loading conversations: ${error.message}`);
            return false;
        }
    },

    // Get active conversation
    getActiveConversation: () => AppState.activeConversation,

    // Set active conversation
    setActiveConversation: (conversation) => {
        AppState.setActiveConversation(conversation);
    },

    // Get unread count for a conversation
    getUnreadCount: (conversationId) => AppState.getUnreadCount(conversationId),

    // Increment unread count
    incrementUnread: (conversationId) => {
        AppState.incrementUnreadCount(conversationId);
    },

    // Decrement unread count
    decrementUnread: (conversationId) => {
        AppState.decrementUnreadCount(conversationId);
    },

    // Open a conversation
    openConversation: async (conversationId) => {
        // Mark messages as read
        try {
            await api.markMessagesRead(conversationId, null);
        } catch (e) {
            // Ignore read receipt errors
        }

        // Fetch conversation details
        try {
            const result = await api.getConversation(conversationId);

            if (result.success && result.data) {
                AppState.setActiveConversation(result.data);
                UI.renderConversationHeader(result.data);
                return result.data;
            } else {
                UI.showError(result.error || "Failed to load conversation");
                return null;
            }
        } catch (error) {
            alert(`Error loading conversation: ${error.message}`);
            return null;
        }
    },
};