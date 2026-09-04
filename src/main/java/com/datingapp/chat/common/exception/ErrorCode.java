package com.datingapp.chat.common.exception;

/**
 * Standard machine-readable error codes across the Chat Service.
 */
public enum ErrorCode {
    // 400 Bad Request
    BAD_REQUEST("INVALID_REQUEST", "The request is malformed or invalid"),
    VALIDATION_FAILED("VALIDATION_FAILED", "Input validation failed"),
    INVALID_CURSOR("INVALID_CURSOR", "Provided pagination cursor is invalid or malformed"),
    INVALID_MESSAGE_TYPE("INVALID_MESSAGE_TYPE", "Message type is not supported"),
    EMPTY_MESSAGE_CONTENT("EMPTY_MESSAGE_CONTENT", "Message content cannot be empty"),
    MESSAGE_TOO_LONG("MESSAGE_TOO_LONG", "Message content exceeds maximum allowed length"),

    // 401 Unauthorized
    UNAUTHORIZED("UNAUTHORIZED", "Authentication credentials are missing or invalid"),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Authentication token has expired"),
    TOKEN_INVALID("TOKEN_INVALID", "Authentication token is signature-invalid or malformed"),

    // 403 Forbidden
    FORBIDDEN("FORBIDDEN", "You do not have permission to perform this action"),
    CHAT_ACCESS_DENIED("CHAT_ACCESS_DENIED", "You are not a participant in this conversation"),
    USER_BLOCKED("USER_BLOCKED", "Cannot send message: A block exists between participants"),
    MATCH_REQUIRED("MATCH_REQUIRED", "Cannot initiate conversation: Users are not matched"),
    NOT_MESSAGE_OWNER("NOT_MESSAGE_OWNER", "You are not authorized to modify or delete this message"),

    // 404 Not Found
    CONVERSATION_NOT_FOUND("CONVERSATION_NOT_FOUND", "The requested conversation was not found"),
    MESSAGE_NOT_FOUND("MESSAGE_NOT_FOUND", "The requested message was not found"),
    PARTICIPANT_NOT_FOUND("PARTICIPANT_NOT_FOUND", "Participant not found in conversation"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "The requested resource was not found"),

    // 409 Conflict
    DUPLICATE_CONVERSATION("DUPLICATE_CONVERSATION", "A conversation already exists between participants"),
    DUPLICATE_MESSAGE("DUPLICATE_MESSAGE", "Duplicate client message ID detected"),
    DUPLICATE_BLOCK("DUPLICATE_BLOCK", "User is already blocked"),

    // 429 Too Many Requests
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Please slow down"),

    // 500 Internal Error
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected server error occurred"),
    DATABASE_ERROR("DATABASE_ERROR", "A database error occurred while processing the request");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
