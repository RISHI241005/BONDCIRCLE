/**
 * BondCircle - Centralized API Client
 * 
 * All REST requests go through this module.
 * Automatically attaches JWT to Authorization header.
 * Handles error responses and request/response logging.
 */

export const api = {
    // Base URL is same-origin - browser sends to same server as frontend
    // All endpoints use /api/v1/... prefix
    
    /**
     * Performs a fetch request with automatic JWT attachment and error handling.
     * 
     * @param {string} endpoint - API endpoint path (e.g., "/chats")
     * @param {string} method - HTTP method (GET, POST, PUT, DELETE)
     * @param {object} [body] - Request body (optional)
     * @returns {Promise<object>} Parsed JSON response
     */
    request: async (endpoint, method, body) => {
        const requestId = `req-${Date.now()}-${Math.random().toString(36).substr(2, 8)}`;
        const url = `${CONFIG.API_PREFIX}${endpoint}`;
        
        const options = {
            method,
            headers: {
                "Content-Type": "application/json",
                // Authorization header attached below if token exists
            },
            body: body ? JSON.stringify(body) : undefined,
        };

        // Attach JWT if available
        const token = storage.getToken();
        if (token) {
            options.headers.Authorization = `Bearer ${token}`;
        }

        try {
            const response = await fetch(url, options);
            const contentType = response.headers.get("content-type");
            
            let data;
            if (contentType && contentType.includes("application/json")) {
                data = await response.json();
            } else {
                data = await response.text();
            }

            if (response.ok) {
                return {
                    success: true,
                    data,
                    requestId,
                    status: response.status,
                    statusText: response.statusText,
                };
            } else {
                // Error response from backend (ApiError format)
                let userMessage = "Something went wrong on the server.";
                let errorCode = "INTERNAL_SERVER_ERROR";

                if (data && data.errorCode) {
                    errorCode = data.errorCode;
                    displayError = data.message || displayError;

                    const errorMessageMap = {
                        UNAUTHORIZED: "Your session has expired. Please log in again.",
                        TOKEN_EXPIRED: "Your session has expired. Please log in again.",
                        TOKEN_INVALID: "Your session is invalid. Please log in again.",
                        FORBIDDEN: "You don't have permission to access this resource.",
                        CHAT_ACCESS_DENIED: "You are not a participant in this conversation.",
                        BAD_REQUEST: "Invalid request. Please check your input.",
                        CONVERSATION_NOT_FOUND: "Conversation not found.",
                        MESSAGE_NOT_FOUND: "Message not found.",
                        RATE_LIMIT_EXCEEDED: "Rate limit exceeded. Please slow down.",
                        VALIDATION_FAILED: "Input validation failed.",
                        RESOURCE_NOT_FOUND: "Resource not found.",
                        UNAUTHENTICATED: "Authentication required. Please log in.",
                    };

                    if (errorMessageMap[errorCode]) {
                        userMessage = errorMessageMap[errorCode];
                    } else {
                        userMessage = data.message || displayError;
                    }

                    // Handle JWT expiry specifically
                    if (errorCode === "TOKEN_EXPIRED" || errorCode === "UNAUTHORIZED") {
                        // Dispatch event so app can handle session expiry
                        window.dispatchEvent(new CustomEvent('sessionexpire', {
                            detail: { error: userMessage, errorCode }
                        }));
                    }
                } else if (data && data.message) {
                    userMessage = data.message;
                } else if (data && data.success === false) {
                    userMessage = data.message || displayError;
                }

                console.error(
                    `API Error [${response.status}] ${endpoint}: ${userMessage}`,
                    { requestId, errorCode, backendData: data }
                );

                return {
                    success: false,
                    error: userMessage,
                    errorCode,
                    requestId,
                    status: response.status,
                    statusText: response.statusText,
                    rawData: data,
                };
            }
        } catch (error) {
            // Network error or other fetch error
            console.error(
                `Network error calling ${endpoint}: ${error.message}`,
                { requestId }
            );

            return {
                success: false,
                error: "Unable to connect to the server. Please check your connection.",
                errorCode: "NETWORK_ERROR",
                requestId,
                status: 0,
                statusText: "Network Error",
            };
        }
    },

    // GET request
    get: (endpoint) => api.request(endpoint, "GET"),

    // POST request
    post: (endpoint, body) => api.request(endpoint, "POST", body),

    // PUT request
    put: (endpoint, body) => api.request(endpoint, "PUT", body),

    // DELETE request
    delete: (endpoint) => api.request(endpoint, "DELETE"),

    // Health check
    health: () => api.get("/health"),

    // Authentication
    login: (phone, password) => api.post("/api/v1/users/login", { phone, password }),

    // Conversations
    getConversations: () => api.get("/chats"),
    getConversation: (conversationId) => api.get(`/chats/${conversationId}`),
    createConversation: (participantId) => api.post("/chats", { participantId }),
    leaveConversation: (conversationId) => api.delete(`/chats/${conversationId}`),
    markMessagesRead: (conversationId, messageId) => api.post(`/chats/${conversationId}/read`, { messageId }),

    // Messages
    getMessages: (conversationId, cursor, limit = 30) => api.get(`/chats/${conversationId}/messages?cursor=${cursor || ""}&limit=${limit}`),
    sendMessage: (conversationId, requestBody) => api.post(`/chats/${conversationId}/messages`, requestBody),
    editMessage: (conversationId, messageId, requestBody) => api.patch(`/chats/${conversationId}/messages/${messageId}`, requestBody),
    deleteMessage: (conversationId, messageId) => api.delete(`/chats/${conversationId}/messages/${messageId}`),

    // Presence
    getUserPresence: (userId) => api.get(`/presence/${userId}`),

    // Reports
    submitReport: (conversationId, reportData) => api.post(`/chats/${conversationId}/reports`, reportData),
};