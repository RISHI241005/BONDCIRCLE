/**
 * BondCircle - Profile Module
 * 
 * Manages the profile page including:
 * - User information display
 * - Logout functionality
 */

import { api } from "./api.js";
import { auth } from "./auth.js";
import { UI } from "./ui.js";

export const profile = {
    // Initialize profile page
    init: () => {
        // Check authentication
        if (!auth.isAuthenticated()) {
            window.location.href = "/login.html";
            return;
        }

        // Render user profile
        UI.renderProfile();

        // Set up logout handler
        const logoutBtn = document.getElementById("logoutBtn");
        if (logoutBtn) {
            logoutBtn.addEventListener("click", () => {
                auth.logout();
                // Navigate to login after logout
                setTimeout(() => {
                    window.location.href = "/login.html";
                }, 500);
            });
        }
    },

    // Get current user
    getCurrentUser: () => auth.getCurrentUser(),

    // Refresh profile data
    refresh: async () => {
        // Profile doesn't typically need refresh from API
        // Could add presence check or update logic here
    },
};