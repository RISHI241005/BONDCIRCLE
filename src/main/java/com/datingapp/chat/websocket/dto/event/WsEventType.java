package com.datingapp.chat.websocket.dto.event;

/**
 * Event types dispatched to client WebSocket subscriptions.
 */
public enum WsEventType {
    NEW_MESSAGE,
    USER_TYPING,
    MESSAGE_STATUS_UPDATE,
    PRESENCE_UPDATE,
    ERROR
}
