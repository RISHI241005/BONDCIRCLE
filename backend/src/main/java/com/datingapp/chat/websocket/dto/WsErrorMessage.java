package com.datingapp.chat.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for STOMP error frames.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsErrorMessage {

    private final String errorCode;
    private final String message;

    public WsErrorMessage(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }
}
