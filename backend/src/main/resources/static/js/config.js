/**
 * BondCircle - Centralized Configuration
 * 
 * API_BASE_URL and WS_BASE_URL are injected via environment variables
 * at deployment time. Local defaults are provided for development.
 */

export const CONFIG = {
    // API base URL - same-origin by default, overridden by env var in production
    API_BASE_URL: typeof API_BASE_URL !== 'undefined' ? API_BASE_URL : '/api/v1',
    
    // WebSocket base URL - same-origin ws:// by default, overridden by env var in production
    WS_BASE_URL: typeof WS_BASE_URL !== 'undefined' ? WS_BASE_URL : 'ws://localhost:8080/ws',
    
    // Development test auth flag - enabled via env var BOND_CIRCLE_TEST_AUTH_ENABLED=true
    TEST_AUTH_ENABLED: typeof BOND_CIRCLE_TEST_AUTH_ENABLED !== 'undefined',
    
    // API prefix for constructing full endpoint paths
    API_PREFIX: '/api/v1',
    
    // WebSocket endpoint path
    WS_ENDPOINT: '/ws',
};

export const API_PREFIX = CONFIG.API_PREFIX;
export const WS_BASE_URL = CONFIG.WS_BASE_URL;
export const API_BASE_URL = CONFIG.API_BASE_URL;
export const TEST_AUTH_ENABLED = CONFIG.TEST_AUTH_ENABLED;