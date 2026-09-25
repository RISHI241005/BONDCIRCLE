/**
 * BondCircle - UI Module
 * 
 * Handles all DOM rendering and UI updates.
 * Uses semantic HTML, accessible elements, and professional styling.
 */

const UI = {
    // Show loading state
    showLoading: (operation) => {
        AppState.setLoading(true, operation);
        // Create or update loading overlay
        let loadingEl = document.getElementById("loadingOverlay");
        if (!loadingEl) {
            loadingEl = document.createElement("div");
            loadingEl.id = "loadingOverlay";
            loadingEl.className = "loading-overlay";
            loadingEl.innerHTML = `
                <div class="loading-content">
                    <div class="spinner"></div>
                    <span>${operation || 'Loading...'}</span>
                </div>
            `;
            document.body.appendChild(loadingEl);
        }
        loadingEl.style.display = 'flex';
    },

    // Hide loading state
    hideLoading: () => {
        AppState.setLoading(false);
        const loadingEl = document.getElementById("loadingOverlay");
        if (loadingEl) {
            loadingEl.style.display = 'none';
        }
    },

    // Show error message
    showError: (message) => {
        // Remove any existing error
        const existingError = document.querySelector(".error-message");
        if (existingError) {
            existingError.remove();
        }

        // Create error element
        const errorEl = document.createElement("div");
        errorEl.className = "error-message";
        // Use textContent for XSS safety - message comes from API but sanitized
        const errorText = document.createElement("span");
        errorText.className = "error-text";
        errorText.textContent = message;
        const errorIcon = document.createElement("span");
        errorIcon.className = "error-icon";
        errorIcon.textContent = "⚠️";
        const errorContent = document.createElement("div");
        errorContent.className = "error-content";
        errorContent.appendChild(errorIcon);
        errorContent.appendChild(errorText);
        errorEl.appendChild(errorContent);
        errorEl.style.cssText = `
            background-color: rgba(220, 53, 69, 0.1);
            border: 1px solid #dc3545;
            border-radius: var(--radius-md);
            padding: var(--space-sm) var(--space-md);
            margin: var(--space-md) 0;
            color: var(--color-error);
            display: flex;
            align-items: center;
            gap: var(--space-sm);
        `;

        // Find a suitable container to insert the error
        const container = document.querySelector('.dashboard, .chat-main, .auth-page, .profile-page');
        if (container && !container.querySelector('.error-message')) {
            container.insertBefore(errorEl, container.firstChild);
        }
    },

    // Show empty state
    showEmptyState: (message) => {
        // Remove any existing content area that might have items
        const existingEmpty = document.querySelector(".empty-state");
        if (existingEmpty) {
            existingEmpty.remove();
        }

        const emptyEl = document.createElement("div");
        emptyEl.className = "empty-state";
        // Use textContent for XSS safety
        const emptyIcon = document.createElement("div");
        emptyIcon.className = "empty-icon";
        emptyIcon.textContent = "💬";
        const emptyText = document.createElement("h3");
        emptyText.textContent = message;
        emptyEl.appendChild(emptyIcon);
        emptyEl.appendChild(emptyText);
        emptyEl.style.cssText = `
            text-align: center;
            padding: var(--space-lg) var(--space-md);
            color: var(--color-text-muted);
        `;

        const container = document.querySelector('.dashboard .conversations-list, .chat-messages, .conversation-list');
        if (container) {
            container.appendChild(emptyEl);
        }
    },

    // Render conversation list
    renderConversations: (conversations) => {
        const listEl = document.querySelector('.conversations-list');
        if (!listEl) return;

        // Show loading state initially - will be replaced
        UI.showLoading('Loading conversations');

        // Remove existing empty state
        const existingEmpty = listEl.querySelector('.empty-state');
        if (existingEmpty) {
            existingEmpty.remove();
        }

        if (!conversations || conversations.length === 0) {
            UI.hideLoading();
            UI.showEmptyState("No conversations yet");
            return;
        }

        // Sort conversations by latest activity (updatedAt descending)
        const sortedConversations = [...conversations].sort((a, b) => {
            const aTime = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
            const bTime = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
            return bTime - aTime;
        });

        const html = sortedConversations.map((conv, index) => {
            const unreadCount = AppState.getUnreadCount(conv.conversationId) || 0;
            const onlineStatus = conv.otherUserOnlineStatus || 'OFFLINE';
            const lastMessage = conv.lastMessageContent || 'No messages yet';
            const lastMessageTime = conv.lastMessageAt ? new Date(conv.lastMessageAt).toLocaleTimeString() : 'No messages yet';

            return `
                <div class="conversation-item" data-conversation-id="${conv.conversationId}">
                    <div class="conversation-avatar">
                        <span class="online-indicator ${onlineStatus === 'ONLINE' ? 'online' : 'offline'}"></span>
                        ${conv.otherUserName ? conv.otherUserName.charAt(0) : '?'}
                    </div>
                    <div class="conversation-details">
                        <p class="participant-name">${conv.otherUserName || 'Unknown User'}</p>
                        <p class="participant-phone">${conv.otherUserPhone || ''}</p>
                        <p class="last-message">${lastMessage}</p>
                        <p class="last-message-time">${lastMessageTime}</p>
                    </div>
                    <span class="unread-badge ${unreadCount > 0 ? 'has-unread' : ''}">${unreadCount}</span>
                </div>
            `;
        }).join('');

        UI.hideLoading();
        listEl.innerHTML = html;

        // Add click handlers
        document.querySelectorAll('.conversation-item').forEach(item => {
            item.addEventListener('click', () => {
                const conversationId = item.dataset.conversationId;
                conversation.openConversation(conversationId);
            });
        });
    },

    // Render conversation header
    renderConversationHeader: (conversation) => {
        const headerEl = document.querySelector('.chat-header .conversation-select');
        if (headerEl) {
            // Use createElement + textContent for XSS safety
            const option = document.createElement('option');
            option.value = conversation.conversationId;
            option.textContent = conversation.otherParticipantId ? `User ${conversation.otherParticipantId}` : 'Conversation';
            headerEl.innerHTML = "";
            headerEl.appendChild(option);
        }

        // Update typing indicator visibility
        const typingIndicator = document.getElementById("typingIndicator");
        if (typingIndicator) {
            typingIndicator.style.display = 'none';
        }

        // Update page title or breadcrumb
        const pageTitle = document.querySelector('.page-title');
        if (pageTitle) {
            pageTitle.textContent = `BondCircle - ${conversation.conversationId.substring(0, 8)}...`;
        }
    },

    // Render messages
    renderMessages: (messages) => {
        const messagesEl = document.querySelector('#chatMessages');
        if (!messagesEl) return;

        // Remove existing empty state
        const existingEmpty = messagesEl.querySelector('.empty-state');
        if (existingEmpty) {
            existingEmpty.remove();
        }

        if (!messages || messages.length === 0) {
            UI.showEmptyState("Select a conversation to start chatting");
            return;
        }

        messagesEl.innerHTML = '';

        messages.forEach((msg) => {
            const isOutgoing = msg.senderId === auth.getCurrentUser()?.userId;
            const wrapper = document.createElement('div');
            wrapper.className = `message ${isOutgoing ? 'outgoing' : 'incoming'}`;
            wrapper.setAttribute('data-message-id', msg.id);

            const contentEl = document.createElement('div');
            contentEl.className = 'message-content';
            contentEl.textContent = msg.content;

            const metaEl = document.createElement('div');
            metaEl.className = 'message-meta';
            metaEl.textContent = msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString() : '';

            wrapper.appendChild(contentEl);
            wrapper.appendChild(metaEl);

            if (msg.status) {
                const statusMap = {
                    SENT: "Sent",
                    DELIVERED: "Delivered",
                    READ: "Read",
                    EDITED: "Edited",
                    DELETED: "Deleted"
                };
                const statusEl = document.createElement('span');
                statusEl.className = `message-status status-${msg.status.toLowerCase()}`;
                statusEl.textContent = statusMap[msg.status] || msg.status;
                wrapper.appendChild(statusEl);
            }

            messagesEl.appendChild(wrapper);
        });

        // Scroll to bottom
        messagesEl.scrollTop = messagesEl.scrollHeight;
    },

    // Render profile page
    renderProfile: () => {
        const user = auth.getCurrentUser();
        if (!user) return;

        const profileEl = document.querySelector('.profile-card .profile-body');
        if (!profileEl) return;

        // Clear existing content
        profileEl.innerHTML = "";

        // User ID
        const infoRow1 = document.createElement("div");
        infoRow1.className = "info-row";
        const infoLabel1 = document.createElement("span");
        infoLabel1.className = "info-label";
        infoLabel1.textContent = "User ID";
        const infoValue1 = document.createElement("span");
        infoValue1.className = "info-value";
        infoValue1.textContent = user.userId;
        infoRow1.appendChild(infoLabel1);
        infoRow1.appendChild(infoValue1);
        profileEl.appendChild(infoRow1);

        // Username
        const infoRow2 = document.createElement("div");
        infoRow2.className = "info-row";
        const infoLabel2 = document.createElement("span");
        infoLabel2.className = "info-label";
        infoLabel2.textContent = "Username";
        const infoValue2 = document.createElement("span");
        infoValue2.className = "info-value";
        infoValue2.textContent = user.username;
        infoRow2.appendChild(infoLabel2);
        infoRow2.appendChild(infoValue2);
        profileEl.appendChild(infoRow2);

        // Roles
        const infoRow3 = document.createElement("div");
        infoRow3.className = "info-row";
        const infoLabel3 = document.createElement("span");
        infoLabel3.className = "info-label";
        infoLabel3.textContent = "Roles";
        const infoValue3 = document.createElement("span");
        infoValue3.className = "info-value";
        infoValue3.textContent = user.roles?.join(', ') || 'ROLE_USER';
        infoRow3.appendChild(infoLabel3);
        infoRow3.appendChild(infoValue3);
        profileEl.appendChild(infoRow3);
    },

// Escape HTML to prevent XSS
    escapeHtml: (unsafe) => {
        if (typeof unsafe !== 'string') return unsafe;
        return unsafe
            .replace(/&/g, "&")
            .replace(/</g, "<")
            .replace(/>/g, ">")
            .replace(/"/g, """)
            .replace(/'/g, "&#039;");
    },

    // Update typing indicator
    updateTypingIndicator: (conversationId, typing) => {
        const typingIndicator = document.getElementById("typingIndicator");
        if (!typingIndicator) return;

        if (typing) {
            typingIndicator.style.display = 'flex';
            // Use textContent for XSS safety
            typingIndicator.textContent = "";
            const span = document.createElement("span");
            span.textContent = " is typing...";
            typingIndicator.appendChild(span);
        } else {
            typingIndicator.style.display = 'none';
        }
    },

    // Update connection status indicator
    updateConnectionStatus: (status) => {
        const statusIndicator = document.getElementById("connectionStatus");
        if (!statusIndicator) return;

        const statusMap = {
            CONNECTED: { class: 'connected', text: 'Connected' },
            CONNECTING: { class: 'connecting', text: 'Connecting...' },
            RECONNECTING: { class: 'reconnecting', text: 'Reconnecting...' },
            DISCONNECTED: { class: 'disconnected', text: 'Offline' }
        };

        const statusInfo = statusMap[status];
        if (statusInfo) {
            statusIndicator.className = `status-dot ${statusInfo.class}`;
            statusIndicator.textContent = statusInfo.text;
        }
    }
};

export { UI };