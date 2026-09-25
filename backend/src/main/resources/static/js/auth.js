/**
 * BondCircle - Authentication Module
 * 
 * Handles JWT-based authentication using the backend's real login API
 * and provides login/logout/registration functionality.
 */

import { storage } from "./storage.js";
import { AppState } from "./state.js";
import { connectWebSocket, disconnectWebSocket } from "./websocket.js";

export const auth = {
    // Login as a development test user
    // In production, this would use a real login API
    loginAsTestUser: async (userId) => {
        try {
            const result = await api.post("/test/auth/token", { userId, roles: ["ROLE_USER"] });

            if (result.success && result.data && result.data.token) {
                // Store JWT token and user profile
                storage.setToken(result.data.token);
                storage.setProfile({
                    userId: userId,
                    username: `User ${userId}`,
                    roles: ["ROLE_USER"],
                });

                AppState.token = result.data.token;
                AppState.user = {
                    userId,
                    username: `User ${userId}`,
                    roles: ["ROLE_USER"],
                };
                AppState.isAuthenticated = true;

                return {
                    success: true,
                    token: result.data.token,
                    user: AppState.user,
                };
            } else {
                return {
                    success: false,
                    error: result.error || "Login failed",
                };
            }
        } catch (error) {
            return {
                success: false,
                error: "Unable to connect to authentication server.",
            };
        }
    },

    // Login as specific test users
    loginAsUserA: async () => auth.loginAsTestUser(101),
    loginAsUserB: async () => auth.loginAsTestUser(202),
    loginAsUserC: async () => auth.loginAsTestUser(303),

    // Register a new user
    register: async (registerData) => {
        try {
            const result = await api.post("/api/v1/users/register", registerData);

            if (result.success && result.data && result.data.userId) {
                // Store JWT token and user profile
                storage.setToken(result.data.token);
                storage.setProfile({
                    userId: result.data.userId,
                    username: result.data.username,
                    roles: ["ROLE_USER"],
                });

                AppState.token = result.data.token;
                AppState.user = {
                    userId: result.data.userId,
                    username: result.data.username,
                    roles: ["ROLE_USER"],
                };
                AppState.isAuthenticated = true;

                return {
                    success: true,
                    token: result.data.token,
                    user: AppState.user,
                    userId: result.data.userId,
                };
            } else {
                return {
                    success: false,
                    error: result.error || "Registration failed",
                };
            }
        } catch (error) {
            return {
                success: false,
                error: "Unable to connect to registration server.",
            };
        }
    },

    // Real login with phone + password
    login: async (phone, password) => {
        try {
            const result = await api.post("/api/v1/users/login", { phone, password });

            if (result.success && result.data && result.data.token) {
                // Store JWT token and user profile
                storage.setToken(result.data.token);
                storage.setProfile({
                    userId: result.data.userId,
                    username: result.data.username,
                    roles: ["ROLE_USER"],
                });

                AppState.token = result.data.token;
                AppState.user = {
                    userId: result.data.userId,
                    username: result.data.username,
                    roles: ["ROLE_USER"],
                };
                AppState.isAuthenticated = true;

                return {
                    success: true,
                    token: result.data.token,
                    user: AppState.user,
                    userId: result.data.userId,
                };
            } else {
                return {
                    success: false,
                    error: result.error || "Login failed",
                };
            }
        } catch (error) {
            return {
                success: false,
                error: "Unable to connect to authentication server.",
            };
        }
    },

    // Get current user
    getCurrentUser: () => AppState.user,

    // Check if user is authenticated
    isAuthenticated: () => AppState.isAuthenticated,

    // Logout
    logout: () => {
        // Clear WebSocket subscriptions and close connection
        if (AppState.websocket) {
            try {
                AppState.websocket.disconnect();
            } catch (e) {
                // Ignore disconnect errors
            }
            AppState.websocket = null;
        }
        AppState.setConnectionStatus("DISCONNECTED");
        AppState.reset();

        // Clear storage
        storage.clearAll();

        // Signal to reload or navigate to login
        window.dispatchEvent(new Event('userlogout'));
    },

    // Initialize auth from stored state
    init: () => {
        const token = storage.getToken();
        const profile = storage.getProfile();

        if (token && profile) {
            AppState.token = token;
            AppState.user = profile;
            AppState.isAuthenticated = true;

            // Check if token is still valid
            if (!auth.validateCurrentToken()) {
                // Token is invalid/expired - clear state and redirect
                auth.logout();
                window.location.href = "/login.html";
            }
        } else {
            // No stored auth state - ensure we're on login page
            if (window.location.pathname !== "/login.html" && window.location.pathname !== "/") {
                window.location.href = "/login.html";
            }
        }
    },

    // Validate the current stored token
    validateCurrentToken: () => {
        const token = storage.getToken();
        if (!token) return false;

        // Use api health check or a simple validation
        // Here we just check if token has minimal structure
        try {
            // Decode JWT payload to check it's not completely broken
            const payload = JSON.parse(atob(token.split('.')[1]));
            return payload && payload.sub;
        } catch (e) {
            return false;
        }
    },
};

export default auth;