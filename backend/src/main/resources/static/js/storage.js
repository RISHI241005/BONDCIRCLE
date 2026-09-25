/**
 * BondCircle - Local Storage Utility
 * 
 * Security note: JWT is stored in sessionStorage for the duration of the session.
 * In production with cookie-based auth, this would be handled by the backend.
 * 
 * Do NOT store: MySQL credentials, JWT secret, database configuration.
 */

export const storage = {
    // Storage keys
    SESSION_KEYS = {
        JWT_TOKEN: 'bondcircle_jwt_token',
        USER_PROFILE: 'bondcircle_user_profile',
    },

    // Get JWT token from sessionStorage
    getToken: () => {
        return sessionStorage.getItem(SESSION_KEYS.JWT_TOKEN);
    },

    // Set JWT token in sessionStorage
    setToken: (token) => {
        if (token) {
            sessionStorage.setItem(SESSION_KEYS.JWT_TOKEN, token);
        } else {
            sessionStorage.removeItem(SESSION_KEYS.JWT_TOKEN);
        }
    },

    // Get user profile from sessionStorage
    getProfile: () => {
        const data = sessionStorage.getItem(SESSION_KEYS.USER_PROFILE);
        return data ? JSON.parse(data) : null;
    },

    // Set user profile in sessionStorage
    setProfile: (profile) => {
        if (profile) {
            sessionStorage.setItem(SESSION_KEYS.USER_PROFILE, JSON.stringify(profile));
        } else {
            sessionStorage.removeItem(SESSION_KEYS.USER_PROFILE);
        }
    },

    // Clear all client-side storage
    clearAll: () => {
        sessionStorage.removeItem(SESSION_KEYS.JWT_TOKEN);
        sessionStorage.removeItem(SESSION_KEYS.USER_PROFILE);
    },

    // Check if user is authenticated
    isAuthenticated: () => {
        return !!storage.getToken();
    },

    // Set last activity timestamp
    setLastActivity: () => {
        sessionStorage.setItem('bondcircle_last_activity', new Date().toISOString());
    },

    // Get last activity timestamp
    getLastActivity: () => {
        const data = sessionStorage.getItem('bondcircle_last_activity');
        return data ? new Date(data) : null;
    },
};