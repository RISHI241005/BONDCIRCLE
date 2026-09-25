package com.datingapp.chat.websocket.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard generic event wrapper for outbound WebSocket messages.
 *
 * @param <T> Payload data type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsEvent<T> {

    private final WsEventType eventType;
    private final T data;
    private final Instant timestamp;

    @com.fasterxml.jackson.annotation.JsonCreator
    public WsEvent(
            @com.fasterxml.jackson.annotation.JsonProperty("eventType") WsEventType eventType,
            @com.fasterxml.jackson.annotation.JsonProperty("data") T data,
            @com.fasterxml.jackson.annotation.JsonProperty("timestamp") Instant timestamp) {
        this.eventType = eventType;
        this.data = data;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public WsEvent(WsEventType eventType, T data) {
        this(eventType, data, Instant.now());
    }

    public static <T> WsEvent<T> of(WsEventType eventType, T data) {
        return new WsEvent<>(eventType, data);
    }

    public static WsEvent<Map<String, Object>> typing(String conversationId, Long userId, boolean typing) {
        Map<String, Object> payload = Map.of(
                "conversationId", conversationId,
                "userId", userId,
                "typing", typing
        );
        return new WsEvent<>(WsEventType.USER_TYPING, payload);
    }

    public static WsEvent<Map<String, Object>> statusUpdate(
            String conversationId, String messageId, String status, Long updatedByUserId) {
        Map<String, Object> payload = Map.of(
                "conversationId", conversationId,
                "messageId", messageId,
                "status", status,
                "updatedByUserId", updatedByUserId != null ? updatedByUserId : 0L
        );
        return new WsEvent<>(WsEventType.MESSAGE_STATUS_UPDATE, payload);
    }

    public static WsEvent<Map<String, Object>> error(String errorCode, String message) {
        Map<String, Object> payload = Map.of(
                "errorCode", errorCode,
                "message", message
        );
        return new WsEvent<>(WsEventType.ERROR, payload);
    }

    public WsEventType getEventType() {
        return eventType;
    }

    public T getData() {
        return data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
